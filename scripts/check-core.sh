#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v kotlinc >/dev/null || { echo 'Kotlin compiler required (1.9+).'; exit 2; }
mkdir -p core/build/offline
mapfile -t sources < <(find core/src/main/kotlin core/src/check/kotlin -name '*.kt' | sort)
kotlinc "${sources[@]}" -jvm-target 17 -include-runtime -d core/build/offline/core-checks.jar
java -jar core/build/offline/core-checks.jar "$@"
