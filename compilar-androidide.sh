#!/usr/bin/env sh
# Compila no terminal do AndroidIDE sem usar o Gradle global/antigo do IDE.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$PROJECT_DIR"

# O AndroidIDE pode manter Java 8 como comando padrão mesmo quando o JDK 17
# já está instalado. Localizamos explicitamente o executável do JDK 17.
JDK_BIN=""
if [ -n "${PREFIX:-}" ] && [ -d "$PREFIX/lib/jvm" ]; then
  JDK_BIN="$(find "$PREFIX/lib/jvm" -type f -path '*17*/bin/java' -print -quit 2>/dev/null || true)"
fi

if [ -n "$JDK_BIN" ]; then
  export JAVA_HOME="$(dirname "$(dirname "$JDK_BIN")")"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1 || true)"
case "$JAVA_VERSION" in
  *"17."*|*" 17 "*|*"\"17"*) ;;
  *)
    echo "JDK 17 não encontrado. O terminal atual está usando: $JAVA_VERSION"
    echo "Instale/configure o openjdk-17 no AndroidIDE antes de compilar."
    exit 1
    ;;
esac

# Um GRADLE_USER_HOME próprio evita o init script antigo do AndroidIDE que
# causou ReflectionCache/Groovy antes de o projeto sequer ser avaliado.
export GRADLE_USER_HOME="$PROJECT_DIR/.gradle-uma"

AAPT2="$HOME/.androidide/aapt2"
if [ -x "$AAPT2" ]; then
  exec bash ./gradlew \
    -Pandroid.aapt2FromMavenOverride="$AAPT2" \
    :app:assembleDebug --stacktrace
else
  exec bash ./gradlew :app:assembleDebug --stacktrace
fi
