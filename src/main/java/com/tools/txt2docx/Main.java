package com.tools.txt2docx;

import com.tools.txt2docx.cli.CliRunner;
import com.tools.txt2docx.ui.MainFrame;

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
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
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
