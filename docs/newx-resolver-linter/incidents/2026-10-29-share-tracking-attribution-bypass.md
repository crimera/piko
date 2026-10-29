# NewX share tracking removal bypassed by direct attribution builder

- Date: 2026-10-29
- Reporter/session: user report in pi session
- APK package/version/build type: `com.twitter.android` 12.39.0-alpha.03 (target of the prior delivered build; installed APK not independently confirmed)
- Reported URL: `https://x.com/kuruminradioh/status/2115678353386877083?a=_BAeK-p8uNbXDquels4&s=19`
- Prior output: `/Users/steven/Downloads/piko-twitter-patched.apk` (overwritten by the fixed build; prior checksum unavailable)
- Source commit: `d73ade22fcdb6f9aeaa5c0db493147eb3768e60e` plus dirty working tree
- Fixed MPP path and checksum: `patches/build/libs/patches-3.11.0-dev.1.mpp` / `e55ca840ee8a752eaaadfa088728ca819f6a922962b60dc42e9063ae899aa0e6`
- Fixed APK checksums:
  - 12.39 alpha: `2c8f381bf0eb5cb383d077786b8be527b60e73849cc4b20f9acba641fdc8da31`
  - 12.38 production: `b93a94333a9f7acd76ffa0b76ff30c4132bd2ab44a77198a2cf0a7c86ca9e3b3`
- Commands: `./gradlew :patches:test :patches:lintNewxResolvers :extensions:newx:test :patches:build --no-daemon`; `./patch-twitter.sh apks/12.39.0-alpha.03.apk`; `PATCH_OUTPUT_APK=/tmp/piko-twitter-12.38-patched.apk ./patch-twitter.sh apks/12.38.0-prod.01.apk`
- Failing patch: `NewX: Remove share tracking parameters` (reported no-op despite `Applied`)
- Cause: resolver logic / missed URL path; not linter, cardinality helper, tooling, or APK drift
- Confidence: high for call-path diagnosis and bytecode fix; device runtime confirmation pending

## Symptom

The user reported that the `a=` parameter remained after enabling the Links toggle. The prior patch rewrote URLs at common share-Intent and share-sheet call sites, but did not cover every path that turns an attributed post URL into an Intent.

## Root cause

`x_lite_share_link_attribution_enabled` gates appending the source code as `s=...`; it is not the source of the reported `a=` parameter. In `Lcom/x/share/impl/i;->b(Lcom/x/share/api/k;)V`, the enabled branch calls the attribution URL builder and passes its result directly to the Intent builder. That branch bypasses `Lcom/x/share/impl/y;->c(...)`, where the existing URL resolver hook was installed.

The attribution builder is semantically stable but its owner and method identity move between releases:

- 12.39 alpha: `Lcom/google/android/gms/internal/mlkit_vision_barcode/sa;->f`
- 12.38 production: `Lcom/google/android/gms/internal/mlkit_vision_text_common/ea;->b`

Both methods take a URL and an attribution-code enum, select `?` or `&`, append `s=`, and return the resulting URL. The resolver now discovers that shape and rewrites the returned URL, so the `a=` parameter is removed while `s=19` remains.

## Fix and evidence

- Added a semantic resolver for the attribution URL builder; it does not depend on the `x_lite_share_link_attribution_enabled` string or obfuscated owner/method name.
- Injected `ShareUrlResolver.rewriteSharedUrl` before the attribution builder returns.
- Final DEX inspection on both patched APKs confirms the call immediately after the `s=` string concatenation and before `return-object`.
- `NewX: Remove share tracking parameters` remains a Links toggle, default enabled.

## Validation

- `:patches:test`, `:patches:lintNewxResolvers`, `:extensions:newx:test`, and `:patches:build` — passed.
- `./patch-twitter.sh apks/12.39.0-alpha.03.apk` — applied `NewX: Remove share tracking parameters` and `NewX: Custom sharing domain`; saved to `/Users/steven/Downloads/piko-twitter-patched.apk`.
- `PATCH_OUTPUT_APK=/tmp/piko-twitter-12.38-patched.apk ./patch-twitter.sh apks/12.38.0-prod.01.apk` — same patches applied; output saved to `/tmp/piko-twitter-12.38-patched.apk`.
- Device runtime not exercised; user should retry the same share action with the Links toggle enabled.
