package com.tools.txt2docx.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathOverlapTest {

    @Test
    void disjointDirsReturnNull(@TempDir Path root) throws IOException {
        Path in = Files.createDirectories(root.resolve("in"));
        Path out = Files.createDirectories(root.resolve("out"));
        assertNull(PathOverlap.check(List.of(in), out));
    }

    @Test
    void identicalInputAndOutputFlagsConflict(@TempDir Path root) throws IOException {
        Path dir = Files.createDirectories(root.resolve("shared"));
        String msg = PathOverlap.check(List.of(dir), dir);
        assertNotNull(msg);
        assertTrue(msg.contains("不能与输入目录相同"), msg);
    }

    @Test
    void outputInsideInputFlagsConflict(@TempDir Path root) throws IOException {
        Path in = Files.createDirectories(root.resolve("in"));
        Path out = Files.createDirectories(in.resolve("nested-out"));
        String msg = PathOverlap.check(List.of(in), out);
        assertNotNull(msg);
        assertTrue(msg.contains("输出目录位于输入目录内"), msg);
    }

    @Test
    void inputInsideOutputFlagsConflict(@TempDir Path root) throws IOException {
        Path out = Files.createDirectories(root.resolve("out"));
        Path in = Files.createDirectories(out.resolve("nested-in"));
        String msg = PathOverlap.check(List.of(in), out);
        assertNotNull(msg);
        assertTrue(msg.contains("输入目录位于输出目录内"), msg);
    }

    @Test
    void siblingNamePrefixDoesNotFalseTrigger(@TempDir Path root) throws IOException {
        // outDir name is "in_archive" — startsWith("in") on the string would mistakenly
        // match if we compared as raw strings instead of path components.
        Path in = Files.createDirectories(root.resolve("in"));
        Path out = Files.createDirectories(root.resolve("in_archive"));
        assertNull(PathOverlap.check(List.of(in), out));
    }

    @Test
    void anyOverlappingInputAmongManyTriggers(@TempDir Path root) throws IOException {
        Path safe = Files.createDirectories(root.resolve("ok"));
        Path conflicting = Files.createDirectories(root.resolve("bad"));
        Path out = Files.createDirectories(conflicting.resolve("nested"));
        assertNotNull(PathOverlap.check(List.of(safe, conflicting), out));
    }
}
