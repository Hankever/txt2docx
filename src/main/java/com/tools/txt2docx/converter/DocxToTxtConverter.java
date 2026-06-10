package com.tools.txt2docx.converter;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DocxToTxtConverter {

    private final ConversionOptions options;

    public DocxToTxtConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputDocx, Path outputTxt) throws IOException {
        String raw;
        try (OPCPackage pkg = OPCPackage.open(inputDocx.toFile(), PackageAccess.READ);
             XWPFDocument doc = new XWPFDocument(pkg);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            // Extractor pulls paragraphs, table cells (tab-separated), headers, footers,
            // footnotes, endnotes, and list numbering — everything getParagraphs() dropped.
            raw = extractor.getText();
        } catch (InvalidFormatException e) {
            throw new IOException("Invalid DOCX package: " + inputDocx, e);
        }

        List<String> lines = splitLines(raw);
        List<String> formatted = TextFormatter.formatLines(lines, options);
        String body = String.join(System.lineSeparator(), formatted);
        Charset charset = resolveCharset();
        DocxDocumentWriter.writeAtomically(outputTxt, target -> Files.writeString(target, body, charset));
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(text.substring(start, i));
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private Charset resolveCharset() {
        String encoding = options.getEncoding();
        if (encoding == null || encoding.isBlank() || "AUTO".equalsIgnoreCase(encoding)) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(encoding);
    }
}
