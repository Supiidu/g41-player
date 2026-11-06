# G41 Glass Player v0.9.0-beta1 — Monet & Full-width Waves

> **Prévia para testes no G41**, ainda não publicada como release estável. O branch principal mantém a versão anterior até validação no dispositivo.

## Mudanças visuais

- **Card:** alinhado à parte inferior da lockscreen para liberar a personagem no wallpaper. Margem padrão de `70 dp` do rodapé, ajustável via Settings.Global.
- **Monet:** superfície translúcida, títulos, controles, placeholder, borda e barra de progresso usam os recursos dinâmicos do Android (`system_neutral1`, `system_neutral2`, `system_accent1`). Não exige Material Components.
- **Ondas:** as duas ondas fluidas continuam usando a paleta **da capa do álbum**, mantendo a captura FFT/RMS. Agora vão de `x=0` à largura total do card, com cantos arredondados recortados pelo próprio painel.
- **Pausa:** não há mais faixa inferior colorida. O desenho da onda, inclusive seus gradientes e traço, é completamente omitido quando pausado ou sem captura.

## Build e teste no aparelho

No notebook com Android SDK 36, JDK 17 e as dependências originais do repositório:

```bash
git fetch origin
git switch feature/v0.9.0-monet-bottom-waves
bash setup.sh
```

Desative o overlay antes de instalar e reiniciar, pois o módulo executa dentro do SystemUI:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

Se a instalação falhar por assinatura, não desinstale o módulo sem garantir que você possui o APK anterior e o procedimento de recuperação para Vector.

## Ajuste fino da posição sem recompilar

```bash
# O padrão usado no wallpaper de teste:
adb shell settings put global g41_glass_player_bottom_margin_dp 70

# 40 = player mais baixo; 100 = player mais alto
adb shell settings put global g41_glass_player_bottom_margin_dp 40
adb shell settings put global g41_glass_player_bottom_margin_dp 100
```

Feche e reabra o painel para aplicar. Intervalo permitido no código: 8–180 dp, sempre limitado pela altura disponível no aparelho.

## Checklist visual e diagnóstico

- [ ] Rosto da personagem livre, card posicionado próximo ao splash inferior.
- [ ] Card permanece translúcido e é tonalizado pelo tema dinâmico do Android.
- [ ] Barra de progresso e botões usam Monet, **não** a cor da capa.
- [ ] Ondas seguem a **capa** e preenchem toda a largura inferior até os cantos arredondados.
- [ ] Ao pausar, as ondas desaparecem completamente, sem base residual.
- [ ] Ao retomar, o visualizador reage ao áudio como na v0.8.1.
- [ ] Sem mídia e após fechar, InfinityBar e relógio originais permanecem preservados.

Logs:

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' | grep -E 'Monet:|Layout v0.9:|Paleta:|Ondas:|GLASS V0.9'
```

**Validação:** código revisado estaticamente. O APK não foi compilado ou testado em um Moto G41 durante a preparação deste branch.

## Recuperação

```bash
adb shell settings put global g41_glass_player_enabled 0
```

Reinstale seu backup conhecido como funcional caso necessário. Se o SystemUI entrar em loop e o acesso via ADB permanecer possível, siga o procedimento de recuperação do seu Vector/Magisk. Não faça a primeira instalação sem ter um plano de recuperação verificado.

## Publicação depois do teste

Quando o beta funcionar no aparelho: atualizar os changelogs/README, promover a versão em `app/build.gradle`, fazer merge do PR, criar uma tag `v0.9.0` e publicar uma GitHub Release contendo o APK **validado** com notas de versão. Commits isolados não criam releases automaticamente no projeto atual.
