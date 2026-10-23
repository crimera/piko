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

## Phase 2: post-anchored restore (12.34+ only)

Follow-up to "Known limits": the index restore is replaced by a post anchor. The patch now
declares only 12.34.0-prod.01 and 12.35.0-prod.01 (the user approved dropping 12.30-12.33).

### Evidence gathered on device (12.35, two accounts, logging probes since removed)

- First visible row key at save: `RegularItem(entryId=tweet-..., sortIndex=...)`
  (`Lcom/x/urt/ui/h0;`, `equals` on exactly those two values). Null only for empty lists.
- On a cold start the persistent restore runs while the timeline state is not loaded
  (`items=not-loaded`); posts arrive ~200 ms later.
- X's own resume (`x_android_timeline_resume_position_enabled`, `com.x.urt.features.o`
  `TimelineResumePosition`) is gated to `timeline == FOR_YOU`, skips saves older than
  `x_android_timeline_resume_position_max_age_minutes` (default 60) and restores only on an exact
  item-index match with offset 0. It cannot serve Following or lists. Its UI step is the useful part:
  a Compose SideEffect calls the component's `u()` while state is Success and a remembered flag is
  false, then `LazyListState.i(II)` (`requestScrollToItem`) on a non-null result.
- Following's screen wrapper (`home/following/m`) and community detail do not override `u()`; the
  interface default returns null, so the post-load step never reached the component there.

### Discarded designs

1. Item-index arithmetic at post-load (`savedRow + foundItem - savedItem`, then `i(II)`): on device
   the saved post was at the same item index after restart, yet the restored row showed a newer post.
   Rows map to items differently in a new process, so item-space arithmetic cannot fix row-space
   drift; `requestScrollToItem` also clears Compose's remembered first-item key.
2. Skipping `i(II)` when the item did not move: still landed on the wrong post (same row drift).

### Implemented design

- Save: `piko$newxSaveAnchor(event)` on the component calls X's resume-position factory
  (items, visible rows, holder, clock.now()) and stores entryId / sortIndex / itemIndex per account
  and timeline next to index/offset.
- Restore: the getter's persistent restore arms a pending anchor. `piko$newxRestoreAnchor()` runs at
  the top of the component's `u()`; it finds the post with X's matcher. Found: stages the anchor and
  returns null. Gone: returns (0,0) so X scrolls to the top.
- UI: right after X's `u()` call in the SideEffect lambda, `piko$newxApplyAnchorKey(listState)`
  writes `new RegularItem(entryId, sortIndex)` into `LazyListScrollPosition.lastKnownFirstItemKey`.
  SideEffect runs before layout, so on the next measure Compose moves to that row by key.
- Delegating wrappers whose `f()` forwards to the component but lack `u()` get a forwarding `u()`
  (12.35: Following, community detail). Topic's wrapper has a different shape and is not covered.

### Device validation (anchor4 build, 12.35, 2026-10-17)

MPP SHA-256 `7603b3d079be4288a845ede104c612d3836c604393b11ecf2eced8ab46039745`, patched APK
SHA-256 `84e96209d79fa802f0d8572f7485d7d6178c11dc4eca50e446f1b695cd622b08`; both 12.34 and 12.35
patch with 47 applied, 0 failed.

| Test | Result | Anchor log |
| --- | --- | --- |
| Following, cold restart | same post | `found=444 savedItem=437 -> relocate-by-key` (post moved 7 items) |
| Following, cold restart | same post | `found=538 savedItem=538 -> relocate-by-key` |
| For You, cold restart | same post | `found=50 savedItem=50 -> relocate-by-key` |

The restored post sits a constant 439 px lower than before the restart in every build, including
index-only ones; that is X's collapsing header expanded on a fresh start, not a restore error.
Earlier builds of this phase failed the same Following tests (wrong, newer post) as described under
"Discarded designs". Not exercised on device: the post-gone path (top).

### Follow-up fixes after account-switch testing (anchor5, anchor6)

1. Non-post anchors. X's resume factory returned `cursor-gap-…` (a "show more" gap row) and, on
   a suspended account, `flip-prompt-message-suspended-prompt`. A gap is filled or moved on reload
   (`found=632 savedItem=607`), so relocating to it landed on an unrelated post. Anchors are now
   stored only for post entries (`tweet-…`, `…conversation-…`; never `promoted-…`); anything
   else clears the anchor and the plain index restore applies. Regression test:
   `TimelineScrollPositionStoreTest.onlyPostEntriesAreAnchors`.
2. Key written without a remeasure. With the key staged and `u()` returning null, the key only
   took effect at Compose's next measure; when nothing else remeasured, the list stayed at the raw
   index (`found=652 savedItem=641`, wrong post). `u()` now returns the restored position when
   the post is found, so X calls `i(II)` (`requestScrollToItem`, which forces a remeasure and
   forgets the old key), and the patch writes the staged key right after that call.

Device validation (anchor6, 12.35, account 992723888814546944; MPP SHA-256 `87c06b40e33a5c13bd99cd74f1fd93866db700bc840f2f9d5ed9f64772bc541b`,
patched APK SHA-256 `e022268f49787d47ca0c1449bbc1f6d81257f1d99d8ed07d8d5a66cbeb5b378b`; 12.34 and 12.35 both 47 applied, 0 failed):

| Test | Result | Anchor log |
| --- | --- | --- |
| Following, cold restart | same post, 0 px | `found=653 savedItem=639 -> relocate-by-key` (moved 14 items) |
| Following, cold restart | same post, 0 px | `found=677 savedItem=677` |
| Following, cold restart | same post, 0 px | `found=694 savedItem=694` |
| For You, cold restart | same post, 0 px | `found=60 savedItem=60` |

Account switching (crimera -> steven -> crimera) kept crimera's post in process. The second
account's Following tab shows only a suspension notice, so its own restore could not be validated
on device; its keys are account-scoped like the first account's.
