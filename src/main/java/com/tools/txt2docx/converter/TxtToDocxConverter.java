package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TxtToDocxConverter {

    private static final double CM_PER_INCH = 2.54;
    private static final int TWIPS_PER_INCH = 1440;
    private static final int TWIPS_PER_CHARACTER = 210;

    private final ConversionOptions options;

    public TxtToDocxConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputTxt, Path outputDocx) throws IOException {
        Charset charset = resolveCharset(inputTxt);
        List<String> rawLines = readLines(inputTxt, charset);
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(outputDocx)) {
            applyPageMargins(doc);
            writeParagraphs(doc, TextFormatter.formatLines(rawLines, options));
            doc.write(out);
        }
    }

    private Charset resolveCharset(Path inputTxt) throws IOException {
        String encoding = options.getEncoding();
        if (encoding == null || encoding.isBlank() || "AUTO".equalsIgnoreCase(encoding)) {
            return EncodingDetector.detect(inputTxt);
        }
        return Charset.forName(encoding);
    }

    private List<String> readLines(Path inputTxt, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(inputTxt);
             PushbackInputStream in = new PushbackInputStream(raw, 4)) {
            // Strip BOM at the byte level — Files.newBufferedReader leaves U+FEFF in the
            // stream for UTF-8 / UTF-16 callers, which would surface as a leading invisible
            // char in the first paragraph.
            byte[] head = new byte[3];
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
        return lines;
    }

    private void applyPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(cmToTwips(options.getMarginTopCm())));
        pageMar.setBottom(BigInteger.valueOf(cmToTwips(options.getMarginBottomCm())));
        pageMar.setLeft(BigInteger.valueOf(cmToTwips(options.getMarginLeftCm())));
        pageMar.setRight(BigInteger.valueOf(cmToTwips(options.getMarginRightCm())));
    }

    private void writeParagraphs(XWPFDocument doc, List<String> lines) {
        if (lines.isEmpty()) {
            XWPFParagraph p = doc.createParagraph();
            applyFont(p.createRun());
            return;
        }

        for (String line : lines) {
            XWPFParagraph p = doc.createParagraph();
            applyParagraphFormat(p, line);
            XWPFRun run = p.createRun();
            applyFont(run);
            if (!line.isEmpty()) {
                run.setText(line);
            }
        }
    }

    private void applyFont(XWPFRun run) {
        String font = options.getFontFamily();
        run.setFontFamily(font);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(options.getFontSize());
    }

    private void applyParagraphFormat(XWPFParagraph paragraph, String line) {
        if (!line.isEmpty() && options.getIndentSize() > 0) {
            paragraph.setIndentationFirstLine(options.getIndentSize() * TWIPS_PER_CHARACTER);
        }
    }

    private static long cmToTwips(double cm) {
        return Math.round(cm / CM_PER_INCH * TWIPS_PER_INCH);
    }
}
