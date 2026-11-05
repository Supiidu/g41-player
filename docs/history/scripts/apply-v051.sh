#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SOURCE='app/src/main/java/dev/g41/glassplayer/GlassOverlay.java'
[[ -f "$SOURCE" && -f './gradlew' && -f 'app/build.gradle' ]] || {
  echo 'ERRO: execute na pasta raiz do projeto G41GlassPlayer.' >&2; exit 1;
}
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
cp "$SOURCE" "backups/GlassOverlay-antes-v051-$stamp.java"
cp app/build.gradle "backups/build-antes-v051-$stamp.gradle"
if [[ -f app/build/outputs/apk/debug/app-debug.apk ]]; then
    cp app/build/outputs/apk/debug/app-debug.apk "backups/G41GlassPlayer-antes-v051-$stamp.apk"
fi
python3 - <<'PY'
from pathlib import Path
import re
p=Path('app/src/main/java/dev/g41/glassplayer/GlassOverlay.java')
s=p.read_text()
old='''            Object views = XposedHelpers.callMethod(global, "getRootViews");'''
new='''            // Android 16: getRootViews() exige um IBinder; para iterar janelas use getWindowViews().
            Object views;
            try {
                views = XposedHelpers.callMethod(global, "getWindowViews");
            } catch (Throwable apiMismatch) {
                // Campo interno AOSP. Somente leitura; nao altera a lista de janelas.
                views = XposedHelpers.getObjectField(global, "mViews");
            }'''
if old not in s:
    raise SystemExit('ERRO: chamada getRootViews() nao encontrada; nao alterei nenhum arquivo. Confirme que usa GlassOverlay v0.5.')
s=s.replace(old,new,1)
needle='''            int before = hiddenClockAlphas.size();'''
if needle not in s:
    raise SystemExit('ERRO: trecho de diagnostico nao encontrado; backup em backups/.')
s=s.replace(needle,'''            Log.i(TAG, "Clock: janelas visiveis ao SystemUI=" + ((List<?>)views).size());
'''+needle,1)
needle='''                || idName.equals("large_clock")'''
if needle not in s:
    raise SystemExit('ERRO: lista de IDs de clock inesperada. Backup guardado em backups/.')
s=s.replace(needle,'''                || idName.equals("large_clock_frame")
                || idName.equals("keyguard_large_clock")
                || idName.equals("large_clock")''',1)
# Log diagnostico de candidatos, sem ocultar Views genericas
needle='''        if (candidate) {
            if (!hiddenClockAlphas.containsKey(v))'''
replacement='''        if ((idName.contains("clock") || cls.contains("clock")) && large
                && clockCandidateLogs < 28) {
            clockCandidateLogs++;
            Log.i(TAG, "Clock: visto " + name + " id=" + idName
                    + " size=" + v.getWidth() + "x" + v.getHeight()
                    + " selecionado=" + candidate);
        }
        if (candidate) {
            if (!hiddenClockAlphas.containsKey(v))'''
if needle not in s:
    raise SystemExit('ERRO: local de identificacao do clock mudou. Backup guardado em backups/.')
s=s.replace(needle,replacement,1)
needle='''    private static final Map<View, Float> hiddenClockAlphas = new IdentityHashMap<>();'''
if needle not in s:
    raise SystemExit('ERRO: mapa do clock nao encontrado. Backup guardado em backups/.')
s=s.replace(needle,needle+'\n    private static int clockCandidateLogs = 0;',1)
s=s.replace('Clock: getRootViews nao retornou lista', 'Clock: consulta de janelas nao retornou lista')
s=s.replace('GLASS V0.5 ABERTO', 'GLASS V0.5.1 ABERTO').replace('GLASS V0.5 FECHADO','GLASS V0.5.1 FECHADO')
s=s.replace('G41 Glass Player v0.5', 'G41 Glass Player v0.5.1')
p.write_text(s)
b=Path('app/build.gradle'); bs=b.read_text();
bs=re.sub(r'versionCode\s+\d+', 'versionCode 6',bs,count=1)
bs=re.sub(r'versionName\s+[\'\"][^\'\"]+[\'\"]', "versionName '0.5.1-clock-fix'",bs,count=1)
b.write_text(bs)
m=Path('app/src/main/AndroidManifest.xml');ms=m.read_text();
ms=re.sub(r'android:label="G41 Glass Player v0\.5"', 'android:label="G41 Glass Player v0.5.1"',ms,count=1)
m.write_text(ms)
print('OK: getWindowViews, IDs de relogio e diagnostico adicionados.')
PY
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
