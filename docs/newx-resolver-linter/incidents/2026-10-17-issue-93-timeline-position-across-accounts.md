# Issue #93: timeline position restored from the wrong account

- Date: 2026-10-17
- Reporter/session: GitHub issue crimera/piko-newx#93 by `stradus64`; latest report on NewX 3.56.1 (comment 6164988676)
- APK package/version/build type: `com.twitter.android`; reported on 12.29.1-prod.01 (prod), Android 15, Samsung A14
- APKs analyzed: `apks/12.35.0-prod.01.apk` (SHA-256 `e843102492b5529116a02755eb5000a2d0dcb3b284bf3642cc4c78b47314ec92`),
  `apks/12.30.0-prod.01.apk` (SHA-256 `7cf769778173006d59509991c09aa7c44d50df03162d27409301bbe3175d8f9d`);
  resolver facts also checked in 12.27.0, 12.29.1, 12.33.1 and 12.34.0 prod
- Source commit analyzed: `8814a722` plus the fix in this commit
- MPP provenance: `patches/build/libs/patches-3.10.0-dev.9.mpp` built from this tree
  (SHA-256 `11b1ae2b1e0a2ae4ec89d0602c0709d4a3cc77f109254f2e74ce1ff17d7f2fcd`); the reporter's 3.56.1 artifact was not inspected
- Failing patch: `NewX: Restore timeline position` (runtime behaviour, not a patch-time failure)
- Cause classification: resolver/extension logic (persistent key not scoped to the account), not linter or cardinality-helper behaviour
- Confidence: high on the cause (source and bytecode); fix not yet validated on a device

## Symptom

With two X accounts added in-app, each account restores correctly after a cold restart, but
switching from account A to account B scrolls B's timeline to an arbitrary post instead of B's
saved position or the top. The earlier report in the same issue ("one account never restores")
is the same collision.

## Cause

`TimelineScrollPositionStore` persisted positions in one SharedPreferences file keyed only by
timeline (`FOLLOWING.index`, `timeline.<listId>.index`, ...). Both accounts read and wrote the same
slot, so B restored A's list index into B's feed and then overwrote the slot.

X's own in-memory position map (`com.x.urt.features.g`, a `ConcurrentHashMap` keyed by timeline
identity) is already per account: it is provided by the per-user Metro graph `Lcom/x/android/z1;`
(fields `a:Lcom/x/android/a2;` parent graph, `b:Lcom/x/models/eh;` UserIdentifier) in 12.35.
Only the patch's persistent store leaked across accounts.

## Fix

- Extension: keys become `account.<userId>.<previous key>`; `restore(Enum,String,J)[I` and
  `save(Enum,String,JII)V` take the account id and do nothing when it is `<= 0`. Unscoped keys
  written by older builds are ignored (their owner is unknown), so each user loses one saved
  position once. `DisableTimelineRefreshPatch` keeps `restore(Enum,String)[I`, which now uses the
  last account id seen by a scoped restore/save in the process and returns null before one is seen
  (the core URT repository `Lcom/x/repositories/urt/y;` has no UserIdentifier to pass).
- Patch: resolves UserIdentifier as the single `new-instance` in the `deserialize` method of the
  serializer whose `<clinit>` holds `"com.x.models.UserIdentifier"`, its single non-static `J` field,
  and the timeline component's single non-static UserIdentifier field (`c0.s` in 12.35, `y.s` in
  12.33.1, `x.s` in 12.27/12.29.1). It adds a synthetic `piko$newxAccountId()J` bridge to the
  component that reads the id in v0/v1 (null UserIdentifier -> 0), and all three hooks call it.

## Discarded approach (regression note)

The first implementation read the id inline with `iget-wide` in each hook. The save method has
152-153 registers (170 after other patches), so the destination register was ~v156, which the
4-bit `iget-wide` encoding cannot address; the patch would have thrown on every target. `compileKotlin`
did not catch it; only patching a real APK does. The bridge keeps the 4-bit read inside a
3-register method and callers use `move-result-wide`, which takes an 8-bit register.

## Validation

- `./gradlew :extensions:newx:testDebugUnitTest :patches:build --no-daemon`: passed
- `./gradlew :patches:lintNewxResolvers --no-daemon`: passed
- `./patch-twitter-cli.sh apks/12.35.0-prod.01.apk` and `apks/12.30.0-prod.01.apk`: 47 applied,
  0 failed each, including `NewX: Restore timeline position` and `NewX: Disable automatic timeline refresh`
- Emitted 12.35 bytecode inspected: bridge body, both getter restore calls
  (`invoke-virtual p0 -> move-result-wide`, `invoke-static restore(Enum,String,J)`), and the save
  hook's `invoke-static/range {v162 .. v167}` in argument order timeline, identity, id (wide), index, offset
- Not yet done: device run with two accounts (switch A -> B -> A and cold restarts). With logging
  enabled, `NewX save`/`NewX restore` lines now include `account=<id>`.

## Known limits / follow-up

The store still saves a list index and offset, not a post id. X 12.34+ ships an account-scoped,
entry-id-validated resume (`x_android_timeline_resume_position_enabled`, `com.x.urt.features.o`
`TimelineResumePosition`), absent in 12.30-12.33. A post-id check for our store needs runtime
evidence first: the LazyColumn row key format and whether cached rows are present at the first
measure (Compose clears `LazyListScrollPosition.lastKnownFirstItemKey` on an empty measure).
