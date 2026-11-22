#!/usr/bin/env bash
# Downloads the small picture model ZT Camera bundles for searching the Private vault by
# what is in a photo (a dog, the beach, food...), read on the phone. MediaPipe's
# EfficientNet-Lite0 image classifier (int8, Apache 2.0), trained on ImageNet's 1,000 kinds
# of things. Its label list is packed inside the model file and copied out next to it. The
# file must match the SHA-256 in tool/vision_model.sha256.
set -euo pipefail
cd "$(dirname "$0")/.."
URL=https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/1/efficientnet_lite0.tflite
OUT=android/capture/src/main/assets/vision
MODEL="$OUT/efficientnet_lite0.tflite"
mkdir -p "$OUT"
if [ ! -s "$MODEL" ]; then
  curl -fsSL --retry 4 -o "$MODEL.part" "$URL"
  mv "$MODEL.part" "$MODEL"
fi
sum=$(sha256sum "$MODEL" | cut -d' ' -f1)
want=$(cut -d' ' -f1 tool/vision_model.sha256)
if [ "$want" != "$sum" ]; then
  echo "::error::efficientnet_lite0.tflite hash $sum does not match the pinned $want"
  rm -f "$MODEL"
  exit 1
fi
# The labels, one per line, in the order of the model's 1,000 outputs.
unzip -p "$MODEL" labels_without_background.txt > "$OUT/labels.txt"
lines=$(wc -l < "$OUT/labels.txt")
if [ "$lines" -ne 1000 ]; then
  echo "::error::expected 1000 labels in the model, found $lines"
  exit 1
fi
echo "efficientnet_lite0.tflite $(stat -c %s "$MODEL") bytes, sha256 $sum, $lines labels"
