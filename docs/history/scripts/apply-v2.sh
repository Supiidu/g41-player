#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SRC='app/src/main/java/dev/g41/glassplayer/G41Hook.java'
GRADLE='app/build.gradle'
if [[ ! -f "$SRC" || ! -f "$GRADLE" || ! -f ./gradlew ]]; then
  echo 'ERRO: coloque estes arquivos na raiz do projeto G41GlassPlayer (ao lado de gradlew).' >&2
  exit 1
fi
cp "$SRC" "$SRC.backup-v1"
cp G41Hook-v2.java "$SRC"
python3 - "$GRADLE" <<'PY2'
from pathlib import Path
import re,sys
p=Path(sys.argv[1]); s=p.read_text()
s=re.sub(r'versionCode\s+\d+', 'versionCode 2',s,count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.2.0-shape-test'",s,count=1)
p.write_text(s)
PY2
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
