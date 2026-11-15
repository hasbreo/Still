#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/androidTest/assets
ffmpeg -y -f lavfi -i testsrc2=size=360x640:rate=30 -t 8 -metadata creation_time=2026-11-09T08:12:22Z -c:v libx264 -pix_fmt yuv420p app/src/androidTest/assets/test.mp4
