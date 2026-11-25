#!/usr/bin/env bash
# Reune en app/src/pleos/assets lo propio de la version para Pleos: los dos
# reconocedores de la demo y la voz italiana, que Pleos no tiene; en ingles y
# espanol habla la de Pleos. Sin el LLM, que son 392 MB. Parte de lo que ya deja tools/fetch-demo-assets.sh.
#
# No se versiona: son ~225 MB. Ver .gitignore.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
FROM="$HERE/app/src/demo/assets"
OUT="$HERE/app/src/pleos/assets"

mkdir -p "$OUT/models" "$OUT/voices"
# Las que ya no van, de una ejecucion anterior.
rm -f "$OUT/voices/en.zip" "$OUT/voices/es.zip"
for f in models/kws-en models/vad models/asr-nemo-multi models/asr-nemo-eu models/denoiser-gtcrn voices/it; do
  [ -f "$FROM/$f.zip" ] || { echo "falta $FROM/$f.zip: ejecutar tools/fetch-demo-assets.sh"; exit 1; }
  # En macOS, copia sin gastar disco; en otros sistemas, normal.
  cp -c "$FROM/$f.zip" "$OUT/$f.zip" 2>/dev/null || cp "$FROM/$f.zip" "$OUT/$f.zip"
done
du -sh "$OUT"
