#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BASE='app/src/main/java/dev/g41/glassplayer'
[[ -f "$BASE/G41Hook.java" && -f "$BASE/GlassOverlay.java" && -f app/build.gradle && -f ./gradlew ]] || {
  echo 'ERRO: execute na raiz do G41GlassPlayer v0.4, junto de gradlew.' >&2
  exit 1
}
for src in G41Hook-v05.java GlassOverlay-v05.java; do
  [[ -f "$src" ]] || { echo "Falta $src" >&2; exit 1; }
done
mkdir -p backups
STAMP="$(date +%Y%m%d-%H%M%S)"
cp "$BASE/G41Hook.java" "backups/G41Hook-antes-v05-$STAMP.java"
cp "$BASE/GlassOverlay.java" "backups/GlassOverlay-antes-v05-$STAMP.java"
if [[ -f app/build/outputs/apk/debug/app-debug.apk ]]; then
  cp app/build/outputs/apk/debug/app-debug.apk "backups/G41GlassPlayer-antes-v05-$STAMP.apk"
fi
cp G41Hook-v05.java "$BASE/G41Hook.java"
cp GlassOverlay-v05.java "$BASE/GlassOverlay.java"
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle')
s=p.read_text()
s=re.sub(r'versionCode\s+\d+', 'versionCode 5',s,count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.5.0-live-waves'",s,count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
s=re.sub(r'android:label="[^"]*"', 'android:label="G41 Glass Player v0.5"',s,count=1)
p.write_text(s)
PY
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nAPK pronto: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
echo 'Backups guardados em backups/; instale com a flag desligada.'
