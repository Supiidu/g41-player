#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SOURCE='app/src/main/java/dev/g41/glassplayer/GlassOverlay.java'
[[ -f "$SOURCE" && -x ./gradlew && -f app/build.gradle ]] || {
  echo 'ERRO: extraia na pasta raiz do G41GlassPlayer (onde esta gradlew).' >&2
  exit 1
}
python3 - <<'PY'
from pathlib import Path
p = Path('app/src/main/java/dev/g41/glassplayer/GlassOverlay.java')
s = p.read_text()
old = 'if (large && (cls.endsWith("animatableclockview")'
if old not in s:
    raise SystemExit('ERRO: trecho de selecao de clock da v0.5.1 nao encontrado. Nada foi modificado.')
if 'ClockStyle' not in s and 'getWindowViews' not in s:
    raise SystemExit('ERRO: nao parece ser a v0.5.1. Nada foi modificado.')
if 'getWindowViews' not in s:
    raise SystemExit('ERRO: a correcao getWindowViews da v0.5.1 esta ausente. Nada foi modificado.')
new = '''if (large && ((cls.equals("com.android.systemui.clocks.clockstyle") && idName.equals("clock_ls"))
                || cls.endsWith("animatableclockview")'''
s2 = s.replace(old, new, 1)
if s2 == s:
    raise SystemExit('ERRO: nao foi possivel aplicar o patch. Nada foi modificado.')
# Opcional: identificacao de versao nos logs existentes.
s2 = s2.replace('GLASS V0.5.1 ABERTO', 'GLASS V0.5.2 ABERTO')
s2 = s2.replace('GLASS V0.5.1 FECHADO', 'GLASS V0.5.2 FECHADO')
s2 = s2.replace('G41 Glass Player v0.5.1', 'G41 Glass Player v0.5.2')
# Validar o alvo antes de escrever qualquer arquivo.
assert 'cls.equals("com.android.systemui.clocks.clockstyle") && idName.equals("clock_ls")' in s2
p.with_suffix('.java.v052check').write_text(s2)
print('Pre-validacao OK: ClockStyle id=clock_ls e unico novo alvo.')
PY
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
cp "$SOURCE" "backups/GlassOverlay-antes-v052-$stamp.java"
cp app/build.gradle "backups/build-antes-v052-$stamp.gradle"
cp app/src/main/AndroidManifest.xml "backups/Manifest-antes-v052-$stamp.xml"
if [[ -f app/build/outputs/apk/debug/app-debug.apk ]]; then
  cp app/build/outputs/apk/debug/app-debug.apk "backups/G41GlassPlayer-antes-v052-$stamp.apk"
fi
mv "${SOURCE}.v052check" "$SOURCE"
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/build.gradle'); s=p.read_text()
s, n = re.subn(r'versionCode\s+\d+', 'versionCode 7', s, count=1)
if n != 1: raise SystemExit('ERRO: versionCode ausente')
s, n = re.subn(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.5.2-clockstyle'", s, count=1)
if n != 1: raise SystemExit('ERRO: versionName ausente')
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml'); s=p.read_text()
s=re.sub(r'android:label="G41 Glass Player v0\.5\.1"', 'android:label="G41 Glass Player v0.5.2"', s)
p.write_text(s)
print('Versao 0.5.2 atualizada.')
PY
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
echo 'O modulo foi compilado; nenhum comando de instalacao foi executado no telefone.'
