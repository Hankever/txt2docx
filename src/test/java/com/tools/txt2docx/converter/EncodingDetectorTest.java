package com.tools.txt2docx.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingDetectorTest {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16_LE = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF16_BE = {(byte) 0xFE, (byte) 0xFF};

    @Test
    void detectsUtf8Bom(@TempDir Path dir) throws IOException {
        Path f = write(dir, "u8bom.txt", concat(BOM_UTF8, "hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(f));
    }

    @Test
    void detectsUtf16LeBom(@TempDir Path dir) throws IOException {
        Path f = write(dir, "u16le.txt", concat(BOM_UTF16_LE, "hi".getBytes(StandardCharsets.UTF_16LE)));
        assertEquals(StandardCharsets.UTF_16LE, EncodingDetector.detect(f));
    }

    @Test
    void detectsUtf16BeBom(@TempDir Path dir) throws IOException {
        Path f = write(dir, "u16be.txt", concat(BOM_UTF16_BE, "hi".getBytes(StandardCharsets.UTF_16BE)));
        assertEquals(StandardCharsets.UTF_16BE, EncodingDetector.detect(f));
    }

    @Test
    void gbkChineseBytesDetectedAsGbFamily(@TempDir Path dir) throws IOException {
        Path f = write(dir, "gbk.txt", "中文测试内容，重复一些常见汉字以便检测器判定。中文测试内容。"
                .getBytes(Charset.forName("GBK")));
        Charset detected = EncodingDetector.detect(f);
        // After fallback hardening, a Western single-byte guess gets overridden to GBK
        // for files with high bytes.
        String name = detected.name().toUpperCase();
        assertEquals(true, name.startsWith("GB"),
                "expected GB-family fallback, got " + name);
    }

    @Test
    void bomlessUtf8DetectedAsUtf8(@TempDir Path dir) throws IOException {
        Path f = write(dir, "u8nobom.txt", "中文 UTF-8 内容，足够多字节让严格解码能区分出 UTF-8 字节模式。"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(f));
    }

    @Test
    void pureAsciiDetectedAsUtf8(@TempDir Path dir) throws IOException {
        Path f = write(dir, "ascii.txt", "Plain ASCII content only.".getBytes(StandardCharsets.US_ASCII));
        // ASCII is a subset of UTF-8; strict UTF-8 decode succeeds so we report UTF-8.
        assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(f));
    }

    @Test
    void skipBomReturnsThreeForUtf8Bom() {
        byte[] bytes = concat(BOM_UTF8, "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(3, EncodingDetector.skipBom(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void skipBomReturnsZeroWhenUtf8HasNoBom() {
        byte[] bytes = "no bom".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, EncodingDetector.skipBom(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void skipBomReturnsTwoForUtf16LeBom() {
        byte[] bytes = concat(BOM_UTF16_LE, "hi".getBytes(StandardCharsets.UTF_16LE));
        assertEquals(2, EncodingDetector.skipBom(bytes, StandardCharsets.UTF_16LE));
    }

    @Test
    void skipBomReturnsTwoForUtf16BeBom() {
        byte[] bytes = concat(BOM_UTF16_BE, "hi".getBytes(StandardCharsets.UTF_16BE));
        assertEquals(2, EncodingDetector.skipBom(bytes, StandardCharsets.UTF_16BE));
    }

    @Test
    void skipBomToleratesShortInput() {
        assertEquals(0, EncodingDetector.skipBom(new byte[0], StandardCharsets.UTF_8));
        assertEquals(0, EncodingDetector.skipBom(new byte[]{0x41}, StandardCharsets.UTF_8));
    }

    private static Path write(Path dir, String name, byte[] bytes) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, bytes);
        return f;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
