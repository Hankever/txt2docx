package com.tools.txt2docx.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRunnerTest {

    private static class Captured {
        final int exit;
        final String out;
        final String err;

        Captured(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }
    }

    private static Captured run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        int code = CliRunner.run(args, out, err);
        return new Captured(code,
                outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8));
    }

    private static Path writeTxt(Path dir, String name, String content) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void helpFlagReturnsZeroAndPrintsUsage() {
        Captured c = run("--help");
        assertEquals(0, c.exit);
        assertTrue(c.out.contains("CLI 模式"), "expected usage text on stdout, got: " + c.out);
    }

    @Test
    void missingInputReportsErrorAndReturnsTwo() {
        Captured c = run("--output", "/tmp/whatever");
        assertEquals(2, c.exit);
        assertTrue(c.err.contains("至少需要一个 --input"), "expected input error, got: " + c.err);
    }

    @Test
    void missingOutputReportsErrorAndReturnsTwo() {
        Captured c = run("--input", "/tmp/whatever");
        assertEquals(2, c.exit);
        assertTrue(c.err.contains("必须指定 --output"), "expected output error, got: " + c.err);
    }

    @Test
    void unknownFlagReportsErrorAndReturnsTwo() {
        Captured c = run("--bogus");
        assertEquals(2, c.exit);
        assertTrue(c.err.contains("不支持的参数"), "expected unknown-flag error, got: " + c.err);
    }

    @Test
    void invalidConflictPolicyValueRejected() {
        Captured c = run("--input", "/tmp", "--output", "/tmp", "--on-conflict", "bogus");
        assertEquals(2, c.exit);
        assertTrue(c.err.contains("rename / overwrite / skip"), "expected policy error, got: " + c.err);
    }

    @Test
    void endToEndConvertsTxtToDocx(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        writeTxt(inputs, "a.txt", "hello world");

        Captured c = run(
                "--input", inputs.toString(),
                "--output", outputs.toString(),
                "--encoding", "UTF-8",
                "--flatten"
        );

        assertEquals(0, c.exit, "expected success exit, got " + c.exit + " | err: " + c.err);
        try (Stream<Path> files = Files.list(outputs)) {
            long docx = files.filter(p -> p.getFileName().toString().endsWith(".docx")).count();
            assertEquals(1, docx, "expected one .docx in " + outputs);
        }
        assertTrue(c.out.contains("成功: 1"), "expected summary success count, got: " + c.out);
        assertTrue(c.out.contains("失败: 0"), "expected summary failed count, got: " + c.out);
    }

    @Test
    void endToEndConvertsEpubToDocx(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        Files.createDirectories(inputs);
        writeMinimalEpub(inputs.resolve("book.epub"));

        Captured c = run(
                "--mode", "epub2docx",
                "--input", inputs.toString(),
                "--output", outputs.toString(),
                "--flatten"
        );

        assertEquals(0, c.exit, "expected success exit, got " + c.exit + " | err: " + c.err);
        assertTrue(Files.exists(outputs.resolve("book.docx")), "expected EPUB output docx");
        assertTrue(c.out.contains("共找到 1 个 .epub 文件"), "expected epub progress text, got: " + c.out);
        assertTrue(c.out.contains("成功: 1"), "expected summary success count, got: " + c.out);
    }

    @Test
    void overwriteFlagIsAliasForOnConflictOverwrite(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        writeTxt(inputs, "a.txt", "v1");
        Files.createDirectories(outputs);
        // Stale collision target — without overwrite the run renames; with overwrite it replaces.
        Files.writeString(outputs.resolve("a.docx"), "stale-bytes");

        Captured c = run(
                "--input", inputs.toString(),
                "--output", outputs.toString(),
                "--encoding", "UTF-8",
                "--flatten",
                "--overwrite"
        );
        assertEquals(0, c.exit, "expected success exit, got: " + c.err);

        // a.docx should now be a real zip (starts with PK), not the stale text.
        byte[] bytes = Files.readAllBytes(outputs.resolve("a.docx"));
        assertTrue(bytes.length > 4 && bytes[0] == 0x50 && bytes[1] == 0x4B,
                "expected real docx after --overwrite");
        assertNotEquals("stale-bytes", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void onConflictSkipPreservesExistingTargetAndExitsZero(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        writeTxt(inputs, "a.txt", "v1");
        Files.createDirectories(outputs);
        Files.writeString(outputs.resolve("a.docx"), "stale");

        Captured c = run(
                "--input", inputs.toString(),
                "--output", outputs.toString(),
                "--encoding", "UTF-8",
                "--flatten",
                "--on-conflict", "skip"
        );

        assertEquals(0, c.exit);
        assertTrue(c.out.contains("跳过: 1"), "expected skipped summary, got: " + c.out);
        assertEquals("stale", Files.readString(outputs.resolve("a.docx")), "stale file should remain untouched");
    }

    private static void writeMinimalEpub(Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                    """);
            put(zip, "OEBPS/chapter.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><h1>标题</h1><p>正文</p></body>
                    </html>
                    """);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
