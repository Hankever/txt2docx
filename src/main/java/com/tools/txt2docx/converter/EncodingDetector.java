package com.tools.txt2docx.converter;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EncodingDetector {

    private static final Charset FALLBACK_CJK = Charset.forName("GBK");

    private EncodingDetector() {}

    public static Charset detect(Path file) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(8192);
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (head.length >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        // BOMless UTF-8 is the most common case for files saved by modern editors. Try strict
        // decode first — if it succeeds, trust UTF-8 regardless of what the heuristic guesses.
        if (isStrictlyDecodable(head, StandardCharsets.UTF_8)) {
            return StandardCharsets.UTF_8;
        }
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(head, 0, head.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        if (encoding != null) {
            try {
                Charset c = Charset.forName(encoding);
                // juniversalchardet often mis-identifies short Simplified Chinese (GBK) samples
                // as KOI8-R / windows-12xx / ISO-8859-x. When the file clearly contains 8-bit
                // bytes and the guess is a Western single-byte charset, prefer GBK instead.
                if (looksLikeCjkMisidentification(c, head)) {
                    return FALLBACK_CJK;
                }
                return c;
            } catch (Exception ignored) {
            }
        }
        return FALLBACK_CJK;
    }

    public static int skipBom(byte[] bytes, Charset charset) {
        if (charset.equals(StandardCharsets.UTF_8) && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return 3;
        }
        if (bytes.length >= 2 && ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                || (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF)) {
            return 2;
        }
        return 0;
    }

    private static boolean isStrictlyDecodable(byte[] bytes, Charset charset) {
        if (bytes.length == 0) return true;
        try {
            charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            // Pure ASCII would also pass UTF-8 strict decoding. That's fine: ASCII is a subset
            // of UTF-8, and the resulting Charset is interchangeable for the actual file body.
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean looksLikeCjkMisidentification(Charset detected, byte[] head) {
        String name = detected.name().toUpperCase();
        // Charsets that genuinely cover CJK — trust them.
        if (name.startsWith("UTF") || name.startsWith("GB") || name.contains("BIG5")
                || name.startsWith("SHIFT") || name.startsWith("EUC") || name.contains("ISO-2022")) {
            return false;
        }
        // Pure ASCII files can be decoded by anything — no point overriding.
        for (byte b : head) {
            if ((b & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }
}
