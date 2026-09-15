#!/usr/bin/env bash
# Descarga y empaqueta los modelos de **escucha**: palabra de activacion (KWS),
# detector de voz (VAD) y reconocedor de ordenes (Whisper), todos para
# sherpa-onnx. Deja en dist/models/ un zip por modelo con su sha256, que es lo
# que sirve el CDN de desarrollo y lo que ModelCatalog verifica.
#
# El LLM de intenciones (Gemma 3 1B) tambien: el fichero oficial de
# litert-community esta detras de un formulario de Hugging Face, pero hay copias
# sin puerta del MISMO fichero; se descarga de una y se comprueba contra el
# sha256 del oficial, asi que da igual de donde venga.
#
# No se versiona nada de esto: son ~120 MB. Ver docs/VOICE-OPTIONS.md.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
BASE=https://github.com/k2-fsa/sherpa-onnx/releases/download
WORK="$(mktemp -d)"
OUT="$HERE/dist/models"
mkdir -p "$OUT"

KWS=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
ASR=sherpa-onnx-whisper-base

pack () {
  # pack <id> <dir con el contenido>
  local id="$1" src="$2"
  local zip="$OUT/$id.zip"
  rm -f "$zip"
  (cd "$src" && zip -q -r -X "$zip" .)
  local bytes sha
  bytes=$(stat -f%z "$zip" 2>/dev/null || stat -c%s "$zip")
  sha=$(shasum -a 256 "$zip" | cut -d' ' -f1)
  echo "$id: bytes=$bytes sha256=$sha"
}

# --- Palabra de activacion -------------------------------------------------
# La variante "-mobile" de este modelo NO vale: tiene formas fijas y aborta en
# el primer decode desde Android (Reshape {17,1,128} -> {8,2,1,128}).
if [ ! -f "$OUT/kws-en.zip" ]; then
  echo "descargando KWS ($KWS)..."
  curl -sL -o "$WORK/kws.tar.bz2" "$BASE/kws-models/$KWS.tar.bz2"
  tar -xjf "$WORK/kws.tar.bz2" -C "$WORK"
  mkdir -p "$WORK/kws-en"
  cp "$WORK/$KWS"/encoder-*.int8.onnx "$WORK/kws-en/encoder.onnx"
  cp "$WORK/$KWS"/decoder-*[!8].onnx "$WORK/kws-en/decoder.onnx"
  cp "$WORK/$KWS"/joiner-*.int8.onnx "$WORK/kws-en/joiner.onnx"
  cp "$WORK/$KWS/tokens.txt" "$WORK/kws-en/tokens.txt"
  # La palabra, escrita como la tokeniza el modelo (BPE de gigaspeech). Varias
  # grafias porque un nombre inventado no tiene una sola pronunciacion; todas
  # devuelven "siky" (@) para que el codigo no tenga que saberlo. El umbral y
  # el refuerzo van en la configuracion (OnDeviceVoiceInput), no aqui.
  # Medido con voz sintetica: lo que el modelo oye al decir "Siky" es
  # "S I K I", "S I KE" o "S IC K Y"; "SEE K Y" no dispara nunca, se deja por
  # si una voz real lo hace.
  cat > "$WORK/kws-en/keywords.txt" <<'EOF'
▁S I K Y @siky
▁S I K I @siky
▁S I KE @siky
▁S I KE Y @siky
▁S IC K Y @siky
▁S IC K I @siky
▁SEE K Y @siky
▁SEE KE E @siky
EOF
  cp "$WORK/$KWS/README.md" "$WORK/kws-en/LICENCE-Apache-2.0.md"
  pack kws-en "$WORK/kws-en"
fi

# --- Detector de voz --------------------------------------------------------
if [ ! -f "$OUT/vad.zip" ]; then
  echo "descargando VAD (silero)..."
  mkdir -p "$WORK/vad"
  curl -sL -o "$WORK/vad/silero_vad.onnx" "$BASE/asr-models/silero_vad.onnx"
  echo "Silero VAD, MIT. https://github.com/snakers4/silero-vad" > "$WORK/vad/LICENCE.txt"
  pack vad "$WORK/vad"
fi

# --- Reconocedor de ordenes -------------------------------------------------
# tiny se probo primero: "what's nearby" salia como "watch the nerve". base lo
# entiende y tarda ~3x, aun por debajo del segundo en un movil medio.
if [ ! -f "$OUT/asr-whisper-base.zip" ]; then
  echo "descargando ASR ($ASR, multilingue, int8)..."
  curl -sL -o "$WORK/asr.tar.bz2" "$BASE/asr-models/$ASR.tar.bz2"
  tar -xjf "$WORK/asr.tar.bz2" -C "$WORK"
  mkdir -p "$WORK/asr"
  cp "$WORK/$ASR/base-encoder.int8.onnx" "$WORK/asr/encoder.onnx"
  cp "$WORK/$ASR/base-decoder.int8.onnx" "$WORK/asr/decoder.onnx"
  cp "$WORK/$ASR/base-tokens.txt" "$WORK/asr/tokens.txt"
  echo "Whisper (OpenAI), MIT. Exportado por k2-fsa/sherpa-onnx." > "$WORK/asr/LICENCE.txt"
  pack asr-whisper-base "$WORK/asr"
fi

# --- LLM de intenciones -----------------------------------------------------
LLM="$OUT/llm-gemma3-1b.zip"
TASK="$HERE/dist/models/gemma3-1b-it-int4.task"
# sha256 de gemma3-1b-it-int4.task en litert-community/Gemma3-1B-IT (oficial).
TASK_SHA=e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee
if [ ! -f "$TASK" ] && [ ! -f "$LLM" ]; then
  echo "descargando LLM (Gemma 3 1B int4, 555 MB)..."
  curl -sL -o "$TASK" "https://huggingface.co/WilburDev/gemma3-1b-it-int4/resolve/main/gemma3-1b-it-int4.task"
  actual=$(shasum -a 256 "$TASK" | cut -d' ' -f1)
  if [ "$actual" != "$TASK_SHA" ]; then
    echo "el .task descargado NO es el oficial (sha256 $actual). Se borra."; rm -f "$TASK"
  fi
fi
if [ -f "$TASK" ] && [ ! -f "$LLM" ]; then
  mkdir -p "$WORK/llm"
  cp "$HERE/dist/models/gemma3-1b-it-int4.task" "$WORK/llm/model.task"
  echo "Gemma 3, Gemma Terms of Use: https://ai.google.dev/gemma/terms" > "$WORK/llm/LICENCE.txt"
  pack llm-gemma3-1b "$WORK/llm"
elif [ ! -f "$LLM" ]; then
  echo "sin LLM: no se pudo obtener gemma3-1b-it-int4.task. Dejarlo en dist/models/ y repetir."
fi

rm -rf "$WORK"
echo
echo "listo. Copiar bytes y sha256 a feature/voice/.../ModelCatalog.kt si han cambiado."
echo "Para instalarlos a mano en un dispositivo de pruebas: tools/sideload-models.sh"
