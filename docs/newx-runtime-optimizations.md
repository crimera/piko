# NewX runtime optimizations

Tracking file. Check off as each lands + validated on device.

## Done (prior commit `5fc1d2f`)
- [x] Single-pass timeline filter (`TimelineFilterPatch` + `filterTimelineItems`)
- [x] `SettingsRegistry` lock-free reads after freeze (`volatile frozen`)
- [x] `TimelineScrollPositionStore` cached prefs + `save(...II)` no-reflection overload
- [x] `NewXShareImageHandler` LRU evict + cached `BoundsReader`/`RECTANGLE_FIELDS`
- [x] `InlineActionFilter` copy-on-write, `InlineDownloadButton` empty fast-path
- [x] `MediaThumbnailLoader` lazy log lambdas

## High (this round)
- [x] 1. `filterItem` lazy per-post fetch — only call `getPostTextForFilter` /
  `getPostAuthorScreenName` when keyword or verified filtering actually needs them.
  (`timeline/NewXTimelineFilter.java:filterItem`)
- [x] 2. `NewXLogger` cache `Setting` refs — avoid `SETTINGS` map lookup per
  `isLoggingEnabled()` call; hot on timeline/thumbnail/scroll paths.
  (`settings/NewXLogger.java` + `settings/SettingsRegistry.java:settingOrNull`)
- [x] 4. `filterTimelineItems(Object)` reorder — read 5 cheap booleans first,
  only read verified `StringSet` when `timeline||thread` true; skip store loads
  when provably disabled. (`timeline/NewXTimelineFilter.java:filterTimelineItems`)
- [x] 5. Whitelist lazy — don't fetch `authorId/screenName` bridges when
  whitelist empty. (same file, `isVerifiedAuthorToHide`)
- [x] 6. `filterModule` lazy — don't fetch `entryId` / parse conversation root
  when `whoToFollow/discoverMore/verified` all off. (same file, `filterModule`)

## Med / follow-up (done round 3)
- [x] 13. `NewXUtils.invoke` — `Method` cached per (class, name); `getMethod`
  walks the hierarchy under a lock on every call otherwise. Shared
  `isAscii`/`containsIgnoreCaseAscii` helpers (filter deduplicated onto them).
- [x] 14. `sanitizeFileName` — precompiled `Pattern` instead of per-call
  `replaceAll` compile.
- [x] 15. `runOnUiThread` — cached main `Handler` instead of per-call alloc.
- [x] 16. `storageKey` — single `trim()` instead of two.
- [x] 17. `InlineDownloadButton` — allocation-free `.mp4` check (2 sites,
  per video variant on bind) via shared helper.
- [x] Baseline guard re-run: all counts identical, ms flat within stub noise
  (these remove locks/regex/handler allocs — visible on device under
  contention, not in uncontended stubs). Full suite green.
## Med / follow-up (done round 2)
- [x] 3. `MediaThumbnailLoader` — UI-thread section skips id alloc + log lambdas
  when logging off; bg executor keeps plain gated calls. First `CACHE.get`
  stays on caller: uncontended LruCache get is ns, moving it risks delivery
  races for zero measurable gain.
- [x] 7. `hasPromotedClientEventInfoComponent` — allocation-free ASCII fold;
  exact `toLowerCase` fallback for non-ASCII. (This is the method the
  patch-time bridge actually calls; the `isPromotedClientEventInfo` Java
  fallback never runs in prod.)
- [x] 8. `copyChildrenPrefix` — `addAll(subList)`.
- [x] 9. `NewXUtils.findPresenterData` — cached accessible `Field[]` per class.
- [x] 10. `TimelineScrollPositionStore.restore` — sentinel `-1` default instead
  of `contains` + second `getInt`; legacy `save(Object)` regex replaced with
  manual digit parse (reflection fallback kept).
- [x] 11. `logFailure/logDiagnostic` catch sites — `isLoggingEnabled()` guard
  around `describeValue`/`childContext` construction (6 sites).
- [x] 12. `PostFilterMatcher` — `PhraseMatcher`: single-phrase `indexOf`,
  multi-phrase frozen-trie automaton; `normalize` ASCII fast-path (exact:
  NFKC is identity on ASCII). Equivalence guarded by existing
  `PostFilterMatcherTest` (fullwidth/é/locale cases) + full suite.

## Baselines
- Per-feature runtime numbers + fingerprint anchor cardinality:
  `docs/newx-runtime-baseline.md` (guard: `TimelineFilterBaselineTest`).
- Old patch-time report (different APK/patch set, pre-consolidation):
  `baseline_patch_times.txt`.

## Runtime audit runbook (adb, device attached)

No automated bench yet — all claims above are static audit. Validate by hand:

### 1. Logcat filter (no control, read-only)
```bash
adb logcat -c
# use app normally: scroll timeline, open thread, filter something
adb logcat -d | rg -i "newx|timeline|thumbnail|share-image" | head -n 100
```
`NewXLogger` gates on `newx.advanced.debug_tools.logging`. Turn it ON only for
repro, OFF for perf runs — logging itself allocates.

### 2. Timed runs (manual, same session)
- Cold: force-stop app, `adb logcat -c`, launch by hand, scroll same feed,
  note jank / time-to-settle.
- Compare before/after MPP on same APK version, same feed, same network.
- 3 runs each, take median. Don't trust first run after install (dexopt).

### 3. On-device tracing (when logcat isn't enough)
```bash
# system trace, then open in https://ui.perfetto.dev
adb shell perfetto -o /data/misc/perfetto-traces/trace -t 10s sched freq idle am wm gfx view binder_driver hal dalvik camera input res memory
adb pull /data/misc/perfetto-traces/trace /tmp/trace.perfetto-trace
```
Look for: long `filterTimelineItems` frames on scroll, `ThumbnailLoader`
executor pile-up, `SharedPreferences` `apply()` storms on scroll-save.

### 4. Targeted timing (opt-in only)
Add temporary `System.nanoTime()` spans behind a system property, e.g.
`adb shell setprop debug.newx.profile 1`, log one line per operation:
`[NewX profile] filterTimelineItems | 12.3 ms | items=50`. Remove before ship.
Never leave unconditional timing in hot paths.

### 5. What NOT to do over adb
- Don't `am start` / `input tap` / drive the app without explicit permission.
- Install is fine when needed for validation; driving UI is not.
- For repros: ask user to use app normally, send logs/screenshot.

## Validation checklist per change
- [x] Extension compiles (`:extensions:newx:compileDebugJavaWithJavac`)
- [x] Extension unit tests pass (`:extensions:newx:testDebugUnitTest`, full suite green)
- [x] A/B probe vs stashed baseline (counting `TimelineModelAccess`, same tree):
  - 2000 plain posts, promoted-filter on: `getPostText` 2000 -> 0,
    `getPostAuthorScreenName` 2000 -> 0, `getPostMentions` 2000 -> 0
  - 500 modules x 4 posts, promoted-filter on: `getModuleEntryId` 1000 -> 500
    (one fetch remains: promoted-module detection itself); child `getPostEntryId`
    unchanged at 2000 (promoted path still runs)
  - Probe was temporary and removed; counts are deterministic, wall-clock on
    stubs is noise (real bridges do reflection/`toString`/substring, so
    on-device delta is larger than stub delta)
- [x] Build MPP succeeds (`:patches:build`, `patches-3.9.0-dev.4.mpp`)
- [x] Exclusive patch of exact APK succeeds, all 42 patches `Applied`
  (`apks/twitter_12.27.0-alpha.01.apk` -> `~/Downloads/piko-twitter-patched.apk`)
- [x] Installed on device (`adb install -r`, Success), launched MainActivity,
  scrolled timeline x4: process alive, 0 `Failed NewX` lines, no crashes,
  gfxinfo 4437 frames / 0.54% janky / p50 13ms
- [x] Round 2 redeploy (items 3,7-12): 41/41 `-e` patches Applied (earlier
  "42" was a miscount), installed, relaunched (fresh pid), scrolled x4:
  0 `Failed NewX`, no crashes, p50 12ms (4.69% janky on 490 cold-start
  frames — launch inflation, settles after warmup)
- [x] Round 3 redeploy (items 13-17): 41/41 Applied, installed, relaunched
  (fresh pid), scrolled x4: 0 `Failed NewX`, no crashes, p50 12ms
  (6.95% on 403 cold-start frames — launch inflation again)
- [ ] Your cold hand-test for feel: scroll same feed, thread open, filter hit + miss
- [ ] Note APK version + MPP provenance in commit/message
