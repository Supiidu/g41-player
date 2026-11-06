# G41 Glass Player v0.9.0-beta4 — Smooth Treble CAVA HD

**Estado:** preview na branch `feature/v0.9.0-monet-bottom-waves`, PR #1. A `main` permanece na versão estável anterior. Ainda precisa de compilação e validação no Moto G41.

## Mudança em relação à beta3

O que **permanece**: graves no centro; médios e médio-graves aprovados; agudos nas extremidades do espectro mono espelhado; Monet e transparência; cor da onda extraída do álbum; card mais baixo; pausa sem qualquer faixa residual.

### Agudos mais presentes

- A magnitude dos agudos é uma mistura de **62% RMS da banda e 38% pico verdadeiro da mesma banda**: preserva hi-hats e pratos que antes se diluíam nas bandas largas do FFT Android, sem inventar sinal.
- Apenas nas bandas 16–23: limiar de entrada moderadamente menor e compensação de frequência crescente em direção à borda.
- Referência dinâmica de graves/médios fica essencialmente como na beta3. O grupo de agudos ganhou um teto um pouco maior.
- Um kernel `0.22/0.56/0.22` distribui um pico agudo real entre bandas vizinhas; nenhuma energia é criada a partir de silêncio.
- Release dos agudos fica um pouco mais lento para melhorar a presença de transientes curtos.

### Curvas sem triângulos

- O `CavaSpectrum.levelAt()` aplica suavização espacial de cinco bandas (`0.07, 0.23, 0.40, 0.23, 0.07`).
- O `GlassSurface` suaviza os 145 pontos visuais com outro kernel leve antes de desenhar.
- Em vez dos segmentos quadráticos anteriores, a onda usa **`Path.cubicTo` com tangentes monotônicas**: topo e vale arredondados, sem overshoot de Catmull-Rom.
- Continua **uma única silhueta preenchida** de ponta a ponta. RMS sem espectro continua sendo platô, não picos artificiais.

## Build e testes locais

```bash
git switch feature/v0.9.0-monet-bottom-waves
git pull --ff-only
bash tests/test-cava-spectrum.sh
bash setup.sh
```

O teste Java cobre silêncio, gate, baixo no centro, agudos nas bordas, espelhamento, dados FFT Android, reforço de agudos baixos, controles de boost, suavidade amostrada e pausa.

## Ajustar agudos pelo ADB sem recompilar

```bash
# 1.25: padrão beta4
adb shell settings put global g41_glass_cava_treble_boost 1.25

# 1.50: agudos um pouco mais presentes
adb shell settings put global g41_glass_cava_treble_boost 1.50

# 1.85: teste forte, útil se as laterais ainda estiverem tímidas
adb shell settings put global g41_glass_cava_treble_boost 1.85

# 1.00: menor reforço
adb shell settings put global g41_glass_cava_treble_boost 1.00
```

Faixa aceita: 0.70 a 2.00. **Feche e reabra o player para aplicar**. Se surgirem falsas reações nas pontas durante trechos silenciosos, volte a 1.00.

Os ajustes já existentes continuam:

```bash
adb shell settings put global g41_glass_cava_sensitivity 1.0
adb shell settings put global g41_glass_player_bottom_margin_dp 70
```

## Instalação com cuidado (Vector/SystemUI)

Confira que `adb devices -l` indica `device`, tenha backup do módulo anterior e um método de recuperação funcionando. Após build com sucesso:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

Não desinstale cegamente caso haja incompatibilidade de assinatura do APK.

## Logs de diagnóstico

```bash
adb logcat -d -s G41GlassPlayer:V '*:S' | grep -E 'CAVA HD v0.9.0-beta4|CAVA FFT 24:|Ondas: modo=|Monet:'
```

Os logs `CAVA FFT 24` mostram médias da captura FFT **antes da equalização e do suavizador**, permitindo identificar se o próprio Android está disponibilizando agudos fracos.

## Checklist real no G41

- [ ] Graves no centro e médios/médio-graves continuam naturais
- [ ] Laterais reagem a pratos/hi-hats; variações seguem a música
- [ ] Picos possuem cristas arredondadas, sem triângulos ou spikes
- [ ] Uma única onda HD preenche a base do card
- [ ] Cor da capa, transparência Monet e posição permanecem intactas
- [ ] Pausa limpa toda a onda sem esconder outros elementos
- [ ] Ajuste de boost realmente muda a atividade dos agudos ao reabrir
- [ ] Com música pausada/silêncio, nenhum movimento agudo artificial
- [ ] InfinityBar e relógio restaurados no fechamento

**Limite do diagnóstico:** não houve teste de áudio real nesta atualização. Os parâmetros são hipóteses de tratamento visual a validar no aparelho, não uma afirmação de equivalência integral ao processamento do CAVA de desktop.
