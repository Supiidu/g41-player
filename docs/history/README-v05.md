# G41 Glass Player v0.5 — Live Waves & Center Fix

Patch incremental **para o projeto v0.4 que ja esta funcionando no G41**. Nao eh um APK pre-compilado nem um modulo Magisk.

## Correcões feitas

1. **Painel realmente centralizado.** `FrameLayout.LayoutParams.gravity = Gravity.CENTER`, em vez de calcular uma distancia a partir de 36% do topo. Centraliza o *card inteiro*.
2. **Controles centralizados.** Tres botoes de 68dp numa linha de 204dp, alinhada ao centro do card independentemente da capa e do texto.
3. **Wallpaper verdadeiro.** Removido o JPEG duplicado, a camada escura e o falso blur da v0.4. A janela e transparente e mostra o wallpaper que a Infinity X realmente desenha (inclusive o efeito de profundidade). O card usa um fundo branco translucido. O *blur real do wallpaper* dentro do card NAO esta implementado nesta versao.
4. **Tentativa seletiva de ocultar o relogio.** Localiza Views com classes/IDs de relogio grande, define alpha=0 apenas enquanto o painel esta aberto e restaura no fechamento. Se nao encontrar um componente compativel da Infinity X, nao esconde nada (seguro). Logs ajudam a identificar o relogio na proxima etapa.
5. **Ondas animadas.** Duas camadas em Canvas, movimento ~30 FPS **apenas enquanto a musica estiver tocando**. Tenta ler FFT real com `android.media.audiofx.Visualizer(0)` para adaptar a amplitude do desenho; ao faltar autorizacao ou captura de audio, anima suavemente conforme o estado play/pause. A animacao de fallback **nao acompanha as batidas reais**. Quando pausado, o movimento para. Captura e liberada ao fechar o card.

## Instalar a partir do projeto no notebook (Arch Linux)

Baixe `G41GlassPlayer-v05-patch.zip`, depois execute:

```bash
cd "$HOME/DAP project/infinitybar-mod/G41GlassPlayer"
unzip -jo ~/Downloads/G41GlassPlayer-v05-patch.zip -d .
bash apply-v05.sh
```

Depois de **BUILD SUCCESSFUL**:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
```

Quando o Android iniciar normalmente:

```bash
adb shell settings put global g41_glass_player_enabled 1
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Toque na InfinityBar enquanto uma faixa estiver tocando. Procure logs `GLASS V0.5 ABERTO`, `Clock: Views ocultadas=`, `Ondas: captura de audio inicializada` e, se houver FFT real, `Ondas: FFT real recebido`.

## Recuperacao

O modulo v0.5 comeca **desligado** se a flag global estiver em 0. Para desativar enquanto ele estiver aberto:

```bash
adb shell settings put global g41_glass_player_enabled 0
```

O card sera fechado pelo ciclo de atualizacao em ate ~1 s. Se precisar recuperar sem hook, no Magisk/Vector retire `com.android.systemui` do escopo ou, com root ADB:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

Isso desliga o Vector por completo, nao apenas o G41 Glass Player. As versoes anteriores (incluindo o APK da v0.4) ficam em `backups/`, com timestamp.

## Limites e seguranca

- Ainda eh uma janela propria adicionada pelo processo SystemUI, nao uma composicao Compose nativa.
- O player *compacto* continua sendo o componente original da Infinity X.
- A deteccao do relogio e experimental, sem garantia de achar clocks customizados; ela nao mexe em wallpaper, statusbar ou desbloqueio. Se o relogio permanecer, envie o log do Vector.
- `Visualizer` exige acesso ao audio do sistema, que pode estar indisponivel ou bloqueado pelo Android. Nem todos os aplicativos permitem capturar o audio. A animacao sempre pode acompanhar play/pause, mas a resposta real ao som so ocorre quando aparecer `FFT real recebido` no Logcat.
- Mudancas num processo SystemUI sempre envolvem algum risco de travamento, mesmo com o hook reversivel.
