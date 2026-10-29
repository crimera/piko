# Restored timeline position lost after a cold start with a deep scroll

## Provenance

- Source: `1b70913c` plus the uncommitted restore/refresh changes described below.
- APK: `apks/12.39.0-alpha.03.apk`, package `com.twitter.android`, alpha. Reported on device
  with "Restore timeline position" and "Disable automatic timeline refresh" enabled.
- MPP: `patches/build/libs/patches-3.11.0-dev.1.mpp`, built locally on 2026-10-29.
- Command: `./patch-twitter.sh apks/12.39.0-alpha.03.apk` (default output
  `~/Downloads/piko-twitter-patched.apk`).
- Failing patch: none at patch time. Every patch reported `Applied`; the failure was runtime.

## Symptom

After scrolling deep into For You (often after the new-posts dot prepended a batch), closing the
app from recents and reopening landed on a different post. Logcat with NewX logging enabled
showed the restore reading the right saved position and then failing to find its anchor:

```text
NewX restore hit timeline=FOR_YOU ... index=152 offset=902
NewX anchor timeline=FOR_YOU ... found=-1 savedItem=153 savedIndex=152 items=95 entryId=tweet-2115492740746146025 reason=post-gone -> top
```

## Classification

Runtime behavior driven by server-side configuration, not linter, cardinality-helper, or
resolver logic. No resolver selected a wrong owner; the save, anchor, and restore hooks all ran
as designed. Two independent X behaviors removed or moved the anchor row:

1. **Startup trim.** `com.x.repositories.urt.a0` builds a `TimelineStartTrim(entriesToKeep,
   minEntries)` config from the feature switches
   `android_xlite_home_timeline_start_trim_entries_to_keep` (default 0 = off) and
   `android_xlite_home_timeline_start_trim_min_entries` (default 200). When the cache exceeds
   `minEntries`, the URT repository deletes every entry below the `entriesToKeep`-th
   (`DELETE FROM timeline_entry WHERE timeline_id = ? AND sort_index < ?`). The server turned the
   switch on, so a saved position past roughly 100 entries was deleted before the restore ran.
   The code is present since at least 12.36.1, so 12.38 is affected identically.
2. **ClearCache on the converted refresh.** With a saved position, the refresh patch turns the
   startup `AUTO_REFRESH` into `VIEWPORT_AWARE_AUTO_REFRESH`. When the response carries a
   `TimelineClearCache` instruction, the response processor deletes every cached entry except the
   reported viewport window and prepends the fresh page. The list shrank from ~663 to ~40 rows.
   Compose `LazyList` only searches for the first-visible key near its previous index, so it
   kept the stale index (~640) and landed a few posts away from the anchor.

The new-posts dot itself is unaffected: its batches carry no ClearCache and only prepend rows,
which Compose follows by key.

## Fix

- `RestoreTimelinePositionPatch`: while the restore toggle is on, force the read of
  `..._start_trim_entries_to_keep` to 0, so X builds no trim config. Anchored on the switch name
  plus `getInt(String, I)I` and the immediate `move-result`; exactly one match required.
- `DisableTimelineRefreshPatch`: the conversion to `VIEWPORT_AWARE_AUTO_REFRESH` arms a one-shot
  `TimelineRefreshGate` mark (15 s). The single `Lcom/x/repositories/urt/` method that reads the
  `TimelineClearCache` Apollo type (resolved through its `<clinit>` type name) has its
  "cleared cache" flag reset when the response's request type is `VIEWPORT_AWARE_AUTO_REFRESH`
  and the mark is consumed. All other refreshes keep honoring ClearCache.
- Kept as a safety net, not a cause: the restore bridge falls back to an entry-id-only match
  when the exact (entry id, sort index) match misses, and stages the row's current sort index
  for the key relocation. No real run needed it.

Rejected: suppressing the startup refresh entirely. At request time the in-memory timeline is
still empty, so a genuinely empty cache (first start, cleared data) cannot be told apart from a
not-yet-loaded one. The user previously saw a blank feed with that approach.

## Validation

- Patched and reported `Applied` for both patches: 12.39.0-alpha.03, 12.38.0-prod.01,
  12.36.1-prod.01. Final DEX inspected with dexscope on all three: trim hook in `urt/a0.a`,
  ClearCache hook in the URT response processor (`m1`/`l1`/`j1`), mark in `urt/z`, and the
  entry-id fallback in `piko$newxRestoreAnchor`.
- `./gradlew :patches:build` (including tests) and `:patches:lintNewxResolvers` passed.
- Device, 12.39.0-alpha.03: after the trim fix, a restart at item 329 restored with 341 rows
  loaded (`match=exact`). After the ClearCache fix, a restart after the dot appeared restored
  item 248 of 275 and stayed on it; the user confirmed the landing.
- Not yet observed on device: a startup response that actually carries ClearCache with the new
  hook (`NewX refresh ignore-clear-cache` log line). The entry-id fallback is also unexercised.

## Known limits

- With the startup trim disabled and ClearCache skipped on the converted refresh, the cached For
  You timeline can grow larger than X intends.
- If the converted refresh's response carries no ClearCache, the mark stays armed for up to 15 s
  and could consume a later viewport-aware response's ClearCache, matching the other gate marks.
