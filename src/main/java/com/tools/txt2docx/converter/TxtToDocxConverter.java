package com.tools.txt2docx.converter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class TxtToDocxConverter {

    private static final double CM_PER_INCH = 2.54;
    private static final int TWIPS_PER_INCH = 1440;

    private final ConversionOptions options;

    public TxtToDocxConverter(ConversionOptions options) {
        this.options = options;
    }

    public void convert(Path inputTxt, Path outputDocx) throws IOException {
        Charset charset = resolveCharset(inputTxt);
        String text = readText(inputTxt, charset);
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(outputDocx)) {
            applyPageMargins(doc);
            writeParagraphs(doc, text);
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

    private String readText(Path inputTxt, Charset charset) throws IOException {
        byte[] all = Files.readAllBytes(inputTxt);
        int skip = EncodingDetector.skipBom(all, charset);
        return new String(all, skip, all.length - skip, charset);
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

    private void writeParagraphs(XWPFDocument doc, String text) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            boolean any = false;
            while ((line = reader.readLine()) != null) {
                any = true;
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                applyFont(run);
                if (!line.isEmpty()) {
                    run.setText(line);
                }
            }
            if (!any) {
                XWPFParagraph p = doc.createParagraph();
                applyFont(p.createRun());
            }
        }
    }

    private void applyFont(XWPFRun run) {
        String font = options.getFontFamily();
        run.setFontFamily(font);
        run.setFontSize(options.getFontSize());
    }

    private static long cmToTwips(double cm) {
        return Math.round(cm / CM_PER_INCH * TWIPS_PER_INCH);
    }
}
