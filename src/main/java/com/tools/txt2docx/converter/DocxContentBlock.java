package com.tools.txt2docx.converter;

import java.io.IOException;

interface DocxContentBlock {
}

record DocxTextBlock(String text) implements DocxContentBlock {
    DocxTextBlock {
        text = text == null ? "" : text;
    }
}

// Holds image metadata + a lazy byte source so EpubToDocxConverter can avoid materializing
// every image's full payload into the block list. Bytes are pulled in at write time and
// released immediately after POI consumes them.
record DocxImageBlock(ByteSource source, String fileName, int pictureType, String altText) implements DocxContentBlock {
    DocxImageBlock {
        if (source == null) source = ByteSource.of(new byte[0]);
        fileName = fileName == null || fileName.isBlank() ? "image" : fileName;
        altText = altText == null ? "" : altText;
    }
}

@FunctionalInterface
interface ByteSource {
    byte[] read() throws IOException;

    static ByteSource of(byte[] bytes) {
        byte[] snapshot = bytes == null ? new byte[0] : bytes;
        return () -> snapshot;
    }
}
