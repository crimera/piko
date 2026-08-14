#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

DEFAULT_APK="${TWITTER_ALPHA_APK:-$HOME/Downloads/twitter_12.17.3-alpha.01.apk}" \
OUTPUT_APK="${TWITTER_ALPHA_OUTPUT_APK:-$HOME/Downloads/piko-twitter-12.17.3-alpha-patched.apk}" \
    exec "$ROOT_DIR/patch-twitter.sh" "$@"
