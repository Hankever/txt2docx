package com.tools.txt2docx.ui;

import java.util.prefs.Preferences;

public final class PreferencesStore {

    private static final String PREF_MODE = "mode";
    private static final String PREF_OUTPUT_DIR = "outputDir";
    private static final String PREF_FONT = "font";
    private static final String PREF_FONT_SIZE = "fontSize";
    private static final String PREF_MARGIN_TOP = "marginTop";
    private static final String PREF_MARGIN_BOTTOM = "marginBottom";
    private static final String PREF_MARGIN_LEFT = "marginLeft";
    private static final String PREF_MARGIN_RIGHT = "marginRight";
    private static final String PREF_INDENT = "indent";
    private static final String PREF_ENCODING = "encoding";
    private static final String PREF_RECURSIVE = "recursive";
    private static final String PREF_PRESERVE_TREE = "preserveTree";
    private static final String PREF_CONFLICT = "conflictPolicy";
    private static final String PREF_OPEN_AFTER_DONE = "openAfterDone";
    private static final String PREF_REMOVE_SPACES = "removeSpaces";
    private static final String PREF_REMOVE_EMPTY_LINES = "removeEmptyLines";
    private static final String PREF_BLANK_LINE_BETWEEN_LINES = "blankLineBetweenLines";
    private static final String PREF_BACKGROUND_IMAGE = "backgroundImage";

    private final Preferences prefs;

    public PreferencesStore() {
        this.prefs = Preferences.userNodeForPackage(MainFrame.class);
    }

    public int getModeIndex()                       { return prefs.getInt(PREF_MODE, 0); }
    public void setModeIndex(int v)                 { prefs.putInt(PREF_MODE, v); }

    public String getOutputDir()                    { return prefs.get(PREF_OUTPUT_DIR, ""); }
    public void setOutputDir(String v)              { prefs.put(PREF_OUTPUT_DIR, v); }

    public String getFont()                         { return prefs.get(PREF_FONT, "宋体"); }
    public void setFont(String v)                   { prefs.put(PREF_FONT, v); }

    public int getFontSize()                        { return prefs.getInt(PREF_FONT_SIZE, 12); }
    public void setFontSize(int v)                  { prefs.putInt(PREF_FONT_SIZE, v); }

    public double getMarginTop()                    { return prefs.getDouble(PREF_MARGIN_TOP, 2.54); }
    public void setMarginTop(double v)              { prefs.putDouble(PREF_MARGIN_TOP, v); }

    public double getMarginBottom()                 { return prefs.getDouble(PREF_MARGIN_BOTTOM, 2.54); }
    public void setMarginBottom(double v)           { prefs.putDouble(PREF_MARGIN_BOTTOM, v); }

    public double getMarginLeft()                   { return prefs.getDouble(PREF_MARGIN_LEFT, 3.18); }
    public void setMarginLeft(double v)             { prefs.putDouble(PREF_MARGIN_LEFT, v); }

    public double getMarginRight()                  { return prefs.getDouble(PREF_MARGIN_RIGHT, 3.18); }
    public void setMarginRight(double v)            { prefs.putDouble(PREF_MARGIN_RIGHT, v); }

    public int getIndent()                          { return prefs.getInt(PREF_INDENT, 2); }
    public void setIndent(int v)                    { prefs.putInt(PREF_INDENT, v); }

    public String getEncoding()                     { return prefs.get(PREF_ENCODING, "AUTO"); }
    public void setEncoding(String v)               { prefs.put(PREF_ENCODING, v); }

    public boolean isRecursive()                    { return prefs.getBoolean(PREF_RECURSIVE, true); }
    public void setRecursive(boolean v)             { prefs.putBoolean(PREF_RECURSIVE, v); }

    public boolean isPreserveTree()                 { return prefs.getBoolean(PREF_PRESERVE_TREE, true); }
    public void setPreserveTree(boolean v)          { prefs.putBoolean(PREF_PRESERVE_TREE, v); }

    public String getConflictLabel(String dflt)     { return prefs.get(PREF_CONFLICT, dflt); }
    public void setConflictLabel(String v)          { prefs.put(PREF_CONFLICT, v); }

    public boolean isOpenAfterDone()                { return prefs.getBoolean(PREF_OPEN_AFTER_DONE, false); }
    public void setOpenAfterDone(boolean v)         { prefs.putBoolean(PREF_OPEN_AFTER_DONE, v); }

    public boolean isRemoveSpaces()                 { return prefs.getBoolean(PREF_REMOVE_SPACES, true); }
    public void setRemoveSpaces(boolean v)          { prefs.putBoolean(PREF_REMOVE_SPACES, v); }

    public boolean isRemoveEmptyLines()             { return prefs.getBoolean(PREF_REMOVE_EMPTY_LINES, true); }
    public void setRemoveEmptyLines(boolean v)      { prefs.putBoolean(PREF_REMOVE_EMPTY_LINES, v); }

    public boolean isBlankLineBetweenLines()        { return prefs.getBoolean(PREF_BLANK_LINE_BETWEEN_LINES, true); }
    public void setBlankLineBetweenLines(boolean v) { prefs.putBoolean(PREF_BLANK_LINE_BETWEEN_LINES, v); }

    public String getBackgroundImage()              { return prefs.get(PREF_BACKGROUND_IMAGE, ""); }
    public void setBackgroundImage(String v) {
        if (v == null || v.isBlank()) {
            prefs.remove(PREF_BACKGROUND_IMAGE);
        } else {
            prefs.put(PREF_BACKGROUND_IMAGE, v);
        }
    }
}
