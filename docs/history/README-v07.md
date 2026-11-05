# G41 Glass Player v0.7 — CAVA Spectrum

Atualização **visual** incremental para a v0.6 com captura de áudio real, paleta dinâmica e correção de `ClockStyle`. O `SystemUI.apk` continua intacto.

## O que muda

- Visualizador estilo **CAVA** com 36 barras verticais de cantos arredondados na região inferior do card; sem mais ondas senoidais.
- FFT real de 24 bandas logarítmicas (já capturada na v0.6) interpolada para as 36 barras. **Nenhum beat aleatório inventado.**
- *Attack* rápido, decaimento suave, marcadores de pico com retenção curta e autoajuste moderado de sensibilidade para áudios diferentes.
- Gradiente das barras e marcadores usando a **cor da capa**; barra de progresso segue dinâmica como na v0.6.
- Atualização da animação aproximadamente a 50 FPS somente enquanto o painel estiver aberto e houver música tocando.
- Um estilo opcional **espelhado**, com barras crescendo para cima e para baixo do centro.
- Sem FFT, o RMS real produz uma amplitude única com envoltória espacial suave; sem áudio, barras em repouso.
- Correções do relógio `clock_ls`, wallpaper real, mídia, botões e progresso preservadas.

## Instalação

Na raiz de `~/DAP project/infinitybar-mod/G41GlassPlayer` (com **v0.6** aplicada):

```bash
unzip -jo ~/Downloads/G41GlassPlayer-v07-CAVA.zip -d .
bash apply-v07.sh
```

O script faz backup dos fontes, Gradle, manifest e APK anteriores em `backups/` antes de compilar. O primeiro passo valida que `GlassOverlay.java` corresponde à **v0.6**.

Após `BUILD SUCCESSFUL`:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

## Modos visuais (sem recompilar)

**Clássico** (padrão, barras de baixo para cima):

```bash
adb shell settings put global g41_glass_cava_mirror 0
```

**Espelhado** (barras crescem em ambas as direções):

```bash
adb shell settings put global g41_glass_cava_mirror 1
```

**Sensibilidade** (padrão `1`, aceita `0.5` a `2.5`):

```bash
adb shell settings put global g41_glass_cava_sensitivity 1.25
```

Essas configurações são lidas ao **abrir** o Glass Player: feche e reabra para aplicar.

## Validação

```bash
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Procure por `CAVA v0.7: 36 barras`, `Ondas: modo=REAL_FFT` e `Paleta: album accent=#...`. A mensagem de ondas ainda se chama 'Ondas' porque mantém o diagnóstico de captura da v0.6.

## Recuperação

Desativar overlay custom:

```bash
adb shell settings put global g41_glass_player_enabled 0
```

Reverter versão APK: localizar o backup mais recente em `backups/G41GlassPlayer-antes-v07-*.apk`, e instalá-lo via `adb install -r -d ARQUIVO.apk` se a configuração de assinatura e downgrade permitir.

Se o SystemUI reiniciar repetidamente: desativar Vector no boot pelo Magisk e reiniciar:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

**Nota de teste:** o patch foi validado estruturalmente, mas a compilação Android e a execução no Moto G41 devem ser confirmadas no seu notebook/aparelho.
