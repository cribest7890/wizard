#!/usr/bin/env bash
# Top-level build script: compiles the Maven project and packages the AppImage.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "=== Step 1/2: Building the executable JAR with Maven ==="
mvn clean package

echo ""
echo "=== Step 2/2: Building the Linux AppImage ==="
chmod +x appimage/build-appimage.sh
./appimage/build-appimage.sh

echo ""
echo "Build complete. Artifacts are in the target/ directory:"
echo "  - target/wizard-app.jar               (executable JAR, requires Java 21+)"
echo "  - target/Application_Wizard-*.AppImage (self-contained, no Java required)"
