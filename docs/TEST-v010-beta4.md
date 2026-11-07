# G41 Glass Player v0.10.0-beta4 — AOD, PIN e cor das ondas

**Status:** preview na `feature/infinitybar-v0.10`, PR #2 (Draft). Esta versão ainda precisa de build Android e validação no Moto G41. A `main` não é modificada.

## Feedback que motivou a beta4

O usuário confirmou que a beta3 funciona na lockscreen e no Always On Display e gostou do resultado, mas reportou:

1. **AOD:** depois de abrir e fechar o player expandido no AOD, a barra compacta não voltava.
2. **Senha/PIN:** o overlay compacto permanecia visível enquanto o usuário digitava a senha.
3. **Cor:** a mini-wave compacta continuava azul; a wave do expandido já extraía cores da capa.

## Correções

### Restaurar compacta no AOD

`CompactBarOverlay.observe()` não limita mais criação da janela a `PowerManager.isInteractive()`. Permite criar quando o display estiver `Display.STATE_DOZE` ou `Display.STATE_DOZE_SUSPEND` (AOD), sem iniciar o `Visualizer(0)` nessas situações. Continuamos evitando criar a janela com display completamente apagado (`Display.STATE_OFF`).

A transição expanded → compact já chama `CompactBarOverlay.expandedChanged()`, que pode reconstruir a barra mesmo sem interação do usuário, desde que o display esteja exibindo o AOD. Se a tela estiver temporariamente em `STATE_OFF` enquanto passa para DOZE, a beta4 faz até **seis tentativas espaçadas em 450 ms**, interrompendo ao restaurar ou quando o evento de mídia deixa de existir.

### Esconder compacta na tela de senha

No `SystemUI-original.apk` da ROM enviada, encontramos a classe real `com.android.systemui.statusbar.policy.KeyguardStateControllerImpl`, com campo booleano `mPrimaryBouncerShowing` e método `notifyPrimaryBouncerShowing(boolean)` em `classes2.dex`.

Um novo hook **somente observacional** (`KeyguardUiState.java`) lê o estado após o método nativo terminar. Quando o bouncer de senha/PIN aparece, `CompactBarOverlay` usa `View.INVISIBLE` na própria janela compacta, libera o Visualizer e limpa a onda. Ele **não** modifica nem intercepta a tela de senha e mantém os dados de mídia para retorno imediato quando o bouncer fecha. O hook de ocultação da barra original também evita interferir no bouncer.

### Wave com cor real do álbum

A compacta agora reutiliza `GlassOverlay.extractAccent(Bitmap)` e `GlassOverlay.accentFromDrawable(Drawable)` — o **mesmo algoritmo** do player expandido, sem duplicar a lógica. A cor é atualizada tanto na primeira arte do evento quanto nas mudanças de `MediaMetadata` (capa/álbum). A mini-wave recebe um `LinearGradient` vertical na mesma paleta RGBA de ondas do expandido; os picos continuam controlados pelo FFT real, sem animação falsa. Se não houver capa, a cor de fallback continua azul.

## Atualizar, testar e compilar

```bash
git fetch origin
git switch feature/infinitybar-v0.10
git pull --ff-only

bash tests/test-cava-spectrum.sh
python3 tests/test-compact-lifecycle.py
bash setup.sh
```

APK: `0.10.0-beta4-aod-bouncer-album-wave`; `versionCode 20`.

## Instalação segura

Tenha backup da versão beta3 que funciona, ADB disponível, e seu procedimento de desativação/recuperação do módulo Vector/Xposed. Instale com a ocultação nativa desligada:

```bash
adb devices -l
adb shell settings put global g41_glass_compact_hide_native 0
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
adb shell settings put global g41_glass_compact_preview 1
```

Confirme que o overlay personalizado aparece antes de ocultar o chip nativo:

```bash
adb shell settings put global g41_glass_compact_hide_native 1
```

Se a barra nativa ainda aparecer, pause/retome ou troque de faixa para provocar a recomposição.

## Checklist no G41

- [ ] **AOD → expanded → fechar:** a compacta reaparece **sem precisar acordar/desbloquear** o dispositivo; não precisa tocar música
- [ ] **Lockscreen → tela de senha/PIN:** nossa compacta some imediatamente e não bloqueia nem cobre o teclado
- [ ] **Cancelar a senha e voltar à lockscreen:** a compacta retorna normalmente sem duplicar a InfinityBar nativa
- [ ] **Desbloquear:** não há overlay residual na tela inicial
- [ ] **Capa de álbum 1:** wave compacta assume a cor predominante da capa (não azul fixo)
- [ ] **Trocar de música/capa:** wave compacta muda de cor junto com a nova capa
- [ ] **Player expandido:** cor, bass-centro/agudos-bordas e animação permanecem aprovadas
- [ ] **Compacta → expanded → compacta:** FFT não compete entre duas instâncias de Visualizer
- [ ] **Música pausada:** não há movimento artificial de ondas, inclusive no AOD
- [ ] **Desativar o módulo/preview:** a barra original volta e o PIN continua normal
- [ ] **Sistema estável:** nenhum travamento do SystemUI nem bootloop

## Logs úteis

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' \
  | grep -E 'Compact v0.10-beta4|Compact beta4|Compact probe|Audio FFT:|Paleta: album wave|Ondas:'
```

Mensagens úteis:
- `Compact beta4: hook bouncer instalado (somente leitura)` — método da ROM identificado
- `Compact v0.10-beta4: bouncer=ABERTO` — a tela de PIN foi detectada
- `Compact v0.10-beta4: barra oculta na tela de senha` — overlay ficou invisível
- `Compact v0.10-beta4: bouncer=FECHADO` — PIN/volta à lockscreen detectados
- `Compact v0.10-beta4: album wave=#...` — cor extraída de capa real
- `Audio FFT: compact liberado antes de iniciar expanded` — ownership de FFT preservado

## Voltar à beta3 sem remover o APK

```bash
# Desligar a supressão da InfinityBar nativa
adb shell settings put global g41_glass_compact_hide_native 0

# Desligar completamente nossa compacta personalizada
adb shell settings put global g41_glass_compact_preview 0
```

Essas flags desligam a personalização sem alterar o firmware. Em instabilidade grave do SystemUI, desative o módulo Vector/Xposed usando seu plano de recuperação. **Não faça merge do PR #2 antes dos testes no aparelho.**
