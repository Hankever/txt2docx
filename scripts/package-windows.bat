@echo off
setlocal EnableExtensions

cd /d "%~dp0\.."

if "%APP_VERSION%"=="" (
  for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "$m = Select-String -Path pom.xml -Pattern '<version>([^<]+)</version>' | Select-Object -First 1; $m.Matches[0].Groups[1].Value"`) do set "APP_VERSION=%%v"
)
if "%APP_NAME%"=="" set "APP_NAME=Txt2Docx"
if "%APP_VENDOR%"=="" set "APP_VENDOR=com.tools"
if "%APP_DESCRIPTION%"=="" set "APP_DESCRIPTION=TXT / EPUB / DOCX 批量转换工具"
if "%WINDOWS_TIMESTAMP_URL%"=="" set "WINDOWS_TIMESTAMP_URL=http://timestamp.digicert.com"

if not exist target\txt2docx.jar (
  echo Missing target\txt2docx.jar. Run "mvn clean package" before packaging.
  exit /b 1
)

if exist dist\windows rmdir /s /q dist\windows
mkdir dist\windows

jpackage ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%APP_VENDOR%" ^
  --description "%APP_DESCRIPTION%" ^
  --input target ^
  --main-jar txt2docx.jar ^
  --main-class com.tools.txt2docx.Main ^
  --dest dist\windows ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --icon src/main/resources/icons/windows2d.ico

if errorlevel 1 exit /b 1

set "EXE_PATH="
for %%f in (dist\windows\*.exe) do set "EXE_PATH=%%f"
if "%EXE_PATH%"=="" (
  echo No EXE installer was generated in dist\windows.
  exit /b 1
)

if "%WINDOWS_CERTIFICATE_PATH%"=="" goto skip_signing
if "%WINDOWS_CERTIFICATE_PASSWORD%"=="" goto skip_signing

set "SIGNTOOL=signtool"
if not "%SIGNTOOL_PATH%"=="" set "SIGNTOOL=%SIGNTOOL_PATH%"
echo Windows code signing enabled.
"%SIGNTOOL%" sign /fd SHA256 /tr "%WINDOWS_TIMESTAMP_URL%" /td SHA256 /f "%WINDOWS_CERTIFICATE_PATH%" /p "%WINDOWS_CERTIFICATE_PASSWORD%" "%EXE_PATH%"
if errorlevel 1 exit /b 1
goto after_signing

:skip_signing
  echo Windows code signing skipped: WINDOWS_CERTIFICATE_PATH / WINDOWS_CERTIFICATE_PASSWORD not fully set.

:after_signing

echo Windows EXE 安装包输出到: dist\windows
