# G41 Glass Player v0.10.0-beta1 — InfinityBar compacta (prévia opt-in)

> O player expandido v0.9.0 foi aprovado pelo autor e integrado à `main`.
> Este é um **primeiro protótipo** da InfinityBar compacta, ainda não uma substituição completa do composable da ROM. O primeiro teste deve ocorrer com ADB e recuperação de bootloop disponíveis.

## Direção da beta2: DAP-first (definida pelo autor)

O G41 será usado prioritariamente como DAP. Para a experiência do usuário, só importa a barra de **música**:

1. Renderizar somente a barra compacta personalizada ao reproduzir música — **não deixar a InfinityBar original duplicada**.
2. Abrir o player expandido já aprovado ao tocar.
3. Não criar trabalho extra de personalização para notificações, cronômetros ou Uber; eventos fora de mídia podem continuar nativos.
4. Ao desativar o módulo, a barra original volta.

**Restrição técnica mantida:** não ocultar a `AndroidComposeView` de tela inteira. O chip nativo pode compartilhar o mesmo Compose root com outros elementos do keyguard. Também não é seguro pular uma função `@Composable` sem preservar seus grupos de composição; um hook incorreto pode derrubar o SystemUI.

O protótipo beta1 é uma janela adicional, não o resultado final. Para a beta2, precisamos identificar no `SystemUI-original.apk` do dispositivo o ponto exato de desenho do chip e ocultar **apenas o conteúdo de mídia**, com fallback via Settings.Global e restauração ao desligar. Se isso não for verificável, manter a beta1 como experimental em vez de ocultar a lockscreen inteira.

## Design retomado do Figma

- Barra horizontal com cantos arredondados, **fundo claro e translúcido**, sem reproduzir/copiar o wallpaper.
- Capa quadrada à esquerda, título e artista **dinâmicos** no meio, play/pause à direita.
- Ondas azuis baseadas em **FFT real**, sem animação aleatória; desaparecer quando a mídia estiver pausada ou quando não houver captura.
- Toque no corpo da barra chama o fluxo nativo `AxDynamicBarKeyguardExpansion.expand()`, já interceptado para abrir o Glass Player expandido.
- O relógio, wallpaper, profundidade da lockscreen e eventos de notificações/timers da InfinityBar devem permanecer intactos.

## Abordagem e limitações

A barra compacta nativa é um nó de **Jetpack Compose** dentro de um contêiner que pode ocupar toda a lockscreen. Não é seguro ocultar essa root.

Nesta beta, um hook `afterHookedMethod` em um composable de `AxDynamicBarKeyguardChipKt` **apenas observa** o item atual. Se for `IslandEvent.Media` selecionado e o usuário ativar o opt-in, criamos uma **janela estreita independente** no próprio processo SystemUI sobre a barra nativa, sem alterar o método Compose nem outros eventos. Se a classe/método não corresponder à ROM, a InfinityBar original continua funcionando e não haverá protótipo.

**Limitações:** a janela pode não coincidir com a posição nativa até ser calibrada no G41. Seu fundo é majoritariamente opaco para cobrir a barra original, apesar da transparência estilizada; o chip Compose **não foi removido**. O protótipo ainda não aplica mudanças persistentes aos nodes da ROM. A saída FFT compacta usa sua própria captura `Visualizer(0)` e libera o recurso antes do player expandido abrir. Se a captura falhar, a barra aparece sem ondas. Não há equivalência declarada ao CAVA estéreo.

## Atualizar e compilar (Arch Linux)

```bash
git fetch origin
git switch feature/infinitybar-v0.10
git pull --ff-only
bash tests/test-cava-spectrum.sh
bash setup.sh
```

Versão de teste: `0.10.0-beta1-compact-preview`, `versionCode 17`.

## Instalação com recuperação

Faça backup do APK anterior e tenha uma forma de desativar o módulo Vector/Xposed mesmo se o SystemUI parar. Confirme `adb devices -l` com estado `device`.

```bash
# Desabilitar a interface experimental antes de instalar:
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

Só depois de abrir/fechar o player expandido normalmente, ativar a prévia compacta:

```bash
adb shell settings put global g41_glass_compact_preview 1
```

Abra/desbloqueie e bloqueie a lockscreen novamente, e reproduza uma música para disparar a recomposição do chip. A janela aparece apenas com evento de mídia selecionado, SystemUI com módulo ativo, lockscreen bloqueada e tela interativa.

## Ajuste de posição

```bash
# padrão beta1, mais alto que a base da tela
adb shell settings put global g41_glass_compact_bottom_margin_dp 135

# posicionar mais abaixo
adb shell settings put global g41_glass_compact_bottom_margin_dp 95

# posicionar mais acima
adb shell settings put global g41_glass_compact_bottom_margin_dp 175
```

O parâmetro é lido quando a janela é criada; após mudar, desative/ative a prévia, ou bloqueie/desbloqueie a tela. O intervalo é limitado de 24 a 320dp. **Ajuste de posição é essencial antes de avaliar fidelidade visual ao Figma.**

## Desligar em caso de sobreposição ou instabilidade

```bash
adb shell settings put global g41_glass_compact_preview 0
```

A janela se fecha automaticamente quando o tick percebe o setting desabilitado. Se necessário, desative o player inteiro:

```bash
adb shell settings put global g41_glass_player_enabled 0
```

Se o SystemUI crashar continuamente, use seu procedimento de recuperação do módulo Vector/Magisk.

## Logs de validação

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' \
  | grep -E 'Compact probe v0.10|Compact v0.10|GLASS V0.9'
```

Esperado: `Compact probe v0.10: composables identificados=1` (se o método existir e corresponder ao ROM). Se aparecer `0`, precisamos dos nomes dos métodos da versão do SystemUI instalada. Não force hooks desconhecidos.

## Checklist

- [ ] Nenhuma alteração visual na lockscreen quando `g41_glass_compact_preview=0`.
- [ ] Ao iniciar mídia, aparece protótipo com capa, título, artista e botão.
- [ ] Mini wave azul acompanha áudio e desaparece na pausa.
- [ ] Toque na capa/texto expande o player já aprovado; o botão pausa sem expandir.
- [ ] Barra compacta desaparece quando o painel expandido está aberto e reaparece depois de fechar.
- [ ] Timer, notificações e chamadas continuam usando a InfinityBar nativa.
- [ ] Ao desbloquear ou apagar a tela, a janela compacta some.
- [ ] Ajustar margem vertical alinha a prévia com a InfinityBar nativa.
- [ ] Nenhum SystemUI crash / bootloop.

**Estado desta entrega:** revisão estática; Gradle/SDK Android e Moto G41 indisponíveis neste ambiente. Não afirmar build ou teste on-device. PR deve permanecer **Draft**.
