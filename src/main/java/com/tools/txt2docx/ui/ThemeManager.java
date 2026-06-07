package com.tools.txt2docx.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public final class ThemeManager {

    public static final String PREF_THEME = "theme";

    private static final Map<String, String> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("light", "浅色");
        THEMES.put("dark", "深色");
        THEMES.put("intellij", "IntelliJ");
        THEMES.put("darcula", "Darcula");
    }

    private ThemeManager() {
    }

    public static Map<String, String> themes() {
        return THEMES;
    }

    public static String getSavedThemeId() {
        return Preferences.userNodeForPackage(MainFrame.class).get(PREF_THEME, "light");
    }

    public static void saveTheme(String id) {
        Preferences.userNodeForPackage(MainFrame.class).put(PREF_THEME, id);
    }

    public static void applySavedTheme() {
        apply(getSavedThemeId());
    }

    public static void apply(String id) {
        LookAndFeel laf;
        switch (id) {
            case "dark":     laf = new FlatDarkLaf(); break;
            case "intellij": laf = new FlatIntelliJLaf(); break;
            case "darcula":  laf = new FlatDarculaLaf(); break;
            case "light":
            default:         laf = new FlatLightLaf(); break;
        }
        try {
            UIManager.setLookAndFeel(laf);
        } catch (Exception ignored) {
        }
    }
}
