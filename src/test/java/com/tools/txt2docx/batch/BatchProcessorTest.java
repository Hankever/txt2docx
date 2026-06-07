package com.tools.txt2docx.batch;

import com.tools.txt2docx.converter.ConversionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchProcessorTest {

    private static ConversionOptions defaultOptions() {
        ConversionOptions o = new ConversionOptions();
        o.setEncoding("UTF-8");
        return o;
    }

    private static Path writeTxt(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, "hello", StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void collectFilesFiltersByModeExtension(@TempDir Path root) throws IOException {
        writeTxt(root, "keep.txt");
        Files.writeString(root.resolve("skip.md"), "x");
        Files.writeString(root.resolve("skip.docx"), "x");
        Files.writeString(root.resolve("skip.epub"), "x");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(root), false, false);

        Set<String> names = items.stream()
                .map(i -> i.source().getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("keep.txt"), names);
    }

    @Test
    void collectFilesFiltersEpubModeExtension(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("keep.epub"), "x");
        Files.writeString(root.resolve("skip.txt"), "x");
        Files.writeString(root.resolve("skip.docx"), "x");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.EPUB_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(root), false, false);

        assertEquals(1, items.size());
        assertEquals("keep.epub", items.get(0).source().getFileName().toString());
        assertEquals("keep.docx", items.get(0).relativeOutput().getFileName().toString());
    }

    @Test
    void collectFilesNonRecursiveSkipsSubdirectories(@TempDir Path root) throws IOException {
        writeTxt(root, "top.txt");
        writeTxt(root.resolve("sub"), "nested.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(root), false, false);

        Set<String> names = items.stream()
                .map(i -> i.source().getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("top.txt"), names);
    }

    @Test
    void collectFilesRecursiveFindsNested(@TempDir Path root) throws IOException {
        writeTxt(root, "top.txt");
        writeTxt(root.resolve("sub"), "nested.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(root), true, false);

        Set<String> names = items.stream()
                .map(i -> i.source().getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("top.txt", "nested.txt"), names);
    }

    @Test
    void preserveTreeKeepsRelativeOutputPathUnderInputRoot(@TempDir Path root) throws IOException {
        Path inputRoot = root.resolve("inputs");
        writeTxt(inputRoot.resolve("sub"), "nested.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(inputRoot), true, true);

        assertEquals(1, items.size());
        Path relOut = items.get(0).relativeOutput();
        // Expected shape: <inputRootName>/sub/nested.docx
        assertEquals("inputs", relOut.getName(0).toString());
        assertEquals("sub", relOut.getName(1).toString());
        assertEquals("nested.docx", relOut.getFileName().toString());
    }

    @Test
    void flatLayoutDropsSubdirectoriesFromOutput(@TempDir Path root) throws IOException {
        Path inputRoot = root.resolve("inputs");
        writeTxt(inputRoot.resolve("sub"), "nested.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(inputRoot), true, false);

        assertEquals(1, items.size());
        assertEquals("nested.docx", items.get(0).relativeOutput().toString());
    }

    @Test
    void autoRenamePolicyKeepsExistingTargetAndCreatesUniquifiedName(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        Files.createDirectories(outputs);
        writeTxt(inputs, "a.txt");
        Files.writeString(outputs.resolve("a.docx"), "stale");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(inputs), false, false);
        List<ConversionResult> results = p.process(items, outputs, ConflictPolicy.AUTO_RENAME, null);

        assertEquals(1, results.size());
        ConversionResult r = results.get(0);
        assertEquals(ConversionResult.Status.SUCCESS, r.getStatus());
        assertEquals("a (2).docx", r.getOutput().getFileName().toString());
        // Pre-existing collision target must be left untouched.
        assertEquals("stale", Files.readString(outputs.resolve("a.docx")));
    }

    @Test
    void overwritePolicyReplacesExistingTarget(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        Files.createDirectories(outputs);
        writeTxt(inputs, "a.txt");
        Files.writeString(outputs.resolve("a.docx"), "stale");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(inputs), false, false);
        List<ConversionResult> results = p.process(items, outputs, ConflictPolicy.OVERWRITE, null);

        assertEquals(ConversionResult.Status.SUCCESS, results.get(0).getStatus());
        assertEquals("a.docx", results.get(0).getOutput().getFileName().toString());
        // Stale content replaced by a real docx (zip header).
        byte[] bytes = Files.readAllBytes(outputs.resolve("a.docx"));
        assertTrue(bytes.length > 4 && bytes[0] == 0x50 && bytes[1] == 0x4B,
                "expected real docx (zip) header at output");
    }

    @Test
    void skipPolicyLeavesExistingTargetAndEmitsSkippedStatus(@TempDir Path root) throws IOException {
        Path inputs = root.resolve("in");
        Path outputs = root.resolve("out");
        Files.createDirectories(outputs);
        writeTxt(inputs, "a.txt");
        Files.writeString(outputs.resolve("a.docx"), "stale");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(inputs), false, false);
        List<ConversionResult> results = p.process(items, outputs, ConflictPolicy.SKIP, null);

        assertEquals(1, results.size());
        assertEquals(ConversionResult.Status.SKIPPED, results.get(0).getStatus());
        // Stale content preserved exactly.
        assertEquals("stale", Files.readString(outputs.resolve("a.docx")));
    }

    @Test
    void sameBatchCollisionStillUniquifiesEvenUnderSkipPolicy(@TempDir Path root) throws IOException {
        // Two source files map to the same relative output. SKIP should not silently drop
        // the second one — the on-disk file doesn't exist yet, so uniquify still kicks in.
        Path dirA = root.resolve("a");
        Path dirB = root.resolve("b");
        Path outputs = root.resolve("out");
        writeTxt(dirA, "name.txt");
        writeTxt(dirB, "name.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(dirA.resolve("name.txt"), dirB.resolve("name.txt")), false, false);
        List<ConversionResult> results = p.process(items, outputs, ConflictPolicy.SKIP, null);

        assertEquals(2, results.size());
        assertEquals(ConversionResult.Status.SUCCESS, results.get(0).getStatus());
        assertEquals(ConversionResult.Status.SUCCESS, results.get(1).getStatus());
        assertEquals("name.docx", results.get(0).getOutput().getFileName().toString());
        assertEquals("name (2).docx", results.get(1).getOutput().getFileName().toString());
    }

    @Test
    void twoSourcesWithSameRelativeOutputUniquifiesSecond(@TempDir Path root) throws IOException {
        Path dirA = root.resolve("a");
        Path dirB = root.resolve("b");
        Path outputs = root.resolve("out");
        writeTxt(dirA, "name.txt");
        writeTxt(dirB, "name.txt");

        BatchProcessor p = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX);
        List<BatchItem> items = p.collectFiles(List.of(dirA.resolve("name.txt"), dirB.resolve("name.txt")), false, false);
        List<ConversionResult> results = p.process(items, outputs, ConflictPolicy.AUTO_RENAME, null);

        assertEquals(2, results.size());
        assertEquals("name.docx", results.get(0).getOutput().getFileName().toString());
        assertEquals("name (2).docx", results.get(1).getOutput().getFileName().toString());
    }

    @Test
    void heavyModesCapParallelismLowerThanCpuCount() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int txt = new BatchProcessor(defaultOptions(), ConversionMode.TXT_TO_DOCX).parallelism();
        int epub = new BatchProcessor(defaultOptions(), ConversionMode.EPUB_TO_DOCX).parallelism();
        int docx = new BatchProcessor(defaultOptions(), ConversionMode.DOCX_TO_TXT).parallelism();

        assertTrue(epub <= 4, "EPUB parallelism should cap at 4, got " + epub);
        assertTrue(docx <= 4, "DOCX_TO_TXT parallelism should cap at 4, got " + docx);
        assertTrue(txt <= 8, "TXT parallelism should cap at 8, got " + txt);
        if (cpus >= 8) {
            assertTrue(txt > epub, "TXT mode should keep a higher ceiling than heavy modes on multi-core machines");
        }
    }
}
