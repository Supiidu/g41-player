# G41 InfinityBar v0.10.0-beta3 — FFT e ciclo da lockscreen

> Correções com base nos logs e nos três problemas reportados pelo autor após testar a beta2 no Moto G41. O PR #2 continua **Draft**. Esta revisão não foi compilada nem executada no dispositivo pelo assistente.

## Problemas reportados (beta2)

1. A prévia compacta usa `Visualizer(0)` e, ao abrir o player expandido, o FFT/CAVA para de reagir no player grande.
2. Quando o player expandido abre, a InfinityBar original volta a ficar visível por baixo.
3. Quando a tela é apagada e acesa com música pausada, a InfinityBar original reaparece.

Os logs `Compact v0.10-beta2: ocultando somente AnimatedVisibility...` confirmam que o hook da visibilidade foi executado na compacta; não confirmam que o chip tenha permanecido oculto em todas as transições.

## Correções beta3

### 1. Dono único da captura FFT

`GlassOverlay.showOrToggle()` agora fecha a prévia compacta com `CompactBarOverlay.expandedChanged()` **imediatamente depois de criar a janela expandida e antes de `startAudioCapture()`**. Isso libera `Visualizer(0)` do compacto antes de o player grande solicitar o próprio. Se a criação expandida falhar, a prévia compacta é restaurada.

A compacta também libera o Visualizer quando a música pausa ou a tela apaga, e só tenta inicializá-lo se estiver tocando, com retentativa limitada ao intervalo de 4 s. A FFT do player expandido, seus 24 grupos e seu desenho CAVA não foram modificados.

### 2. Chip nativo oculto também com o player expandido

A condicional `NativeMediaChipVisibility.maySuppress()` agora permite ocultar o chip original se **nossa barra compacta OU nosso player expandido** estiver ativo. As condições adicionais permanecem: mídia selecionada, `notificationAlert == null`, keyguard e os dois opt-ins habilitados.

### 3. Persistência na pausa e screen-off

O compacto mantém sua janela vinculada ao `WindowManager` durante screen-off, mas **libera o FFT**, limpa as ondas e reduz o monitoramento para ~600 ms. Quando a tela volta, o ícone/metadados e a captura são atualizados; se a música estiver pausada, não inicia Visualizer. Isso evita perder o estado visual da substituição.

O hook de observação passa de `KeyguardChipBody()` para o pai `AxDynamicBarKeyguardChip()`. O pai continua a compor mesmo quando o `AnimatedVisibility` nativo remove o filho; é uma forma de reconstruir a prévia após wake/recomposição quando necessário.

## Atualizar e verificar fontes

```bash
git fetch origin
git switch feature/infinitybar-v0.10
git pull --ff-only

bash tests/test-cava-spectrum.sh
python3 tests/test-compact-lifecycle.py
bash setup.sh
```

Versão: `0.10.0-beta3-audio-handoff`, versionCode **19**.

## Instalar (com backup/recovery Vector)

```bash
adb devices -l

# mantenha a ocultação desligada até o sistema iniciar normalmente
adb shell settings put global g41_glass_compact_hide_native 0
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_player_enabled 0

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot

adb shell settings put global g41_glass_player_enabled 1
adb shell settings put global g41_glass_compact_preview 1
```

Confirme o player compacto primeiro. Depois:

```bash
adb shell settings put global g41_glass_compact_hide_native 1
```

Pode ser necessário pausar/retomar ou trocar de faixa para que o Compose recomponha o chip com a nova configuração.

## Testes reais (sequência sugerida)

1. **Reprodução:** no Spotify, confirme apenas nossa InfinityBar compacta + mini-wave azul e que a barra original está oculta.
2. **Troca para expanded:** toque na compacta. A barra compacta deve sumir, o player grande manter os graves no centro/altas laterais e a InfinityBar original **não** deve reaparecer.
3. **Troca de volta:** toque fora do expandido para fechar. A nossa compacta retorna com play/pause e a onda azul, sem disputa de FFT.
4. **Pausa:** pause, confirme que as ondas desaparecem; mantenha a música pausada.
5. **Apagar e acender:** sem dar play, apague a tela, espere alguns segundos e acenda. Deve voltar **só a compacta personalizada**, sem o chip original.
6. **Desligar o módulo/opt-in:** confirme que a InfinityBar original volta após recomposição; outros tipos de eventos seguem nativos.

## Logs a enviar

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' \
  | grep -E 'Compact v0.10-beta3|Compact probe v0.10-beta3|Compact native v0.10-beta3|Audio FFT: compact|Ondas: Visualizer|Ondas: modo=|CAVA FFT 24:'
```

Logs esperados:
- `Audio FFT: compact liberado antes de iniciar expanded` ocorre antes de `Ondas: Visualizer session=0 habilitado`.
- No expandido, `Ondas: modo=REAL_FFT` e `CAVA FFT 24:` indicam FFT chegando. O primeiro só demonstra modo; valide também a onda na interface.
- `Compact v0.10-beta3: tela apagada; preservando barra, liberando FFT compact` em screen-off, e `tela ligada; retomando estado` em wake.
- `Compact v0.10-beta3: ocultando somente AnimatedVisibility ...` indica que o hook nativo continua ativo.

## Desativação imediata

```bash
adb shell settings put global g41_glass_compact_hide_native 0
adb shell settings put global g41_glass_compact_preview 0
```

Se o SystemUI estiver instável, desative também `g41_glass_player_enabled` ou use o plano de recuperação do Vector/Magisk. Não faça merge do PR #2 até os três comportamentos serem confirmados no aparelho.

**Limite:** os testes `test-compact-lifecycle.py` validam somente a estrutura de código. Não substituem compilação Android nem verificam concorrência real do `Visualizer(0)` no dispositivo.
