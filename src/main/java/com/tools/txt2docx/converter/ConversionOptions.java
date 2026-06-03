package com.tools.txt2docx.converter;

public class ConversionOptions {
    private String fontFamily = "宋体";
    private int fontSize = 12;
    private double marginTopCm = 2.54;
    private double marginBottomCm = 2.54;
    private double marginLeftCm = 3.18;
    private double marginRightCm = 3.18;
    private String encoding = "AUTO";

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }

    public double getMarginTopCm() { return marginTopCm; }
    public void setMarginTopCm(double marginTopCm) { this.marginTopCm = marginTopCm; }

    public double getMarginBottomCm() { return marginBottomCm; }
    public void setMarginBottomCm(double marginBottomCm) { this.marginBottomCm = marginBottomCm; }

    public double getMarginLeftCm() { return marginLeftCm; }
    public void setMarginLeftCm(double marginLeftCm) { this.marginLeftCm = marginLeftCm; }

    public double getMarginRightCm() { return marginRightCm; }
    public void setMarginRightCm(double marginRightCm) { this.marginRightCm = marginRightCm; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
}
