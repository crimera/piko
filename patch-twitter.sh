#!/usr/bin/env bash
# Patch Twitter/X with the unified X-Lite patch bundle.
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"

INSTALL=false
FASTDEPLOY_PLATFORM_TOOLS_VERSION="${FASTDEPLOY_PLATFORM_TOOLS_VERSION:-36.0.0}"
FASTDEPLOY_PLATFORM_TOOLS_DIR="${FASTDEPLOY_PLATFORM_TOOLS_DIR:-${HOME}/.cache/piko/platform-tools-${FASTDEPLOY_PLATFORM_TOOLS_VERSION}}"

VER=$(grep "^version" gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="patches/build/libs/patches-${VER}.mpp"
if [[ ! -f "$MPP" ]]; then
  echo "Missing patch bundle: $MPP" >&2
  echo "Build it first with: ./gradlew :patches:build" >&2
  exit 1
fi

# BETA PATH: current default input for future X-Lite updates.
# ALPHA PATH: pass the 12.17.3-alpha.01 APK explicitly while compatibility is retained.
# TODO: Remove the alpha invocation note when the alpha target is deprecated.
DEFAULT_APK="$HOME/Downloads/twitter_12.18.0-beta.0.apk"
OUTPUT_APK="$HOME/Downloads/piko-twitter-patched.apk"
APK="$DEFAULT_APK"
FLAGS=()
for arg in "$@"; do
  case "$arg" in
    --install|--fastdeploy)
      INSTALL=true
      ;;
    *.apk)
      APK="$arg"
      ;;
    *)
      FLAGS+=("$arg")
      ;;
  esac
done

java -jar ../piko/morphe-desktop-1.11.0-all.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive \
  -e "X-Lite: Remove ads" \
  -e "X-Lite: Browse tweet object" \
  -e "X-Lite: Share post as image" \
  -e "X-Lite: Disable automatic timeline refresh" \
  -e "X-Lite: Restore timeline position" \
  -e "X-Lite: Customize inline actions" \
  -e "X-Lite: Unlock downloads" \
  -e "X-Lite: Hide new posts pill" \
  -e "X-Lite: Filter posts by keyword" \
  -e "X-Lite: Customize navigation bar items" \
  -e "X-Lite: Hide premium upsell" \
  -e "X-Lite: Hide compose button" \
  -e "X-Lite: Customize drawer items" \
  -e "X-Lite: Inline download button" \
  -e "X-Lite: Hide Spaces bar" \
  -e "X-Lite: Feature switch overrides" \
  -e "X-Lite: Show sensitive media" \
  -e "X-Lite: Dynamic color" \
  -e "X-Lite: Customize default reply sorting" \
  -e "X-Lite: Hide Discover more" \
  -e "X-Lite: Hide who to follow" \
  -e "X-Lite: Hide AI-generated posts" \
  -e "X-Lite: Customize default media tab" \
  -e "X-Lite: Custom font" \
  -e "X-Lite: Open canonical URLs" \
  -e "X-Lite: Filter For You by topic" \
  --striplibs=arm64-v8a \
  --force \
  -o "$OUTPUT_APK" \
  ${FLAGS[@]+"${FLAGS[@]}"} \
  -- \
  "$APK"
  # -e "Bring back twitter" \
  # -e "X-Lite: Collect AI-filter training posts" \

if [[ "$INSTALL" != true ]]; then
  exit 0
fi

if [[ -n "${ADB:-}" ]]; then
  ADB_BIN="$ADB"
else
  ADB_BIN="$FASTDEPLOY_PLATFORM_TOOLS_DIR/adb"

  if [[ ! -x "$ADB_BIN" ]]; then
    case "$(uname -s)" in
      Darwin) PLATFORM_TOOLS_OS=darwin ;;
      Linux) PLATFORM_TOOLS_OS=linux ;;
      *)
        echo "Unsupported host OS; set ADB=/path/to/adb" >&2
        exit 1
        ;;
    esac

    if ! command -v curl >/dev/null || ! command -v unzip >/dev/null; then
      echo "curl and unzip are required to download pinned adb" >&2
      exit 1
    fi

    DOWNLOAD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/piko-platform-tools.XXXXXX")
    trap 'rm -rf "$DOWNLOAD_DIR"' EXIT
    mkdir -p "$(dirname "$FASTDEPLOY_PLATFORM_TOOLS_DIR")"

    echo "Downloading platform-tools ${FASTDEPLOY_PLATFORM_TOOLS_VERSION}"
    curl -fsSL --retry 3 \
      -o "$DOWNLOAD_DIR/platform-tools.zip" \
      "https://dl.google.com/android/repository/platform-tools_r${FASTDEPLOY_PLATFORM_TOOLS_VERSION}-${PLATFORM_TOOLS_OS}.zip"
    unzip -q "$DOWNLOAD_DIR/platform-tools.zip" -d "$DOWNLOAD_DIR"
    rm -rf "$FASTDEPLOY_PLATFORM_TOOLS_DIR"
    mv "$DOWNLOAD_DIR/platform-tools" "$FASTDEPLOY_PLATFORM_TOOLS_DIR"
    ADB_BIN="$FASTDEPLOY_PLATFORM_TOOLS_DIR/adb"
  fi
fi

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found or not executable: $ADB_BIN" >&2
  echo "Unset ADB to let the script download platform-tools ${FASTDEPLOY_PLATFORM_TOOLS_VERSION}." >&2
  exit 1
fi

ADB_VERSION=$("$ADB_BIN" version | awk '/^Version / { version=$2; sub(/-.*/, "", version); print version; exit }')
if [[ "$ADB_VERSION" != "$FASTDEPLOY_PLATFORM_TOOLS_VERSION" ]]; then
  echo "Wrong adb version: ${ADB_VERSION:-unknown} (need $FASTDEPLOY_PLATFORM_TOOLS_VERSION)" >&2
  echo "Use ADB=/path/to/adb or unset ADB for the pinned copy." >&2
  exit 1
fi

echo "Checking fast deploy support"
FASTDEPLOY_PROBE=$("$ADB_BIN" install --fastdeploy -r "${OUTPUT_APK}.probe.apk" 2>&1 || true)
if grep -Eq 'Fast Deploy .*ignoring|fastdeploy is disabled' <<<"$FASTDEPLOY_PROBE"; then
  printf '%s\n' "$FASTDEPLOY_PROBE" >&2
  echo "This adb cannot use fast deploy; refusing a full APK transfer." >&2
  exit 1
fi

echo "Installing APK with fast deploy: $OUTPUT_APK"
"$ADB_BIN" install --fastdeploy -r "$OUTPUT_APK"
