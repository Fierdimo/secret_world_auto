#!/usr/bin/env bash
# Descarga la librería de sintesis y las voces. No se versionan: son ~100 MB.
# Ver docs/VOICE-OPTIONS.md para por que estas y no otras.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
BASE=https://github.com/k2-fsa/sherpa-onnx/releases/download
SHERPA_VERSION=1.13.7

EN_MODEL=vits-piper-en_US-ljspeech-medium-int8
IT_MODEL=vits-piper-it_IT-paola-medium-int8

mkdir -p "$HERE/libs" "$HERE/feature/voice/src/main/assets/tts"

if [ ! -f "$HERE/libs/sherpa-onnx.aar" ]; then
  echo "descargando sherpa-onnx $SHERPA_VERSION..."
  curl -sL -o "$HERE/libs/sherpa-onnx.aar" \
    "$BASE/v$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION.aar"
fi

fetch_voice () {
  local lang="$1" model="$2"
  local dest="$HERE/feature/voice/src/main/assets/tts/$lang"
  [ -d "$dest" ] && { echo "voz $lang ya presente"; return; }
  echo "descargando voz $lang ($model)..."
  local tmp; tmp="$(mktemp -d)"
  curl -sL -o "$tmp/v.tar.bz2" "$BASE/tts-models/$model.tar.bz2"
  tar -xjf "$tmp/v.tar.bz2" -C "$tmp"
  mkdir -p "$dest"
  cp -R "$tmp/$model/." "$dest/"
  rm -rf "$tmp"
}

fetch_voice en "$EN_MODEL"
fetch_voice it "$IT_MODEL"

echo "listo. Licencias de cada voz en feature/voice/src/main/assets/tts/*/"
