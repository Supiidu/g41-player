# G41 Glass Player - etapa 0: diagnostico

Este e um projeto **Xposed legacy (API 82)** que usa o classloader do SystemUI para
observar dois metodos identificados no `SystemUI-original.apk` da Infinity X 3.12:

* `AxDynamicBarKeyguardExpansion.expand()`
* `KeyguardExpandedContentKt.KeyguardMediaPanel(IslandEvent.Media, IslandActions, Composer, int)`

O modulo **nao** altera retorno, argumentos, UI, arquivos do sistema, overlays ou Magisk.

## Build em Arch Linux

Requisitos: Android SDK API 36 em ANDROID_HOME, JDK 17, Gradle de sistema,
`curl` e `unzip`. O script configura o wrapper Gradle 8.13 e baixa um jar de API
Xposed 82 somente para compilacao (`compileOnly`).

```bash
cd G41GlassPlayer
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
bash setup.sh
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Uso com Vector

1. Confirme Magisk + Zygisk, inicializacao normal do Vector e plano de recuperacao.
2. Instale com `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. No Vector Manager, ative o modulo e marque SOMENTE `com.android.systemui` como escopo.
4. Reinicie o Android para reiniciar o SystemUI e carregar o hook.
5. `adb logcat -c` e `adb logcat -v time -s G41GlassPlayer:I '*:S'`.
6. Trave a tela e toque na InfinityBar quando houver musica.

Esperado: `Modulo carregado`, `Hooks ... instalados: 1`,
`expand() chamado`, `KeyguardMediaPanel executado`.
O nome de classe e assinatura dependem da versao de SystemUI descompilada.

## Recuperacao

* Se o sistema iniciar: desative o modulo no Vector, reinicie e remova o APK
  (`adb uninstall dev.g41.glassplayer`).
* Se SystemUI falhar mas ADB continuar: `adb uninstall dev.g41.glassplayer` e reinicie;
  se o framework tiver carregado a classe no processo antigo, so reiniciar resolve.
* Se houver bootloop: Magisk Safe Mode desativa TODOS os modulos Magisk,
  inclusive os de audio/botao do seu G41, ate reabilitacao individual.
  Nao use `magisk --remove-modules` sem entender que remove todos eles.

Fonte: https://github.com/JingMatrix/Vector/blob/master/legacy/README.md
