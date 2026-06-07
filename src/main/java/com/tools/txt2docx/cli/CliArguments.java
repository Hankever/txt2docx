package com.tools.txt2docx.cli;

import com.tools.txt2docx.batch.ConflictPolicy;
import com.tools.txt2docx.batch.ConversionMode;
import com.tools.txt2docx.converter.ConversionOptions;

import java.nio.file.Path;
import java.util.List;

public record CliArguments(
        List<Path> inputs,
        Path outputDir,
        ConversionMode mode,
        boolean recursive,
        ConflictPolicy onConflict,
        boolean preserveDirectoryStructure,
        boolean showHelp,
        ConversionOptions options
) {
}
