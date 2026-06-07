package com.tools.txt2docx.batch;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class PathOverlap {

    private PathOverlap() {
    }

    /**
     * Returns a human-readable problem description when the output directory and any input
     * overlap in a way that would cause re-scanning, accidental overwrites, or reading the
     * previous batch's output as input. Returns {@code null} when the layout is safe.
     */
    public static String check(List<Path> inputs, Path outputDir) {
        Path out = outputDir.toAbsolutePath().normalize();
        for (Path raw : inputs) {
            Path in = raw.toAbsolutePath().normalize();
            if (pathEquals(out, in)) {
                return "输出目录不能与输入目录相同: " + in;
            }
            if (pathStartsWith(out, in)) {
                return "输出目录位于输入目录内,会导致重复扫描或覆盖源文件:\n输入: " + in + "\n输出: " + out;
            }
            if (pathStartsWith(in, out)) {
                return "输入目录位于输出目录内,可能误读旧的转换结果:\n输出: " + out + "\n输入: " + in;
            }
        }
        return null;
    }

    private static boolean pathEquals(Path a, Path b) {
        return isCaseInsensitiveFileSystem()
                ? a.toString().equalsIgnoreCase(b.toString())
                : a.equals(b);
    }

    private static boolean pathStartsWith(Path child, Path parent) {
        if (!isCaseInsensitiveFileSystem()) return child.startsWith(parent);
        String childStr = child.toString();
        String parentStr = parent.toString();
        if (childStr.length() < parentStr.length()) return false;
        if (!childStr.regionMatches(true, 0, parentStr, 0, parentStr.length())) return false;
        if (childStr.length() == parentStr.length()) return true;
        char sep = child.getFileSystem().getSeparator().charAt(0);
        return childStr.charAt(parentStr.length()) == sep;
    }

    private static boolean isCaseInsensitiveFileSystem() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac");
    }
}
