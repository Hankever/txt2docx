package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxToTxtConverterTest {

    private static ConversionOptions defaultOptions() {
        ConversionOptions opts = new ConversionOptions();
        opts.setEncoding("UTF-8");
        return opts;
    }

    @Test
    void paragraphTextIsExtracted(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("simple.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(src)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("第一段");
            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("第二段");
            doc.write(out);
        }

        Path dst = dir.resolve("simple.txt");
        new DocxToTxtConverter(defaultOptions()).convert(src, dst);

        String txt = Files.readString(dst, StandardCharsets.UTF_8);
        assertTrue(txt.contains("第一段"), "missing first paragraph in: " + txt);
        assertTrue(txt.contains("第二段"), "missing second paragraph in: " + txt);
    }

    @Test
    void tableContentIsExtracted(@TempDir Path dir) throws IOException {
        // Regression test for the prior bug: getParagraphs() silently dropped table cells.
        Path src = dir.resolve("table.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(src)) {
            XWPFParagraph header = doc.createParagraph();
            header.createRun().setText("以下为表格");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("姓名");
            table.getRow(0).getCell(1).setText("分数");
            table.getRow(1).getCell(0).setText("张三");
            table.getRow(1).getCell(1).setText("95");
            XWPFParagraph footer = doc.createParagraph();
            footer.createRun().setText("以上为表格");
            doc.write(out);
        }

        Path dst = dir.resolve("table.txt");
        new DocxToTxtConverter(defaultOptions()).convert(src, dst);

        String txt = Files.readString(dst, StandardCharsets.UTF_8);
        assertTrue(txt.contains("以下为表格"), "missing leading paragraph: " + txt);
        assertTrue(txt.contains("姓名") && txt.contains("分数"), "missing table header row: " + txt);
        assertTrue(txt.contains("张三") && txt.contains("95"), "missing table body row: " + txt);
        assertTrue(txt.contains("以上为表格"), "missing trailing paragraph: " + txt);
    }
}
