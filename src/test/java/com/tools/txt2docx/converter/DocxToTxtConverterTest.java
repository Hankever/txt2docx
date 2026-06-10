package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.util.Units;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    @Test
    void docxWithStoredImageDataDescriptorIsExtracted(@TempDir Path dir) throws Exception {
        Path normal = dir.resolve("normal-image.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(normal)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("图片前文本");
            XWPFRun imageRun = p.createRun();
            imageRun.addPicture(new ByteArrayInputStream(onePixelPng()),
                    Document.PICTURE_TYPE_PNG, "pixel.png", Units.toEMU(1), Units.toEMU(1));
            doc.createParagraph().createRun().setText("图片后文本");
            doc.write(out);
        }

        String mediaEntry = findFirstEntry(normal, "word/media/");
        Path descriptorDocx = dir.resolve("stored-descriptor-image.docx");
        rewriteWithStoredDataDescriptor(normal, descriptorDocx, mediaEntry);

        Path dst = dir.resolve("stored-descriptor-image.txt");
        new DocxToTxtConverter(defaultOptions()).convert(descriptorDocx, dst);

        String txt = Files.readString(dst, StandardCharsets.UTF_8);
        assertTrue(txt.contains("图片前文本"), "missing text before image in: " + txt);
        assertTrue(txt.contains("图片后文本"), "missing text after image in: " + txt);
    }

    private static byte[] onePixelPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    }

    private static String findFirstEntry(Path zipPath, String prefix) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    return entry.getName();
                }
            }
        }
        throw new IOException("Missing zip entry with prefix: " + prefix);
    }

    private static void rewriteWithStoredDataDescriptor(Path sourceZip,
                                                        Path targetZip,
                                                        String descriptorEntryName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<CentralDirectoryEntry> centralDirectory = new ArrayList<>();
        try (ZipFile zip = new ZipFile(sourceZip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content;
                try (InputStream in = zip.getInputStream(entry)) {
                    content = in.readAllBytes();
                }
                boolean useDescriptor = entry.getName().equals(descriptorEntryName);
                centralDirectory.add(writeStoredEntry(out, entry.getName(), content, useDescriptor));
            }
        }

        int centralDirectoryOffset = out.size();
        for (CentralDirectoryEntry entry : centralDirectory) {
            writeCentralDirectoryEntry(out, entry);
        }
        int centralDirectorySize = out.size() - centralDirectoryOffset;
        writeEndOfCentralDirectory(out, centralDirectory.size(), centralDirectorySize, centralDirectoryOffset);
        Files.write(targetZip, out.toByteArray());
    }

    private static CentralDirectoryEntry writeStoredEntry(ByteArrayOutputStream out,
                                                          String name,
                                                          byte[] content,
                                                          boolean useDescriptor) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        long crc = crc32.getValue();
        int offset = out.size();
        int flags = useDescriptor ? 0x0808 : 0x0800;

        writeInt(out, 0x04034b50);
        writeShort(out, 20);
        writeShort(out, flags);
        writeShort(out, ZipEntry.STORED);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, useDescriptor ? 0 : crc);
        writeInt(out, useDescriptor ? 0 : content.length);
        writeInt(out, useDescriptor ? 0 : content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.writeBytes(nameBytes);
        out.writeBytes(content);
        if (useDescriptor) {
            writeInt(out, 0x08074b50);
            writeInt(out, crc);
            writeInt(out, content.length);
            writeInt(out, content.length);
        }

        return new CentralDirectoryEntry(nameBytes, flags, crc, content.length, offset);
    }

    private static void writeCentralDirectoryEntry(ByteArrayOutputStream out, CentralDirectoryEntry entry) {
        writeInt(out, 0x02014b50);
        writeShort(out, 20);
        writeShort(out, 20);
        writeShort(out, entry.flags());
        writeShort(out, ZipEntry.STORED);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, entry.crc());
        writeInt(out, entry.size());
        writeInt(out, entry.size());
        writeShort(out, entry.nameBytes().length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, entry.localHeaderOffset());
        out.writeBytes(entry.nameBytes());
    }

    private static void writeEndOfCentralDirectory(ByteArrayOutputStream out,
                                                   int entryCount,
                                                   int centralDirectorySize,
                                                   int centralDirectoryOffset) {
        writeInt(out, 0x06054b50);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, entryCount);
        writeShort(out, entryCount);
        writeInt(out, centralDirectorySize);
        writeInt(out, centralDirectoryOffset);
        writeShort(out, 0);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, long value) {
        out.write((int) value & 0xff);
        out.write((int) (value >>> 8) & 0xff);
        out.write((int) (value >>> 16) & 0xff);
        out.write((int) (value >>> 24) & 0xff);
    }

    private record CentralDirectoryEntry(byte[] nameBytes,
                                         int flags,
                                         long crc,
                                         int size,
                                         int localHeaderOffset) {
    }
}
