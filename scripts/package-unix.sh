#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

mvn -q clean package
# A versão vem do jar recém-construído: fixá-la aqui quebrava o empacotamento
# a cada release, e ler o pom exigiria um plugin extra do Maven.
JAR_PATH="$(ls -1 target/agent-meter-*.jar | head -1)"
JAR="$(basename "$JAR_PATH")"
VERSION="${JAR#agent-meter-}"
VERSION="${VERSION%.jar}"
rm -rf target/package-input target/dist
mkdir -p target/package-input target/dist
cp "$JAR_PATH" target/package-input/
cp target/libs/*.jar target/package-input/

OS_NAME="$(uname -s)"
COMMON=(
  --name "Agent Meter"
  --app-version "$VERSION"
  --vendor "Agent Meter"
  --description "Monitor local de recursos do Codex e Claude Code"
  --input target/package-input
  --main-jar "$JAR"
  --main-class dev.agentmeter.Launcher
  --dest target/dist
  --java-options "-Dfile.encoding=UTF-8"
)

if [[ "$OS_NAME" == "Linux" ]]; then
  jpackage "${COMMON[@]}" --type app-image --icon assets/icon.png
  printf 'Aplicativo criado em: %s\n' "$PROJECT_DIR/target/dist/Agent Meter"
  if [[ "${1:-}" == "deb" ]]; then
    rm -rf "target/dist/Agent Meter"
    jpackage "${COMMON[@]}" --type deb --icon assets/icon.png \
      --linux-shortcut --linux-menu-group "Utilitários"
  fi
elif [[ "$OS_NAME" == "Darwin" ]]; then
  ICON_ARGS=()
  [[ -f assets/icon.icns ]] && ICON_ARGS=(--icon assets/icon.icns)
  jpackage "${COMMON[@]}" --type app-image "${ICON_ARGS[@]}"
  if [[ "${1:-}" == "dmg" ]]; then
    rm -rf "target/dist/Agent Meter.app"
    jpackage "${COMMON[@]}" --type dmg "${ICON_ARGS[@]}"
  fi
else
  printf 'No Windows use scripts/package-windows.ps1.\n' >&2
  exit 1
fi
