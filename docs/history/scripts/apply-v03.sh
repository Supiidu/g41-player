#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
MAIN='app/src/main/java/dev/g41/glassplayer/G41Hook.java'
OTHER='app/src/main/java/dev/g41/glassplayer/GlassOverlay.java'
GRADLE='app/build.gradle'
if [[ ! -e "$MAIN" || ! -e "$GRADLE" || ! -x ./gradlew ]]; then
  echo 'ERRO: descompacte no diretorio raiz de G41GlassPlayer (ao lado de gradlew).' >&2
  exit 1
fi
cp -n "$MAIN" "$MAIN.backup-v02" || true
cp G41Hook-v03.java "$MAIN"
cp GlassOverlay.java "$OTHER"
python3 - "$GRADLE" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1]); s = p.read_text()
s = re.sub(r'versionCode\s+\d+', 'versionCode 3', s, count=1)
s = re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.3.0-custom-panel'", s, count=1)
p.write_text(s)
PY
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
