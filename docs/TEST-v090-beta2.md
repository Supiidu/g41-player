# G41 Glass Player v0.9.0-beta2 — Single CAVA HD Wave

> Beta de teste. A `main` permanece na v0.8.1 até compilar e validar o módulo no G41.

## Comportamento esperado

- **Monet e posição:** mantidos da beta1. Vidro translúcido dinâmico, tipografia/controles/progresso em cores do sistema e card alinhado à parte inferior.
- **FFT:** nenhuma mudança no `Visualizer(0)` ou na captura FFT de 24 bandas; 36 níveis de desenho são interpolados em 144 amostras de tela para formar **uma única silhueta** de ondas 2D.
- **Album:** o preenchimento com gradiente semitransparente usa somente a cor extraída da capa; Monet nunca substitui a cor da onda.
- **RMS fallback:** se o Android só oferecer loudness, a onda se transforma em platô de volume em vez de inventar picos de frequências.
- **Pausa / sem áudio:** onda totalmente ausente (sem gradiente residual, contorno da onda ou banda colorida inferior).
- **Largura:** do x=0 até a última coluna do card, sem margens nem efeito de bordas desvanecidas. Os cantos seguem o recorte arredondado do card.

## Compilar

```bash
git fetch origin
git switch feature/v0.9.0-monet-bottom-waves
git pull --ff-only
bash setup.sh
```

Necessário Android SDK 36 e Java 17. O `setup.sh` já contém a correção do erro de nome de projeto `.wrapper-bootstrap` no Gradle 9.x.

## Instalar no G41

Só depois de confirmar que `adb devices -l` lista o aparelho como `device`, ter um backup funcional e um procedimento de recuperação do Vector/Magisk:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

Se a assinatura do APK não coincidir, **não desinstale** o módulo sem uma estratégia de recuperação.

## Ajustes sem recompilar

```bash
# Posição original da beta1; menor = mais baixo, maior = mais alto:
adb shell settings put global g41_glass_player_bottom_margin_dp 70

# Sensibilidade original da onda (0.5 - 2.5, padrão 1.0):
adb shell settings put global g41_glass_cava_sensitivity 1.0
```

Feche e reabra o player após alterar qualquer setting.

## Diagnóstico

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' | grep -E 'CAVA HD v0.9.0-beta2|Monet:|Ondas: modo=|Paleta:|Layout v0.9'
```

Checklist:
- [ ] Monet preservado ao trocar a música.
- [ ] Uma única onda preenchida, sem contorno/segunda camada.
- [ ] Onda tem cor da capa e responde à música.
- [ ] A crista da onda ocupa toda a largura, inclusive os lados.
- [ ] Ao pausar, só o vidro/controles ficam; nenhum resíduo no rodapé.
- [ ] Ao retomar, a onda reaparece sem alterar comportamento do FFT.
- [ ] InfinityBar e relógio são restaurados ao fechar, sem bootloop.
- [ ] Mudar `g41_glass_player_bottom_margin_dp` altera a altura ao reabrir.

**Validação:** verificação estática do código. Nenhum APK foi compilado/testado no dispositivo por esta alteração.

Se a ROM falhar, desative o painel em ADB com:
```bash
adb shell settings put global g41_glass_player_enabled 0
```
Se necessário, recupere usando seu backup e o método já validado para desativar o módulo Vector.
