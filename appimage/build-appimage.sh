#!/usr/bin/env bash
# Builds a self-contained Linux AppImage for Application Wizard, bundling
# Eclipse Temurin JRE 21 so end users do not need Java installed.
#
# Usage: ./appimage/build-appimage.sh
# Must be run from the project root, after `mvn clean package` has produced
# target/wizard-app.jar.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPIMAGE_DIR="$PROJECT_ROOT/appimage"
BUILD_DIR="$PROJECT_ROOT/target/AppDir"
JAR_PATH="$PROJECT_ROOT/target/wizard-app.jar"
OUTPUT_DIR="$PROJECT_ROOT/target"

TEMURIN_MAJOR=21
ARCH="$(uname -m)"

case "$ARCH" in
    x86_64) TEMURIN_ARCH="x64" ;;
    aarch64|arm64) TEMURIN_ARCH="aarch64" ;;
    *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

echo "=== Building Application Wizard AppImage ($ARCH) ==="

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: $JAR_PATH not found. Run 'mvn clean package' first."
    exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/usr/bin" "$BUILD_DIR/usr/jre"

# --- 1. Bundle a private Eclipse Temurin JRE 21 (Adoptium) ---------------------
echo "Downloading Eclipse Temurin JRE ${TEMURIN_MAJOR} (${TEMURIN_ARCH})..."
JRE_ARCHIVE="$BUILD_DIR/temurin-jre.tar.gz"
TEMURIN_URL="https://api.adoptium.net/v3/binary/latest/${TEMURIN_MAJOR}/ga/linux/${TEMURIN_ARCH}/jre/hotspot/normal/eclipse?project=jdk"

curl -fL "$TEMURIN_URL" -o "$JRE_ARCHIVE"
tar -xzf "$JRE_ARCHIVE" -C "$BUILD_DIR/usr/jre" --strip-components=1
rm -f "$JRE_ARCHIVE"

# --- 2. Copy the application jar ------------------------------------------------
cp "$JAR_PATH" "$BUILD_DIR/usr/bin/wizard-app.jar"

# --- 3. Copy AppRun, .desktop file and icon ------------------------------------
cp "$APPIMAGE_DIR/AppRun" "$BUILD_DIR/AppRun"
chmod +x "$BUILD_DIR/AppRun"
cp "$APPIMAGE_DIR/wizard-app.desktop" "$BUILD_DIR/wizard-app.desktop"
cp "$APPIMAGE_DIR/wizard-app.png" "$BUILD_DIR/wizard-app.png"
# Also expose the icon at the standard hicolor path for desktop-integration tools.
mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/256x256/apps"
cp "$APPIMAGE_DIR/wizard-app.png" "$BUILD_DIR/usr/share/icons/hicolor/256x256/apps/wizard-app.png"

# --- 4. Fetch appimagetool if not already available locally --------------------
APPIMAGETOOL="$PROJECT_ROOT/target/appimagetool.AppImage"
if [ ! -x "$APPIMAGETOOL" ]; then
    echo "Downloading appimagetool..."
    APPIMAGETOOL_URL="https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-${ARCH}.AppImage"
    curl -fL "$APPIMAGETOOL_URL" -o "$APPIMAGETOOL"
    chmod +x "$APPIMAGETOOL"
fi

# --- 5. Build the AppImage -------------------------------------------------------
echo "Assembling AppImage..."
cd "$OUTPUT_DIR"
ARCH="$ARCH" "$APPIMAGETOOL" "$BUILD_DIR" "Application_Wizard-${ARCH}.AppImage"

echo ""
echo "=== AppImage created: $OUTPUT_DIR/Application_Wizard-${ARCH}.AppImage ==="
