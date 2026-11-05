# G41 Glass Player v0.6 — Live Spectrum + Album Palette

Patch incremental para **v0.5.2 ClockStyle** da Infinity X GSI no Moto G41, rodando no SystemUI via Vector/LSPosed API. **Não substitui SystemUI.apk**.

## O que muda

- Preserva ocultação temporária do `com.android.systemui.clocks.ClockStyle` (`clock_ls`) e restauração ao fechar.
- Quando o `Visualizer(0)` entrega **FFT com sinal**, 24 bandas logarítmicas de 45 Hz a 12 kHz dirigem a forma da onda. O desenho usa *attack/release smoothing* e movimento derivado dos dados reais.
- Se não houver FFT com sinal, mas o `Visualizer` entregar **waveform RMS com sinal**, o volume real controla as ondas (sem informação de frequência).
- Se nenhum sinal for acessível, a onda **fica em repouso**: sem batidas ou números aleatórios. O log distingue `REAL_FFT`, `REAL_WAVEFORM_RMS` e `SEM_CAPTURA_REPOUSO`.
- Extrai cor temática de capa/metadata (`Bitmap` ou `Drawable` do evento) por amostragem leve sem bibliotecas extras. Ondas e barra de progresso acompanham a cor da capa. Fundos e textos continuam iguais ao Figma.
- O `Visualizer` só opera enquanto o painel está aberto; libera os recursos ao fechar. Não ativa microfone nem pede permissão ao usuário.

## Como compilar

Na raiz do projeto `~/DAP project/infinitybar-mod/G41GlassPlayer`:

```bash
unzip -jo ~/Downloads/G41GlassPlayer-v06-patch.zip -d .
bash apply-v06.sh
```

A aplicação gera `app/build/outputs/apk/debug/app-debug.apk`, sem instalar no celular.

## Teste controlado

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
# depois que o Android reiniciar:
adb shell settings put global g41_glass_player_enabled 1
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Reproduza algo no Auxio ou Spotify; bloqueie e abra o Glass Player.

Procure por:
- `Paleta: album accent=#...`
- `Ondas: permissoes do processo SystemUI: RECORD_AUDIO=... MODIFY_AUDIO_SETTINGS=...`
- `Ondas: Visualizer session=0 habilitado ... (aguardando callbacks reais)`
- `Ondas: modo=REAL_FFT` (ideal)
- `Ondas: modo=REAL_WAVEFORM_RMS` (reage ao volume real)
- `Ondas: modo=SEM_CAPTURA_REPOUSO` (captura não disponibilizada)
- `Ondas: diagnostico apos 6s: FFT=... waveform=...`

### Limitação de captura de áudio

A documentação oficial da API Android informa que `Visualizer` precisa de `RECORD_AUDIO`; `Visualizer(0)` (output mix global) também exige `MODIFY_AUDIO_SETTINGS`. O uso dentro do processo SystemUI **não garante** que uma ROM entregue sinal FFT. Inicializar o efeito não prova ter recebido áudio. Se os callbacks não chegarem, a onda não finge sincronização. Não conceda permissões sensíveis a aplicativos sem avaliar consequências.

### Recuperação

```bash
adb shell settings put global g41_glass_player_enabled 0
```

O script armazena o APK e os arquivos da versão anterior em `backups/` para recuperação. Se quiser voltar ao APK antigo, instale o arquivo `backups/G41GlassPlayer-antes-v06-<data>.apk` com `adb install -r -d` (debuggable); desative a flag antes de fazê-lo. Se a interface do sistema falhar gravemente, desative temporariamente o Vector no boot:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

**Risco:** o módulo roda dentro do SystemUI. A v0.6 não foi executada em um Moto G41 real aqui; valide os logs e a aparência no aparelho.
