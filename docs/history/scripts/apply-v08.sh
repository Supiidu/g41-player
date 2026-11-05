#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

DIR="app/src/main/java/dev/g41/glassplayer"
SOURCE="$DIR/GlassOverlay.java"
COMPACT="$DIR/CompactBarVisibility.java"
PATCH="GlassOverlay-v08.java"
IMPL="CompactBarVisibility.java"
APK="app/build/outputs/apk/debug/app-debug.apk"

for f in "$SOURCE" "$PATCH" "$IMPL" "app/build.gradle" "app/src/main/AndroidManifest.xml" "./gradlew"; do
  [[ -f "$f" ]] || { echo "Arquivo ausente: $f" >&2; exit 1; }
done
if ! grep -Fq 'GLASS V0.7 ABERTO' "$SOURCE"; then
  echo "ABORTADO: esperado GlassOverlay v0.7; nenhuma mudanca feita." >&2
  exit 1
fi
if ! grep -Fq 'CompactBarVisibility.hide(app, candidate)' "$PATCH"; then
  echo "ABORTADO: patch nao parece ser v0.8." >&2
  exit 1
fi
if [[ -e "$COMPACT" ]]; then
  echo "ABORTADO: $COMPACT ja existe; faca backup manual antes de sobrescrever." >&2
  exit 1
fi

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
cp "$SOURCE" "backups/GlassOverlay-antes-v08-$stamp.java"
cp app/build.gradle "backups/build-antes-v08-$stamp.gradle"
cp app/src/main/AndroidManifest.xml "backups/Manifest-antes-v08-$stamp.xml"
if [[ -f "$APK" ]]; then
  cp "$APK" "backups/G41GlassPlayer-antes-v08-$stamp.apk"
fi
cp "$PATCH" "$SOURCE"
cp "$IMPL" "$COMPACT"
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle')
s=p.read_text()
if not re.search(r'versionCode\s+\d+',s): raise SystemExit('versionCode nao encontrado')
if not re.search(r'versionName\s+[\'\"][^\'\"]+[\'\"]',s): raise SystemExit('versionName nao encontrado')
s=re.sub(r'versionCode\s+\d+','versionCode 10',s,count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]',"versionName '0.8.0-compact-visibility'",s,count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
s=re.sub(r'android:label="[^"]*"','android:label="G41 Glass Player v0.8"',s,count=1)
p.write_text(s)
print('v0.8 aplicada. Ocultacao conservadora + diagnostico. Backups salvos.')
PY

if [[ "${G41_SKIP_BUILD:-0}" == "1" ]]; then
  echo "G41_SKIP_BUILD=1: compilacao ignorada (teste do patch)."
  exit 0
fi
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
[[ -f "$APK" ]] || { echo "ERRO: nao encontrei APK" >&2; exit 1; }
echo "APK: $PWD/$APK"
echo "Instalacao no celular NAO executada automaticamente."
