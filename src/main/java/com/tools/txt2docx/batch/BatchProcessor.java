package com.tools.txt2docx.batch;

import com.tools.txt2docx.converter.ConversionOptions;
import com.tools.txt2docx.converter.DocxToTxtConverter;
import com.tools.txt2docx.converter.TxtToDocxConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class BatchProcessor {

    public interface ProgressListener {
        void onProgress(int doneCount, int totalCount, ConversionResult lastResult);
    }

    private final ConversionOptions options;
    private final ConversionMode mode;
    private volatile boolean cancelled;

    public BatchProcessor(ConversionOptions options, ConversionMode mode) {
        this.options = options;
        this.mode = mode;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public List<BatchItem> collectFiles(List<Path> inputs, boolean recursive, boolean preserveDirectoryStructure) throws IOException {
        List<BatchItem> result = new ArrayList<>();
        for (Path p : inputs) {
            Path normalized = p.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized) && matchesMode(normalized)) {
                result.add(new BatchItem(normalized, replaceExtension(normalized.getFileName())));
            } else if (Files.isDirectory(normalized)) {
                try (Stream<Path> walk = recursive ? Files.walk(normalized) : Files.list(normalized)) {
                    walk.filter(Files::isRegularFile)
                            .filter(this::matchesMode)
                            .sorted()
                            .forEach(file -> result.add(buildBatchItem(normalized, file, preserveDirectoryStructure)));
                }
            }
        }
        return result;
    }

    public List<ConversionResult> process(List<BatchItem> files,
                                          Path outputDir,
                                          boolean overwrite,
                                          ProgressListener listener) {
        List<ConversionResult> results = new ArrayList<>();
        Set<Path> reservedTargets = new HashSet<>();
        int total = files.size();
        int done = 0;
        for (BatchItem item : files) {
            if (cancelled) break;
            ConversionResult r = convertOne(item, outputDir, overwrite, reservedTargets);
            results.add(r);
            done++;
            if (listener != null) listener.onProgress(done, total, r);
        }
        return results;
    }

    private ConversionResult convertOne(BatchItem item,
                                        Path outputDir,
                                        boolean overwrite,
                                        Set<Path> reservedTargets) {
        try {
            ResolvedTarget resolvedTarget = resolveOutput(item, outputDir, overwrite, reservedTargets);
            Path target = resolvedTarget.path();
            Files.createDirectories(target.getParent());
            convertFile(item.source(), target);
            return new ConversionResult(item.source(), target, ConversionResult.Status.SUCCESS, resolvedTarget.message());
        } catch (Exception e) {
            return new ConversionResult(item.source(), null, ConversionResult.Status.FAILED, e.getMessage());
        }
    }

    private void convertFile(Path source, Path target) throws IOException {
        if (mode == ConversionMode.DOCX_TO_TXT) {
            new DocxToTxtConverter(options).convert(source, target);
            return;
        }
        new TxtToDocxConverter(options).convert(source, target);
    }

    private BatchItem buildBatchItem(Path inputDir, Path file, boolean preserveDirectoryStructure) {
        if (!preserveDirectoryStructure) {
            return new BatchItem(file, replaceExtension(file.getFileName()));
        }

        Path rootName = inputDir.getFileName();
        Path relativeFile = inputDir.relativize(file);
        Path relativeOutput = rootName == null
                ? replaceExtension(relativeFile)
                : rootName.resolve(replaceExtension(relativeFile));
        return new BatchItem(file, relativeOutput);
    }

    private ResolvedTarget resolveOutput(BatchItem item,
                                         Path outputDir,
                                         boolean overwrite,
                                         Set<Path> reservedTargets) throws IOException {
        Path requested = outputDir.resolve(item.relativeOutput()).normalize();
        boolean existed = Files.exists(requested);
        Path selected = requested;

        if (reservedTargets.contains(selected) || (!overwrite && existed)) {
            selected = uniquify(requested, reservedTargets);
        }

        reservedTargets.add(selected);
        if (!selected.equals(requested)) {
            return new ResolvedTarget(selected, "目标重名，已自动重命名");
        }
        if (overwrite && existed) {
            return new ResolvedTarget(selected, "已覆盖已有文件");
        }
        return new ResolvedTarget(selected, "OK");
    }

    private Path uniquify(Path requested, Set<Path> reservedTargets) throws IOException {
        Path parent = requested.getParent();
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";

        int index = 2;
        Path candidate = requested;
        while (reservedTargets.contains(candidate) || Files.exists(candidate)) {
            candidate = parent.resolve(base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }

    private static Path replaceExtension(Path path) {
        Path parent = path.getParent();
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path renamed = Paths.get(base + targetExtensionFor(path));
        return parent == null ? renamed : parent.resolve(renamed);
    }

    private boolean matchesMode(Path p) {
        return mode == ConversionMode.DOCX_TO_TXT ? isDocx(p) : isTxt(p);
    }

    private static String targetExtensionFor(Path path) {
        return isDocx(path) ? ".txt" : ".docx";
    }

    private static boolean isTxt(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".txt");
    }

    private static boolean isDocx(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".docx");
    }

    public static void forEachInput(List<Path> paths, Consumer<Path> consumer) {
        paths.forEach(consumer);
    }

    private record ResolvedTarget(Path path, String message) {
    }
}
