package com.tools.txt2docx.batch;

import java.nio.file.Path;

public record BatchItem(Path source, Path relativeOutput) {
}
