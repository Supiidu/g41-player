# v0.10.0-beta5 — Basil Skip Previous / Next

**Objetivo:** trocar exclusivamente os ícones de faixa anterior e próxima do **player expandido**, conforme os SVGs enviados pelo autor. A InfinityBar compacta não possui botões prev/next nesta implementação, portanto seu play/pause e o layout permanecem inalterados.

## Implementação

- Arquivos originais preservados em `design/icons/basil_skip-prev-outline.svg` e `design/icons/basil_skip-next-outline.svg`.
- `BasilTransportIcons.java` usa exatamente as curvas Bézier e contornos dos SVGs (viewBox 53 × 53, preenchimento `EVEN_ODD`) convertidos para `android.graphics.Path`.
- No `GlassOverlay.MediaButton`, as ações `kind=0` e `kind=2` chamam o desenho Basil. `kind=1` (play/pause) mantém seu visual atual.
- Os ícones mantêm a cor `controlsColor` do Monet e o mesmo centro/tamanho da área clicável dos controles originais; apenas a forma desenhada muda.
- Preferimos Canvas autossuficiente a carregar um drawable do APK, porque a interface é executada dentro de `com.android.systemui` por Xposed e usa o `Context` da ROM.

## Compilar e testar no G41

```bash
git fetch origin
git switch feature/infinitybar-v0.10
git pull --ff-only
bash tests/test-cava-spectrum.sh
python3 tests/test-compact-lifecycle.py
python3 tests/test-basil-transport-icons.py
bash setup.sh
```

Versão `0.10.0-beta5-basil-transport-icons`, `versionCode 21`.

Com o APK beta4 funcional guardado e recuperação Vector/Magisk disponível, instalar:

```bash
adb shell settings put global g41_glass_compact_hide_native 0
adb shell settings put global g41_glass_compact_preview 0
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
adb shell settings put global g41_glass_compact_preview 1
adb shell settings put global g41_glass_compact_hide_native 1
```

### Checklist

- [ ] Player expandido mostra o Basil `skip-prev-outline` à esquerda e `skip-next-outline` à direita, sem distorção, com partes internas vazadas.
- [ ] Os dois ícones assumem a cor Monet já usada nos controles e permanecem centralizados.
- [ ] Botões continuam pulando faixas corretamente ao tocar, com a mesma área de toque.
- [ ] Ícone central de play/pause permanece como estava.
- [ ] InfinityBar compacta, wave colorida por álbum, PIN e FFT não sofrem alterações.
- [ ] Nenhum crash do SystemUI, especialmente ao abrir/fechar player.

**Status:** SVGs e implementação submetidos ao PR #2; revisão estática feita, sem compilação Android nem execução desta versão no Moto G41 pelo assistente. Não fazer merge do PR antes de validar.
