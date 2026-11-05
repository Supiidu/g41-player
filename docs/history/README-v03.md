# G41 Glass Player v0.3 – painel nativo experimental

**ATENÇÃO: código experimental; não testado no Moto G41.** Requer Vector com escopo **somente** `com.android.systemui`, Magisk+Zygisk, ROM Infinity X Android 16. O código `G41Hook-v03.java` substitui o hook anterior e `GlassOverlay.java` é uma nova classe Android. Não modifica SystemUI.apk, e não usa o hook global de `Surface` da v0.2.

## Ideia

Interromper a expansão normal **somente quando** o evento selecionado for `IslandEvent.Media`, o botão estiver na lockscreen, o usuário tiver optado pelo novo painel via `Settings.Global`, e `WindowManager.addView(...)` retornar sucesso. Em qualquer falha, a execução do `expand()` original é preservada.

A camada usa um tipo de janela **interno do Android** (`TYPE_STATUS_BAR_SUB_PANEL=2017`) e depende das permissões do SystemUI da ROM. Pode falhar com `BadTokenException`, `SecurityException` ou ficar atrás de outra janela. Nesse caso, os logs e o painel original devem aparecer. **Não afirmamos que funcionará nesta ROM antes de testar.**

## Passos

1. Descompacte **estes quatro arquivos na pasta `G41GlassPlayer`**, ao lado do `gradlew` (ou copie os arquivos separadamente).
2. Compile: `bash apply-v03.sh`.
3. Instale APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Garanta o escopo Vector = `com.android.systemui`; reinicie: `adb reboot`.
5. **O novo painel inicia desativado por segurança.** Comprove que a InfinityBar original ainda expande normalmente.
6. No terminal: `adb shell settings put global g41_glass_player_enabled 1`.
7. Toque na InfinityBar com música tocando na lockscreen, observe janela e Logcat: `adb logcat -v time -s G41GlassPlayer:V '*:S'`.
8. Se aparecer, o painel tem botão X. Ele também fecha ao desligar a tela. Para voltar para o original: `adb shell settings put global g41_glass_player_enabled 0` (desligue e ligue a tela para remover um painel ainda aberto).

A tela do player apresenta **capa 112dp, título e artista à direita, barra de progresso e botões grandes**. Os botões usam a `MediaController` ativa se disponível; caso contrário enviam teclas padrão através de `AudioManager`. Sem acesso à sessão, o seekbar fica desativado. O fundo tem cores translúcidas e uma borda iluminada, mas **não implementa ainda o blur real do wallpaper**.

## Recuperação

Ajuste imediatamente `adb shell settings put global g41_glass_player_enabled 0` para desligar a troca na próxima tentativa. Se o SystemUI entrar em loop e o shell continuar acessível, é possível desabilitar o Vector todo: `adb shell "su -c 'touch /data/adb/modules/zygisk_vector/disable'"; adb reboot`. Assim você perde temporariamente todos os hooks Vector, não os outros módulos Magisk.

Uma forma de voltar à v0.2 sem desinstalar o framework: `cp app/src/main/java/dev/g41/glassplayer/G41Hook.java.backup-v02 app/src/main/java/dev/g41/glassplayer/G41Hook.java`, remova `GlassOverlay.java`, altere versionCode no app/build.gradle para 4, reconstrua e instale novamente. Se o backup não for encontrado, use os arquivos originais do patch v0.2.
