package com.tools.txt2docx.converter;

interface DocxContentBlock {
}

record DocxTextBlock(String text) implements DocxContentBlock {
    DocxTextBlock {
        text = text == null ? "" : text;
    }
}

record DocxImageBlock(byte[] data, String fileName, int pictureType, String altText) implements DocxContentBlock {
    DocxImageBlock {
        data = data == null ? new byte[0] : data;
        fileName = fileName == null || fileName.isBlank() ? "image" : fileName;
        altText = altText == null ? "" : altText;
    }
}
