#!/usr/bin/env bash
#
# Builds the plugins and puts them in dist/, ready for install.sh.
# Run it on the server, or locally and upload dist/.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "[build] tests + jars (needs JDK 25)"
./gradlew clean build

mkdir -p dist
rm -f dist/*.jar

cp engine-core/build/libs/engine-core-*.jar        dist/PvPEngine.jar
cp modes/mode-duel/build/libs/mode-duel-*.jar      dist/ModeDuel.jar

echo
echo "[build] dist/"
ls -la dist/
