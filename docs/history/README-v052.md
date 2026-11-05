# G41 Glass Player v0.5.2 — ClockStyle fix

## Diagnóstico confirmado no Moto G41

O log da v0.5.1 encontrou:

```
Clock: visto com.android.systemui.clocks.ClockStyle id=clock_ls size=1080x1124 selecionado=false
Clock: visto android.widget.RelativeLayout id=clock_frame size=912x1085 selecionado=false
Clock: visto android.widget.LinearLayout id=clock_frame size=912x1085 selecionado=false
Clock: visto android.widget.TextClock id= size=912x1064 selecionado=false
Clock: Views ocultadas=0
```

O novo alvo é **exatamente** `com.android.systemui.clocks.ClockStyle`, com `id=clock_ls`, dimensões de relógio grande. Não alteramos `clock_frame` e não ocultamos o container inteiro da tela de bloqueio.

A alteração usa `setAlpha(0f)` na View selecionada. A implementação existente guarda o valor original do alpha em `hiddenClockAlphas` e restaura ao fechar a expansão. Essa técnica pode também ocultar elementos que a ROM colocou **dentro** do ClockStyle; teste o efeito de profundidade e mande uma captura caso isso aconteça. O relógio não será ocultado se a ROM reconstruir a View depois da varredura.

## Aplicar

Dentro de `~/DAP project/infinitybar-mod/G41GlassPlayer`:

```bash
unzip -jo ~/Downloads/G41GlassPlayer-v052-clockstyle.zip -d .
bash apply-v052.sh
```

Depois de `BUILD SUCCESSFUL`:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
# apos reiniciar
adb shell settings put global g41_glass_player_enabled 1
```

Abra o player pela InfinityBar e examine:

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' | grep -E 'Clock:|GLASS V0.5.2'
```

O esperado é:

```
Clock: visto com.android.systemui.clocks.ClockStyle id=clock_ls ... selecionado=true
Clock: ocultando com.android.systemui.clocks.ClockStyle id=clock_ls
Clock: Views ocultadas=1
```

Depois feche o player e confirme que o relógio reaparece.

## Desativação e restauração

```bash
adb shell settings put global g41_glass_player_enabled 0
```

Se for preciso restaurar APK anterior, use o arquivo de backup que o script guardou em `backups/G41GlassPlayer-antes-v052-<data>.apk` e reinicie. Se houver reinicializações do SystemUI e o ADB continuar acessível:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

Esse último passo desativa o Vector todo no próximo boot, não só o Glass Player.
