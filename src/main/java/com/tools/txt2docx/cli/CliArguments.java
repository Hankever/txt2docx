package com.tools.txt2docx.cli;

import com.tools.txt2docx.converter.ConversionOptions;

import java.nio.file.Path;
import java.util.List;

public record CliArguments(
        List<Path> inputs,
        Path outputDir,
        boolean recursive,
        boolean overwrite,
        boolean preserveDirectoryStructure,
        boolean showHelp,
        ConversionOptions options
) {
}
