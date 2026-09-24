# NewX 12.29 open canonical URLs profile-link resolver repair

- Date: 2026-09-24
- Reporter/session: 12.29 port session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh`, `./patch-twitter.sh apks/12.28.0-prod.01.apk --continue-on-error`,
  `./patch-twitter.sh apks/12.27.0-prod.01.apk --continue-on-error`
- Failing patch: `NewX: Open canonical URLs`
- Severity: medium (patch set could not complete without `--continue-on-error`)
- Cause: resolver logic — the profile-link builder is an R8-merged class whose owner moved out of
  the hardcoded `Lcom/x/media/imageloader/` scope into `Lcom/x/media/playback/` in 12.29, so the
  scoped fingerprint matched 0 candidates
- Confidence: high; injected reads verified in the final DEX of all three versions

## Symptom

```text
SEVERE: FAILED: NewX: Open canonical URLs
app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: app.morphe.patcher.Fingerprint@2475be03
    at app.morphe.patcher.Fingerprint.patchException(Fingerprint.kt:664)
    at app.crimera.patches.utils.ScopedFingerprintMatchingKt.scopedMatchAll(ScopedFingerprintMatching.kt:142)
    at app.crimera.patches.newx.misc.canonicalurls.CanonicalUrlsPatchKt.requireSingleMatch(CanonicalUrlsPatch.kt:1075)
    at app.crimera.patches.newx.misc.canonicalurls.CanonicalUrlsPatchKt.patchProfileLinkValues(CanonicalUrlsPatch.kt:815)
```

The abort happened at `patchProfileLinkValues`, after `resolveUrlEntityFields` and the earlier
canonical-URL insertion points had already resolved.

## Root cause

`patchProfileLinkValues` located the profile-header link builder with:

```kotlin
Fingerprint(
    definingClass = "Lcom/x/media/imageloader/",
    filters = listOf(displayUrlRead, urlRead),
)
```

The builder lived at `Lcom/x/media/imageloader/telemetry/h;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;`
through 12.28. In 12.29 R8 merged it into `Lcom/x/media/playback/u;->c(Ljava/lang/Object;)Ljava/lang/Object;`,
so the image-loader scope produced zero candidates.

Methods that also read both URL-entity fields prove the owner is not the discriminator:

| Release | Profile-link builder | Other field-pair readers |
| --- | --- | --- |
| 12.28.0-prod.01 | `Lcom/x/media/imageloader/telemetry/h;->invokeSuspend` | `Lcom/x/urt/items/post/i;->j` (reconstructs `Lmodels/text/l1`), rich-text builders |
| 12.29.0-alpha.04 | `Lcom/x/media/playback/u;->c` | `Lcom/x/urt/items/post/i;->j` (reconstructs `Lmodels/text/l1`), `Lcom/google/android/gms/internal/mlkit_vision_face/s9;->b` (text builder) |

The stable semantic invariant is that the builder constructs a `Lcom/x/profile/header/` model from
the URL entity (`profile/header/s4` + `profile/header/v` in 12.28,
`profile/header/r4` + `profile/header/w` in 12.29). The package prefix survives the leaf churn.

## Fix

`patchProfileLinkValues` now anchors on:

1. The `(Ljava/lang/Object;)Ljava/lang/Object;` shape shared by both releases.
2. The ordered `displayUrl` then `url` `IGET_OBJECT` field pair.
3. `constructsProfileHeaderModel()`, which requires a `NEW_INSTANCE` under
   `Lcom/x/profile/header/`.

`requireSingleMatch` remains the fail-closed boundary. A regression fixture
(`profile link resolver accepts profile-header construction and rejects URL-entity copies`) was
added to the existing `NewXResolverLinterTest` suite; `constructsProfileHeaderModel` is now
`internal` for it.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- 12.29.0-alpha.04 — `Applied: NewX: Open canonical URLs`. Final DEX shows the hook at
  `media/playback/u->c`: both the displayUrl read (`l1;->a`) and the url read (`l1;->c`) fall back
  to `expandedUrl` (`l1;->b`) when `newx.content.use_canonical_urls` is enabled.
- 12.28.0-prod.01 and 12.27.0-prod.01 — `Applied` and `Saved to`; the hook lands on
  `imageloader/telemetry/h->invokeSuspend` in both.

## Remaining 12.29 failures

After this fix the same 12.29 run progresses past `Open canonical URLs` and reports independent
failures in `NewX: Server error logging` and `NewX: Set default profile post sorting`. Those are
tracked separately and are not caused by this change.
