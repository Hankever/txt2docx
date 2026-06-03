# Txt2Docx

Java 实现的 TXT 批量转 DOCX 工具，支持桌面界面和命令行两种使用方式。

## 功能

- 批量选择多个 `txt` 文件或整个目录进行转换
- 自动识别常见文本编码，也可手动指定编码
- 可设置字体、字号、页边距
- 支持递归扫描子目录
- 默认保留输入目录结构，避免批量转换时同名文件互相覆盖
- 生成单文件可运行的 fat jar，便于分发

## 构建

要求：

- JDK 17 或更高版本
- `mvn` 和 `jpackage` 需要使用同一套 JDK
- 如果出现 `invalid target release: 17`，通常是 Maven 实际使用了较低版本的 JDK

```bash
mvn clean package
```

构建后主产物为：

- `target/txt2docx.jar`

## 运行

GUI 模式：

```bash
java -jar target/txt2docx.jar
```

CLI 模式：

```bash
java -jar target/txt2docx.jar --input ./txt --output ./docx --recursive
```

更多参数：

```bash
java -jar target/txt2docx.jar --help
```

## 打包发布

### macOS

生成 `dmg` 安装包：

```bash
./scripts/package-mac.sh
```

输出目录：

- `dist/macos`

### Windows

生成 `exe` 安装包：

```bat
scripts\package-windows.bat
```

输出目录：

- `dist\windows`

注意：

- Windows 的 `jpackage --type exe` 需要在 Windows 环境运行。
- 使用 `jpackage` 生成 Windows `exe` 时需要先安装 WiX Toolset。
- 如果你在 Mac 上开发，仓库里已经提供了 GitHub Actions 工作流 `.github/workflows/build-windows-exe.yml`，可以在 Windows Runner 上自动生成 `exe` 并下载产物。
