package com.tools.txt2docx.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubToTxtConverterTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    );

    private static ConversionOptions defaultOptions() {
        ConversionOptions opts = new ConversionOptions();
        opts.setEncoding("UTF-8");
        opts.setRemoveSpaces(false);
        opts.setRemoveEmptyLines(false);
        opts.setAddBlankLineBetweenLines(false);
        return opts;
    }

    @Test
    void spineOrderedXhtmlIsConvertedToPlainText(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("book.epub");
        writeMinimalEpub(src);

        Path dst = dir.resolve("book.txt");
        new EpubToTxtConverter(defaultOptions()).convert(src, dst);

        String txt = Files.readString(dst, StandardCharsets.UTF_8);
        int chapterOne = txt.indexOf("第一章");
        int chapterTwo = txt.indexOf("第二章");
        assertTrue(chapterOne >= 0, "missing first chapter in: " + txt);
        assertTrue(chapterTwo > chapterOne, "spine order was not preserved in: " + txt);
        assertTrue(txt.contains("Tom & Jerry"), "HTML entity not decoded in: " + txt);
        // Images must not surface in the TXT body; alt text is the only acceptable fallback,
        // and the minimal EPUB's image has its own alt that should not appear here either.
        assertFalse(txt.contains("PNG"), "image marker leaked into txt: " + txt);
    }

    @Test
    void missingImagesDoNotLeakAltTextIntoPlainText(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("book-missing-image.epub");
        writeEpubWithMissingImage(src);

        Path dst = dir.resolve("book-missing-image.txt");
        new EpubToTxtConverter(defaultOptions()).convert(src, dst);

        String txt = Files.readString(dst, StandardCharsets.UTF_8);
        assertTrue(txt.contains("正文"), "missing surrounding body text in: " + txt);
        assertFalse(txt.contains("失效插图"), "broken image alt text leaked into txt: " + txt);
    }

    private static void writeMinimalEpub(Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/chapters/chapter1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>ignored</title></head>
                      <body>
                        <h1>第一章</h1>
                        <p>Tom &amp; Jerry</p>
                        <p><img src="../images/cover.png" alt="封面图"/></p>
                      </body>
                    </html>
                    """);
            put(zip, "OEBPS/chapters/chapter2.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>ignored</title></head>
                      <body>
                        <h1>第二章</h1>
                        <p>第二章正文</p>
                      </body>
                    </html>
                    """);
            zip.putNextEntry(new ZipEntry("OEBPS/images/cover.png"));
            zip.write(TINY_PNG);
            zip.closeEntry();
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="c1" href="chapters/chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="chapters/chapter2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="cover" href="images/cover.png" media-type="image/png"/>
                      </manifest>
                      <spine>
                        <itemref idref="c1"/>
                        <itemref idref="c2"/>
                      </spine>
                    </package>
                    """);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeEpubWithMissingImage(Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            put(zip, "OEBPS/chapter.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body>
                        <p>正文</p>
                        <p><img src="images/missing.png" alt="失效插图"/></p>
                      </body>
                    </html>
                    """);
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                    """);
        }
    }
}
