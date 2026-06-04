package com.tools.txt2docx.ui;

import com.tools.txt2docx.batch.BatchProcessor;
import com.tools.txt2docx.batch.BatchItem;
import com.tools.txt2docx.batch.ConversionMode;
import com.tools.txt2docx.batch.ConversionResult;
import com.tools.txt2docx.converter.ConversionOptions;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MainFrame extends JFrame {

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
    private static final String PREF_OVERWRITE = "overwrite";
    private static final String PREF_REMOVE_SPACES = "removeSpaces";
    private static final String PREF_REMOVE_EMPTY_LINES = "removeEmptyLines";
    private static final String PREF_BLANK_LINE_BETWEEN_LINES = "blankLineBetweenLines";

    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final JTextField outputDirField = new JTextField();
    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"TXT -> DOCX", "DOCX -> TXT"});
    private final JComboBox<String> fontBox = new JComboBox<>(new String[]{"宋体", "微软雅黑", "黑体", "楷体", "仿宋", "Times New Roman", "Arial"});
    private final JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 6, 72, 1));
    private final JSpinner marginTopSpinner = new JSpinner(new SpinnerNumberModel(2.54, 0.0, 10.0, 0.1));
    private final JSpinner marginBottomSpinner = new JSpinner(new SpinnerNumberModel(2.54, 0.0, 10.0, 0.1));
    private final JSpinner marginLeftSpinner = new JSpinner(new SpinnerNumberModel(3.18, 0.0, 10.0, 0.1));
    private final JSpinner marginRightSpinner = new JSpinner(new SpinnerNumberModel(3.18, 0.0, 10.0, 0.1));
    private final JSpinner indentSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 10, 1));
    private final JComboBox<String> encodingBox = new JComboBox<>(new String[]{"AUTO", "UTF-8", "GBK", "GB2312", "GB18030", "UTF-16LE", "UTF-16BE", "Big5"});
    private final JCheckBox recursiveBox = new JCheckBox("递归子目录", true);
    private final JCheckBox preserveTreeBox = new JCheckBox("保留目录结构", true);
    private final JCheckBox overwriteBox = new JCheckBox("覆盖已有文件", false);
    private final JCheckBox removeSpacesBox = new JCheckBox("删除空格", true);
    private final JCheckBox removeEmptyLinesBox = new JCheckBox("删除空行", true);
    private final JCheckBox blankLineBetweenLinesBox = new JCheckBox("行间加空行", true);

    private final JButton addFilesBtn = new JButton("添加文件...");
    private final JButton addDirBtn = new JButton("添加目录...");
    private final JButton removeBtn = new JButton("移除选中");
    private final JButton clearBtn = new JButton("清空");
    private final JButton chooseOutputBtn = new JButton("选择输出目录...");
    private final JButton convertBtn = new JButton("开始转换");
    private final JButton cancelBtn = new JButton("取消");

    private final List<Path> inputPaths = new ArrayList<>();
    private SwingWorker<Void, String> currentWorker;
    private BatchProcessor currentProcessor;

    public MainFrame() {
        super("TXT 批量转 DOCX 工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 760);
        setMinimumSize(new Dimension(820, 620));
        setLocationRelativeTo(null);

        applyOptionFieldSizes();
        setContentPane(buildContent());
        wireActions();
        loadPreferences();
        cancelBtn.setEnabled(false);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(buildTopPanel(), BorderLayout.NORTH);
        root.add(buildCenterSplit(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildOutputRow());
        top.add(Box.createVerticalStrut(6));
        top.add(buildOptionsPanel());
        return top;
    }

    private JPanel buildOutputRow() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBorder(BorderFactory.createTitledBorder("输出目录"));
        p.add(outputDirField, BorderLayout.CENTER);
        p.add(chooseOutputBtn, BorderLayout.EAST);
        return p;
    }

    private JPanel buildOptionsPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createTitledBorder("转换设置"));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(buildBasicOptionsSection(), BorderLayout.WEST);
        top.add(buildLayoutOptionsSection(), BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        p.add(buildCheckboxSection(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBasicOptionsSection() {
        JPanel p = createSectionPanel("基础");
        GridBagConstraints c = baseConstraints();

        addLabel(p, c, 0, 0, "转换方向");
        addField(p, c, 1, 0, modeBox);
        addLabel(p, c, 2, 0, "编码");
        addField(p, c, 3, 0, encodingBox);

        addLabel(p, c, 0, 1, "字体");
        addField(p, c, 1, 1, fontBox);
        addLabel(p, c, 2, 1, "字号");
        addField(p, c, 3, 1, fontSizeSpinner);

        return p;
    }

    private JPanel buildLayoutOptionsSection() {
        JPanel p = createSectionPanel("版式");
        GridBagConstraints c = baseConstraints();

        addLabel(p, c, 0, 0, "文本缩进");
        addField(p, c, 1, 0, indentSpinner);
        addLabel(p, c, 2, 0, "上边距(cm)");
        addField(p, c, 3, 0, marginTopSpinner);

        addLabel(p, c, 0, 1, "下边距(cm)");
        addField(p, c, 1, 1, marginBottomSpinner);
        addLabel(p, c, 2, 1, "左边距(cm)");
        addField(p, c, 3, 1, marginLeftSpinner);

        addLabel(p, c, 0, 2, "右边距(cm)");
        addField(p, c, 1, 2, marginRightSpinner);

        return p;
    }

    private JPanel buildCheckboxSection() {
        JPanel p = createSectionPanel("选项");
        GridBagConstraints c = baseConstraints();

        c.gridx = 0; c.gridy = 0; c.gridwidth = 1;
        p.add(removeSpacesBox, c);
        c.gridx = 1; c.gridy = 0; c.gridwidth = 1;
        p.add(removeEmptyLinesBox, c);
        c.gridx = 2; c.gridy = 0; c.gridwidth = 1;
        p.add(blankLineBetweenLinesBox, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 1;
        p.add(recursiveBox, c);
        c.gridx = 1; c.gridy = 1; c.gridwidth = 1;
        p.add(preserveTreeBox, c);
        c.gridx = 2; c.gridy = 1; c.gridwidth = 1;
        p.add(overwriteBox, c);

        return p;
    }

    private JPanel createSectionPanel(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        return p;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        return c;
    }

    private void addLabel(JPanel p, GridBagConstraints c, int x, int y, String text) {
        c.gridx = x; c.gridy = y; c.weightx = 0;
        p.add(new JLabel(text), c);
    }

    private void addField(JPanel p, GridBagConstraints c, int x, int y, java.awt.Component comp) {
        c.gridx = x; c.gridy = y; c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(comp, c);
        c.fill = GridBagConstraints.HORIZONTAL;
    }

    private void applyOptionFieldSizes() {
        setCompactWidth(modeBox, 150);
        setCompactWidth(fontBox, 120);
        setCompactWidth(fontSizeSpinner, 90);
        setCompactWidth(marginTopSpinner, 90);
        setCompactWidth(marginBottomSpinner, 90);
        setCompactWidth(marginLeftSpinner, 90);
        setCompactWidth(marginRightSpinner, 90);
        setCompactWidth(indentSpinner, 90);
        setCompactWidth(encodingBox, 120);
    }

    private void setCompactWidth(java.awt.Component comp, int width) {
        Dimension preferred = comp.getPreferredSize();
        Dimension size = new Dimension(width, preferred.height);
        comp.setPreferredSize(size);
        comp.setMinimumSize(size);
    }

    private JSplitPane buildCenterSplit() {
        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        listPanel.setBorder(BorderFactory.createTitledBorder("待转换文件"));
        listPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        listButtons.add(addFilesBtn);
        listButtons.add(addDirBtn);
        listButtons.add(removeBtn);
        listButtons.add(clearBtn);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("日志"));
        logArea.setEditable(false);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, logPanel);
        split.setResizeWeight(0.55);
        return split;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        progressBar.setStringPainted(true);
        p.add(progressBar, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.add(convertBtn);
        right.add(cancelBtn);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private void wireActions() {
        addFilesBtn.addActionListener(e -> onAddFiles());
        addDirBtn.addActionListener(e -> onAddDir());
        removeBtn.addActionListener(e -> onRemoveSelected());
        clearBtn.addActionListener(e -> { fileListModel.clear(); inputPaths.clear(); });
        chooseOutputBtn.addActionListener(e -> onChooseOutput());
        convertBtn.addActionListener(e -> onConvert());
        cancelBtn.addActionListener(e -> onCancel());
        modeBox.addActionListener(e -> refreshModeDependentUi());
        refreshModeDependentUi();
    }

    private void onAddFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (getSelectedMode() == ConversionMode.DOCX_TO_TXT) {
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Word 文件 (*.docx)", "docx"));
        } else {
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
            inputPaths.remove(idx[i]);
        }
    }

    private void onChooseOutput() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void addInputPath(Path p) {
        inputPaths.add(p);
        fileListModel.addElement(p.toString());
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

        ConversionOptions options = buildOptions();
        boolean recursive = recursiveBox.isSelected();
        boolean preserveTree = preserveTreeBox.isSelected();
        boolean overwrite = overwriteBox.isSelected();
        ConversionMode mode = getSelectedMode();
        savePreferences();

        currentProcessor = new BatchProcessor(options, mode);
        List<Path> snapshot = new ArrayList<>(inputPaths);

        logArea.setText("");
        progressBar.setValue(0);
        progressBar.setString("准备中...");
        convertBtn.setEnabled(false);
        cancelBtn.setEnabled(true);

        currentWorker = new SwingWorker<>() {
            int total = 0;

            @Override
            protected Void doInBackground() {
                try {
                    List<BatchItem> files = currentProcessor.collectFiles(snapshot, recursive, preserveTree);
                    total = files.size();
                    publish("共找到 " + total + " 个" + (mode == ConversionMode.DOCX_TO_TXT ? " .docx " : " .txt ") + "文件");
                    if (total == 0) return null;
                    progressBar.setMaximum(total);
                    currentProcessor.process(files, outputDir, overwrite, (done, tot, last) -> {
                        publish(formatResult(last));
                        setProgress((int) ((done * 100.0) / tot));
                        progressBar.setValue(done);
                        progressBar.setString(done + " / " + tot);
                    });
                } catch (Exception ex) {
                    publish("错误: " + ex.getMessage());
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
                convertBtn.setEnabled(true);
                cancelBtn.setEnabled(false);
                progressBar.setString("完成");
                logArea.append("---- 转换结束 ----" + System.lineSeparator());
            }
        };
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentProcessor != null) currentProcessor.cancel();
        if (currentWorker != null) currentWorker.cancel(true);
        cancelBtn.setEnabled(false);
        progressBar.setString("已取消");
    }

    private ConversionOptions buildOptions() {
        ConversionOptions o = new ConversionOptions();
        o.setFontFamily((String) fontBox.getSelectedItem());
        o.setFontSize(((Number) fontSizeSpinner.getValue()).intValue());
        o.setMarginTopCm(((Number) marginTopSpinner.getValue()).doubleValue());
        o.setMarginBottomCm(((Number) marginBottomSpinner.getValue()).doubleValue());
        o.setMarginLeftCm(((Number) marginLeftSpinner.getValue()).doubleValue());
        o.setMarginRightCm(((Number) marginRightSpinner.getValue()).doubleValue());
        o.setEncoding((String) encodingBox.getSelectedItem());
        o.setRemoveSpaces(removeSpacesBox.isSelected());
        o.setRemoveEmptyLines(removeEmptyLinesBox.isSelected());
        o.setIndentSize(((Number) indentSpinner.getValue()).intValue());
        o.setAddBlankLineBetweenLines(blankLineBetweenLinesBox.isSelected());
        return o;
    }

    private void loadPreferences() {
        modeBox.setSelectedIndex(prefs.getInt(PREF_MODE, 0));
        outputDirField.setText(prefs.get(PREF_OUTPUT_DIR, ""));
        setComboValue(fontBox, prefs.get(PREF_FONT, "宋体"));
        fontSizeSpinner.setValue(prefs.getInt(PREF_FONT_SIZE, 12));
        marginTopSpinner.setValue(prefs.getDouble(PREF_MARGIN_TOP, 2.54));
        marginBottomSpinner.setValue(prefs.getDouble(PREF_MARGIN_BOTTOM, 2.54));
        marginLeftSpinner.setValue(prefs.getDouble(PREF_MARGIN_LEFT, 3.18));
        marginRightSpinner.setValue(prefs.getDouble(PREF_MARGIN_RIGHT, 3.18));
        indentSpinner.setValue(prefs.getInt(PREF_INDENT, 2));
        setComboValue(encodingBox, prefs.get(PREF_ENCODING, "AUTO"));
        recursiveBox.setSelected(prefs.getBoolean(PREF_RECURSIVE, true));
        preserveTreeBox.setSelected(prefs.getBoolean(PREF_PRESERVE_TREE, true));
        overwriteBox.setSelected(prefs.getBoolean(PREF_OVERWRITE, false));
        removeSpacesBox.setSelected(prefs.getBoolean(PREF_REMOVE_SPACES, true));
        removeEmptyLinesBox.setSelected(prefs.getBoolean(PREF_REMOVE_EMPTY_LINES, true));
        blankLineBetweenLinesBox.setSelected(prefs.getBoolean(PREF_BLANK_LINE_BETWEEN_LINES, true));
        refreshModeDependentUi();
    }

    private void savePreferences() {
        prefs.putInt(PREF_MODE, modeBox.getSelectedIndex());
        prefs.put(PREF_OUTPUT_DIR, outputDirField.getText().trim());
        prefs.put(PREF_FONT, String.valueOf(fontBox.getSelectedItem()));
        prefs.putInt(PREF_FONT_SIZE, ((Number) fontSizeSpinner.getValue()).intValue());
        prefs.putDouble(PREF_MARGIN_TOP, ((Number) marginTopSpinner.getValue()).doubleValue());
        prefs.putDouble(PREF_MARGIN_BOTTOM, ((Number) marginBottomSpinner.getValue()).doubleValue());
        prefs.putDouble(PREF_MARGIN_LEFT, ((Number) marginLeftSpinner.getValue()).doubleValue());
        prefs.putDouble(PREF_MARGIN_RIGHT, ((Number) marginRightSpinner.getValue()).doubleValue());
        prefs.putInt(PREF_INDENT, ((Number) indentSpinner.getValue()).intValue());
        prefs.put(PREF_ENCODING, String.valueOf(encodingBox.getSelectedItem()));
        prefs.putBoolean(PREF_RECURSIVE, recursiveBox.isSelected());
        prefs.putBoolean(PREF_PRESERVE_TREE, preserveTreeBox.isSelected());
        prefs.putBoolean(PREF_OVERWRITE, overwriteBox.isSelected());
        prefs.putBoolean(PREF_REMOVE_SPACES, removeSpacesBox.isSelected());
        prefs.putBoolean(PREF_REMOVE_EMPTY_LINES, removeEmptyLinesBox.isSelected());
        prefs.putBoolean(PREF_BLANK_LINE_BETWEEN_LINES, blankLineBetweenLinesBox.isSelected());
    }

    private void setComboValue(JComboBox<String> comboBox, String value) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (comboBox.getItemAt(i).equals(value)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private ConversionMode getSelectedMode() {
        return modeBox.getSelectedIndex() == 1 ? ConversionMode.DOCX_TO_TXT : ConversionMode.TXT_TO_DOCX;
    }

    private void refreshModeDependentUi() {
        boolean txtToDocx = getSelectedMode() == ConversionMode.TXT_TO_DOCX;
        fontBox.setEnabled(txtToDocx);
        fontSizeSpinner.setEnabled(txtToDocx);
        marginTopSpinner.setEnabled(txtToDocx);
        marginBottomSpinner.setEnabled(txtToDocx);
        marginLeftSpinner.setEnabled(txtToDocx);
        marginRightSpinner.setEnabled(txtToDocx);
        addFilesBtn.setText(txtToDocx ? "添加 TXT..." : "添加 DOCX...");
        setTitle(txtToDocx ? "TXT 批量转 DOCX 工具" : "DOCX 批量转 TXT 工具");
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
}
