#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist/macos"
APP_VERSION="${APP_VERSION:-$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$ROOT_DIR/pom.xml" | head -n 1)}"
APP_NAME="${APP_NAME:-Txt2Docx}"
APP_VENDOR="${APP_VENDOR:-com.tools}"
APP_DESCRIPTION="${APP_DESCRIPTION:-TXT / EPUB / DOCX 批量转换工具}"
MAC_BUNDLE_ID="${MAC_BUNDLE_ID:-com.tools.txt2docx}"

cd "$ROOT_DIR"
mvn -B clean package

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

JPACKAGE_ARGS=(
  --type dmg
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "$APP_VENDOR"
  --description "$APP_DESCRIPTION"
  --input "$ROOT_DIR/target"
  --main-jar txt2docx.jar
  --main-class com.tools.txt2docx.Main
  --dest "$DIST_DIR"
  --icon src/main/resources/icons/macos.icns
  --mac-package-identifier "$MAC_BUNDLE_ID"
)

if [[ -n "${MAC_SIGNING_KEY_USER_NAME:-}" ]]; then
  JPACKAGE_ARGS+=(--mac-sign --mac-signing-key-user-name "$MAC_SIGNING_KEY_USER_NAME")
  if [[ -n "${MAC_SIGNING_KEYCHAIN:-}" ]]; then
    JPACKAGE_ARGS+=(--mac-signing-keychain "$MAC_SIGNING_KEYCHAIN")
  fi
  echo "macOS signing enabled: $MAC_SIGNING_KEY_USER_NAME"
else
  echo "macOS signing skipped: MAC_SIGNING_KEY_USER_NAME is not set"
fi

jpackage "${JPACKAGE_ARGS[@]}"

DMG_PATH="$(find "$DIST_DIR" -maxdepth 1 -type f -name '*.dmg' | head -n 1)"
if [[ -z "$DMG_PATH" ]]; then
  echo "No DMG was generated in $DIST_DIR" >&2
  exit 1
fi

if [[ -n "${MAC_SIGNING_KEY_USER_NAME:-}" && -n "${APPLE_ID:-}" && -n "${APPLE_TEAM_ID:-}" && -n "${APPLE_APP_SPECIFIC_PASSWORD:-}" ]]; then
  echo "Submitting DMG for Apple notarization..."
  xcrun notarytool submit "$DMG_PATH" \
    --apple-id "$APPLE_ID" \
    --team-id "$APPLE_TEAM_ID" \
    --password "$APPLE_APP_SPECIFIC_PASSWORD" \
    --wait
  xcrun stapler staple "$DMG_PATH"
  echo "macOS notarization complete: $DMG_PATH"
else
  echo "macOS notarization skipped: signing or Apple notarization credentials not fully set"
fi

echo "macOS 安装包输出到: $DIST_DIR"
