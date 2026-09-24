# NewX 12.29 server error logging POST_SUCCESS relocation

- Date: 2026-09-24
- Reporter/session: 12.29 port session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh --continue-on-error`,
  `./patch-twitter.sh apks/12.28.0-prod.01.apk --continue-on-error`,
  `./patch-twitter.sh apks/12.27.0-prod.01.apk --continue-on-error`
- Failing patch: `NewX: Server error logging`
- Severity: medium (patch abort)
- Cause: resolver logic — 12.29 moved the POST_SUCCESS analytics read out of the submit handler, so
  the operation-register search had no upper bound
- Confidence: high; injection verified in the patched 12.29 bytecode and the method verifies

## Symptom

```text
SEVERE: FAILED: NewX: Server error logging
app.morphe.patcher.patch.PatchException: Expected one NewX POST_SUCCESS event, found 0:
    at app.crimera.patches.newx.misc.serverlogging.ServerLoggingPatchKt.patchSubmitFailureMethod(ServerLoggingPatch.kt:270)
    at app.crimera.patches.newx.misc.serverlogging.ServerLoggingPatchKt.patchSubmitFailureHandler(ServerLoggingPatch.kt:224)
```

## Root cause

`patchSubmitFailureMethod` found the failure operation by scanning the `Post` enum comparison
between the POST_FAILURE event and the POST_SUCCESS event. Through 12.28 the submit handler
(`Lcom/x/composer/work/n0;->c` / `p0;->c`) logged both events itself. In 12.29 the handler still
reads POST_FAILURE (twice) but logs POST_SUCCESS through the new
`Lcom/x/composer/upload/v2/steps/submit/b;->b(...)` step, so `findEventFields(POST_SUCCESS)` returned
zero.

Observed 12.29 anchors in `Lcom/x/composer/work/p0;->c`:

| Anchor | Index | Notes |
| --- | ---: | --- |
| `iget-object ... result/b;->a:Ljava/lang/Throwable` | 642 | the captured throwable |
| `POST_FAILURE` analytics read | 711 | after the throwable read |
| `h0;->Post` failure comparison | 728 | operation register `v9`, branch target 737 |
| `h0;->Post` success comparison | 892 | gates `upload/v2/steps/submit/b;->b(...)` |
| `upload/v2/steps/submit/b;->b(...)` call | 900 | success logging delegated here |

Both 12.28 and 12.29 have exactly two `Post` operation comparisons after POST_FAILURE (the failure
and success comparisons); only the success event location changed.

## Fix

`patchSubmitFailureMethod` keeps the 12.27/12.28 path unchanged when a POST_SUCCESS event is present,
and adds a fallback when it is absent:

- `successEventIndex` is now nullable. `1` keeps the existing enum-type/order validation and bound;
  `0` uses the fallback; `> 1` still fails closed.
- Extracted `selectSubmitFailureOperation(laterOperationCandidates, successEventIndex)`:
  - with an event: exactly one comparison bounded by the event index;
  - without an event: exactly two comparisons, failure is the first, and its branch target must be
    before the success comparison.
- Added the regression fixture
  `submit failure operation selection handles absent POST_SUCCESS event` to the existing
  `NewXResolverLinterTest` suite.

## Evidence

Injected after the throwable read in the patched 12.29 APK:

```smali
[04bc] (642) iget-object v0, v1, Lcom/x/result/b;->a:Ljava/lang/Throwable;
[04be] (643) invoke-static v0, v9, Lapp/morphe/extension/newx/settings/NewXLogger;->captureSubmitFailure(Ljava/lang/Throwable;Ljava/lang/Object;)V
```

`v9` is proven to be the operation enum: it is loaded from the coroutine state field
`m0;->u:Lcom/x/composer/work/h0;` and is the same register used by the failure operation
comparison at index 730.

`dexscope verify-method` on the patched 12.29 submit handler exits 0 with no errors (only the
tool's field-assignability modeling warnings).

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- 12.29.0-alpha.04 — `Applied: NewX: Server error logging`.
- 12.28.0-prod.01 and 12.27.0-prod.01 — `Applied` and `Saved to` (old POST_SUCCESS-bounded path
  unchanged).

## Remaining 12.29 failure

After this fix the only remaining failure in the patched run is `NewX: Set default profile post
sorting` (`Expected exactly one profile post sorting state initializer, found 0`). It is independent
of this change.
