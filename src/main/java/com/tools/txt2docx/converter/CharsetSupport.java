package com.tools.txt2docx.converter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

public final class CharsetSupport {

    private CharsetSupport() {
    }

    public static Charset resolveInputCharset(String encoding, Path inputFile) throws IOException {
        if (isAuto(encoding)) {
            return EncodingDetector.detect(inputFile);
        }
        return toCharset(encoding);
    }

    public static Charset resolveOutputCharset(String encoding) {
        if (isAuto(encoding)) {
            return StandardCharsets.UTF_8;
        }
        return toCharset(encoding);
    }

    public static Charset toCharset(String encoding) {
        return Charset.forName(normalizeEncodingName(encoding));
    }

    public static String normalizeEncodingName(String encoding) {
        if (encoding == null) {
            return "AUTO";
        }
        String trimmed = encoding.trim();
        if (trimmed.isEmpty()) {
            return "AUTO";
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "UTF16", "UTF-16", "UNICODE" -> "UTF-16";
            case "UTF16LE", "UTF-16LE", "UNICODELITTLE", "UNICODEFFFE" -> "UTF-16LE";
            case "UTF16BE", "UTF-16BE", "UNICODEBIG", "UNICODEBIGUNMARKED" -> "UTF-16BE";
            default -> trimmed;
        };
    }

    private static boolean isAuto(String encoding) {
        return normalizeEncodingName(encoding).equalsIgnoreCase("AUTO");
    }
}
