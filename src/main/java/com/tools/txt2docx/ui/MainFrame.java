package com.tools.txt2docx.ui;

import com.tools.txt2docx.batch.BatchProcessor;
import com.tools.txt2docx.batch.BatchItem;
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

public class MainFrame extends JFrame {

    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final JTextField outputDirField = new JTextField();
    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();

    private final JComboBox<String> fontBox = new JComboBox<>(new String[]{"宋体", "微软雅黑", "黑体", "楷体", "仿宋", "Times New Roman", "Arial"});
    private final JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 6, 72, 1));
    private final JSpinner marginTopSpinner = new JSpinner(new SpinnerNumberModel(2.54, 0.0, 10.0, 0.1));
    private final JSpinner marginBottomSpinner = new JSpinner(new SpinnerNumberModel(2.54, 0.0, 10.0, 0.1));
    private final JSpinner marginLeftSpinner = new JSpinner(new SpinnerNumberModel(3.18, 0.0, 10.0, 0.1));
    private final JSpinner marginRightSpinner = new JSpinner(new SpinnerNumberModel(3.18, 0.0, 10.0, 0.1));
    private final JComboBox<String> encodingBox = new JComboBox<>(new String[]{"AUTO", "UTF-8", "GBK", "GB2312", "GB18030", "UTF-16LE", "UTF-16BE", "Big5"});
    private final JCheckBox recursiveBox = new JCheckBox("递归子目录", true);
    private final JCheckBox preserveTreeBox = new JCheckBox("保留目录结构", true);
    private final JCheckBox overwriteBox = new JCheckBox("覆盖已有文件", false);

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
        setSize(900, 640);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);

        setContentPane(buildContent());
        wireActions();
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
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("格式与选项"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addLabel(p, c, 0, row, "字体");
        addField(p, c, 1, row, fontBox);
        addLabel(p, c, 2, row, "字号");
        addField(p, c, 3, row, fontSizeSpinner);
        addLabel(p, c, 4, row, "编码");
        addField(p, c, 5, row, encodingBox);

        row++;
        addLabel(p, c, 0, row, "上边距(cm)");
        addField(p, c, 1, row, marginTopSpinner);
        addLabel(p, c, 2, row, "下边距(cm)");
        addField(p, c, 3, row, marginBottomSpinner);
        addLabel(p, c, 4, row, "左边距(cm)");
        addField(p, c, 5, row, marginLeftSpinner);

        row++;
        addLabel(p, c, 0, row, "右边距(cm)");
        addField(p, c, 1, row, marginRightSpinner);
        c.gridx = 2; c.gridy = row; c.gridwidth = 2;
        p.add(recursiveBox, c);
        c.gridx = 4; c.gridy = row; c.gridwidth = 2;
        p.add(preserveTreeBox, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        p.add(overwriteBox, c);
        c.gridwidth = 1;

        return p;
    }

    private void addLabel(JPanel p, GridBagConstraints c, int x, int y, String text) {
        c.gridx = x; c.gridy = y; c.weightx = 0;
        p.add(new JLabel(text), c);
    }

    private void addField(JPanel p, GridBagConstraints c, int x, int y, java.awt.Component comp) {
        c.gridx = x; c.gridy = y; c.weightx = 1.0;
        p.add(comp, c);
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
    }

    private void onAddFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
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

        currentProcessor = new BatchProcessor(options);
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
                    List<BatchItem> files = currentProcessor.collectTxtFiles(snapshot, recursive, preserveTree);
                    total = files.size();
                    publish("共找到 " + total + " 个 .txt 文件");
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
        return o;
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
