# Patch Twitter/X with the unified NewX patch bundle.
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"

INSTALL=false
# --recompile skips patching and installing: it compiles the installed X with the usage profile
# it has collected, like Android's idle background dexopt. Run it after using X for a while.
RECOMPILE=false
for arg in "$@"; do
  [[ "$arg" == --recompile ]] && RECOMPILE=true
done
# Default to 2 GiB: 1 GiB OOMed in apkzlib align/sign on 12.35/12.37 (512 MB on 12.34/12.35).
# Override PATCHER_MAX_HEAP_MB when deliberately comparing smaller heaps.
PATCHER_MAX_HEAP_MB="${PATCHER_MAX_HEAP_MB:-2048}"
# Overridable so the bundle can also be exercised against a pre-release/manager patcher build,
# which is the ABI the shipped manager app actually executes.
MORPHE_CLI_JAR="${MORPHE_CLI_JAR:-../piko/morphe-desktop-1.19.0-all.jar}"
FASTDEPLOY_PLATFORM_TOOLS_VERSION="${FASTDEPLOY_PLATFORM_TOOLS_VERSION:-36.0.0}"
FASTDEPLOY_PLATFORM_TOOLS_DIR="${FASTDEPLOY_PLATFORM_TOOLS_DIR:-${HOME}/.cache/piko/platform-tools-${FASTDEPLOY_PLATFORM_TOOLS_VERSION}}"

if [[ ! "$PATCHER_MAX_HEAP_MB" =~ ^[1-9][0-9]*$ ]]; then
  echo "PATCHER_MAX_HEAP_MB must be a positive integer: $PATCHER_MAX_HEAP_MB" >&2
  exit 1
fi

VER=$(grep "^version" gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="patches/build/libs/patches-${VER}.mpp"
if [[ "$RECOMPILE" != true && ! -f "$MPP" ]]; then
  echo "Missing patch bundle: $MPP" >&2
  echo "Build it first with: ./gradlew :patches:build" >&2
  exit 1
fi

DEFAULT_APK="./apks/12.31.0-alpha.02.apk"
OUTPUT_APK="${PATCH_OUTPUT_APK:-$HOME/Downloads/piko-twitter-patched.apk}"
APK="$DEFAULT_APK"
FLAGS=()
for arg in "$@"; do
  case "$arg" in
    --install|--fastdeploy)
      INSTALL=true
      ;;
    --recompile)
      ;;
    *.apk|*.apkm|*.apks)
      APK="$arg"
      ;;
    *)
      FLAGS+=("$arg")
      ;;
  esac
done

if [[ "$RECOMPILE" != true ]]; then
echo "Patcher JVM heap limit: ${PATCHER_MAX_HEAP_MB} MB"
java "-Xmx${PATCHER_MAX_HEAP_MB}m" -jar "$MORPHE_CLI_JAR" patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive \
  -e "NewX: Remove ads" \
  -e "NewX: Browse tweet object" \
  -e "NewX: Customize inline actions" \
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
  -e "NewX: Enable quality-of-life feature flags" \
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
  -e "NewX: Remove share tracking parameters" \
  -e "NewX: Permanent share links" \
  -e "NewX: Inline download button" \
  -e "NewX: Redirect downloads to chosen folder" \
  -e "NewX: Theme" \
  -e "NewX: Set default reply sorting" \
  -e "NewX: Server error logging" \
  -e "NewX: Crash logs" \
  -e "NewX: Filter posts by keyword" \
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
  -e "NewX: Hide post author actions" \
  -e "NewX: Show poll results" \
  -e "NewX: Translator" \
  -e "NewX: Customize post menu items" \
  -e "NewX: Customize media menu items" \
  -e "NewX: Customize profile tabs" \
  -e "NewX: Restore pinned home tab" \
  --striplibs=arm64-v8a \
  --force \
  -o "$OUTPUT_APK" \
  ${FLAGS[@]+"${FLAGS[@]}"} \
  -- \
  "$APK"
  # -e "NewX: Collect AI-filter training posts" \
fi

if [[ "$INSTALL" != true && "$RECOMPILE" != true ]]; then
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

PACKAGE=com.twitter.android

print_compile_state() {
  "$ADB_BIN" shell dumpsys package dexopt \
    | awk -v pkg="[$PACKAGE]" 'index($0, pkg) { found=1; next } found && /status=/ { print "Compile state:" $0; exit }'
}

if [[ "$RECOMPILE" == true ]]; then
  # X's profile saver writes periodically; SIGUSR1 makes a running X save its usage profile now.
  if "$ADB_BIN" shell su -c id 2>/dev/null | grep -q 'uid=0'; then
    "$ADB_BIN" shell "su -c 'pid=\$(pidof $PACKAGE) && kill -s USR1 \$pid'" || true
    sleep 2
  fi
  echo "Recompiling $PACKAGE with its saved usage profile"
  "$ADB_BIN" shell cmd package compile -m speed-profile -f "$PACKAGE"
  print_compile_state
  exit 0
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

# Patching rewrites X's dex files, which invalidates its bundled baseline profile, so a fresh
# install stays at `verify` (interpreted/JIT) until idle background dexopt. Rebuild the profile
# for the patched APK on the device and compile it now. Needs root for profman and the app's
# profile slot; set SKIP_BASELINE_COMPILE=1 to skip.
if [[ "${SKIP_BASELINE_COMPILE:-0}" == 1 ]]; then
  exit 0
fi

REMOTE_RULES=/data/local/tmp/piko-baseline-rules.txt
REMOTE_SCRIPT=/data/local/tmp/piko-baseline.sh

if ! "$ADB_BIN" shell su -c id 2>/dev/null | grep -q 'uid=0'; then
  echo "Skipping baseline profile: device has no root (adb shell su)." >&2
  echo "X will compile itself during idle background dexopt instead." >&2
  exit 0
fi

BASELINE_DIR=$(mktemp -d "${TMPDIR:-/tmp}/piko-baseline.XXXXXX")
trap 'rm -rf "${DOWNLOAD_DIR:-}" "$BASELINE_DIR"' EXIT
echo "Extracting baseline profile rules from $APK"
python3 -I scripts/baseline_profile_rules.py "$APK" > "$BASELINE_DIR/rules.txt"

cat > "$BASELINE_DIR/apply.sh" <<EOF
set -e
base=\$(pm path $PACKAGE | sed -n 's/^package://p' | grep '/base.apk\$')
user=\$(am get-current-user)
current=/data/misc/profiles/cur/\$user/$PACKAGE/primary.prof
generated=/data/local/tmp/piko-baseline.prof
rm -f "\$generated"
/apex/com.android.art/bin/profman --create-profile-from=$REMOTE_RULES \
  --apk="\$base" --dex-location="\$base" --reference-profile-file="\$generated"
# A fresh install leaves an empty current profile; cat keeps its owner and SELinux label.
cat "\$generated" > "\$current"
rm -f "\$generated" $REMOTE_RULES $REMOTE_SCRIPT
EOF

"$ADB_BIN" push -q "$BASELINE_DIR/rules.txt" "$REMOTE_RULES"
"$ADB_BIN" push -q "$BASELINE_DIR/apply.sh" "$REMOTE_SCRIPT"
echo "Building baseline profile for the patched APK"
"$ADB_BIN" shell "su -c 'sh $REMOTE_SCRIPT'"
echo "Compiling $PACKAGE with speed-profile"
"$ADB_BIN" shell cmd package compile -m speed-profile -f "$PACKAGE"
print_compile_state
echo "After using X for a few minutes, run: $0 --recompile"

