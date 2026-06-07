package com.tools.txt2docx.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatterTest {

    @Test
    void emptyInputReturnsEmpty() {
        ConversionOptions opts = new ConversionOptions();
        assertTrue(TextFormatter.formatLines(List.of(), opts).isEmpty());
    }

    @Test
    void removeSpacesStripsAsciiTabAndFullwidthSpaces() {
        ConversionOptions opts = new ConversionOptions();
        opts.setRemoveSpaces(true);
        // contains: ASCII space, tab, U+3000 IDEOGRAPHIC SPACE, NBSP
        List<String> in = List.of("a b\tc　d e");
        List<String> out = TextFormatter.formatLines(in, opts);
        assertEquals(List.of("abcde"), out);
    }

    @Test
    void removeSpacesDisabledKeepsOriginal() {
        ConversionOptions opts = new ConversionOptions();
        opts.setRemoveSpaces(false);
        List<String> in = List.of("a b c");
        assertEquals(List.of("a b c"), TextFormatter.formatLines(in, opts));
    }

    @Test
    void removeEmptyLinesSkipsBlankAndWhitespaceOnly() {
        ConversionOptions opts = new ConversionOptions();
        opts.setRemoveEmptyLines(true);
        List<String> in = List.of("first", "", "   ", "\t", "last");
        assertEquals(List.of("first", "last"), TextFormatter.formatLines(in, opts));
    }

    @Test
    void addBlankLineBetweenLinesInsertsBetweenButNotAroundEdges() {
        ConversionOptions opts = new ConversionOptions();
        opts.setAddBlankLineBetweenLines(true);
        List<String> in = List.of("a", "b", "c");
        assertEquals(List.of("a", "", "b", "", "c"), TextFormatter.formatLines(in, opts));
    }

    @Test
    void addBlankLineWithSingleLineDoesNotInsert() {
        ConversionOptions opts = new ConversionOptions();
        opts.setAddBlankLineBetweenLines(true);
        assertEquals(List.of("only"), TextFormatter.formatLines(List.of("only"), opts));
    }

    @Test
    void removeEmptyThenAddBlankInsertsExactlyOneBlankBetweenContentLines() {
        ConversionOptions opts = new ConversionOptions();
        opts.setRemoveEmptyLines(true);
        opts.setAddBlankLineBetweenLines(true);
        // Without removeEmptyLines, the original blanks would survive and we'd double up.
        List<String> in = List.of("first", "", "", "second");
        assertEquals(List.of("first", "", "second"), TextFormatter.formatLines(in, opts));
    }

    @Test
    void allFlagsOffPreservesInputExactly() {
        ConversionOptions opts = new ConversionOptions();
        List<String> in = List.of("hello world", "", "second line");
        assertEquals(in, TextFormatter.formatLines(in, opts));
    }
}
