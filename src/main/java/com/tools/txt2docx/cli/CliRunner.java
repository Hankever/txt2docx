package com.tools.txt2docx.cli;

import com.tools.txt2docx.batch.BatchItem;
import com.tools.txt2docx.batch.BatchProcessor;
import com.tools.txt2docx.batch.ConversionResult;
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

        BatchProcessor processor = new BatchProcessor(cliArgs.options());
        try {
            Files.createDirectories(cliArgs.outputDir());
            List<BatchItem> items = processor.collectTxtFiles(
                    cliArgs.inputs(),
                    cliArgs.recursive(),
                    cliArgs.preserveDirectoryStructure()
            );
            if (items.isEmpty()) {
                out.println("未找到任何 .txt 文件");
                return 0;
            }

            out.println("共找到 " + items.size() + " 个 .txt 文件，开始转换...");
            List<ConversionResult> results = processor.process(
                    items,
                    cliArgs.outputDir(),
                    cliArgs.overwrite(),
                    (done, total, last) -> out.println(formatProgress(done, total, last))
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
        boolean recursive = false;
        boolean overwrite = false;
        boolean preserveDirectoryStructure = true;
        boolean showHelp = false;

        ConversionOptions options = new ConversionOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> showHelp = true;
                case "-i", "--input" -> inputs.add(Path.of(requireValue(args, ++i, arg)));
                case "-o", "--output" -> outputDir = Path.of(requireValue(args, ++i, arg));
                case "-r", "--recursive" -> recursive = true;
                case "--overwrite" -> overwrite = true;
                case "--flatten" -> preserveDirectoryStructure = false;
                case "--preserve-tree" -> preserveDirectoryStructure = true;
                case "--encoding" -> options.setEncoding(requireValue(args, ++i, arg));
                case "--font" -> options.setFontFamily(requireValue(args, ++i, arg));
                case "--font-size" -> options.setFontSize(parseInt(args, ++i, arg));
                case "--margin-top" -> options.setMarginTopCm(parseDouble(args, ++i, arg));
                case "--margin-bottom" -> options.setMarginBottomCm(parseDouble(args, ++i, arg));
                case "--margin-left" -> options.setMarginLeftCm(parseDouble(args, ++i, arg));
                case "--margin-right" -> options.setMarginRightCm(parseDouble(args, ++i, arg));
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

        return new CliArguments(inputs, outputDir, recursive, overwrite, preserveDirectoryStructure, showHelp, options);
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

    private static double parseDouble(String[] args, int index, String option) {
        String value = requireValue(args, index, option);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " 需要数字: " + value);
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("TXT 批量转 DOCX");
        out.println();
        out.println("GUI 模式:");
        out.println("  java -jar target/txt2docx.jar");
        out.println("  java -jar target/txt2docx.jar --gui");
        out.println();
        out.println("CLI 模式:");
        out.println("  java -jar target/txt2docx.jar --input ./txt --output ./docx --recursive");
        out.println("  java -jar target/txt2docx.jar -i a.txt -i b.txt -o ./out --encoding UTF-8");
        out.println();
        out.println("参数:");
        out.println("  -i, --input <path>       输入文件或目录，可重复");
        out.println("  -o, --output <dir>      输出目录");
        out.println("  -r, --recursive         递归扫描子目录");
        out.println("      --overwrite         允许覆盖已存在的输出文件");
        out.println("      --flatten           不保留输入目录结构");
        out.println("      --preserve-tree     保留输入目录结构，默认开启");
        out.println("      --encoding <name>   文本编码，默认 AUTO");
        out.println("      --font <name>       字体，默认 宋体");
        out.println("      --font-size <n>     字号，默认 12");
        out.println("      --margin-top <cm>   上边距，默认 2.54");
        out.println("      --margin-bottom <cm>下边距，默认 2.54");
        out.println("      --margin-left <cm>  左边距，默认 3.18");
        out.println("      --margin-right <cm> 右边距，默认 3.18");
        out.println("  -h, --help              显示帮助");
    }
}
