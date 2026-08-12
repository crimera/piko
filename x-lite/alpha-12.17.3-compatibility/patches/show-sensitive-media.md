# Show sensitive media

## Status

**Ported; patch application and final-Dex verification passed on 12.17.3-alpha.01. Runtime device verification is pending because no ADB device is connected.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/timeline/ShowSensitiveMediaPatch.kt`

## Breakage

The old fingerprint targeted the removed stable `ContextualPost.getMediaVisibilityResults()` descriptor. The alpha preserves the model's semantic `toString()` label but obfuscates the class and fields.

## Findings and fix

- APK: `/Users/steven/Downloads/twitter_12.17.3-alpha.01.apk`
- APK SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- Alpha model evidence: one `ContextualPost(canonicalPost=..., mediaVisibilityResults=...)` model match.
- Resolved the media field from the semantic `toString()` chain, then matched exactly two constructor methods and two field writes.
- Each constructor is cloned with preserved parameter positions and extra locals. The setting is read through the shared `injectReadWithDefault` settings helper using reserved consecutive locals; when enabled, the visibility result is replaced with null.
- MPP build: `./gradlew :patches:build --no-daemon` passed.
- Exclusive patch: `Applied: X-Lite: Show sensitive media`.
- Output: `/tmp/twitter-12.17.3-alpha.01-show-sensitive-helper2.apk`
- Output SHA-256: `4010dc59026c1ebd21672c13b3de4f384f572c035a2b54fe8db25f18b877e0af`
- Final DEX: apktool decoded all 12 DEX files; both constructors contain the reachable setting read and null override.

Runtime testing remains to be done on a connected device: open a sensitive-media post, verify the default-enabled behavior, toggle the setting off, and confirm the warning returns.
