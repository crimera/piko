#!/usr/bin/env bash
# Patch Twitter/X with the main bundle plus the complete X-Lite patch delta.
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"

VER=$(grep "^version" gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="patches/build/libs/patches-${VER}.mpp"
XLITE_MPP="x-lite/patches-${VER}.mpp"
for bundle in "$MPP" "$XLITE_MPP"; do
  if [[ ! -f "$bundle" ]]; then
    echo "Missing patch bundle: $bundle" >&2
    echo "Build it first with: bash .gradle-gh.sh ./gradlew :patches:build --no-daemon" >&2
    exit 1
  fi
done

for entry in \
  "app/crimera/patches/xlite/misc/aidataset/AiDatasetPatchKt.class" \
  "app/crimera/patches/xlite/utils/Constants.class" \
  "app/crimera/patches/xlite/settings/Groups.class" \
  "extensions/xlite.mpe"; do
  if ! unzip -q -t "$XLITE_MPP" "$entry"; then
    echo "X-Lite bundle is missing required entry: $entry" >&2
    exit 1
  fi
done

# Merge the main and X-Lite patch artifacts into one bundle. The patcher must
# see one bundle because X-Lite's Compose post-option hook is shared by all menu patches.
TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/piko-patches.XXXXXX")
MERGED_DIR="$TMP_ROOT/unpacked"
MERGED_MPP="$TMP_ROOT/patches-${VER}.mpp"
mkdir -p "$MERGED_DIR"
cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

unzip -q "$MPP" -d "$MERGED_DIR"

# Import every X-Lite-owned class and resource. Copying individual patch
# classes leaves their shared fingerprints/utilities out of the merged jar.
unzip -q "$XLITE_MPP" \
  "app/crimera/patches/xlite/*" \
  "addresources/values/xlite/*" \
  "extensions/xlite.mpe" \
  -d "$MERGED_DIR"
(
  cd "$MERGED_DIR"
  zip -q -X -r "$MERGED_MPP" .
)

java -jar morphe-desktop-1.11.0-all.jar patch \
  -p "$MERGED_MPP" \
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
  -e "Bring back twitter" \
  -e "X-Lite: Hide who to follow" \
  -e "X-Lite: Hide AI-generated posts" \
  -e "X-Lite: Customize default media tab" \
  -e "X-Lite: Custom font" \
  -e "X-Lite: Open canonical URLs" \
  -e "X-Lite: Collect AI-filter training posts" \
  -e "X-Lite: Log network diagnostics" \
  --striplibs=arm64-v8a \
  --force \
  -o ~/Downloads/piko-twitter-patched.apk \
  --continue-on-error \
  ~/Downloads/twitter_12.15.1-release.0.apk
  # ~/Downloads/twitter_12.12.0-release.0.apk 2>&1
  # -e "X-Lite Browse Object option" \
  # -e "Disable auto timeline scroll on launch" \
