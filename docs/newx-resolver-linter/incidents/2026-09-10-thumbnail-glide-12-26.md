# NewX 12.26 alpha thumbnail cache backend

- Date: 2026-09-10
- Reporter/session: user report; pi repair session
- APK package/version/build type: `com.twitter.android` 12.26.0-alpha.01 alpha
- APK path and checksum: `/Volumes/realme/Dev/twitter-analysis/apks/twitter_12.26.0-alpha.01.apk` / `b3d0e3e7bc6ab4704d91697842ca64e80b86f420bb620a06b49f812850bd46ac`
- Source commit: `6ed7364f27cd5c717dee0c7aa76c863bff7fb274` plus working-tree thumbnail bridge changes
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `55138b7ae5a51046064bc3fe157d3eced054ded39d8acf4400fd26260bac84f1`
- Extension artifact paths/checksums: `/Volumes/realme/Dev/piko/morphe-data/extensions/newx.mpe` / `285745207b89d20048f3b4548f986df3dd35e3d04a0c0b991189ef665f2c8163`
- Output artifact path/checksum: `/Users/steven/Downloads/piko-twitter-patched.apk` / `5b026270411ada42b45b24959723596a651e9622e0bc06ad7d14267b3c1e85ae`
- Command: `./patch-twitter.sh /Volumes/realme/Dev/twitter-analysis/apks/twitter_12.26.0-alpha.01.apk`
- Failing patch: `NewX: Inline download button` dependency `newXThumbnailCachePatch`
- Severity: medium
- Confidence: likely; bytecode cause confirmed, runtime fix not device-tested

## Symptom

With the Glide feature switch enabled, the 12.26 alpha media renderer uses the
Glide branch while the patched thumbnail helper was still routed to Coil. The
same user path works when Glide is disabled.

## Complete error

No patch-time exception. The report was a runtime cache-path failure.

## Reproduction

The 12.26 APK's media renderer invokes `Lcom/x/media/imageloader/b;->isEnabled()Z`
and its true branch calls `Lcom/x/ui/common/glide/c;`. The old patch routed all
non-12.25-alpha versions to Coil. A Glide-only patch resolved and applied to the
same APK; no Android device was connected for runtime verification.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [x] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior

## Isolation comparison

The old source was rebuilt and patched before the repair: patching succeeded but
selected Coil for 12.26. The target APK was independently inspected with
`dexscope`; the Glide cache shape matched the existing resolver. The repair was
then built from the working tree and patched successfully for 12.22 production,
12.24 production, 12.25 production, 12.25 alpha, and 12.26 alpha.

## Fix and validation

The version gate was removed. When the recognized Glide cache shape is present,
the primary helper checks Glide and falls through to a separately patched Coil
helper, allowing the server-side switch to change after patching. Targets
without that shape retain the Coil-only path.

Validation:

- `./gradlew :patches:test :patches:lintNewxResolvers --console=plain`
- `./gradlew :patches:build --console=plain`
- exact APK patch matrix listed above; all saved successfully
- final 12.26 helper contains Glide lookup followed by Coil fallback

## Fixture or test added

No linter fixture was needed; the resolver linter was unchanged.

## Lessons

Do not route this backend by app version. The feature switch is remote and can
change after patching, so a single version-selected cache bridge is stale by
construction.
