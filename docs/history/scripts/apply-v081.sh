#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SOURCE="app/src/main/java/dev/g41/glassplayer/GlassOverlay.java"
PART="GlassSurface-fluid.java.txt"
GRADLE="app/build.gradle"
MANIFEST="app/src/main/AndroidManifest.xml"
APK="app/build/outputs/apk/debug/app-debug.apk"

for f in "$SOURCE" "$PART" "$GRADLE" "$MANIFEST" "./gradlew"; do
  [[ -f "$f" ]] || { echo "Arquivo ausente: $f" >&2; exit 1; }
done

# Fail safely if the source is not the expected v0.8 with the original CAVA bars.
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/java/dev/g41/glassplayer/GlassOverlay.java').read_text()
expected = [
    'CompactBarVisibility.hide(app, candidate)',
    'private static final class GlassSurface extends View',
    'private static final int BARS = 36;',
    'CAVA v0.7:',
    'private void ensureGradient(float top, float bottom)',
    '/** Black outlined transport controls',
]
missing=[x for x in expected if x not in s]
if missing:
    raise SystemExit('ABORTADO: fonte nao corresponde a v0.8: ' + repr(missing))
part=Path('GlassSurface-fluid.java.txt').read_text()
if 'FLUID v0.8.1:' not in part or part.count('private static final class GlassSurface')!=1:
    raise SystemExit('ABORTADO: arquivo de onda preenchida nao reconhecido')
print('Origem v0.8 e arquivo Fluid Wave verificados.')
PY

mkdir -p backups
STAMP="$(date +%Y%m%d-%H%M%S)"
cp "$SOURCE" "backups/GlassOverlay-antes-v081-$STAMP.java"
cp "$GRADLE" "backups/build-antes-v081-$STAMP.gradle"
cp "$MANIFEST" "backups/Manifest-antes-v081-$STAMP.xml"
if [[ -f "$APK" ]]; then
  cp "$APK" "backups/G41GlassPlayer-antes-v081-$STAMP.apk"
fi

python3 - <<'PY'
from pathlib import Path
import re
src=Path('app/src/main/java/dev/g41/glassplayer/GlassOverlay.java')
s=src.read_text()
a=s.index('    private static final class GlassSurface extends View {')
# Include old Javadoc immediately before class to avoid stale comments.
comment=s.rfind('    /**',0,a)
if comment!=-1 and a-comment<800: a=comment
b=s.index('    /** Black outlined transport controls',a)
part=Path('GlassSurface-fluid.java.txt').read_text()
new=s[:a]+part+'\n'+s[b:]
assert new.count('private static final class GlassSurface extends View')==1
assert new.count('void animateFrame(')==1
assert new.count('new Visualizer(0)')==1
assert 'CompactBarVisibility.hide(app, candidate)' in new
assert 'clock_ls' in new
src.write_text(new)
p=Path('app/build.gradle'); s=p.read_text()
assert re.search(r'versionCode\s+\d+',s)
assert re.search(r'versionName\s+[\'\"][^\'\"]+[\'\"]',s)
s=re.sub(r'versionCode\s+\d+','versionCode 11',s,count=1)
s=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]',"versionName '0.8.1-fluid-wave'",s,count=1)
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml'); s=p.read_text()
s=re.sub(r'android:label="[^"]*"','android:label="G41 Glass Player v0.8.1"',s,count=1)
p.write_text(s)
print('v0.8.1 aplicada: somente visualizacao alterada. Backups preservados.')
PY

if [[ "${G41_SKIP_BUILD:-0}" == "1" ]]; then
  echo 'G41_SKIP_BUILD=1: compilacao omitida (teste do patch).'
  exit 0
fi
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
[[ -f "$APK" ]] || { echo 'ERRO: APK nao gerado.' >&2; exit 1; }
echo "APK gerado: $PWD/$APK"
echo 'Instalacao no celular nao executada automaticamente.'
