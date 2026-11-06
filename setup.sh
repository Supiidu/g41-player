#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_HOME:=/opt/android-sdk}"
: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]; then
  echo "ERRO: nao encontrei $ANDROID_HOME/platforms/android-36/android.jar" >&2
  echo "Defina ANDROID_HOME para o SDK instalado." >&2
  exit 1
fi
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "ERRO: Java 17 nao encontrado em $JAVA_HOME" >&2
  exit 1
fi

mkdir -p app/libs
if [ ! -s app/libs/api-82.jar ]; then
  echo 'Baixando apenas a API Xposed para compilacao (nao vai para dentro do APK)...'
  curl -fL --retry 2     'https://repository-cdn.liferay.com/nexus/content/repositories/gradle-plugins/de/robv/android/xposed/api/82/api-82.jar'     -o app/libs/api-82.jar
fi
if ! unzip -tq app/libs/api-82.jar >/dev/null; then
  echo 'ERRO: api-82.jar invalido ou download incompleto.' >&2
  exit 1
fi

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo 'Gerando Gradle Wrapper 8.13 usando o Gradle instalado no computador...'
  mkdir -p .wrapper-bootstrap
  # Gradle 9+ rejects directory-derived project names that begin with '.'.
  # Explicitly name the temporary bootstrap project to support host Gradle 9.x.
  printf '%s\n' "rootProject.name = 'g41-wrapper-bootstrap'" > .wrapper-bootstrap/settings.gradle
  printf '%s\n' 'tasks.register("placeholder")' > .wrapper-bootstrap/build.gradle
  ( cd .wrapper-bootstrap && gradle wrapper --gradle-version 8.13 --distribution-type bin )
  mkdir -p gradle
  cp -r .wrapper-bootstrap/gradle/wrapper gradle/
  cp .wrapper-bootstrap/gradlew ./gradlew
  chmod +x ./gradlew
fi

printf '\nSDK: %s\nJDK: %s\n' "$ANDROID_HOME" "$JAVA_HOME"
echo 'Compilando APK de diagnostico...'
./gradlew --no-daemon :app:assembleDebug
printf '\nAPK pronto: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
