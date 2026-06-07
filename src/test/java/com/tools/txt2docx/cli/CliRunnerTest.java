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
}
