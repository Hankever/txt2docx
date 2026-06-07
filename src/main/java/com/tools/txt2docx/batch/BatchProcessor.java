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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

public class BatchProcessor {

    public interface ProgressListener {
        void onProgress(int doneCount, int totalCount, ConversionResult lastResult);
    }

    private static final int MAX_PARALLELISM = 8;

    private final ConversionOptions options;
    private final ConversionMode mode;
    private final int parallelism;
    private volatile boolean cancelled;
    private volatile ExecutorService activeExecutor;

    public BatchProcessor(ConversionOptions options, ConversionMode mode) {
        this(options, mode, defaultParallelism());
    }

    BatchProcessor(ConversionOptions options, ConversionMode mode, int parallelism) {
        this.options = options;
        this.mode = mode;
        this.parallelism = Math.max(1, parallelism);
    }

    private static int defaultParallelism() {
        return Math.max(1, Math.min(MAX_PARALLELISM, Runtime.getRuntime().availableProcessors()));
    }

    public void cancel() {
        this.cancelled = true;
        ExecutorService e = activeExecutor;
        if (e != null) {
            e.shutdownNow();
        }
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
                                          ConflictPolicy policy,
                                          ProgressListener listener) {
        int total = files.size();
        if (total == 0) return new ArrayList<>();

        // Phase 1: resolve all targets sequentially. uniquify must see a deterministic
        // reservation order — otherwise parallel runs would race on the same target name.
        List<PlannedItem> plan = new ArrayList<>(total);
        Set<Path> reservedTargets = new HashSet<>();
        for (BatchItem item : files) {
            try {
                ResolvedTarget t = resolveOutput(item, outputDir, policy, reservedTargets);
                plan.add(new PlannedItem(item, t, null));
            } catch (Exception e) {
                plan.add(new PlannedItem(item, null, e));
            }
        }

        // Phase 2: execute. Single file or single core stays sequential to skip the
        // executor overhead.
        if (parallelism == 1 || total == 1) {
            return runSequentially(plan, total, listener);
        }
        return runInParallel(plan, total, listener);
    }

    private List<ConversionResult> runSequentially(List<PlannedItem> plan, int total, ProgressListener listener) {
        List<ConversionResult> results = new ArrayList<>(plan.size());
        int done = 0;
        for (PlannedItem p : plan) {
            if (cancelled) break;
            ConversionResult r = executePlanned(p);
            results.add(r);
            done++;
            if (listener != null) listener.onProgress(done, total, r);
        }
        return results;
    }

    private List<ConversionResult> runInParallel(List<PlannedItem> plan, int total, ProgressListener listener) {
        int threads = Math.min(parallelism, plan.size());
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread t = new Thread(runnable, "txt2docx-worker");
            t.setDaemon(true);
            return t;
        });
        activeExecutor = executor;
        AtomicInteger counter = new AtomicInteger();
        AtomicReferenceArray<ConversionResult> resultsArr = new AtomicReferenceArray<>(plan.size());
        try {
            List<Future<?>> futures = new ArrayList<>(plan.size());
            for (int i = 0; i < plan.size(); i++) {
                int idx = i;
                PlannedItem p = plan.get(i);
                futures.add(executor.submit(() -> {
                    if (cancelled || Thread.currentThread().isInterrupted()) return;
                    ConversionResult r = executePlanned(p);
                    resultsArr.set(idx, r);
                    int done = counter.incrementAndGet();
                    if (listener != null) listener.onProgress(done, total, r);
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                    // Cancellation surfaces as InterruptedException / CancellationException —
                    // already-recorded results in resultsArr are still returned.
                }
            }
        } finally {
            executor.shutdown();
            activeExecutor = null;
        }

        List<ConversionResult> results = new ArrayList<>(plan.size());
        for (int i = 0; i < resultsArr.length(); i++) {
            ConversionResult r = resultsArr.get(i);
            if (r != null) results.add(r);
        }
        return results;
    }

    private ConversionResult executePlanned(PlannedItem p) {
        if (p.error() != null) {
            return new ConversionResult(p.item().source(), null,
                    ConversionResult.Status.FAILED, p.error().getMessage());
        }
        ResolvedTarget t = p.target();
        if (t.skip()) {
            return new ConversionResult(p.item().source(), t.path(),
                    ConversionResult.Status.SKIPPED, t.message());
        }
        try {
            Files.createDirectories(t.path().getParent());
            convertFile(p.item().source(), t.path());
            return new ConversionResult(p.item().source(), t.path(),
                    ConversionResult.Status.SUCCESS, t.message());
        } catch (Exception e) {
            return new ConversionResult(p.item().source(), null,
                    ConversionResult.Status.FAILED, e.getMessage());
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
                                         ConflictPolicy policy,
                                         Set<Path> reservedTargets) throws IOException {
        Path requested = outputDir.resolve(item.relativeOutput()).normalize();
        boolean reserved = reservedTargets.contains(requested);
        boolean existedOnDisk = Files.exists(requested);

        // SKIP only honors on-disk collisions. A same-batch reservation must still produce
        // output, otherwise two inputs sharing a name would both silently disappear.
        if (existedOnDisk && !reserved && policy == ConflictPolicy.SKIP) {
            return new ResolvedTarget(requested, true, "目标已存在，跳过");
        }
        if (existedOnDisk && !reserved && policy == ConflictPolicy.OVERWRITE) {
            reservedTargets.add(requested);
            return new ResolvedTarget(requested, false, "已覆盖已有文件");
        }
        if (reserved || existedOnDisk) {
            Path selected = uniquify(requested, reservedTargets);
            reservedTargets.add(selected);
            return new ResolvedTarget(selected, false, "目标重名，已自动重命名");
        }
        reservedTargets.add(requested);
        return new ResolvedTarget(requested, false, "OK");
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

    private record ResolvedTarget(Path path, boolean skip, String message) {
    }

    private record PlannedItem(BatchItem item, ResolvedTarget target, Exception error) {
    }
}
