# InfinityBar v0.10.0-beta2 — Ocultar chip nativo de mídia

**Estado:** beta experimental na branch `feature/infinitybar-v0.10`, PR #2 em rascunho. Não foi compilada nem testada no aparelho pelo assistente. O G41/SystemUI pode sofrer falhas: mantenha backup e acesso à recuperação do módulo.

## Evidências do SystemUI enviado pelo autor

- `SystemUI-original.apk` (SHA-256 `e73d97af78dc7e68c01c06406d0f082c065fb21174d8a181f3111cbfc08144a4`)
- `AxDynamicBarKeyguardChipKt.java` exportado pelo JADX (SHA-256 `bebec3fad8bbb668bfc55545a061306aeaf4016d9b6359cf79e44a6efcea3e5b`)
- `AxDynamicBarKeyguardChip()` começa por volta da linha 602 da fonte decompilada. Há **dois** `AnimatedVisibility` independentes:
  - ~linha 685: `AnimatedVisibility(MutableTransitionState, ...)` — expansão/transições do chip
  - ~linha 778: `AnimatedVisibility(boolean z2, Modifier modifierPointerInput, ...)` — **chip compacto da tela de bloqueio**
- A fonte declara a escolha do evento de mídia dentro do chip com `AxDynamicBarChipState.event`, e permite um `notificationAlert` que tem prioridade.

A classe `NativeMediaChipVisibility` agora intercepta apenas a sobrecarga booleana, identifica o chamador direto `AxDynamicBarKeyguardChip` e altera o argumento de visibilidade para `false` sob condições estritas. **Não** pula um composable nem oculta o `AndroidComposeView` de tela cheia. A mudança usa o controle de transição que a própria ROM implementou, mas o mecanismo de hook ainda é experimental.

## Condições de ocultação

A InfinityBar original só recebe o pedido de invisibilidade quando **todas** estas condições coincidem:
1. `g41_glass_compact_preview=1` e `g41_glass_compact_hide_native=1` (segundo ajuste desativado por padrão)
2. Nosso próprio overlay compacto foi realmente adicionado e está visível
3. `g41_glass_player_enabled=1` e keyguard bloqueada
4. `AxDynamicBarChipState.event` é `IslandEvent.Media`, sem `notificationAlert` prioritário
5. O evento selecionado na expansão continua sendo mídia
6. O método chamada é exatamente a sobrecarga booleana de `AnimatedVisibility` chamada diretamente pelo compositor da InfinityBar

O fallback permanece a implementação da ROM quando qualquer condição não se verificar.

## Atualizar e compilar no Arch

```bash
git fetch origin
git switch feature/infinitybar-v0.10
git pull --ff-only
bash tests/test-cava-spectrum.sh
bash setup.sh
```

Versão: `0.10.0-beta2-native-visibility` (`versionCode 18`).

## Instalação segura

Confira `adb devices -l` e tenha o APK anterior e recuperação do Vector/Xposed. Instale com ambos os testes desativados:

```bash
adb shell settings put global g41_glass_compact_hide_native 0
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

### Primeiro teste — barra personalizada sem ocultação

```bash
adb shell settings put global g41_glass_compact_preview 1
```

Na lockscreen com música, confirme que nossa barra compacta aparece e o toque abre o player expandido.

### Segundo teste — solicitar ocultação da InfinityBar nativa

```bash
adb shell settings put global g41_glass_compact_hide_native 1
```

Ative/desative play ou mude de música para provocar uma recomposição do chip nativo. A mudança no valor de `Settings.Global` **não é observada automaticamente pelo Compose**; até uma recomposição, a barra original pode continuar aparecendo.

Esperado após recomposição: a ROM faz uma transição para ocultar seu chip compacto de mídia, enquanto nossa barra permanece visível e clicável. Com a expansão aberta, nossa barra compacta desaparece e o player grande continua normal.

### Desativar de imediato

```bash
adb shell settings put global g41_glass_compact_hide_native 0
```

Para voltar ao protótipo beta1 sem suprimir nada:
```bash
adb shell settings put global g41_glass_compact_preview 1
adb shell settings put global g41_glass_compact_hide_native 0
```

Para remover completamente nossa barra:
```bash
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_compact_hide_native 0
```

O chip original pode precisar de nova recomposição para restaurar a visibilidade. Bloquear/desbloquear a tela e iniciar/pausar mídia provoca atualização normalmente; se a UI ficar instável, desative o módulo conforme seu procedimento de recuperação.

## Logs a enviar

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' \
  | grep -E 'Compact native v0.10-beta2|Compact v0.10-beta2|Compact probe|Compact v0.10'
```

Interpretar:
- `Compact native v0.10-beta2: hooks Compose=1 AnimatedVisibility=1`: hook instalou as assinaturas esperadas. Não confirma ocultação sem log de entrada.
- `Compact v0.10-beta2: ocultando somente AnimatedVisibility ...`: atingiu a chamada esperada e pediu `visible=false`.
- Se aparecerem contagens 0, a ROM instalada divergiu da versão enviada.
- Se o log indicar ocultação, mas o chip continuar na tela, isso pode indicar outro chip/janela ou atualização de composição não aplicada. **Não** aumentar agressividade para ocultar a root.

## Checklist

- [ ] Player expandido aprovado continua intacto
- [ ] Antes do opt-in, InfinityBar original continua normal
- [ ] Com música e ambas as opções ativadas, só a barra compacta personalizada aparece
- [ ] Toque na barra abre o player grande
- [ ] Pausa/retomada atualizam ícone e ondas sem criar áudio falso
- [ ] Desativar o opt-in faz o chip nativo reaparecer após recomposição
- [ ] Outra atividade (Uber/notificação) não sofre supressão do chip nativo
- [ ] Tela bloqueada/desbloqueada e troca de músicas funcionam sem travamento/bootloop
- [ ] Se a supressão falhar, manter a beta experimental e coletar logs

**Não fazer merge do PR #2 até validar no Moto G41.**
