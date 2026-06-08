package com.tools.txt2docx.ui;

import com.tools.txt2docx.batch.BatchProcessor;
import com.tools.txt2docx.batch.BatchItem;
import com.tools.txt2docx.batch.ConflictPolicy;
import com.tools.txt2docx.batch.ConversionMode;
import com.tools.txt2docx.batch.ConversionResult;
import com.tools.txt2docx.batch.PathOverlap;
import com.tools.txt2docx.converter.ConversionOptions;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainFrame extends JFrame {

    private final PreferencesStore prefs = new PreferencesStore();
    private final BackgroundPanel backgroundPanel = new BackgroundPanel();
    private final OptionsPanel optionsPanel = new OptionsPanel();

    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final JTextField outputDirField = new JTextField();
    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();

    private final JButton addFilesBtn = new JButton("添加文件...");
    private final JButton addDirBtn = new JButton("添加目录...");
    private final JButton removeBtn = new JButton("移除选中");
    private final JButton clearBtn = new JButton("清空");
    private final JButton chooseOutputBtn = new JButton("选择输出目录...");
    private final JButton convertBtn = new JButton("开始转换");
    private final JButton cancelBtn = new JButton("取消");
    private final JMenuItem chooseBackgroundItem = new JMenuItem("选择背景图...");
    private final JMenuItem clearBackgroundItem = new JMenuItem("清除背景图");

    private final List<Path> inputPaths = new ArrayList<>();
    private final Set<String> inputPathKeys = new HashSet<>();
    private SwingWorker<Void, String> currentWorker;
    private BatchProcessor currentProcessor;

    public MainFrame() {
        super("TXT / EPUB / DOCX 批量转换工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 760);
        setMinimumSize(new Dimension(820, 620));
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());
        applyComponentStyles();
        wireActions();
        loadPreferences();
        cancelBtn.setEnabled(false);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu view = new JMenu("视图");
        JMenu theme = new JMenu("主题");
        ButtonGroup group = new ButtonGroup();
        String current = ThemeManager.getSavedThemeId();
        for (Map.Entry<String, String> e : ThemeManager.themes().entrySet()) {
            String id = e.getKey();
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(e.getValue(), id.equals(current));
            item.addActionListener(ev -> switchTheme(id));
            group.add(item);
            theme.add(item);
        }
        JMenu background = new JMenu("背景图");
        chooseBackgroundItem.addActionListener(e -> onChooseBackgroundImage());
        clearBackgroundItem.addActionListener(e -> clearBackgroundImage());
        clearBackgroundItem.setEnabled(false);
        background.add(chooseBackgroundItem);
        background.add(clearBackgroundItem);
        view.add(theme);
        view.add(background);
        bar.add(view);
        return bar;
    }

    private void switchTheme(String id) {
        ThemeManager.saveTheme(id);
        ThemeManager.apply(id);
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
        makeTransparentContainers(getContentPane());
        repaint();
    }

    private void applyComponentStyles() {
        convertBtn.putClientProperty("JButton.buttonType", "default");
        convertBtn.setFont(convertBtn.getFont().deriveFont(Font.BOLD));

        addFilesBtn.putClientProperty("JButton.buttonType", "roundRect");
        addDirBtn.putClientProperty("JButton.buttonType", "roundRect");
        removeBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearBtn.putClientProperty("JButton.buttonType", "roundRect");
        chooseOutputBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");

        outputDirField.putClientProperty("JTextField.placeholderText", "选择转换结果保存的目录");

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        logArea.setFont(mono);
        logArea.setBackground(new Color(0xFAFBFC));
        logArea.setMargin(new Insets(6, 8, 6, 8));

        fileList.setFixedCellHeight(22);

        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, 22));
    }

    private JPanel buildContent() {
        BackgroundPanel root = backgroundPanel;
        root.setLayout(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        root.add(buildTopPanel(), BorderLayout.NORTH);
        root.add(buildCenterSplit(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);
        makeTransparentContainers(root);
        return root;
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildOutputRow());
        top.add(Box.createVerticalStrut(10));
        top.add(optionsPanel);
        return top;
    }

    private JPanel buildOutputRow() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("输出目录"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)
        ));
        p.add(outputDirField, BorderLayout.CENTER);
        p.add(chooseOutputBtn, BorderLayout.EAST);
        return p;
    }

    private JSplitPane buildCenterSplit() {
        JPanel listPanel = new JPanel(new BorderLayout(4, 6));
        listPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("待转换文件"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)
        ));
        listPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        listButtons.add(addFilesBtn);
        listButtons.add(addDirBtn);
        listButtons.add(removeBtn);
        listButtons.add(clearBtn);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("日志"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)
        ));
        logArea.setEditable(false);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, logPanel);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setDividerSize(6);
        return split;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        progressBar.setStringPainted(true);
        p.add(progressBar, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(cancelBtn);
        right.add(convertBtn);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private void wireActions() {
        addFilesBtn.addActionListener(e -> onAddFiles());
        addDirBtn.addActionListener(e -> onAddDir());
        removeBtn.addActionListener(e -> onRemoveSelected());
        clearBtn.addActionListener(e -> { fileListModel.clear(); inputPaths.clear(); inputPathKeys.clear(); });
        chooseOutputBtn.addActionListener(e -> onChooseOutput());
        convertBtn.addActionListener(e -> onConvert());
        cancelBtn.addActionListener(e -> onCancel());
        optionsPanel.onModeChanged(mode -> refreshModeDependentUi());
        installFileDropHandler();
        refreshModeDependentUi();
    }

    private void installFileDropHandler() {
        fileList.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> dropped = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : dropped) {
                        addInputPath(f.toPath());
                    }
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        fileList.setDropMode(DropMode.ON);
        outputDirField.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> dropped = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : dropped) {
                        if (f.isDirectory()) {
                            outputDirField.setText(f.getAbsolutePath());
                            return true;
                        }
                    }
                    return false;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    private void onAddFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        switch (optionsPanel.getMode()) {
            case DOCX_TO_TXT ->
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Word 文件 (*.docx)", "docx"));
            case EPUB_TO_DOCX, EPUB_TO_TXT ->
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("EPUB 文件 (*.epub)", "epub"));
            case TXT_TO_DOCX ->
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) {
                addInputPath(f.toPath());
            }
        }
    }

    private void onAddDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addInputPath(fc.getSelectedFile().toPath());
        }
    }

    private void onRemoveSelected() {
        int[] idx = fileList.getSelectedIndices();
        for (int i = idx.length - 1; i >= 0; i--) {
            fileListModel.remove(idx[i]);
            Path removed = inputPaths.remove(idx[i]);
            inputPathKeys.remove(pathKey(removed));
        }
    }

    private void onChooseOutput() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onChooseBackgroundImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(
                "图片文件 (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
                "png", "jpg", "jpeg", "gif", "bmp"
        ));
        String saved = prefs.getBackgroundImage();
        if (!saved.isBlank()) {
            File current = new File(saved);
            fc.setCurrentDirectory(current.getParentFile());
            fc.setSelectedFile(current);
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            applyBackgroundImage(fc.getSelectedFile().toPath(), true);
        }
    }

    private boolean applyBackgroundImage(Path imagePath, boolean showError) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new IOException("不支持的图片格式");
            }
            backgroundPanel.setBackgroundImage(image);
            prefs.setBackgroundImage(imagePath.toAbsolutePath().normalize().toString());
            clearBackgroundItem.setEnabled(true);
            repaint();
            return true;
        } catch (Exception ex) {
            if (showError) {
                JOptionPane.showMessageDialog(
                        this,
                        "背景图加载失败: " + ex.getMessage(),
                        "背景图",
                        JOptionPane.WARNING_MESSAGE
                );
            }
            return false;
        }
    }

    private void clearBackgroundImage() {
        backgroundPanel.setBackgroundImage(null);
        prefs.setBackgroundImage("");
        clearBackgroundItem.setEnabled(false);
        repaint();
    }

    private void addInputPath(Path p) {
        String key = pathKey(p);
        if (!inputPathKeys.add(key)) {
            return;
        }
        inputPaths.add(p);
        fileListModel.addElement(p.toString());
    }

    private static String pathKey(Path p) {
        String normalized = p.toAbsolutePath().normalize().toString();
        return isCaseInsensitiveFileSystem() ? normalized.toLowerCase() : normalized;
    }

    private void onConvert() {
        if (inputPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先添加待转换文件或目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String outDir = outputDirField.getText().trim();
        if (outDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择输出目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path outputDir = Paths.get(outDir);

        String overlap = PathOverlap.check(inputPaths, outputDir);
        if (overlap != null) {
            JOptionPane.showMessageDialog(this, overlap, "目录冲突", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ConversionOptions options = optionsPanel.buildConversionOptions();
        boolean recursive = optionsPanel.isRecursive();
        boolean preserveTree = optionsPanel.isPreserveTree();
        ConflictPolicy policy = optionsPanel.getConflictPolicy();
        boolean openAfter = optionsPanel.isOpenAfterDone();
        ConversionMode mode = optionsPanel.getMode();
        savePreferences();

        currentProcessor = new BatchProcessor(options, mode);
        List<Path> snapshot = new ArrayList<>(inputPaths);

        logArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        progressBar.setString("收集文件中...");
        convertBtn.setEnabled(false);
        cancelBtn.setEnabled(true);

        currentWorker = new SwingWorker<>() {
            int total = 0;
            volatile String failureMessage;

            @Override
            protected Void doInBackground() {
                try {
                    List<BatchItem> files = currentProcessor.collectFiles(snapshot, recursive, preserveTree);
                    total = files.size();
                    publish("共找到 " + total + " 个 " + mode.sourceExtension() + " 文件");
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum(Math.max(1, total));
                    });
                    if (total == 0) return null;
                    currentProcessor.process(files, outputDir, policy, (done, tot, last) -> {
                        publish(formatResult(last));
                        setProgress((int) ((done * 100.0) / tot));
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(done);
                            progressBar.setString(done + " / " + tot);
                        });
                    });
                } catch (Exception ex) {
                    failureMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    publish("错误: " + failureMessage);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    logArea.append(s + System.lineSeparator());
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                convertBtn.setEnabled(true);
                cancelBtn.setEnabled(false);

                // Keep "已取消" / surface "失败" instead of clobbering with "完成". SwingWorker
                // runs done() after both successful completion and cancel(), so we have to
                // distinguish here rather than relying on get().
                boolean cancelled = isCancelled();
                if (cancelled) {
                    progressBar.setString("已取消");
                    logArea.append("---- 已取消 ----" + System.lineSeparator());
                } else if (failureMessage != null) {
                    progressBar.setString("失败: " + failureMessage);
                    logArea.append("---- 转换失败 ----" + System.lineSeparator());
                } else {
                    progressBar.setString("完成");
                    logArea.append("---- 转换结束 ----" + System.lineSeparator());
                }
                if (!cancelled && failureMessage == null && openAfter && total > 0) {
                    openOutputDir(outputDir);
                }
            }
        };
        currentWorker.execute();
    }

    private static boolean isCaseInsensitiveFileSystem() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") || os.contains("mac");
    }

    private void openOutputDir(Path dir) {
        // Desktop.open() shells out to the OS file manager and can block the EDT for hundreds
        // of milliseconds on some systems. Run it off the EDT and marshal failures back.
        Thread t = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir.toFile());
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        logArea.append("打开输出目录失败: " + ex.getMessage() + System.lineSeparator()));
            }
        }, "txt2docx-open-output");
        t.setDaemon(true);
        t.start();
    }

    private void onCancel() {
        if (currentProcessor != null) currentProcessor.cancel();
        if (currentWorker != null) currentWorker.cancel(true);
        cancelBtn.setEnabled(false);
        progressBar.setString("已取消");
    }

    private void loadPreferences() {
        outputDirField.setText(prefs.getOutputDir());
        optionsPanel.loadFrom(prefs);
        String backgroundImage = prefs.getBackgroundImage();
        if (backgroundImage.isBlank()) {
            clearBackgroundItem.setEnabled(false);
        } else if (!applyBackgroundImage(Paths.get(backgroundImage), false)) {
            prefs.setBackgroundImage("");
            clearBackgroundItem.setEnabled(false);
        }
        refreshModeDependentUi();
    }

    private void savePreferences() {
        prefs.setOutputDir(outputDirField.getText().trim());
        optionsPanel.saveTo(prefs);
    }

    private void refreshModeDependentUi() {
        optionsPanel.refreshModeDependentUi();
        ConversionMode mode = optionsPanel.getMode();
        addFilesBtn.setText(switch (mode) {
            case DOCX_TO_TXT -> "添加 DOCX...";
            case EPUB_TO_DOCX, EPUB_TO_TXT -> "添加 EPUB...";
            case TXT_TO_DOCX -> "添加 TXT...";
        });
        setTitle(switch (mode) {
            case DOCX_TO_TXT -> "DOCX 批量转 TXT 工具";
            case EPUB_TO_DOCX -> "EPUB 批量转 DOCX 工具";
            case EPUB_TO_TXT -> "EPUB 批量转 TXT 工具";
            case TXT_TO_DOCX -> "TXT 批量转 DOCX 工具";
        });
    }

    private String formatResult(ConversionResult r) {
        String tag;
        switch (r.getStatus()) {
            case SUCCESS: tag = "[OK]"; break;
            case SKIPPED: tag = "[跳过]"; break;
            default: tag = "[失败]"; break;
        }
        String out = r.getOutput() == null ? "" : " -> " + r.getOutput();
        String msg = (r.getMessage() == null || r.getMessage().isEmpty()) ? "" : " (" + r.getMessage() + ")";
        return tag + " " + r.getSource() + out + msg;
    }

    private static void makeTransparentContainers(Component component) {
        if (component instanceof BackgroundPanel panel) {
            panel.setOpaque(true);
        } else if (component instanceof JPanel panel) {
            panel.setOpaque(false);
        } else if (component instanceof JSplitPane splitPane) {
            splitPane.setOpaque(false);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                makeTransparentContainers(child);
            }
        }
    }

    private static final class BackgroundPanel extends JPanel {
        private static final float IMAGE_OPACITY = 0.22f;

        private BufferedImage backgroundImage;

        void setBackgroundImage(BufferedImage backgroundImage) {
            this.backgroundImage = backgroundImage;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            int imageWidth = backgroundImage.getWidth();
            int imageHeight = backgroundImage.getHeight();
            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            double scale = Math.max(getWidth() / (double) imageWidth, getHeight() / (double) imageHeight);
            int drawWidth = (int) Math.round(imageWidth * scale);
            int drawHeight = (int) Math.round(imageHeight * scale);
            int x = (getWidth() - drawWidth) / 2;
            int y = (getHeight() - drawHeight) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.SrcOver.derive(IMAGE_OPACITY));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(backgroundImage, x, y, drawWidth, drawHeight, null);
            } finally {
                g2.dispose();
            }
        }
    }
}
