package com.tools.txt2docx.converter;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class DocxDocumentWriter {

    private static final double CM_PER_INCH = 2.54;
    private static final double POINTS_PER_INCH = 72.0;
    private static final int TWIPS_PER_INCH = 1440;
    private static final int TWIPS_PER_CHARACTER = 210;
    private static final double DEFAULT_PAGE_WIDTH_CM = 21.0;
    private static final double DEFAULT_PAGE_HEIGHT_CM = 29.7;
    private static final int DEFAULT_IMAGE_WIDTH_PX = 480;
    private static final int DEFAULT_IMAGE_HEIGHT_PX = 360;

    private final ConversionOptions options;

    DocxDocumentWriter(ConversionOptions options) {
        this.options = options;
    }

    void writeLines(List<String> rawLines, Path outputDocx) throws IOException {
        writeAtomically(outputDocx, target -> {
            try (XWPFDocument doc = new XWPFDocument();
                 OutputStream out = Files.newOutputStream(target)) {
                applyPageMargins(doc);
                writeParagraphs(doc, TextFormatter.formatLines(rawLines, options));
                doc.write(out);
            }
        });
    }

    void writeBlocks(List<DocxContentBlock> blocks, Path outputDocx) throws IOException {
        writeAtomically(outputDocx, target -> {
            try (XWPFDocument doc = new XWPFDocument();
                 OutputStream out = Files.newOutputStream(target)) {
                applyPageMargins(doc);
                int written = writeContentBlocks(doc, blocks);
                if (written == 0) {
                    XWPFParagraph p = doc.createParagraph();
                    applyFont(p.createRun());
                }
                doc.write(out);
            }
        });
    }

    // Write to a sibling .tmp file and move it into place once complete. Prevents readers (and
    // the user) from seeing a half-written docx if the JVM is interrupted mid-write — which is
    // exactly what BatchProcessor.cancel() triggers via executor.shutdownNow().
    static void writeAtomically(Path target, IoConsumer<Path> writer) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            writer.accept(tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw ex;
        }
    }

    @FunctionalInterface
    interface IoConsumer<T> {
        void accept(T t) throws IOException;
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

    private int writeContentBlocks(XWPFDocument doc, List<DocxContentBlock> blocks) {
        int written = 0;
        List<String> pendingText = new ArrayList<>();
        for (DocxContentBlock block : blocks) {
            if (block instanceof DocxTextBlock text) {
                pendingText.add(text.text());
                continue;
            }
            written += writePendingText(doc, pendingText);
            if (block instanceof DocxImageBlock image) {
                written += writeImage(doc, image);
            }
        }
        written += writePendingText(doc, pendingText);
        return written;
    }

    private int writePendingText(XWPFDocument doc, List<String> pendingText) {
        if (pendingText.isEmpty()) {
            return 0;
        }
        List<String> lines = TextFormatter.formatLines(pendingText, options);
        pendingText.clear();
        for (String line : lines) {
            XWPFParagraph p = doc.createParagraph();
            applyParagraphFormat(p, line);
            XWPFRun run = p.createRun();
            applyFont(run);
            if (!line.isEmpty()) {
                run.setText(line);
            }
        }
        return lines.size();
    }

    private int writeImage(XWPFDocument doc, DocxImageBlock image) {
        if (image.pictureType() < 0) {
            return writeFallbackAltText(doc, image.altText());
        }
        byte[] data;
        try {
            data = image.source().read();
        } catch (IOException ex) {
            return writeFallbackAltText(doc, image.altText());
        }
        if (data.length == 0) {
            return writeFallbackAltText(doc, image.altText());
        }

        ImageSize size = readImageSize(data);
        ImageSize scaled = scaleToPageWidth(size);
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            run.addPicture(
                    in,
                    image.pictureType(),
                    image.fileName(),
                    Units.pixelToEMU(scaled.widthPx()),
                    Units.pixelToEMU(scaled.heightPx())
            );
            return 1;
        } catch (IOException | InvalidFormatException ex) {
            return writeFallbackAltText(doc, image.altText());
        }
    }

    private int writeFallbackAltText(XWPFDocument doc, String altText) {
        if (altText == null || altText.isBlank()) {
            return 0;
        }
        List<String> lines = TextFormatter.formatLines(List.of(altText), options);
        for (String line : lines) {
            XWPFParagraph p = doc.createParagraph();
            applyParagraphFormat(p, line);
            XWPFRun run = p.createRun();
            applyFont(run);
            if (!line.isEmpty()) {
                run.setText(line);
            }
        }
        return lines.size();
    }

    private static ImageSize readImageSize(byte[] data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                return new ImageSize(image.getWidth(), image.getHeight());
            }
        } catch (IOException ignored) {
        }
        return new ImageSize(DEFAULT_IMAGE_WIDTH_PX, DEFAULT_IMAGE_HEIGHT_PX);
    }

    private ImageSize scaleToPageWidth(ImageSize size) {
        int maxWidthPx = Math.max(96, emuToPixel(maxImageWidthEmu()));
        int maxHeightPx = Math.max(96, emuToPixel(maxImageHeightEmu()));

        double widthScale = size.widthPx() <= maxWidthPx ? 1.0 : maxWidthPx / (double) size.widthPx();
        double heightScale = size.heightPx() <= maxHeightPx ? 1.0 : maxHeightPx / (double) size.heightPx();
        double scale = Math.min(widthScale, heightScale);
        if (scale >= 1.0) {
            return size;
        }
        int scaledWidth = Math.max(1, (int) Math.round(size.widthPx() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(size.heightPx() * scale));
        return new ImageSize(scaledWidth, scaledHeight);
    }

    private int maxImageWidthEmu() {
        double widthCm = DEFAULT_PAGE_WIDTH_CM - options.getMarginLeftCm() - options.getMarginRightCm();
        widthCm = Math.max(2.0, widthCm);
        return Units.toEMU(widthCm / CM_PER_INCH * POINTS_PER_INCH);
    }

    private int maxImageHeightEmu() {
        double heightCm = DEFAULT_PAGE_HEIGHT_CM - options.getMarginTopCm() - options.getMarginBottomCm();
        heightCm = Math.max(2.0, heightCm);
        return Units.toEMU(heightCm / CM_PER_INCH * POINTS_PER_INCH);
    }

    private static int emuToPixel(int emu) {
        return Math.max(1, (int) Math.round(emu / (double) Units.EMU_PER_PIXEL));
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

    private record ImageSize(int widthPx, int heightPx) {
    }
}
