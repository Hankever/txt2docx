package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TxtToDocxConverterTest {

    @Test
    void autoDetectsUtf16LeBomTxt(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("unicode-le.txt");
        Files.write(input, prependBom("第一行\n第二行".getBytes(StandardCharsets.UTF_16LE)));

        Path output = dir.resolve("unicode-le.docx");
        new TxtToDocxConverter(new ConversionOptions()).convert(input, output);

        assertDocxContains(output, "第一行", "第二行");
    }

    @Test
    void acceptsUnicodeAliasForUtf16Input(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("unicode-bom.txt");
        Files.write(input, prependBom("正文内容".getBytes(StandardCharsets.UTF_16LE)));

        ConversionOptions options = new ConversionOptions();
        options.setEncoding("Unicode");

        Path output = dir.resolve("unicode-bom.docx");
        new TxtToDocxConverter(options).convert(input, output);

        assertDocxContains(output, "正文内容");
    }

    private static void assertDocxContains(Path docx, String... expectedTexts) throws IOException {
        try (InputStream in = Files.newInputStream(docx);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String body = extractor.getText();
            for (String expectedText : expectedTexts) {
                assertTrue(body.contains(expectedText), "missing text in docx text: " + expectedText);
            }
        }
    }

    private static byte[] prependBom(byte[] body) {
        byte[] result = new byte[body.length + 2];
        result[0] = (byte) 0xFF;
        result[1] = (byte) 0xFE;
        System.arraycopy(body, 0, result, 2, body.length);
        return result;
    }
}
