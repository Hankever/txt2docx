package com.tools.txt2docx.batch;

public enum ConversionMode {
    TXT_TO_DOCX(".txt", ".docx"),
    DOCX_TO_TXT(".docx", ".txt"),
    EPUB_TO_DOCX(".epub", ".docx"),
    EPUB_TO_TXT(".epub", ".txt");

    private final String sourceExtension;
    private final String targetExtension;

    ConversionMode(String sourceExtension, String targetExtension) {
        this.sourceExtension = sourceExtension;
        this.targetExtension = targetExtension;
    }

    public String sourceExtension() {
        return sourceExtension;
    }

    public String targetExtension() {
        return targetExtension;
    }

    public boolean outputsDocx() {
        return ".docx".equals(targetExtension);
    }
}
