package com.tools.txt2docx.converter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EpubToDocxConverter {

    private static final String CONTAINER_PATH = "META-INF/container.xml";

    // DocumentBuilder is not thread-safe, but we never share an instance across threads;
    // each EPUB is parsed on a single worker thread inside BatchProcessor.
    private static final ThreadLocal<DocumentBuilder> XML_BUILDER = ThreadLocal.withInitial(() -> {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder;
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("无法初始化 XML 解析器", ex);
        }
    });

    private static void trySetAttribute(DocumentBuilderFactory factory, String name, String value) {
        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern ATTR_PATTERN =
            Pattern.compile("(?is)([\\w:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern HTML_STRIP_NOISE =
            Pattern.compile("(?is)<(script|style|head)\\b[^>]*>.*?</\\1>");
    private static final Pattern HTML_BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_BLOCK_TAG = Pattern.compile(
            "(?i)</?(address|article|aside|blockquote|caption|dd|div|dl|dt|figcaption|figure|"
                    + "footer|form|h[1-6]|header|hr|li|main|nav|ol|p|pre|section|table|tbody|td|"
                    + "tfoot|th|thead|tr|ul)\\b[^>]*>");
    private static final Pattern HTML_ANY_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern HTML_HSPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern HTML_LINE_SPLIT = Pattern.compile("\\R+");

    private final ConversionOptions options;

    public EpubToDocxConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputEpub, Path outputDocx) throws IOException {
        List<DocxContentBlock> blocks = readEpubBlocks(inputEpub);
        new DocxDocumentWriter(options).writeBlocks(blocks, outputDocx);
    }

    private List<DocxContentBlock> readEpubBlocks(Path inputEpub) throws IOException {
        try (ZipFile zip = new ZipFile(inputEpub.toFile())) {
            String opfPath = readPackagePath(zip);
            List<ManifestItem> spine = readSpine(zip, opfPath);
            List<DocxContentBlock> blocks = new ArrayList<>();
            for (ManifestItem item : spine) {
                if (!isReadableContent(item)) {
                    continue;
                }
                byte[] content = readEntry(zip, item.path());
                List<DocxContentBlock> contentBlocks = extractContentBlocks(content, item.path(), zip);
                if (!blocks.isEmpty() && !contentBlocks.isEmpty()) {
                    blocks.add(new DocxTextBlock(""));
                }
                blocks.addAll(contentBlocks);
            }
            return blocks;
        }
    }

    private String readPackagePath(ZipFile zip) throws IOException {
        byte[] container = readEntry(zip, CONTAINER_PATH);
        Document doc = parseXml(container, CONTAINER_PATH);
        for (Element rootfile : elements(doc, "rootfile")) {
            String fullPath = rootfile.getAttribute("full-path");
            if (!fullPath.isBlank()) {
                return normalizeZipPath(percentDecode(fullPath));
            }
        }
        throw new IOException("EPUB container.xml 未声明 OPF 文件路径");
    }

    private List<ManifestItem> readSpine(ZipFile zip, String opfPath) throws IOException {
        Document doc = parseXml(readEntry(zip, opfPath), opfPath);
        Map<String, ManifestItem> manifest = new HashMap<>();
        for (Element item : elements(doc, "item")) {
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id.isBlank() || href.isBlank()) {
                continue;
            }
            manifest.put(id, new ManifestItem(
                    resolveZipPath(opfPath, href),
                    item.getAttribute("media-type")
            ));
        }

        List<ManifestItem> spine = new ArrayList<>();
        for (Element itemref : elements(doc, "itemref")) {
            if ("no".equalsIgnoreCase(itemref.getAttribute("linear"))) {
                continue;
            }
            String idref = itemref.getAttribute("idref");
            ManifestItem item = manifest.get(idref);
            if (item == null) {
                throw new IOException("EPUB spine 引用了不存在的 manifest item: " + idref);
            }
            spine.add(item);
        }
        if (spine.isEmpty()) {
            throw new IOException("EPUB 未声明可转换的正文 spine");
        }
        return spine;
    }

    private static boolean isReadableContent(ManifestItem item) {
        String mediaType = item.mediaType().toLowerCase(Locale.ROOT);
        String path = item.path().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/xhtml+xml")
                || mediaType.equals("text/html")
                || path.endsWith(".xhtml")
                || path.endsWith(".html")
                || path.endsWith(".htm");
    }

    private static List<DocxContentBlock> extractContentBlocks(byte[] content, String path, ZipFile zip) {
        try {
            return extractXhtmlBlocks(parseXml(content, path), path, zip);
        } catch (IOException | RuntimeException ignored) {
            // DOM traversal in extractXhtmlBlocks can throw RuntimeExceptions on dirty input
            // (e.g. unexpected null/typed nodes). Falling back to the regex-based HTML pass
            // keeps a single bad chapter from failing the whole EPUB.
            return extractHtmlBlocks(content, path, zip);
        }
    }

    private static List<DocxContentBlock> extractXhtmlBlocks(Document doc, String path, ZipFile zip) {
        List<DocxContentBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Element body = firstElement(doc, "body");
        appendNode(body == null ? doc.getDocumentElement() : body, blocks, current, path, zip);
        flushTextBlock(blocks, current);
        return blocks;
    }

    private static void appendNode(Node node,
                                   List<DocxContentBlock> blocks,
                                   StringBuilder current,
                                   String basePath,
                                   ZipFile zip) {
        if (node == null) {
            return;
        }
        switch (node.getNodeType()) {
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> appendText(current, node.getNodeValue());
            case Node.ELEMENT_NODE -> appendElement((Element) node, blocks, current, basePath, zip);
            default -> {
            }
        }
    }

    private static void appendElement(Element element,
                                      List<DocxContentBlock> blocks,
                                      StringBuilder current,
                                      String basePath,
                                      ZipFile zip) {
        String name = localName(element);
        if (isIgnoredElement(name)) {
            return;
        }
        if ("br".equals(name)) {
            flushTextBlock(blocks, current);
            return;
        }
        if ("img".equals(name)) {
            appendImage(element.getAttribute("src"), element.getAttribute("alt"), blocks, current, basePath, zip);
            return;
        }
        if ("image".equals(name)) {
            String href = element.getAttribute("href");
            if (href.isBlank()) {
                href = element.getAttribute("xlink:href");
            }
            appendImage(href, element.getAttribute("alt"), blocks, current, basePath, zip);
            return;
        }

        boolean block = isBlockElement(name);
        if (block) {
            flushTextBlock(blocks, current);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendNode(children.item(i), blocks, current, basePath, zip);
        }
        if (block) {
            flushTextBlock(blocks, current);
        }
    }

    private static void appendImage(String src,
                                    String alt,
                                    List<DocxContentBlock> blocks,
                                    StringBuilder current,
                                    String basePath,
                                    ZipFile zip) {
        flushTextBlock(blocks, current);
        ImageResource image = readImageResource(zip, basePath, src);
        if (image == null) {
            if (alt != null && !alt.isBlank()) {
                blocks.add(new DocxTextBlock(alt));
            }
            return;
        }
        blocks.add(new DocxImageBlock(image.data(), image.fileName(), image.pictureType(), alt));
    }

    private static boolean isIgnoredElement(String name) {
        return switch (name) {
            case "head", "script", "style", "metadata", "meta", "link", "title" -> true;
            default -> false;
        };
    }

    private static boolean isBlockElement(String name) {
        return switch (name) {
            case "address", "article", "aside", "blockquote", "body", "caption", "dd", "div",
                 "dl", "dt", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
                 "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p",
                 "pre", "section", "table", "tbody", "td", "tfoot", "th", "thead", "tr",
                 "ul" -> true;
            default -> false;
        };
    }

    private static void appendText(StringBuilder current, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace('\u00A0', ' ').replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return;
        }
        if (current.isEmpty()) {
            normalized = normalized.stripLeading();
        }
        current.append(normalized);
    }

    private static void flushTextBlock(List<DocxContentBlock> blocks, StringBuilder current) {
        String line = current.toString().strip();
        if (!line.isEmpty()) {
            blocks.add(new DocxTextBlock(line));
        }
        current.setLength(0);
    }

    private static List<DocxContentBlock> extractHtmlBlocks(byte[] content, String path, ZipFile zip) {
        String html = new String(content, detectDeclaredCharset(content));
        List<DocxContentBlock> blocks = new ArrayList<>();
        Matcher matcher = IMG_TAG.matcher(html);
        int start = 0;
        while (matcher.find()) {
            appendHtmlTextBlocks(html.substring(start, matcher.start()), blocks);
            Map<String, String> attrs = parseHtmlAttributes(matcher.group(1));
            String src = attrs.getOrDefault("src", "");
            String alt = attrs.getOrDefault("alt", "");
            appendImage(src, alt, blocks, new StringBuilder(), path, zip);
            start = matcher.end();
        }
        appendHtmlTextBlocks(html.substring(start), blocks);
        return blocks;
    }

    private static void appendHtmlTextBlocks(String html, List<DocxContentBlock> blocks) {
        html = HTML_STRIP_NOISE.matcher(html).replaceAll("\n");
        html = HTML_BR.matcher(html).replaceAll("\n");
        html = HTML_BLOCK_TAG.matcher(html).replaceAll("\n");
        html = HTML_ANY_TAG.matcher(html).replaceAll(" ");
        html = decodeHtmlEntities(html).replace('\u00A0', ' ');

        for (String raw : HTML_LINE_SPLIT.split(html)) {
            String line = HTML_HSPACE.matcher(raw).replaceAll(" ").strip();
            if (!line.isEmpty()) {
                blocks.add(new DocxTextBlock(line));
            }
        }
    }

    private static Map<String, String> parseHtmlAttributes(String rawAttributes) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(rawAttributes);
        while (matcher.find()) {
            String value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            if (value == null) value = matcher.group(5);
            attrs.put(matcher.group(1).toLowerCase(Locale.ROOT), decodeHtmlEntities(value == null ? "" : value));
        }
        return attrs;
    }

    private static ImageResource readImageResource(ZipFile zip, String basePath, String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String cleaned = src.strip();
        if (cleaned.regionMatches(true, 0, "data:", 0, 5)) {
            return readDataUriImage(cleaned);
        }
        if (cleaned.regionMatches(true, 0, "http://", 0, 7)
                || cleaned.regionMatches(true, 0, "https://", 0, 8)) {
            return null;
        }

        String path = resolveZipPath(basePath, cleaned);
        try {
            byte[] data = readEntry(zip, path);
            int pictureType = pictureTypeFor(path, "", data);
            if (pictureType < 0) {
                return null;
            }
            return new ImageResource(data, fileNameFor(path), pictureType);
        } catch (IOException ex) {
            return null;
        }
    }

    private static ImageResource readDataUriImage(String dataUri) {
        int comma = dataUri.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String metadata = dataUri.substring(5, comma);
        String payload = dataUri.substring(comma + 1);
        String mediaType = metadata.split(";", 2)[0].toLowerCase(Locale.ROOT);
        boolean base64 = metadata.toLowerCase(Locale.ROOT).contains(";base64");
        try {
            byte[] data = base64
                    ? Base64.getDecoder().decode(payload.replaceAll("\\s+", ""))
                    : percentDecode(payload).getBytes(StandardCharsets.ISO_8859_1);
            int pictureType = pictureTypeFor("", mediaType, data);
            if (pictureType < 0) {
                return null;
            }
            String ext = extensionForPictureType(pictureType);
            return new ImageResource(data, "embedded-image." + ext, pictureType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int pictureTypeFor(String path, String mediaType, byte[] data) {
        String type = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/jpeg", "image/jpg" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
            case "image/png" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
            case "image/gif" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF;
            case "image/bmp", "image/x-ms-bmp" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_BMP;
            case "image/tiff" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_TIFF;
            default -> pictureTypeForExtensionOrHeader(path, data);
        };
    }

    private static int pictureTypeForExtensionOrHeader(String path, byte[] data) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF;
        }
        if (lower.endsWith(".bmp") || lower.endsWith(".dib")) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_BMP;
        }
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_TIFF;
        }
        return pictureTypeForHeader(data);
    }

    private static int pictureTypeForHeader(byte[] data) {
        if (data == null || data.length < 4) {
            return -1;
        }
        if (data.length >= 8
                && data[0] == (byte) 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
        }
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
        }
        if (data.length >= 6
                && data[0] == 0x47
                && data[1] == 0x49
                && data[2] == 0x46
                && data[3] == 0x38) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF;
        }
        if (data[0] == 0x42 && data[1] == 0x4D) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_BMP;
        }
        if ((data[0] == 0x49 && data[1] == 0x49 && data[2] == 0x2A && data[3] == 0x00)
                || (data[0] == 0x4D && data[1] == 0x4D && data[2] == 0x00 && data[3] == 0x2A)) {
            return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_TIFF;
        }
        return -1;
    }

    private static String extensionForPictureType(int pictureType) {
        if (pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG) return "jpg";
        if (pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG) return "png";
        if (pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF) return "gif";
        if (pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_BMP) return "bmp";
        if (pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_TIFF) return "tiff";
        return "img";
    }

    private static String fileNameFor(String path) {
        String name = path == null ? "" : path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? "image" : name;
    }

    private static Charset detectDeclaredCharset(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 4096), StandardCharsets.ISO_8859_1);
        String lower = head.toLowerCase(Locale.ROOT);
        String marker = "charset";
        int idx = lower.indexOf(marker);
        if (idx >= 0) {
            int start = lower.indexOf('=', idx + marker.length());
            if (start >= 0) {
                String value = head.substring(start + 1).stripLeading();
                if (!value.isEmpty() && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
                    char quote = value.charAt(0);
                    int end = value.indexOf(quote, 1);
                    value = end >= 1 ? value.substring(1, end) : value.substring(1);
                } else {
                    int end = 0;
                    while (end < value.length()) {
                        char c = value.charAt(end);
                        if (Character.isWhitespace(c) || c == ';' || c == '/' || c == '>') {
                            break;
                        }
                        end++;
                    }
                    value = value.substring(0, end);
                }
                try {
                    if (!value.isBlank()) {
                        return Charset.forName(value);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String decodeHtmlEntities(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 12) {
                out.append(c);
                continue;
            }
            String entity = text.substring(i + 1, semi);
            String decoded = decodeEntity(entity);
            if (decoded == null) {
                out.append(c);
            } else {
                out.append(decoded);
                i = semi;
            }
        }
        return out.toString();
    }

    private static String decodeEntity(String entity) {
        return switch (entity) {
            case "amp" -> "&";
            case "apos" -> "'";
            case "gt" -> ">";
            case "lt" -> "<";
            case "nbsp" -> " ";
            case "quot" -> "\"";
            default -> decodeNumericEntity(entity);
        };
    }

    private static String decodeNumericEntity(String entity) {
        try {
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(entity.substring(2), 16)));
            }
            if (entity.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(entity.substring(1))));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readEntry(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            throw new IOException("EPUB 缺少文件: " + path);
        }
        try (var in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static Document parseXml(byte[] content, String sourceName) throws IOException {
        DocumentBuilder builder = XML_BUILDER.get();
        builder.reset();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            return builder.parse(in);
        } catch (SAXException ex) {
            throw new IOException("无法解析 XML 文件 " + sourceName + ": " + ex.getMessage(), ex);
        }
    }

    private static List<Element> elements(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        nodes = doc.getElementsByTagName(localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static Element firstElement(Document doc, String localName) {
        List<Element> matches = elements(doc, localName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        if (local == null || local.isBlank()) {
            local = node.getNodeName();
            int colon = local.indexOf(':');
            if (colon >= 0) {
                local = local.substring(colon + 1);
            }
        }
        return local.toLowerCase(Locale.ROOT);
    }

    private static String resolveZipPath(String baseFile, String href) {
        String cleaned = href;
        int fragment = cleaned.indexOf('#');
        if (fragment >= 0) {
            cleaned = cleaned.substring(0, fragment);
        }
        int query = cleaned.indexOf('?');
        if (query >= 0) {
            cleaned = cleaned.substring(0, query);
        }
        cleaned = percentDecode(cleaned).replace('\\', '/');
        if (cleaned.startsWith("/")) {
            return normalizeZipPath(cleaned.substring(1));
        }

        int slash = baseFile.lastIndexOf('/');
        String baseDir = slash >= 0 ? baseFile.substring(0, slash + 1) : "";
        return normalizeZipPath(baseDir + cleaned);
    }

    private static String normalizeZipPath(String path) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
                continue;
            }
            parts.addLast(part);
        }
        return String.join("/", parts);
    }

    private static String percentDecode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '%' || i + 2 >= value.length() || hexValue(value.charAt(i + 1)) < 0 || hexValue(value.charAt(i + 2)) < 0) {
                out.append(c);
                continue;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (i + 2 < value.length() && value.charAt(i) == '%') {
                int hi = hexValue(value.charAt(i + 1));
                int lo = hexValue(value.charAt(i + 2));
                if (hi < 0 || lo < 0) {
                    break;
                }
                bytes.write((hi << 4) + lo);
                i += 3;
            }
            i--;
            out.append(bytes.toString(StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private record ManifestItem(String path, String mediaType) {
    }

    private record ImageResource(byte[] data, String fileName, int pictureType) {
    }
}
