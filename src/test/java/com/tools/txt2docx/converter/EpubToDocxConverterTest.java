package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubToDocxConverterTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    );

    private static ConversionOptions defaultOptions() {
        ConversionOptions opts = new ConversionOptions();
        opts.setEncoding("UTF-8");
        return opts;
    }

    @Test
    void convertsSpineOrderedXhtmlToDocx(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("book.epub");
        writeMinimalEpub(src);

        Path dst = dir.resolve("book.docx");
        new EpubToDocxConverter(defaultOptions()).convert(src, dst);

        String text = extractDocxText(dst);
        int chapterOne = text.indexOf("第一章");
        int chapterTwo = text.indexOf("第二章");
        assertTrue(chapterOne >= 0, "missing first chapter in: " + text);
        assertTrue(chapterTwo > chapterOne, "spine order was not preserved in: " + text);
        assertTrue(text.contains("Tom & Jerry"), "HTML entity was not decoded through XML parsing: " + text);
        assertTrue(text.contains("第二章正文"), "missing second chapter body in: " + text);

        try (InputStream in = Files.newInputStream(dst);
             XWPFDocument doc = new XWPFDocument(in)) {
            assertTrue(doc.getAllPictures().size() >= 1, "expected embedded EPUB image in generated docx");
            assertTrue(doc.getAllPictures().get(0).suggestFileExtension().equals("png"),
                    "expected embedded png image");
        }
    }

    @Test
    void multipleImagesEmbeddedFromOpenZipAcrossWritePhase(@TempDir Path dir) throws IOException {
        // Regression for lazy image loading: the ByteSource lambdas are invoked during the
        // write phase, which runs while the source ZipFile is still open. If convert() ever
        // closes the zip before writing, addPicture would silently fall back to alt-text.
        Path src = dir.resolve("book.epub");
        writeEpubWithMultipleImages(src);

        Path dst = dir.resolve("book.docx");
        new EpubToDocxConverter(defaultOptions()).convert(src, dst);

        try (InputStream in = Files.newInputStream(dst);
             XWPFDocument doc = new XWPFDocument(in)) {
            List<XWPFPictureData> pictures = doc.getAllPictures();
            assertTrue(!pictures.isEmpty(), "expected at least one embedded image");
        }
        // If lazy ByteSource ever failed, every <img> would degrade to its alt text. Asserting
        // the alts are absent rules that out without depending on POI's image-dedup behavior.
        String text = extractDocxText(dst);
        assertTrue(!text.contains("一") && !text.contains("二") && !text.contains("三"),
                "image fallback alt-text leaked into docx body: " + text);
    }

    private static String extractDocxText(Path docx) throws IOException {
        try (InputStream in = Files.newInputStream(docx);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static void writeMinimalEpub(Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/chapters/chapter2.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>ignored</title></head>
                      <body>
                        <h1>第二章</h1>
                        <p>第二章正文</p>
                      </body>
                    </html>
                    """);
            put(zip, "OEBPS/chapters/chapter1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>ignored</title></head>
                      <body>
                        <h1>第一章</h1>
                        <p>Tom &amp; Jerry</p>
                        <p><img src="../images/cover.png" alt="封面图"/></p>
                      </body>
                    </html>
                    """);
            zip.putNextEntry(new ZipEntry("OEBPS/images/cover.png"));
            zip.write(TINY_PNG);
            zip.closeEntry();
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="c2" href="chapters/chapter2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c1" href="chapters/chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="cover" href="images/cover.png" media-type="image/png"/>
                      </manifest>
                      <spine>
                        <itemref idref="c1"/>
                        <itemref idref="c2"/>
                      </spine>
                    </package>
                    """);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeEpubWithMultipleImages(Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/chapter.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body>
                        <p><img src="images/one.png" alt="一"/></p>
                        <p><img src="images/two.png" alt="二"/></p>
                        <p><img src="images/three.png" alt="三"/></p>
                      </body>
                    </html>
                    """);
            for (String name : new String[]{"one.png", "two.png", "three.png"}) {
                zip.putNextEntry(new ZipEntry("OEBPS/images/" + name));
                zip.write(TINY_PNG);
                zip.closeEntry();
            }
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="ch" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="ch"/>
                      </spine>
                    </package>
                    """);
        }
    }
}
