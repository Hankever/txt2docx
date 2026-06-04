package com.tools.txt2docx.converter;

import java.util.ArrayList;
import java.util.List;

public final class TextFormatter {

    private TextFormatter() {
    }

    public static List<String> formatLines(List<String> inputLines, ConversionOptions options) {
        List<String> normalized = new ArrayList<>();

        for (String rawLine : inputLines) {
            String line = options.isRemoveSpaces() ? removeInlineSpaces(rawLine) : rawLine;
            if (options.isRemoveEmptyLines() && line.isBlank()) {
                continue;
            }
            normalized.add(line);
        }

        if (options.isAddBlankLineBetweenLines() && !normalized.isEmpty()) {
            List<String> expanded = new ArrayList<>();
            for (int i = 0; i < normalized.size(); i++) {
                expanded.add(normalized.get(i));
                if (i < normalized.size() - 1) {
                    expanded.add("");
                }
            }
            return expanded;
        }

        return normalized;
    }

    private static String removeInlineSpaces(String line) {
        return line.replaceAll("[\\p{Z}\\t]+", "");
    }
}
