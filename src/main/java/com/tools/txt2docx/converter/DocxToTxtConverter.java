package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
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
        List<String> lines = new ArrayList<>();
        try (InputStream in = Files.newInputStream(inputDocx);
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                lines.add(paragraph.getText());
            }
        }

        List<String> formatted = TextFormatter.formatLines(lines, options);
        Files.writeString(outputTxt, String.join(System.lineSeparator(), formatted), resolveCharset());
    }

    private Charset resolveCharset() {
        String encoding = options.getEncoding();
        if (encoding == null || encoding.isBlank() || "AUTO".equalsIgnoreCase(encoding)) {
            return Charset.forName("UTF-8");
        }
        return Charset.forName(encoding);
    }
}
