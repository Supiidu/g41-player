# G41 Glass Player v0.8 — InfinityBar Visibility

Primeira etapa da personalização da InfinityBar **compacta**. Esta versão **não redesenha** a barra. Ao abrir o Glass Player, tenta ocultar apenas uma View **independente e identificada** que hospede a InfinityBar. Ao fechar, restaura seu alpha original. Mantém o relógio (`ClockStyle`), FFT/CAVA, cores dinâmicas e wallpaper real da v0.7.

> A InfinityBar usa Jetpack Compose. Em algumas ROMs, o chip é um nó Compose dentro de uma View de tela cheia, e **não existe View Android independente para ocultar**. Por segurança, a v0.8 não oculta um `AndroidComposeView` de tela inteira. Nesse caso ela emitirá logs de diagnóstico e a InfinityBar ainda ficará visível até prepararmos um hook Compose dedicado. Não confunda `ocultados=0` com falha do Vector.

## Instalar patch no Arch

Na raiz de `~/DAP project/infinitybar-mod/G41GlassPlayer`, já atualizado à v0.7:

```sh
unzip -jo ~/Downloads/G41GlassPlayer-v08-Compact.zip -d .
bash apply-v08.sh
```

A v0.8 salva backups em `backups/` e não modifica o `SystemUI.apk` nem instala nada automaticamente no celular.

Após `BUILD SUCCESSFUL`:

```sh
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
```

## Testar

Com música tocando, toque na InfinityBar, depois feche o Glass Player tocando fora do card. A barra deve sumir **apenas se houver uma View isolada e identificável**. Veja os logs:

```sh
adb logcat -d -s G41GlassPlayer:V '*:S' | grep 'Compact v0.8'
```

- `OCULTADO nome-identificado ...` confirma que o alpha de uma View candidata foi alterado.
- `RESTAURADO 1 Views` confirma que foi restaurado ao fechar.
- `ocultados=0` indica que não foi encontrada uma View independente segura; envie os logs `VIEW` e `COMPOSE candidato`. O próximo passo será investigar um ponto de hook Compose, sem tentar ocultar toda a lockscreen.

## Modo opcional agressivo (somente se houver um candidato pequeno)

O modo agressivo está **desligado por padrão**. Ele só oculta um ComposeView pequeno na região inferior quando **exatamente um** candidato elegível for encontrado. Pode esconder outro elemento; ative somente se a busca conservadora não encontrar a barra.

```sh
adb shell settings put global g41_glass_hide_compact_aggressive 1
```

Feche e reabra o player para aplicar. Para desativar:

```sh
adb shell settings put global g41_glass_hide_compact_aggressive 0
```

## Desativar qualquer tentativa de ocultação da barra

```sh
adb shell settings put global g41_glass_hide_compact 0
```

Feche e reabra o player para aplicar. `g41_glass_hide_compact` retorna ao padrão 1 com:

```sh
adb shell settings delete global g41_glass_hide_compact
```

## Recuperação

```sh
adb shell settings put global g41_glass_player_enabled 0
```

Se houver falhas graves do SystemUI, via ADB com root desative o Vector para o próximo boot:

```sh
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

Testes: aplicação do patch e checagens estáticas; **não foi possível compilar o APK Android ou testá-lo no aparelho** neste ambiente. Confira `BUILD SUCCESSFUL` no notebook.
