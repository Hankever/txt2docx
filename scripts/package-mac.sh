#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist/macos"

cd "$ROOT_DIR"
mvn clean package

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

jpackage \
  --type dmg \
  --name 贰拾 \
  --app-version 1.0.0 \
  --vendor com.tools \
  --description "贰拾文档转换工具" \
  --input "$ROOT_DIR/target" \
  --main-jar txt2docx.jar \
  --main-class com.tools.txt2docx.Main \
  --dest "$DIST_DIR" \
  --icon macos.icns

echo "macOS 安装包输出到: $DIST_DIR"
