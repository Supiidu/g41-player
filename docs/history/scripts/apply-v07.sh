#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SOURCE="app/src/main/java/dev/g41/glassplayer/GlassOverlay.java"
PATCH="GlassOverlay-v07.java"
BUILD="app/build.gradle"
MANIFEST="app/src/main/AndroidManifest.xml"
APK="app/build/outputs/apk/debug/app-debug.apk"

for f in "$SOURCE" "$PATCH" "$BUILD" "$MANIFEST" "./gradlew"; do
  [[ -f "$f" ]] || { echo "Arquivo ausente: $f" >&2; exit 1; }
done
if ! grep -Fq 'GLASS V0.6 ABERTO' "$SOURCE"; then
  echo "ERRO: esperado GlassOverlay v0.6. Nao alterarei outra versao." >&2
  exit 1
fi
if ! grep -Fq 'CAVA v0.7' "$PATCH"; then
  echo "ERRO: patch v0.7 invalido" >&2
  exit 1
fi
if ! grep -Fq 'cls.equals("com.android.systemui.clocks.clockstyle") && idName.equals("clock_ls")' "$PATCH"; then
  echo "ERRO: ClockStyle v0.5.2 nao preservado" >&2
  exit 1
fi

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
cp "$SOURCE" "backups/GlassOverlay-antes-v07-$stamp.java"
cp "$BUILD" "backups/build-antes-v07-$stamp.gradle"
cp "$MANIFEST" "backups/Manifest-antes-v07-$stamp.xml"
[[ ! -f "$APK" ]] || cp "$APK" "backups/G41GlassPlayer-antes-v07-$stamp.apk"

cp "$PATCH" "$SOURCE"
python3 - <<'PY2'
from pathlib import Path
import re
p=Path('app/build.gradle')
s=p.read_text()
assert re.search(r'versionCode\s+\d+',s), 'versionCode ausente'
assert re.search(r'versionName\s+[\'\"][^\'\"]+[\'\"]',s), 'versionName ausente'
s=re.sub(r'versionCode\s+\d+','versionCode 9',s,count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]',"versionName '0.7.0-cava-spectrum'",s,count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
s=re.sub(r'android:label="[^"]*"','android:label="G41 Glass Player v0.7"',s,count=1)
p.write_text(s)
print('v0.7 aplicada, backup salvo. ClockStyle e Visualizer preservados.')
PY2

if [[ "${G41_SKIP_BUILD:-0}" == "1" ]]; then
  echo "G41_SKIP_BUILD=1: teste local, compilacao ignorada."
  exit 0
fi
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
[[ -f "$APK" ]] || { echo "ERRO: APK nao gerado" >&2; exit 1; }
echo "APK gerado: $PWD/$APK"
echo "Nada foi instalado automaticamente no telefone."
