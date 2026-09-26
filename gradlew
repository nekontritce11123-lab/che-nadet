#!/usr/bin/env bash
# Source-only, checksum-pinned bootstrap. Not the generated Gradle Wrapper JAR.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION=8.13
SHA=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
BIN="$ROOT/.tools/gradle-$VERSION/bin/gradle"
if [[ ! -x "$BIN" ]]; then
  command -v java >/dev/null || { echo 'Install JDK 17+ first.' >&2; exit 2; }
  command -v curl >/dev/null || { echo 'curl is required for first bootstrap.' >&2; exit 2; }
  command -v unzip >/dev/null || { echo 'unzip is required for first bootstrap.' >&2; exit 2; }
  mkdir -p "$ROOT/.tools"
  mkdir "$ROOT/.tools/bootstrap.lock" 2>/dev/null || { echo 'Another bootstrap is active. Inspect .tools/bootstrap.lock if a previous run was interrupted.' >&2; exit 2; }
  ZIP="$ROOT/.tools/gradle-$VERSION.zip.part"
  trap 'rm -f "$ZIP"; rmdir "$ROOT/.tools/bootstrap.lock" 2>/dev/null || true' EXIT
  curl --fail --location --proto '=https' --proto-redir '=https' --connect-timeout 10 --max-time 180 \
    "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" --output "$ZIP"
  if command -v sha256sum >/dev/null; then ACTUAL="$(sha256sum "$ZIP" | cut -d' ' -f1)"
  else ACTUAL="$(shasum -a 256 "$ZIP" | cut -d' ' -f1)"; fi
  [[ "$ACTUAL" == "$SHA" ]] || { echo 'Gradle checksum mismatch; refusing to run.' >&2; exit 3; }
  unzip -q "$ZIP" -d "$ROOT/.tools"
  chmod +x "$BIN"
  rm -f "$ZIP"; rmdir "$ROOT/.tools/bootstrap.lock"; trap - EXIT
fi
exec "$BIN" -p "$ROOT" "$@"
