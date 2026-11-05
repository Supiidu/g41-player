#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SOURCE='app/src/main/java/dev/g41/glassplayer/GlassOverlay.java'
PATCH='GlassOverlay-v06.java'
MANIFEST='app/src/main/AndroidManifest.xml'
BUILD='app/build.gradle'
APK='app/build/outputs/apk/debug/app-debug.apk'

for f in "$SOURCE" "$PATCH" "$MANIFEST" "$BUILD" './gradlew'; do
  [[ -f "$f" ]] || { echo "ERRO: arquivo ausente: $f" >&2; exit 1; }
done
# This patch intentionally preserves the v0.5.2 clock fix.
grep -Fq 'cls.equals("com.android.systemui.clocks.clockstyle") && idName.equals("clock_ls")' "$SOURCE" || {
  echo 'ERRO: ClockStyle v0.5.2 nao encontrado. Nao sobrescreverei uma versao diferente.' >&2
  exit 1
}
grep -Fq 'private static void startAudioCapture()' "$SOURCE" || {
  echo 'ERRO: estrutura do GlassOverlay inesperada.' >&2; exit 1
}
grep -Fq 'GLASS V0.6 ABERTO' "$PATCH" || {
  echo 'ERRO: patch v0.6 nao identificado.' >&2; exit 1
}

python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle')
s=p.read_text()
assert re.search(r'versionCode\s+\d+',s), 'versionCode faltando'
assert re.search(r'versionName\s+[\'\"][^\'\"]+[\'\"]',s), 'versionName faltando'
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
assert 'G41 Glass Player' in s, 'label do aplicativo inesperado'
print('Validacao preliminar OK: patch e projeto encontrados.')
PY

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
cp "$SOURCE" "backups/GlassOverlay-antes-v06-$stamp.java"
cp "$BUILD" "backups/build-antes-v06-$stamp.gradle"
cp "$MANIFEST" "backups/Manifest-antes-v06-$stamp.xml"
if [[ -f "$APK" ]]; then
  cp "$APK" "backups/G41GlassPlayer-antes-v06-$stamp.apk"
fi

cp "$PATCH" "$SOURCE"
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle'); s=p.read_text()
s = re.sub(r'versionCode\s+\d+', 'versionCode 8', s, count=1)
s = re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.6.0-live-spectrum-album-palette'",s,count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml');s=p.read_text()
s = re.sub(r'android:label="[^"]*"', 'android:label="G41 Glass Player v0.6"',s,count=1)
p.write_text(s)
print('Codigo v0.6 aplicado; backups criados.')
PY

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
[[ -f "$APK" ]] || { echo 'ERRO: build nao gerou APK.' >&2; exit 1; }
echo "BUILD FINALIZADO — APK: $PWD/$APK"
echo 'Nada foi instalado automaticamente no celular.'
