#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BASE='app/src/main/java/dev/g41/glassplayer'
if [[ ! -f "$BASE/G41Hook.java" || ! -f app/build.gradle || ! -x ./gradlew ]]; then
    echo 'ERRO: execute na raiz do seu projeto G41GlassPlayer (mesma pasta de gradlew).' >&2
    exit 1
fi
for source in G41Hook-v04.java GlassOverlay.java g41_lockscreen.jpg; do
    [[ -f "$source" ]] || { echo "Falta arquivo: $source" >&2; exit 1; }
done
mkdir -p backups app/src/main/assets
STAMP="$(date +%Y%m%d-%H%M%S)"
cp "$BASE/G41Hook.java" "backups/G41Hook-antes-v04-$STAMP.java"
if [[ -f "$BASE/GlassOverlay.java" ]]; then
    cp "$BASE/GlassOverlay.java" "backups/GlassOverlay-antes-v04-$STAMP.java"
fi
if [[ -f app/build/outputs/apk/debug/app-debug.apk ]]; then
    cp app/build/outputs/apk/debug/app-debug.apk "backups/G41GlassPlayer-antes-v04-$STAMP.apk"
    echo "APK anterior salvo: backups/G41GlassPlayer-antes-v04-$STAMP.apk"
fi
cp G41Hook-v04.java "$BASE/G41Hook.java"
cp GlassOverlay.java "$BASE/GlassOverlay.java"
cp g41_lockscreen.jpg app/src/main/assets/g41_lockscreen.jpg
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle')
s=p.read_text()
s=re.sub(r'versionCode\s+\d+', 'versionCode 4', s, count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.4.0-figma-expanded'", s, count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
s=re.sub(r'android:label="[^"]*"', 'android:label="G41 Glass Player (Figma v0.4)"', s, count=1)
s=re.sub(r'android:name="xposeddescription" android:value="[^"]*"',
         'android:name="xposeddescription" android:value="Player expandido com visual do Figma (experimental)."', s, count=1)
p.write_text(s)
PY
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nSUCESSO. APK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
echo 'ATENCAO: desative a flag antes de instalar e faca o primeiro boot com flag 0.'
