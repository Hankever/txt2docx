@echo off
setlocal

cd /d %~dp0\..
call mvn clean package
if errorlevel 1 exit /b 1

if exist dist\windows rmdir /s /q dist\windows
mkdir dist\windows

jpackage ^
  --type exe ^
  --name Txt2Docx ^
  --app-version 1.0.0 ^
  --vendor com.tools ^
  --description "TXT 批量转 DOCX 工具" ^
  --input target ^
  --main-jar txt2docx.jar ^
  --main-class com.tools.txt2docx.Main ^
  --dest dist\windows ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --icon windows.ico

if errorlevel 1 exit /b 1

echo Windows EXE 安装包输出到: dist\windows
