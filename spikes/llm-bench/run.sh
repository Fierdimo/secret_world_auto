#!/usr/bin/env bash
# Compara Gemma 3 1B (MediaPipe) con Qwen3 0.6B (LiteRT-LM) en un movil conectado.
#
# Uso: spikes/llm-bench/run.sh [qwen|gemma|ambos]   (por defecto ambos)
#
# Instala el banco, copia los modelos que falten y ejecuta un modelo cada vez,
# en procesos separados para que la memoria medida sea la de cada uno. Deja los
# resultados en spikes/llm-bench/results/.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=com.secretworld.auto.llmbench
DEST="/sdcard/Android/data/$PKG/files"
QWEN="$ROOT/dist/models/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
QWEN_SHA=bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0
GEMMA_ZIP="$ROOT/app/src/demo/assets/models/llm-gemma3-1b.zip"
WHICH="${1:-ambos}"

"$ADB" get-state >/dev/null 2>&1 || { echo "no hay dispositivo conectado (adb devices)"; exit 1; }

if [ ! -f "$QWEN" ]; then
  echo "descargando Qwen3 0.6B (343 MB)..."
  mkdir -p "$(dirname "$QWEN")"
  curl -sL -o "$QWEN" "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
fi
[ "$(shasum -a 256 "$QWEN" | cut -d' ' -f1)" = "$QWEN_SHA" ] || { echo "el .litertlm no es el publicado; se borra"; rm -f "$QWEN"; exit 1; }

APK="$HERE/build/outputs/apk/release/llm-bench-release.apk"
if [ ! -f "$APK" ]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
  (cd "$ROOT" && ./gradlew :spikes:llm-bench:assembleRelease -q)
fi
echo "instalando el banco..."
"$ADB" install -r "$APK" >/dev/null

# Un modelo copiado a medias se detecta por el tamano, no por el nombre.
push_if_needed () {
  local src="$1" dst="$2"
  local want; want=$(stat -f%z "$src")
  local have; have=$("$ADB" shell "stat -c%s '$dst' 2>/dev/null || echo 0" | tr -d '\r')
  if [ "$have" != "$want" ]; then
    echo "copiando $(basename "$dst") ($((want / 1048576)) MB)..."
    "$ADB" shell "mkdir -p '$(dirname "$dst")'"
    "$ADB" push "$src" "$dst" >/dev/null
  fi
}

push_if_needed "$QWEN" "$DEST/models/qwen3-0.6b/model.litertlm"
if [ "$WHICH" != qwen ]; then
  [ -f "$GEMMA_ZIP" ] || { echo "falta $GEMMA_ZIP: ejecutar tools/fetch-demo-assets.sh"; exit 1; }
  WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
  unzip -q "$GEMMA_ZIP" model.task -d "$WORK"
  push_if_needed "$WORK/model.task" "$DEST/models/llm-gemma3-1b/model.task"
fi

mkdir -p "$HERE/results"
run_model () {
  local model="$1"
  echo "=== $model ==="
  "$ADB" logcat -c
  "$ADB" shell am start -S -n "$PKG/.BenchActivity" --es model "$model" >/dev/null
  # Hasta 15 minutos: en un movil modesto cada orden puede costar segundos.
  for _ in $(seq 1 180); do
    "$ADB" logcat -d -s SWLlmBench:I | grep -q "BENCH DONE" && break
    sleep 5
  done
  "$ADB" logcat -d -s SWLlmBench:I | sed 's/^.*SWLlmBench: //' | tee "$HERE/results/$model.log" | grep -E '^(===|OK|MAL|cargado|prefijo|memoria|[a-z]{2}:|total|tiempo|FALLO|BENCH)'
  "$ADB" pull "$DEST/results-$model.tsv" "$HERE/results/" >/dev/null 2>&1 || true
}

case "$WHICH" in
  qwen|gemma) run_model "$WHICH" ;;
  *) run_model qwen; run_model gemma ;;
esac
echo "resultados en $HERE/results/"
