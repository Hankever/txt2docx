package com.tools.txt2docx.ui;

import com.tools.txt2docx.batch.ConflictPolicy;
import com.tools.txt2docx.batch.ConversionMode;
import com.tools.txt2docx.converter.ConversionOptions;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

public final class OptionsPanel extends JPanel {

    static final String CONFLICT_RENAME = "已存在则自动重命名";
    static final String CONFLICT_OVERWRITE = "已存在则覆盖";
    static final String CONFLICT_SKIP = "已存在则跳过";

    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"TXT -> DOCX", "DOCX -> TXT", "EPUB -> DOCX"});
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
    private final JComboBox<String> conflictBox = new JComboBox<>(new String[]{CONFLICT_RENAME, CONFLICT_OVERWRITE, CONFLICT_SKIP});
    private final JCheckBox openAfterDoneBox = new JCheckBox("完成后打开输出目录", false);
    private final JCheckBox removeSpacesBox = new JCheckBox("删除空格", true);
    private final JCheckBox removeEmptyLinesBox = new JCheckBox("删除空行", true);
    private final JCheckBox blankLineBetweenLinesBox = new JCheckBox("行间加空行", true);

    public OptionsPanel() {
        applyFieldSizes();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(buildOptionsContainer());
        add(Box.createVerticalStrut(2));
    }

    private JPanel buildOptionsContainer() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createTitledBorder("转换设置"));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(buildBasicSection(), BorderLayout.WEST);
        top.add(buildLayoutSection(), BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        p.add(buildSwitchSection(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBasicSection() {
        JPanel p = section("基础");
        GridBagConstraints c = base();
        addLabel(p, c, 0, 0, "转换方向"); addField(p, c, 1, 0, modeBox);
        addLabel(p, c, 2, 0, "编码");     addField(p, c, 3, 0, encodingBox);
        addLabel(p, c, 0, 1, "字体");     addField(p, c, 1, 1, fontBox);
        addLabel(p, c, 2, 1, "字号");     addField(p, c, 3, 1, fontSizeSpinner);
        return p;
    }

    private JPanel buildLayoutSection() {
        JPanel p = section("版式");
        GridBagConstraints c = base();
        addLabel(p, c, 0, 0, "文本缩进");   addField(p, c, 1, 0, indentSpinner);
        addLabel(p, c, 2, 0, "上边距(cm)"); addField(p, c, 3, 0, marginTopSpinner);
        addLabel(p, c, 0, 1, "下边距(cm)"); addField(p, c, 1, 1, marginBottomSpinner);
        addLabel(p, c, 2, 1, "左边距(cm)"); addField(p, c, 3, 1, marginLeftSpinner);
        addLabel(p, c, 0, 2, "右边距(cm)"); addField(p, c, 1, 2, marginRightSpinner);
        return p;
    }

    private JPanel buildSwitchSection() {
        JPanel p = section("选项");
        GridBagConstraints c = base();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 1; p.add(removeSpacesBox, c);
        c.gridx = 1; p.add(removeEmptyLinesBox, c);
        c.gridx = 2; p.add(blankLineBetweenLinesBox, c);
        c.gridx = 0; c.gridy = 1; p.add(recursiveBox, c);
        c.gridx = 1; p.add(preserveTreeBox, c);
        c.gridx = 2; p.add(openAfterDoneBox, c);
        c.gridx = 0; c.gridy = 2; p.add(new JLabel("冲突策略"), c);
        c.gridx = 1; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(conflictBox, c);
        return p;
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        return p;
    }

    private static GridBagConstraints base() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        return c;
    }

    private static void addLabel(JPanel p, GridBagConstraints c, int x, int y, String text) {
        c.gridx = x; c.gridy = y; c.weightx = 0;
        p.add(new JLabel(text), c);
    }

    private static void addField(JPanel p, GridBagConstraints c, int x, int y, Component comp) {
        c.gridx = x; c.gridy = y; c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(comp, c);
        c.fill = GridBagConstraints.HORIZONTAL;
    }

    private void applyFieldSizes() {
        compact(modeBox, 150);
        compact(fontBox, 120);
        compact(fontSizeSpinner, 90);
        compact(marginTopSpinner, 90);
        compact(marginBottomSpinner, 90);
        compact(marginLeftSpinner, 90);
        compact(marginRightSpinner, 90);
        compact(indentSpinner, 90);
        compact(encodingBox, 120);
        compact(conflictBox, 200);
    }

    private static void compact(Component comp, int width) {
        Dimension preferred = comp.getPreferredSize();
        Dimension size = new Dimension(width, preferred.height);
        comp.setPreferredSize(size);
        comp.setMinimumSize(size);
    }

    public void onModeChanged(Consumer<ConversionMode> listener) {
        modeBox.addActionListener(e -> listener.accept(getMode()));
    }

    public ConversionMode getMode() {
        return switch (modeBox.getSelectedIndex()) {
            case 1 -> ConversionMode.DOCX_TO_TXT;
            case 2 -> ConversionMode.EPUB_TO_DOCX;
            default -> ConversionMode.TXT_TO_DOCX;
        };
    }

    public void refreshModeDependentUi() {
        boolean outputsDocx = getMode().outputsDocx();
        fontBox.setEnabled(outputsDocx);
        fontSizeSpinner.setEnabled(outputsDocx);
        indentSpinner.setEnabled(outputsDocx);
        marginTopSpinner.setEnabled(outputsDocx);
        marginBottomSpinner.setEnabled(outputsDocx);
        marginLeftSpinner.setEnabled(outputsDocx);
        marginRightSpinner.setEnabled(outputsDocx);
    }

    public boolean isRecursive()         { return recursiveBox.isSelected(); }
    public boolean isPreserveTree()      { return preserveTreeBox.isSelected(); }
    public boolean isOpenAfterDone()     { return openAfterDoneBox.isSelected(); }

    public ConflictPolicy getConflictPolicy() {
        String selected = String.valueOf(conflictBox.getSelectedItem());
        switch (selected) {
            case CONFLICT_OVERWRITE: return ConflictPolicy.OVERWRITE;
            case CONFLICT_SKIP:      return ConflictPolicy.SKIP;
            default:                 return ConflictPolicy.AUTO_RENAME;
        }
    }

    public ConversionOptions buildConversionOptions() {
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

    public void loadFrom(PreferencesStore prefs) {
        int modeIndex = prefs.getModeIndex();
        modeBox.setSelectedIndex(modeIndex >= 0 && modeIndex < modeBox.getItemCount() ? modeIndex : 0);
        setComboValue(fontBox, prefs.getFont());
        fontSizeSpinner.setValue(prefs.getFontSize());
        marginTopSpinner.setValue(prefs.getMarginTop());
        marginBottomSpinner.setValue(prefs.getMarginBottom());
        marginLeftSpinner.setValue(prefs.getMarginLeft());
        marginRightSpinner.setValue(prefs.getMarginRight());
        indentSpinner.setValue(prefs.getIndent());
        setComboValue(encodingBox, prefs.getEncoding());
        recursiveBox.setSelected(prefs.isRecursive());
        preserveTreeBox.setSelected(prefs.isPreserveTree());
        setComboValue(conflictBox, prefs.getConflictLabel(CONFLICT_RENAME));
        openAfterDoneBox.setSelected(prefs.isOpenAfterDone());
        removeSpacesBox.setSelected(prefs.isRemoveSpaces());
        removeEmptyLinesBox.setSelected(prefs.isRemoveEmptyLines());
        blankLineBetweenLinesBox.setSelected(prefs.isBlankLineBetweenLines());
        refreshModeDependentUi();
    }

    public void saveTo(PreferencesStore prefs) {
        prefs.setModeIndex(modeBox.getSelectedIndex());
        prefs.setFont(String.valueOf(fontBox.getSelectedItem()));
        prefs.setFontSize(((Number) fontSizeSpinner.getValue()).intValue());
        prefs.setMarginTop(((Number) marginTopSpinner.getValue()).doubleValue());
        prefs.setMarginBottom(((Number) marginBottomSpinner.getValue()).doubleValue());
        prefs.setMarginLeft(((Number) marginLeftSpinner.getValue()).doubleValue());
        prefs.setMarginRight(((Number) marginRightSpinner.getValue()).doubleValue());
        prefs.setIndent(((Number) indentSpinner.getValue()).intValue());
        prefs.setEncoding(String.valueOf(encodingBox.getSelectedItem()));
        prefs.setRecursive(recursiveBox.isSelected());
        prefs.setPreserveTree(preserveTreeBox.isSelected());
        prefs.setConflictLabel(String.valueOf(conflictBox.getSelectedItem()));
        prefs.setOpenAfterDone(openAfterDoneBox.isSelected());
        prefs.setRemoveSpaces(removeSpacesBox.isSelected());
        prefs.setRemoveEmptyLines(removeEmptyLinesBox.isSelected());
        prefs.setBlankLineBetweenLines(blankLineBetweenLinesBox.isSelected());
    }

    private static void setComboValue(JComboBox<String> comboBox, String value) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (comboBox.getItemAt(i).equals(value)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
    }
}
