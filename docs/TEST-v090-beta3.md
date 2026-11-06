# G41 Glass Player v0.9.0-beta3 — Balanced CAVA Spectrum

**Situação:** branch de testes, não houve build de APK ou validação no Moto G41 durante esta revisão.

## Por que mudar

O CAVA em modo stereo coloca graves ao centro, agudos nas pontas e aplica EQ em função da frequência e suavização. O projeto anterior simplesmente distribuía suas bandas FFT da esquerda para a direita; o espectro visual não tinha o mesmo comportamento. O Android Visualizer fornece a mistura mono, então aqui espelhamos **um único espectro** — não afirmamos separação stereo real.

Referências:
- https://github.com/karlstav/cava/blob/master/example_files/config
- https://github.com/karlstav/cava/blob/master/cavacore.c
- https://developer.android.com/reference/android/media/audiofx/Visualizer

## Alterações

1. **35–12000 Hz / 24 bandas logarítmicas**: extração do FFT de 8 bits do Android, sem modificar o modo de captura. Utiliza RMS de magnitude em cada banda, em vez da média simples, evitando abafar picos isolados dentro de bandas largas. Interpola apenas intervalos entre bins de FFT nos graves quando faltam amostras devido à resolução limitada.
2. **Graves no centro, agudos nas pontas**: uma única onda 2D preenchida com 144 pontos de desenho. Canal mono do Android é espelhado, não convertido falsamente em stereo.
3. **Equilíbrio**: inclinação de EQ moderada para frequências mais altas + 3 grupos de 8 bandas com ganho adaptativo limitado (0.65–2.0x). Ataque, decaimento e ganho global suaves. A intensidade geral das bandas tende a ficar mais equilibrada, mas as alturas **não** são falsificadas para serem idênticas.
4. **Ruído**: gate de entrada evita que o ganho adaptativo faça ruído virar agudos visíveis.
5. **Sem interferência**: cor da onda segue o álbum; Monet continua somente no vidro/textos/controles; card permanece mais baixo; pausa elimina a onda completamente; RMS fornece platô de volume, pois não tem bandas de frequência.

É uma aproximação inspirada no comportamento do CAVA, **não** o porte integral do seu algoritmo FFT/FFTW ou das duas fontes estéreo.

## Testes locais (somente Java/JDK)

```bash
bash tests/test-cava-spectrum.sh
```

Verificam silêncio, gate, espelhamento, graves no centro, agudos nas bordas, equilíbrio em sinais sintéticos, extração Android FFT com bins graves/agudos, RMS sem conteúdo espectral artificial e limpeza após pausa.

## Compilar no Arch Linux

```bash
git switch feature/v0.9.0-monet-bottom-waves
git pull --ff-only
bash tests/test-cava-spectrum.sh
bash setup.sh
```

Se houver falha no build, compartilhe o primeiro bloco de erro do Gradle.

## Instalar (somente após build bem-sucedido e backup)

Conecte o Moto G41 com depuração USB e confirme `device` em `adb devices -l`. Depois:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

Como o módulo executa no SystemUI via Vector/Xposed, mantenha uma recuperação funcional e o APK anterior. Não desinstale cegamente caso a assinatura do APK não corresponda.

## Ajustes sem rebuild

```bash
# Amplificação geral de onda, padrão 1.0 (aceita 0.5 - 2.5):
adb shell settings put global g41_glass_cava_sensitivity 1.0

# Margem inferior preservada da beta1:
adb shell settings put global g41_glass_player_bottom_margin_dp 70
```

Feche e reabra o player para aplicar.

## Logs para ajuste em música real

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' | grep -E 'CAVA HD v0.9.0-beta3|CAVA FFT 24:|Ondas: modo=|Monet:'
```

A cada ~160 callbacks FFT, aparece `CAVA FFT 24: graves=... medios=... agudos=... max=...`. Isso mede o sinal *antes* do EQ e ajuda a calibrar a nova distribuição na ROM.

## Checklist

- [ ] Metade esquerda e metade direita com graves no centro e agudos nas pontas.
- [ ] Graves/médios/agudos têm presença semelhante, sem virar retângulo uniforme.
- [ ] Hi-hats e pratos aparecem nas bordas, se a captura FFT os detectar.
- [ ] A onda muda com a música e não produz animação aleatória.
- [ ] Onda desaparece completamente ao pausar.
- [ ] Cor do álbum e vidro Monet preservados.
- [ ] Posição inferior e restauração da InfinityBar/relógio preservadas.

Não criar uma release estável antes de validar as novas curvas, a resposta ao áudio e a estabilidade do SystemUI no aparelho.
