#!/usr/bin/env bash
# Downloads the Tesseract language files ZT Camera bundles for reading text on the phone
# (document scanner, "Copy text" in the Private vault). From tessdata_fast, the small and
# quick models, at a fixed release tag. Each file must match its SHA-256 in
# tool/tessdata.sha256; a language missing there prints its hash to add.
set -euo pipefail
cd "$(dirname "$0")/.."
TAG=4.1.0
OUT=android/capture/src/main/assets/tessdata
SUMS=tool/tessdata.sha256
mkdir -p "$OUT"
for lang in eng heb; do
  file="$OUT/$lang.traineddata"
  if [ ! -s "$file" ]; then
    curl -fsSL --retry 4 -o "$file.part" \
      "https://github.com/tesseract-ocr/tessdata_fast/raw/$TAG/$lang.traineddata"
    mv "$file.part" "$file"
  fi
  sum=$(sha256sum "$file" | cut -d' ' -f1)
  want=$(grep -E " $lang.traineddata\$" "$SUMS" 2>/dev/null | cut -d' ' -f1 || true)
  if [ -z "$want" ]; then
    echo "::warning::No pinned hash for $lang.traineddata: $sum (add it to $SUMS)"
  elif [ "$want" != "$sum" ]; then
    echo "::error::$lang.traineddata hash $sum does not match the pinned $want"
    rm -f "$file"
    exit 1
  fi
  echo "$lang.traineddata $(stat -c %s "$file") bytes, sha256 $sum"
done
