# NewX settings renderer drift: 12.27.0-alpha.01

- **Detected:** 2026-09-13
- **APK:** `com.twitter.android` 12.27.0-alpha.01, version code `312270201`
- **APK path:** `apks/twitter_12.27.0-alpha.01.apk`
- **APK SHA-256:** `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d`
- **Source commit:** `764cd7bf765d96482b3412f356710098ce55fa97` plus working-tree settings/post-options fixes
- **Final MPP:** `patches/build/libs/patches-3.9.0-dev.4.mpp`
- **Final MPP SHA-256:** `3d563f51a3e7b26681e433ef05049f5c9b9e6bfd629698e3909c193295652425`
- **NewX extension:** `extensions/newx/build/morphe/extensions/newx.mpe`
- **NewX extension SHA-256:** `b7014093238c4d04cb6507b40e682b31bccff7796ed632034751f3a6f9b1e36d`
- **Command:** `./patch-twitter.sh`
- **Failing patch:** `NewX: Browse tweet object`
- **Classification:** APK contract drift; the follow-up post-options failure was resolver logic exposed by the same drift
- **Confidence:** confirmed

## Failure

The settings row renderer kept its parameter shape, but the 12.27 Compose
compiler removed the `checkNotNullParameter(..., "title")` anchor and changed
the composer prologue result from `Composer` to its runtime implementation.
The original settings fingerprint therefore found no renderer.

After that resolver was adapted, the post-options finalizer found an extra
`move-object`/`goto` pair writing register `v59`; it was a temporary branch
value, not an icon assignment. The actual icon assignments were field-backed
and used `v23`.

The full bundle then reached a separate unresolved `NewX: Custom font`
fingerprint failure. That is another Compose text contract drift.

## Complete errors

```text
Failed to match the fingerprint: app.morphe.patcher.Fingerprint@3cf5271e
at app.crimera.patches.newx.settings.SettingsPatchKt.newXSettingsPatch$lambda$0$0(SettingsPatch.kt:68)
```

After the settings fix:

```text
Expected one NewX post-options icon result register, found 2: 23, 59
at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.injectLabelsAndIcons(PostOptionsPatch.kt:288)
```

## Isolation comparison

The settings failure reproduces on 12.27 and does not reproduce on
12.26.0-alpha.03. With the settings shape update, 12.27 reaches post-options
finalization. With the field-backed icon filter, Browse tweet object patches
successfully on both releases. The custom-font-only command still fails at its
paragraph typeface fingerprint, independent of post-options.

## Fix and validation

- Settings renderer matching now uses the stable Compose runtime package/type
  shape instead of generated method names or optional null-check strings.
- Post-options icon candidates are accepted only when their source register
  resolves to a reaching field read; this excludes the `v59` temporary branch.
- `./gradlew :patches:test :patches:lintNewxResolvers :patches:build` — passed
- 12.27.0-alpha.01 Browse tweet object patch — passed
- 12.26.0-alpha.03 Browse tweet object patch — passed
- Patched 12.27 output: `/tmp/piko-twitter-12.27-patched-final.apk`
- Patched 12.27 SHA-256: `bc5dd5299e49ae7c3bda057a3c021cc272e06ccfbc79f02cac1b3ed1d7e79efb`
- Final DEX inspection confirms the settings hook and post-option label/icon
  hooks are reachable in the patched artifact.

## Fixture or test added

No source fixture was added; the real APK matrix is the relevant regression
coverage for this bytecode contract change.
