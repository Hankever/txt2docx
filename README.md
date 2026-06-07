# Txt2Docx

Java 实现的 TXT / EPUB / DOCX 批量转换工具，支持桌面界面和命令行两种使用方式。

## 下载

- [Windows 安装包](https://github.com/Hankever/txt2docx/releases/latest)
- [macOS 安装包](https://github.com/Hankever/txt2docx/releases/latest)

说明：

- 请在 GitHub Releases 页面下载对应平台的安装包。
- 当前仓库地址：`https://github.com/Hankever/txt2docx`

## 功能

- 批量选择多个 `txt` / `epub` / `docx` 文件或整个目录进行转换
- 支持 `txt` 转 `docx`、`epub` 转 `docx`、`docx` 转 `txt`
- EPUB 转 DOCX 会按章节顺序提取正文，并嵌入 EPUB 内的常见图片资源（PNG/JPEG/GIF/BMP/TIFF）
- 自动识别常见文本编码，也可手动指定编码
- 可设置字体、字号、页边距
- 可按需删除空格、删除空行、设置 Word 段落首行缩进、在行间插入空行
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

DOCX 转 TXT：

```bash
java -jar target/txt2docx.jar --mode docx2txt --input ./docx --output ./txt --recursive
```

EPUB 转 DOCX：

```bash
java -jar target/txt2docx.jar --mode epub2docx --input ./epub --output ./docx --recursive
```

带格式选项的 TXT / EPUB 转 DOCX：

```bash
java -jar target/txt2docx.jar \
  --input ./txt \
  --output ./docx \
  --recursive \
  --remove-spaces \
  --remove-empty-lines \
  --indent 2 \
  --blank-line-between-lines
```

更多参数：

```bash
java -jar target/txt2docx.jar --help
```

文本处理参数说明：

- `--remove-spaces` 删除每行中的空格和制表符
- `--remove-empty-lines` 删除空行
- `--indent <n>` 设置 Word 段落首行缩进 `n` 个字符，`2` 即常见中文段首缩进
- `--blank-line-between-lines` 在相邻文本行之间插入一空行

EPUB 图片说明：

- 支持 EPUB 包内相对路径图片和 `data:image/...;base64` 图片
- 不下载外链图片，不保留 CSS、SVG 矢量图或复杂版式

构建提示：

- 如果 `mvn package` 报 `invalid target release: 17` 或 `无效的目标发行版: 17`，说明 Maven 没有使用 JDK 17。
- 可以显式指定：

```bash
JAVA_HOME=/Users/gqh/jdk/jdk17/Contents/Home mvn clean package
```

## 打包发布

项目提供两类 GitHub Actions：

- `.github/workflows/ci.yml`：每次 push / PR 运行 `mvn -B clean verify`，并上传可运行的 `txt2docx.jar`。
- `.github/workflows/release.yml`：推送 `vX.Y.Z` 标签或手动触发时，自动构建 jar、macOS DMG、Windows EXE，并创建 GitHub Release。

发版前先确认 `pom.xml` 中的 `<version>` 与要发布的版本一致。Release workflow 会校验标签版本和 Maven 版本，如果 `v2.1.0` 与 `pom.xml` 的 `2.1.0` 不一致会直接失败。

```bash
git tag v2.1.0
git push origin v2.1.0
```

也可以在 GitHub Actions 页面手动运行 `release` workflow，并填写不带 `v` 前缀的版本号。

正式 Release 产物：

- `txt2docx-<version>.jar`
- `Txt2Docx-<version>-macos-x64.dmg`
- `Txt2Docx-<version>-macos-arm64.dmg`
- `Txt2Docx-<version>-windows.exe`

Release Notes 使用 GitHub 自动生成能力，分类配置在 `.github/release.yml`。给 PR 打上 `feature` / `enhancement` / `bug` / `fix` / `ci` / `release` / `build` 等标签后，发布说明会自动分组。

### macOS

生成 `dmg` 安装包：

```bash
./scripts/package-mac.sh
```

输出目录：

- `dist/macos`

macOS 签名和公证是可选的。未配置证书时仍会生成未签名 DMG；配置以下 GitHub Secrets 后，Release workflow 会导入 Developer ID 证书、执行 `jpackage --mac-sign`，并在 Apple 账号信息完整时提交 notarization 和 stapler：

- `MAC_CERTIFICATE_P12_BASE64`：Developer ID Application `.p12` 证书的 base64 内容
- `MAC_CERTIFICATE_PASSWORD`：`.p12` 证书密码
- `MAC_SIGNING_KEY_USER_NAME`：证书名称，例如 `Developer ID Application: Example Inc (TEAMID)`
- `MAC_KEYCHAIN_PASSWORD`：CI 临时 keychain 密码，可选
- `APPLE_ID`：Apple ID 邮箱
- `APPLE_TEAM_ID`：Apple Developer Team ID
- `APPLE_APP_SPECIFIC_PASSWORD`：Apple app-specific password

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
- 如果你在 Mac 上开发，仓库里提供了 GitHub Actions 工作流 `.github/workflows/build-windows-exe.yml`，可以在 Windows Runner 上手动生成 `exe` 并下载产物。

Windows 代码签名也是可选的。未配置证书时会生成未签名 EXE；配置以下 GitHub Secrets 后，Release workflow 和手动 Windows workflow 会用 `signtool` 签名：

- `WINDOWS_CERTIFICATE_PFX_BASE64`：代码签名 PFX 证书的 base64 内容
- `WINDOWS_CERTIFICATE_PASSWORD`：PFX 证书密码

本地 Windows 打包也可以通过环境变量启用签名：

```bat
set WINDOWS_CERTIFICATE_PATH=C:\path\codesign.pfx
set WINDOWS_CERTIFICATE_PASSWORD=your-password
scripts\package-windows.bat
```

### 证书编码

GitHub Secrets 中的证书内容建议使用单行 base64：

```bash
base64 -i DeveloperID.p12 | tr -d '\n'
base64 -i codesign.pfx | tr -d '\n'
```
