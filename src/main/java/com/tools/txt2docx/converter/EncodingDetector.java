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
import java.util.Arrays;

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
        Charset bomlessUtf16 = detectBomlessUtf16(head);
        if (bomlessUtf16 != null) {
            return bomlessUtf16;
        }
        Charset detectedByLibrary = detectWithLibrary(head);
        if (detectedByLibrary != null && isUtf16Family(detectedByLibrary)) {
            return detectedByLibrary;
        }
        // BOMless UTF-8 is the most common case for files saved by modern editors. Try strict
        // decode first — if it succeeds, trust UTF-8 regardless of what the heuristic guesses.
        // Trim to the last complete UTF-8 sequence so a multi-byte char straddling the 8192-byte
        // read boundary doesn't masquerade as a decode failure.
        if (isStrictlyDecodable(trimToUtf8Boundary(head), StandardCharsets.UTF_8)) {
            return StandardCharsets.UTF_8;
        }
        if (detectedByLibrary != null) {
            // juniversalchardet often mis-identifies short Simplified Chinese (GBK) samples
            // as KOI8-R / windows-12xx / ISO-8859-x. When the file clearly contains 8-bit
            // bytes and the guess is a Western single-byte charset, prefer GBK instead.
            if (looksLikeCjkMisidentification(detectedByLibrary, head)) {
                return FALLBACK_CJK;
            }
            return detectedByLibrary;
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

    private static Charset detectBomlessUtf16(byte[] bytes) {
        int evenLength = bytes.length - bytes.length % 2;
        if (evenLength < 4) {
            return null;
        }
        byte[] sample = evenLength == bytes.length ? bytes : Arrays.copyOf(bytes, evenLength);
        return detectUtf16ByZeroPattern(sample);
    }

    private static Charset detectUtf16ByZeroPattern(byte[] bytes) {
        int pairs = bytes.length / 2;
        int evenZero = 0;
        int oddZero = 0;
        for (int i = 0; i < bytes.length; i += 2) {
            if (bytes[i] == 0) {
                evenZero++;
            }
            if (bytes[i + 1] == 0) {
                oddZero++;
            }
        }
        double evenRatio = evenZero / (double) pairs;
        double oddRatio = oddZero / (double) pairs;
        if (oddRatio >= 0.30 && evenRatio <= 0.05) {
            return StandardCharsets.UTF_16LE;
        }
        if (evenRatio >= 0.30 && oddRatio <= 0.05) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private static byte[] trimToUtf8Boundary(byte[] bytes) {
        if (bytes.length == 0) return bytes;
        int cut = bytes.length;
        // Walk back over continuation bytes (10xxxxxx) until we hit either an ASCII byte or a
        // lead byte. If that lead byte's expected sequence runs off the end of the buffer,
        // drop it too.
        int trailing = 0;
        while (cut > 0 && (bytes[cut - 1] & 0xC0) == 0x80 && trailing < 3) {
            cut--;
            trailing++;
        }
        if (cut > 0) {
            int lead = bytes[cut - 1] & 0xFF;
            int expected;
            if ((lead & 0x80) == 0) expected = 0;
            else if ((lead & 0xE0) == 0xC0) expected = 1;
            else if ((lead & 0xF0) == 0xE0) expected = 2;
            else if ((lead & 0xF8) == 0xF0) expected = 3;
            else expected = -1; // illegal lead byte — let the strict decoder reject it
            if (expected >= 0 && trailing < expected) {
                cut--; // sequence is incomplete at the buffer edge
            }
        }
        return cut == bytes.length ? bytes : java.util.Arrays.copyOf(bytes, cut);
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

    private static Charset detectWithLibrary(byte[] head) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(head, 0, head.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        if (encoding == null) {
            return null;
        }
        try {
            return CharsetSupport.toCharset(encoding);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isUtf16Family(Charset charset) {
        String name = charset.name().toUpperCase();
        return name.equals("UTF-16") || name.equals("UTF-16LE") || name.equals("UTF-16BE");
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
