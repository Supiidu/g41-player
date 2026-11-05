# G41 Glass Player v0.5.1 — Clock Fix

Este patch atualiza somente a busca do relógio no `GlassOverlay.java` da versão v0.5. Não modifica o painel, os botões, a captura de áudio, nem o SystemUI instalado no telefone.

## Por que a v0.5 falhou?

O log mostrou `NoSuchMethodError: WindowManagerGlobal#getRootViews()`. Na implementação do Android, `getRootViews` exige um parâmetro `IBinder` e retorna `ViewRootImpl`, enquanto `getWindowViews()` retorna a lista de `View` de janelas. O patch muda a consulta para `getWindowViews()` e usa leitura de `mViews` como alternativa. Adiciona o ID `large_clock_frame` e logs resumidos dos candidatos a relógio.

Não existe garantia de que a ROM Infinity X exponha o relógio customizado como uma View identificável: se for um componente Compose ou tiver outro identificador, precisaremos encontrar a classe real. A v0.5.1 evita esconder containers não identificados.

## Uso

Baixe o ZIP e extraia na pasta do projeto (não no telefone):

```bash
cd "$HOME/DAP project/infinitybar-mod/G41GlassPlayer"
unzip -jo ~/Downloads/G41GlassPlayer-v051-clockfix.zip -d .
bash apply-v051.sh
```

Após `BUILD SUCCESSFUL`:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
# quando o aparelho iniciar:
adb shell settings put global g41_glass_player_enabled 1
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Toque no player compacto e procure `Clock: janelas visiveis ao SystemUI`, `Clock: visto` e `Clock: Views ocultadas`.

## Recuperação

```bash
adb shell settings put global g41_glass_player_enabled 0
```

O APK anterior fica salvo em `backups/G41GlassPlayer-antes-v051-*.apk`. Se o SystemUI entrar em ciclo de falhas e ADB estiver disponível:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

Isso desativa temporariamente **todo o Vector** no próximo boot. Não remove os outros módulos Magisk.
