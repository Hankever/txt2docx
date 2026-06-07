package com.tools.txt2docx;

import com.tools.txt2docx.cli.CliRunner;
import com.tools.txt2docx.ui.MainFrame;
import com.tools.txt2docx.ui.ThemeManager;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && !contains(args, "--gui")) {
            int exitCode = CliRunner.run(args, System.out, System.err);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }

        try {
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
            UIManager.put("TitledBorder.titleColor", new java.awt.Color(0x1F6FEB));
            ThemeManager.applySavedTheme();
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static boolean contains(String[] args, String expected) {
        for (String arg : args) {
            if (expected.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
