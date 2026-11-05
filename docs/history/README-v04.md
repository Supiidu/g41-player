# G41 Glass Player — v0.4 Figma Edition

Implementação do **player expandido** baseada nos três PNGs do Figma enviados pelo usuário.

## O que muda

- Janela própria, ocupando a lockscreen enquanto o player está expandido.
- Painel centralizado, branco leitoso/translúcido, cantos de 20dp.
- Capa quadrada grande, título e artista dinâmicos.
- Controles anterior / play-pause / próximo (desenhados em Canvas) e barra de progresso azul interativa.
- Ondas suaves azuladas na metade inferior do card.
- Desfoque real do wallpaper *dentro* do card no Android 12+ (`RenderEffect`), sobreposto pelo material translúcido.
- Cópia do wallpaper fornecido na conversa dentro do APK, para encobrir temporariamente o relógio gigante sem alterá-lo no SystemUI.
- Toque fora do painel fecha e devolve a lockscreen original. Ao desligar a tela ou desbloquear, o overlay fecha.
- A InfinityBar **compacta continua sendo a original da Infinity X** nesta versão. Será alterada separadamente.
- Capa e música do mockup são exemplos: o player busca metadados na sessão de mídia atual.

## Requisitos

- Projeto G41GlassPlayer v0.3 previamente compilado e funcionando.
- Vector v2.2 / Zygisk ativados, módulo aplicado SOMENTE a `com.android.systemui`.
- API Android 36, JDK 17 e Gradle configurados, como no seu Arch.

## Instalação — no notebook

```bash
cd "$HOME/DAP project/infinitybar-mod/G41GlassPlayer"
unzip -jo ~/Downloads/G41GlassPlayer-v04-Figma.zip -d .
bash apply-v04.sh
```

O script cria cópias do código e do APK anterior em `backups/`. Após `BUILD SUCCESSFUL`:

```bash
# Desativa o recurso por precaução, ANTES de instalar.
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
```

Quando o Android iniciar normalmente:

```bash
adb shell settings put global g41_glass_player_enabled 1
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Toque na InfinityBar compacta enquanto uma música estiver tocando. Toque fora do card para fechar.

## Desfazer sem reiniciar

```bash
adb shell settings put global g41_glass_player_enabled 0
```

O player verifica a configuração e fecha em aproximadamente um segundo. Se o SystemUI travar antes de o ADB funcionar, tente entrar no modo seguro do Magisk. Se ADB root estiver disponível:

```bash
adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"
adb reboot
```

O comando anterior desativa o Vector inteiro. Para restaurar apenas a versão anterior do APK, use a cópia do `backups/` criada pelo script (nome inclui horário), instale com `adb install -r` e reinicie o SystemUI/aparelho. Note que o nome de APK anterior é gerado pelo script.

## Limitações conhecidas

- **Não é a substituição definitiva da composição Compose**: é uma janela própria criada no processo SystemUI. A InfinityBar compacta permanece a original da ROM.
- Para esconder o relógio, a v0.4 desenha uma cópia do wallpaper enviado pelo usuário, **não** desativa o relógio internamente. Se mudar de wallpaper, é preciso atualizar `g41_lockscreen.jpg` e recompilar, ou aceitar o fallback do WallpaperManager.
- O desfoque afeta a imagem clonada dentro do card, e não a renderização do SystemUI subjacente.
- A janela usa `TYPE_STATUS_BAR_SUB_PANEL` (2017), API interna/privilegiada. O teste bem-sucedido da v0.3 é um bom indício, mas fullscreen pode ter comportamento diferente. Se falhar, o hook deixa a expansão nativa funcionar.
- `MediaSessionManager.getActiveSessions(null)` depende das permissões efetivas do SystemUI. Sem sessão acessível, os botões enviam eventos multimídia pelo AudioManager e não será possível exibir progresso real.
- **Não foi possível compilar e executar esse patch dentro do ambiente de preparação**, que não tem o SDK Android. Os testes de compilação e execução serão feitos no Moto G41 do usuário.
