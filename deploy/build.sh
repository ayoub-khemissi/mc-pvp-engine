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

cp engine-core/build/libs/engine-core-*.jar dist/PvPEngine.jar

# Every mode, discovered rather than listed.
#
# This used to name mode-duel by hand, and mode-fortress was duly forgotten — the remote
# built a server with a mode missing and nothing said a word. A mode's jar is named after
# the "name:" in its own plugin.yml, which is the same name the server will load it under.
for module in modes/*/; do
    plugin_yml="$module/src/main/resources/plugin.yml"
    [[ -f "$plugin_yml" ]] || continue

    name="$(grep -m1 '^name:' "$plugin_yml" | cut -d: -f2 | tr -d " '\"")"
    jar="$(find "$module/build/libs" -name '*.jar' -print -quit 2>/dev/null || true)"

    if [[ -z "$jar" ]]; then
        echo "[warn] $module produced no jar — skipped"
        continue
    fi
    cp "$jar" "dist/$name.jar"
    echo "[build] $name.jar"
done

echo
echo "[build] dist/"
ls -la dist/
