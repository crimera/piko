# Patch Twitter/X with the unified NewX patch bundle.
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"

INSTALL=false
# Morphe Manager's adaptive default is higher, but 512 MB is its supported heap floor. Keeping
# local patch runs at that floor catches memory regressions that a desktop-sized heap would hide.
PATCHER_MAX_HEAP_MB="${PATCHER_MAX_HEAP_MB:-512}"
FASTDEPLOY_PLATFORM_TOOLS_VERSION="${FASTDEPLOY_PLATFORM_TOOLS_VERSION:-36.0.0}"
FASTDEPLOY_PLATFORM_TOOLS_DIR="${FASTDEPLOY_PLATFORM_TOOLS_DIR:-${HOME}/.cache/piko/platform-tools-${FASTDEPLOY_PLATFORM_TOOLS_VERSION}}"

if [[ ! "$PATCHER_MAX_HEAP_MB" =~ ^[1-9][0-9]*$ ]]; then
  echo "PATCHER_MAX_HEAP_MB must be a positive integer: $PATCHER_MAX_HEAP_MB" >&2
  exit 1
fi

VER=$(grep "^version" gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="patches/build/libs/patches-${VER}.mpp"
if [[ ! -f "$MPP" ]]; then
  echo "Missing patch bundle: $MPP" >&2
  echo "Build it first with: ./gradlew :patches:build" >&2
  exit 1
fi

DEFAULT_APK="./apks/12.28.0-prod.01.apk"
OUTPUT_APK="$HOME/Downloads/piko-twitter-patched.apk"
APK="$DEFAULT_APK"
FLAGS=()
for arg in "$@"; do
  case "$arg" in
    --install|--fastdeploy)
      INSTALL=true
      ;;
    *.apk|*.apkm|*.apks)
      APK="$arg"
      ;;
    *)
      FLAGS+=("$arg")
      ;;
  esac
done

echo "Patcher JVM heap limit: ${PATCHER_MAX_HEAP_MB} MB"
java "-Xmx${PATCHER_MAX_HEAP_MB}m" -jar ../piko/morphe-desktop-1.11.0-all.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive \
  -e "NewX: Remove ads" \
  -e "NewX: Browse tweet object" \
  -e "NewX: Share post as image" \
  -e "NewX: Customize inline actions" \
  -e "NewX: Classic inline action spacing" \
  -e "NewX: Unlock downloads" \
  -e "NewX: Hide new posts pill" \
  -e "NewX: Customize navigation bar" \
  -e "NewX: Hide premium upsell" \
  -e "NewX: Unlock color customization" \
  -e "NewX: Disable blur effects" \
  -e "NewX: Hide compose button" \
  -e "NewX: Hide post dividers" \
  -e "NewX: Customize drawer items" \
  -e "NewX: Hide Spaces bar" \
  -e "NewX: Feature switch overrides" \
  -e "NewX: Show sensitive media" \
  -e "NewX: Hide Discover more" \
  -e "NewX: Hide who to follow" \
  -e "NewX: Hide AI-generated posts" \
  -e "NewX: Hide posts by verified account type" \
  -e "NewX: Set default media tab" \
  -e "NewX: Gallery profile Photos tab" \
  -e "NewX: Set default profile post sorting" \
  -e "NewX: Custom font" \
  -e "NewX: Custom sharing domain" \
  -e "NewX: Inline download button" \
  -e "NewX: Theme" \
  -e "NewX: Set default reply sorting" \
  -e "NewX: Server error logging" \
  -e "NewX: Crash logs" \
  -e "NewX: Filter posts by keyword" \
  -e "NewX: Force highest video/audio quality" \
  -e "NewX: Filter For You by topic" \
  -e "NewX: Hide timeline tabs bar" \
  -e "NewX: Customize timeline tabs" \
  -e "NewX: Disable video player scrolling" \
  -e "NewX: Restore video picture-in-picture" \
  -e "NewX: Disable automatic timeline refresh" \
  -e "NewX: Restore timeline position" \
  -e "NewX: Open canonical URLs" \
  -e "NewX: Restore Twitter branding" \
  -e "NewX: Hide post reply bar" \
  -e "NewX: Show poll results" \
  -e "NewX: Customize post menu items" \
  --striplibs=arm64-v8a \
  --force \
  -o "$OUTPUT_APK" \
  ${FLAGS[@]+"${FLAGS[@]}"} \
  -- \
  "$APK"
  # -e "NewX: Collect AI-filter training posts" \

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
