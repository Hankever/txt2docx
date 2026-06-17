package com.tools.txt2docx.converter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class EpubToTxtConverter {

    private final ConversionOptions options;

    public EpubToTxtConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputEpub, Path outputTxt) throws IOException {
        List<String> rawLines;
        try (ZipFile zip = new ZipFile(inputEpub.toFile())) {
            // Image blocks have lazy ByteSource lambdas that read from the open zip; we only
            // consume text blocks here, so the lambdas never fire and closing the zip on exit
            // is safe.
            List<DocxContentBlock> blocks = new EpubToDocxConverter(options).readEpubBlocks(zip, false);
            rawLines = new ArrayList<>(blocks.size());
            for (DocxContentBlock block : blocks) {
                if (block instanceof DocxTextBlock text) {
                    rawLines.add(text.text());
                }
            }
        }

        List<String> formatted = TextFormatter.formatLines(rawLines, options);
        String body = String.join(System.lineSeparator(), formatted);
        Charset charset = resolveCharset();
        DocxDocumentWriter.writeAtomically(outputTxt, target -> Files.writeString(target, body, charset));
    }

    private Charset resolveCharset() {
        return CharsetSupport.resolveOutputCharset(options.getEncoding());
    }
}
