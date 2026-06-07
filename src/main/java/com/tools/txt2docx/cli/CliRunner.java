package com.tools.txt2docx.cli;

import com.tools.txt2docx.batch.BatchItem;
import com.tools.txt2docx.batch.BatchProcessor;
import com.tools.txt2docx.batch.ConflictPolicy;
import com.tools.txt2docx.batch.ConversionMode;
import com.tools.txt2docx.batch.ConversionResult;
import com.tools.txt2docx.batch.PathOverlap;
import com.tools.txt2docx.converter.ConversionOptions;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CliRunner {

    private CliRunner() {
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        CliArguments cliArgs;
        try {
            cliArgs = parse(args);
        } catch (IllegalArgumentException ex) {
            err.println("参数错误: " + ex.getMessage());
            err.println();
            printUsage(err);
            return 2;
        }

        if (cliArgs.showHelp()) {
            printUsage(out);
            return 0;
        }

        BatchProcessor processor = new BatchProcessor(cliArgs.options(), cliArgs.mode());
        try {
            String overlap = PathOverlap.check(cliArgs.inputs(), cliArgs.outputDir());
            if (overlap != null) {
                err.println("目录冲突: " + overlap);
                return 2;
            }
            Files.createDirectories(cliArgs.outputDir());
            List<BatchItem> items = processor.collectFiles(
                    cliArgs.inputs(),
                    cliArgs.recursive(),
                    cliArgs.preserveDirectoryStructure()
            );
            if (items.isEmpty()) {
                out.println("未找到任何 " + cliArgs.mode().sourceExtension() + " 文件");
                return 0;
            }

            out.println("共找到 " + items.size() + " 个 " + cliArgs.mode().sourceExtension() + " 文件，开始转换...");
            // Parallel workers can finish out of order; serialize the listener so each progress
            // line is atomic and the on-screen done counter monotonically increases.
            Object printLock = new Object();
            List<ConversionResult> results = processor.process(
                    items,
                    cliArgs.outputDir(),
                    cliArgs.onConflict(),
                    (done, total, last) -> {
                        synchronized (printLock) {
                            out.println(formatProgress(done, total, last));
                        }
                    }
            );
            return printSummary(results, cliArgs.outputDir(), out);
        } catch (Exception ex) {
            err.println("执行失败: " + ex.getMessage());
            return 1;
        }
    }

    private static int printSummary(List<ConversionResult> results, Path outputDir, PrintStream out) {
        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (ConversionResult result : results) {
            switch (result.getStatus()) {
                case SUCCESS -> success++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        out.println();
        out.println("转换完成");
        out.println("成功: " + success);
        out.println("跳过: " + skipped);
        out.println("失败: " + failed);
        out.println("输出目录: " + outputDir.toAbsolutePath().normalize());
        return failed > 0 ? 1 : 0;
    }

    private static String formatProgress(int done, int total, ConversionResult result) {
        return "[" + done + "/" + total + "] " + formatResult(result);
    }

    private static String formatResult(ConversionResult result) {
        String tag = switch (result.getStatus()) {
            case SUCCESS -> "[OK]";
            case SKIPPED -> "[跳过]";
            case FAILED -> "[失败]";
        };
        String output = result.getOutput() == null ? "" : " -> " + result.getOutput();
        String message = result.getMessage() == null || result.getMessage().isBlank()
                ? ""
                : " (" + result.getMessage() + ")";
        return tag + " " + result.getSource() + output + message;
    }

    private static CliArguments parse(String[] args) {
        List<Path> inputs = new ArrayList<>();
        Path outputDir = null;
        ConversionMode mode = ConversionMode.TXT_TO_DOCX;
        boolean recursive = false;
        ConflictPolicy onConflict = ConflictPolicy.AUTO_RENAME;
        boolean preserveDirectoryStructure = true;
        boolean showHelp = false;

        ConversionOptions options = new ConversionOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> showHelp = true;
                case "-i", "--input" -> inputs.add(Path.of(requireValue(args, ++i, arg)));
                case "-o", "--output" -> outputDir = Path.of(requireValue(args, ++i, arg));
                case "--mode" -> mode = parseMode(args, ++i, arg);
                case "-r", "--recursive" -> recursive = true;
                case "--overwrite" -> onConflict = ConflictPolicy.OVERWRITE;
                case "--on-conflict" -> onConflict = parseConflictPolicy(args, ++i, arg);
                case "--flatten" -> preserveDirectoryStructure = false;
                case "--preserve-tree" -> preserveDirectoryStructure = true;
                case "--encoding" -> options.setEncoding(requireValue(args, ++i, arg));
                case "--font" -> options.setFontFamily(requireValue(args, ++i, arg));
                case "--font-size" -> options.setFontSize(parsePositiveInt(args, ++i, arg));
                case "--margin-top" -> options.setMarginTopCm(parseNonNegativeDouble(args, ++i, arg));
                case "--margin-bottom" -> options.setMarginBottomCm(parseNonNegativeDouble(args, ++i, arg));
                case "--margin-left" -> options.setMarginLeftCm(parseNonNegativeDouble(args, ++i, arg));
                case "--margin-right" -> options.setMarginRightCm(parseNonNegativeDouble(args, ++i, arg));
                case "--remove-spaces" -> options.setRemoveSpaces(true);
                case "--remove-empty-lines" -> options.setRemoveEmptyLines(true);
                case "--indent" -> options.setIndentSize(parseNonNegativeInt(args, ++i, arg));
                case "--blank-line-between-lines" -> options.setAddBlankLineBetweenLines(true);
                default -> throw new IllegalArgumentException("不支持的参数: " + arg);
            }
        }

        if (!showHelp) {
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("至少需要一个 --input");
            }
            if (outputDir == null) {
                throw new IllegalArgumentException("必须指定 --output");
            }
        }

        return new CliArguments(inputs, outputDir, mode, recursive, onConflict, preserveDirectoryStructure, showHelp, options);
    }

    private static ConflictPolicy parseConflictPolicy(String[] args, int index, String option) {
        String value = requireValue(args, index, option);
        return switch (value.toLowerCase()) {
            case "rename", "auto-rename", "auto_rename" -> ConflictPolicy.AUTO_RENAME;
            case "overwrite" -> ConflictPolicy.OVERWRITE;
            case "skip" -> ConflictPolicy.SKIP;
            default -> throw new IllegalArgumentException(option + " 仅支持 rename / overwrite / skip: " + value);
        };
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " 缺少参数值");
        }
        return args[index];
    }

    private static int parseInt(String[] args, int index, String option) {
        String value = requireValue(args, index, option);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " 需要整数: " + value);
        }
    }

    private static int parseNonNegativeInt(String[] args, int index, String option) {
        int value = parseInt(args, index, option);
        if (value < 0) {
            throw new IllegalArgumentException(option + " 不能小于 0");
        }
        return value;
    }

    private static int parsePositiveInt(String[] args, int index, String option) {
        int value = parseInt(args, index, option);
        if (value < 1) {
            throw new IllegalArgumentException(option + " 必须大于 0");
        }
        return value;
    }

    private static double parseDouble(String[] args, int index, String option) {
        String value = requireValue(args, index, option);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " 需要数字: " + value);
        }
    }

    private static double parseNonNegativeDouble(String[] args, int index, String option) {
        double value = parseDouble(args, index, option);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(option + " 不能小于 0");
        }
        return value;
    }

    private static ConversionMode parseMode(String[] args, int index, String option) {
        String value = requireValue(args, index, option);
        return switch (value.toLowerCase()) {
            case "txt2docx", "txt-to-docx" -> ConversionMode.TXT_TO_DOCX;
            case "docx2txt", "docx-to-txt" -> ConversionMode.DOCX_TO_TXT;
            case "epub2docx", "epub-to-docx" -> ConversionMode.EPUB_TO_DOCX;
            default -> throw new IllegalArgumentException(option + " 仅支持 txt2docx / docx2txt / epub2docx: " + value);
        };
    }

    private static void printUsage(PrintStream out) {
        out.println("TXT / EPUB / DOCX 批量转换");
        out.println();
        out.println("GUI 模式:");
        out.println("  java -jar target/txt2docx.jar");
        out.println("  java -jar target/txt2docx.jar --gui");
        out.println();
        out.println("CLI 模式:");
        out.println("  java -jar target/txt2docx.jar --input ./txt --output ./docx --recursive");
        out.println("  java -jar target/txt2docx.jar --mode docx2txt -i a.docx -o ./out --encoding UTF-8");
        out.println("  java -jar target/txt2docx.jar --mode epub2docx -i ./epub -o ./docx --recursive");
        out.println();
        out.println("参数:");
        out.println("  -i, --input <path>       输入文件或目录，可重复");
        out.println("  -o, --output <dir>      输出目录");
        out.println("      --mode <name>        转换方向: txt2docx(默认) / docx2txt / epub2docx");
        out.println("  -r, --recursive         递归扫描子目录");
        out.println("      --overwrite         冲突时覆盖已存在文件 (等价 --on-conflict overwrite)");
        out.println("      --on-conflict <p>   冲突策略: rename(默认) / overwrite / skip");
        out.println("      --flatten           不保留输入目录结构");
        out.println("      --preserve-tree     保留输入目录结构，默认开启");
        out.println("      --encoding <name>   文本编码，默认 AUTO");
        out.println("      --font <name>       字体，默认 宋体");
        out.println("      --font-size <n>     字号，默认 12");
        out.println("      --margin-top <cm>   上边距，默认 2.54");
        out.println("      --margin-bottom <cm>下边距，默认 2.54");
        out.println("      --margin-left <cm>  左边距，默认 3.18");
        out.println("      --margin-right <cm> 右边距，默认 3.18");
        out.println("      --remove-spaces     删除每行中的空格和制表符");
        out.println("      --remove-empty-lines 删除空行");
        out.println("      --indent <n>        Word 段落首行缩进 n 个字符");
        out.println("      --blank-line-between-lines  行与行之间插入一空行");
        out.println("  -h, --help              显示帮助");
    }
}
