#!/usr/bin/env bash
# Reune dentro de app/src/demo/assets todo lo que la demo necesita para
# funcionar sola, sin adb ni CDN: escucha (KWS, VAD, reconocedor con italiano),
# el LLM de intenciones y la voz italiana. La app lo instala desde el APK en el
# primer arranque (ModelStore.installBundled) y verifica cada sha256.
#
# Parte de lo que ya dejan en dist/ tools/fetch-voice-models.sh y
# tools/fetch-tts-assets.sh; lo unico propio es el reconocedor italiano.
#
# No se versiona: son ~520 MB. Ver .gitignore.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
BASE=https://github.com/k2-fsa/sherpa-onnx/releases/download
MODELS="$HERE/dist/models"
VOICES="$HERE/dist/voices"
OUT="$HERE/app/src/demo/assets"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- Reconocedor con italiano ----------------------------------------------
# El FastConformer que usa el coche entiende en/es/fr/de, no italiano. Este es
# el mismo modelo en diez idiomas (be de en es fr hr it pl ru uk): misma
# arquitectura, mismo tamano, misma configuracion en OnDeviceVoiceInput.
ASR=sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8
if [ ! -f "$MODELS/asr-nemo-eu.zip" ]; then
  echo "descargando ASR ($ASR, 106 MB)..."
  curl -sL -o "$WORK/asr.tar.bz2" "$BASE/asr-models/$ASR.tar.bz2"
  tar -xjf "$WORK/asr.tar.bz2" -C "$WORK"
  mkdir -p "$WORK/asr"
  cp "$WORK/$ASR"/encoder*.onnx "$WORK/asr/encoder.onnx"
  cp "$WORK/$ASR"/decoder*.onnx "$WORK/asr/decoder.onnx"
  cp "$WORK/$ASR"/joiner*.onnx "$WORK/asr/joiner.onnx"
  cp "$WORK/$ASR/tokens.txt" "$WORK/asr/tokens.txt"
  echo "NVIDIA NeMo FastConformer (be/de/en/es/fr/hr/it/pl/ru/uk), CC-BY-4.0. Exportado por k2-fsa/sherpa-onnx." > "$WORK/asr/LICENCE.txt"
  mkdir -p "$MODELS"
  (cd "$WORK/asr" && zip -q -r -X "$MODELS/asr-nemo-eu.zip" .)
fi

# --- Reductor de ruido -------------------------------------------------------
# GTCRN, 535 KB: limpia cada frase antes del reconocedor. Con musica y voces de
# cafeteria de fondo, "Sicret" se transcribia «Sigred» y no despertaba.
if [ ! -f "$MODELS/denoiser-gtcrn.zip" ]; then
  echo "descargando reductor de ruido (gtcrn)..."
  mkdir -p "$WORK/denoiser"
  curl -sL -o "$WORK/denoiser/gtcrn_simple.onnx" "$BASE/speech-enhancement-models/gtcrn_simple.onnx"
  echo "GTCRN (Rong et al., ICASSP 2024), MIT. Exportado por k2-fsa/sherpa-onnx." > "$WORK/denoiser/LICENCE.txt"
  (cd "$WORK/denoiser" && zip -q -r -X "$MODELS/denoiser-gtcrn.zip" .)
fi

# --- Voz italiana ------------------------------------------------------------
# riccardo (x_low): la mas rapida de sintetizar. paola (medium) suena mejor pero
# en un movil lento tarda en sintetizar mas de lo que dura la frase. Ademas
# riccardo esta entrenada desde cero sobre M-AILABS, sin heredar lessac.
VOICE=vits-piper-it_IT-riccardo-x_low-int8
if [ ! -f "$VOICES/it-riccardo.zip" ]; then
  echo "descargando voz italiana ($VOICE)..."
  curl -sL -o "$WORK/voice.tar.bz2" "$BASE/tts-models/$VOICE.tar.bz2"
  tar -xjf "$WORK/voice.tar.bz2" -C "$WORK"
  mkdir -p "$VOICES"
  (cd "$WORK/$VOICE" && zip -q -r -X "$VOICES/it-riccardo.zip" .)
fi

# --- Lo que ya existe en dist/ ---------------------------------------------
need () { [ -f "$1" ] || { echo "falta $1: $2"; exit 1; }; }
need "$MODELS/kws-en.zip" "ejecutar tools/fetch-voice-models.sh"
need "$MODELS/vad.zip" "ejecutar tools/fetch-voice-models.sh"
need "$MODELS/llm-gemma3-1b.zip" "ejecutar tools/fetch-voice-models.sh"

mkdir -p "$OUT/models" "$OUT/voices"
for id in kws-en vad asr-nemo-eu denoiser-gtcrn llm-gemma3-1b; do
  cp "$MODELS/$id.zip" "$OUT/models/$id.zip"
done
cp "$VOICES/it-riccardo.zip" "$OUT/voices/it.zip"
# Español e inglés también: el idioma se cambia en los ajustes de la app.
need "$VOICES/es.zip" "ejecutar tools/fetch-tts-assets.sh y empaquetar la voz"
need "$VOICES/en.zip" "ejecutar tools/fetch-tts-assets.sh y empaquetar la voz"
cp "$VOICES/es.zip" "$OUT/voices/es.zip"
cp "$VOICES/en.zip" "$OUT/voices/en.zip"

echo
for f in "$OUT"/models/*.zip "$OUT"/voices/*.zip; do
  bytes=$(stat -f%z "$f" 2>/dev/null || stat -c%s "$f")
  sha=$(shasum -a 256 "$f" | cut -d' ' -f1)
  echo "$(basename "$f"): bytes=$bytes sha256=$sha"
done
echo
echo "listo. Si asr-nemo-eu o la voz cambian, copiar bytes y sha256 a ModelCatalog.ASR_EU y VoiceCatalog."
