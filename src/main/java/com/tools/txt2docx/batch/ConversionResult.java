package com.tools.txt2docx.batch;

import java.nio.file.Path;

public class ConversionResult {
    public enum Status { SUCCESS, FAILED, SKIPPED }

    private final Path source;
    private final Path output;
    private final Status status;
    private final String message;

    public ConversionResult(Path source, Path output, Status status, String message) {
        this.source = source;
        this.output = output;
        this.status = status;
        this.message = message;
    }

    public Path getSource() { return source; }
    public Path getOutput() { return output; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
}
