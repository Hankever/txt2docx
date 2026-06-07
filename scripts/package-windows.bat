@echo off
setlocal

cd /d %~dp0\..

if not exist target\txt2docx.jar (
  echo Missing target\txt2docx.jar. Run "mvn clean package" before packaging.
  exit /b 1
)

if exist dist\windows rmdir /s /q dist\windows
mkdir dist\windows

jpackage ^
  --type exe ^
  --name Txt2Docx ^
  --app-version 2.0.1 ^
  --vendor com.tools ^
  --description "TXT 批量转 DOCX 工具" ^
  --input target ^
  --main-jar txt2docx.jar ^
  --main-class com.tools.txt2docx.Main ^
  --dest dist\windows ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --icon src/main/resources/icons/windows2d.ico

if errorlevel 1 exit /b 1

echo Windows EXE 安装包输出到: dist\windows
