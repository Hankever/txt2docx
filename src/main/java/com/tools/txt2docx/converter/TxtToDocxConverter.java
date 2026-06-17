package com.tools.txt2docx.converter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TxtToDocxConverter {

    private final ConversionOptions options;

    public TxtToDocxConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputTxt, Path outputDocx) throws IOException {
        Charset charset = resolveCharset(inputTxt);
        List<String> rawLines = readLines(inputTxt, charset);
        new DocxDocumentWriter(options).writeLines(rawLines, outputDocx);
    }

    private Charset resolveCharset(Path inputTxt) throws IOException {
        return CharsetSupport.resolveInputCharset(options.getEncoding(), inputTxt);
    }

    private List<String> readLines(Path inputTxt, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(inputTxt)) {
            if (charset.equals(CharsetSupport.toCharset("UTF-16"))) {
                // UTF-16 relies on the BOM to decide endianness; leave it in the stream and let
                // the decoder consume it instead of stripping it ourselves.
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(raw, charset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                return lines;
            }

            try (PushbackInputStream in = new PushbackInputStream(raw, 4)) {
                // Strip BOM at the byte level — Files.newBufferedReader leaves U+FEFF in the
                // stream for UTF-8 / UTF-16LE/BE callers, which would surface as a leading
                // invisible char in the first paragraph. Read 4 bytes so UTF-16 BOM stripping
                // does not split the first code unit and lose one byte from the actual content.
                byte[] head = new byte[4];
                int n = in.read(head);
                int present = Math.max(0, n);
                int skip = present > 0
                        ? EncodingDetector.skipBom(Arrays.copyOf(head, present), charset)
                        : 0;
                if (present - skip > 0) {
                    in.unread(head, skip, present - skip);
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }
}
