# Customize default reply sorting

## Status

**Ported to 12.17.3-alpha.01; patch-applied and final-APK verified.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/replysorting/DefaultReplySortingPatch.kt`

## Breakage

1. `XLiteComposeReplySortingFingerprint` previously filtered on `methodCall("Lcom/x/models/PostIdentifier;->getValue()J")`, which was removed/inlined as a primitive `J` field access on `Lcom/x/models/b6;` in alpha.
2. In `XLiteComposeReplySortingSelectionFingerprint`, lambda consolidation under R8 placed multiple `Function1` lambdas into `Lcom/x/photoeditor/j2;` with a large `packed-switch`. Finding the initial `CHECK_CAST` at index 0 selected an unrelated branch, and passing high parameter registers (`p1` mapped to `v22` / `v23`) in 4-bit `invoke-static` format 35c caused Dalvik assembly errors.

## Findings and fix

1. **Repository Initializer Anchor:** Updated `XLiteComposeReplySortingFingerprint` to filter on `string("rankingMode")` and `string("timelineRepository")`, locating `com/x/postdetail/l.<init>` with cardinality 1. The target `sget-object` for `Lcom/x/models/ye;->Relevance` is found preceding `const-string "rankingMode"`.
2. **Selection Handler Anchor & Range Invocation:** In `XLiteComposeReplySortingSelectionFingerprint` (`com/x/photoeditor/j2`), locate the `CHECK_CAST` preceding `string("defaultUrtTimelineComponent")` within the reply sorting switch branch. Read the checked register and invoke `remember` via `invoke-static/range {v$selectedRegister .. v$selectedRegister}` to handle high register allocations safely.
3. **UI State Initializer:** `XLiteComposeReplySortingUiStateFingerprint` matched `com/x/ui/common/user/b0.invoke()` with cardinality 1, dynamically injecting `getEnumDefault(Ljava/lang/Class;)` for `Lcom/x/models/ye;`.

## Verification

- `:patches:build`: Passed.
- Patch exclusively applied against `com.twitter.android` `12.17.3-alpha.01`.
- Output APK generated and signed: `/tmp/twitter-12.17.3-alpha.01-reply-sorting.apk`.
