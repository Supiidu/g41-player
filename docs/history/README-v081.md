# G41 Glass Player v0.8.1 — Fluid Wave

**Objetivo:** trocar as 36 barras CAVA por uma **onda contínua preenchida**, no estilo do mockup do Figma, mantendo **a mesma detecção real da música**.

## O que NÃO foi alterado
- `Visualizer(0)`, FFT de 24 bandas, captura de waveform RMS e escolha de modo `REAL_FFT` / `REAL_WAVEFORM_RMS` / sem captura.
- `WAVE_TICK` a ~50 FPS, ganho automático, sensibilidade e suavização temporal originais.
- Cor do álbum, cor da barra de progresso, controles, posição do card, relógio e ocultação da InfinityBar na v0.8.
- `SystemUI.apk` não é modificado. O módulo continua operando via Vector.

## O que mudou
- Desenho das amplitudes em **curvas suaves fechadas e preenchidas** (`Canvas` + `Path.quadTo`).
- A onda frontal segue os mesmos 36 valores do CAVA, com suavização horizontal. A onda traseira é uma média mais suave **dos mesmos valores reais**, para profundidade — sem ruído aleatório, sem batidas inventadas.
- Gradientes semitransparentes adaptados à capa e contorno fino na crista da onda.
- 72 amostras de desenho por quadro **não significam 72 bandas de FFT**. É interpolação visual para reduzir serrilhado.
- Quando pausado ou sem sinal, o relevo da onda desce a zero; permanece somente a base colorida da faixa inferior do card.

## Aplicar no Arch Linux (partindo da v0.8)

```bash
cd "$HOME/DAP project/infinitybar-mod/G41GlassPlayer"
unzip -jo ~/Downloads/G41GlassPlayer-v081-FluidWave.zip -d .
bash apply-v081.sh
```

O script valida a presença do código v0.8, cria backups em `backups/`, altera somente a classe visual interna `GlassSurface`, atualiza a versão e compila o APK.

## Instalar

Após `BUILD SUCCESSFUL`:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
```

Depois do reboot:

```bash
adb shell settings put global g41_glass_player_enabled 1
```

Use o Logcat:

```bash
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Procure `FLUID v0.8.1: onda preenchida`, `Ondas: modo=REAL_FFT` e `Paleta: album accent=#...`.

A sensibilidade continua a mesma:

```bash
adb shell settings put global g41_glass_cava_sensitivity 1.25
```

Feche e reabra o player para aplicar.

## Recuperação

Desative o painel customizado:

```bash
adb shell settings put global g41_glass_player_enabled 0
```

Se o APK da v0.8 estiver no backup, reinstale-o com `adb install -r -d <caminho-do-backup.apk>` (pode depender das restrições de downgrade do Android). O backup do fonte também fica preservado.

Se houver falhas graves no SystemUI, desative o módulo Vector no próximo boot:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

**Validação:** patch conferido estaticamente e aplicado em cópia da v0.8. Ainda precisa ser compilado no SDK Android do seu notebook e testado no G41.
