#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v javac >/dev/null || ! command -v java >/dev/null; then
  echo "ERRO: precisa de um JDK instalado (Java 17 recomendado)." >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

javac -d "$temp_dir" \
  app/src/main/java/dev/g41/glassplayer/CavaSpectrum.java \
  tests/java/dev/g41/glassplayer/CavaSpectrumTest.java

java -cp "$temp_dir" dev.g41.glassplayer.CavaSpectrumTest
