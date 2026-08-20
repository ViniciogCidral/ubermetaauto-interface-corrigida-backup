#!/usr/bin/env sh
set -eu

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION="8.9"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
DIST_DIR="${GRADLE_HOME}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${DIST_DIR}/bin/gradle"

# Use an installed Gradle only when it is new enough for Android Gradle Plugin 8.7.3.
if command -v gradle >/dev/null 2>&1; then
  GV="$(gradle --version 2>/dev/null | sed -n 's/^Gradle \([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p' | head -n 1 || true)"
  MAJOR="$(printf '%s\n' "$GV" | awk '{print $1}')"
  MINOR="$(printf '%s\n' "$GV" | awk '{print $2}')"
  if [ "${MAJOR:-0}" -gt 8 ] || { [ "${MAJOR:-0}" -eq 8 ] && [ "${MINOR:-0}" -ge 9 ]; }; then
    exec gradle "$@"
  fi
fi

# If AndroidIDE/Termux has a known Gradle location, use it only when it is 8.9+.
for candidate in \
  "${PREFIX:-}/bin/gradle" \
  "/data/data/com.itsaky.androidide/files/usr/bin/gradle" \
  "/data/data/com.itsaky.androidide/files/home/.gradle/gradle/bin/gradle"
do
  if [ -x "$candidate" ]; then
    GV="$("$candidate" --version 2>/dev/null | sed -n 's/^Gradle \([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p' | head -n 1 || true)"
    MAJOR="$(printf '%s\n' "$GV" | awk '{print $1}')"
    MINOR="$(printf '%s\n' "$GV" | awk '{print $2}')"
    if [ "${MAJOR:-0}" -gt 8 ] || { [ "${MAJOR:-0}" -eq 8 ] && [ "${MINOR:-0}" -ge 9 ]; }; then
      exec "$candidate" "$@"
    fi
  fi
done

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_HOME"
  TMP="${GRADLE_HOME}/gradle-${GRADLE_VERSION}-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Gradle ${GRADLE_VERSION} não encontrado. Baixando..."
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --retry 3 -o "$TMP" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP" "$URL"
  else
    echo "Erro: nem gradle, nem curl/wget estão disponíveis." >&2
    exit 1
  fi
  rm -rf "$DIST_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP" -d "$GRADLE_HOME"
  else
    echo "Erro: unzip não está disponível para extrair o Gradle." >&2
    exit 1
  fi
  rm -f "$TMP"
fi

exec "$GRADLE_BIN" "$@"
