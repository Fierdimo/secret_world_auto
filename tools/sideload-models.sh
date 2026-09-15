#!/usr/bin/env bash
# Carga a mano los modelos de escucha y el LLM en un dispositivo de pruebas.
#
# Es la comodidad de desarrollo de SPEC RF-1.8.9: en la tienda los modelos los
# descarga la app en la primera ejecucion y los verifica; aqui se copian con adb
# al directorio externo de la app, sin verificar, porque los pone quien prueba.
#
# Uso: tools/sideload-models.sh [applicationId]   (por defecto el sabor bench)
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
APP="${1:-com.secretworld.auto.bench}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DEST="/sdcard/Android/data/$APP/files/models"
DIST="$HERE/dist/models"

[ -d "$DIST" ] || { echo "no hay dist/models: ejecutar antes tools/fetch-voice-models.sh"; exit 1; }
"$ADB" get-state >/dev/null 2>&1 || { echo "no hay dispositivo conectado (adb devices)"; exit 1; }

WORK="$(mktemp -d)"
push_zip () {
  local id="$1"
  [ -f "$DIST/$id.zip" ] || { echo "falta $id.zip, se salta"; return; }
  rm -rf "$WORK/$id"; mkdir -p "$WORK/$id"
  unzip -q "$DIST/$id.zip" -d "$WORK/$id"
  "$ADB" shell "mkdir -p $DEST && rm -rf $DEST/$id"
  echo "→ $id"
  "$ADB" push "$WORK/$id" "$DEST/$id" >/dev/null
}

push_zip kws-en
push_zip vad
push_zip asr-nemo-multi

if [ -f "$DIST/gemma3-1b-it-int4.task" ]; then
  echo "→ llm-gemma3-1b (~530 MB, tarda)"
  "$ADB" shell "mkdir -p $DEST/llm-gemma3-1b"
  "$ADB" push "$DIST/gemma3-1b-it-int4.task" "$DEST/llm-gemma3-1b/model.task" >/dev/null
else
  echo "sin LLM: no existe dist/models/gemma3-1b-it-int4.task (ver tools/fetch-voice-models.sh)"
fi

rm -rf "$WORK"
echo
"$ADB" shell "ls -la $DEST"
echo
echo "listo. La app los encuentra al arrancar; mirar 'adb logcat -s SWVoice SWVoiceInput SWVoiceLlm'."
