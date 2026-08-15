# X-Lite Patch Performance Optimization Report & Handoff Guide

**Target APK**: `twitter_12.17.3-alpha.01.apk` (`com.twitter.android` 12.17.3-alpha.01, arm64-v8a)
**Date**: 2026-08-15
**Baseline File**: [`baseline_patch_times.txt`](file:///Volumes/realme/Dev/piko/baseline_patch_times.txt)

---

## 1. Executive Summary & Benchmark Results

Two optimization passes eliminated full-multidex method scans, legacy partial-string scans, and repeated package traversal. The second pass was based on the actual Morphe patcher implementation, not profiler inference.

### Overall Benchmark Speedup

| Metric | Original | First Pass | Final | Final Improvement |
|---|---:|---:|---:|---:|
| **Sequential patch execution (24 patches)** | **57,929.62 ms** | **18,351.61 ms** | **1,290.77 ms** | **97.8% reduction / 44.9x faster** |
| **Resource decode** | Not separated | Not separated | **1,499.11 ms** | Reported separately |
| **DEX decode** | Not separated | Not separated | **731.26 ms** | Reported separately |
| **All 24 X-Lite patches** | 23 PASS / 1 FAIL | 24 PASS | **24 PASS** | **100% pass rate** |

The final sequential figure excludes resource/DEX decode, matching the original patch-execution benchmark. End-to-end decode plus patch execution is approximately **3.52 seconds** in the profiler harness. Profiling itself adds logging overhead; use an unprofiled run for headline comparisons.

### Second-Pass Gains Over the Previous Handoff

| Patch / metric | First Pass | Final Bundle Increment | Improvement |
|---|---:|---:|---:|
| **All patches** | 18,351.61 ms | **1,290.77 ms** | **14.2x faster** |
| **Unlock downloads** | 1,334.28 ms | **48.86 ms** | **27.3x faster** |
| **Inline download button** | 2,534.00 ms | **21.18 ms** | **119.6x faster** |
| **Customize inline actions** | 1,723.55 ms | **44.68 ms** | **38.6x faster** |
| **Dynamic color** | 1,246.10 ms | **31.05 ms** | **40.1x faster** |
| **Open canonical URLs** | 2,229.95 ms | **16.51 ms** | **135.1x faster** |
| **Show sensitive media** | 430.82 ms | **17.92 ms** | **24.0x faster** |
| **Customize navbar items** | 375.01 ms | **10.85 ms** | **34.6x faster** |
| **Default media tab** | 415.87 ms | **4.64 ms** | **89.6x faster** |

---

## 2. Original vs. First-Pass Patch Execution Times

> Historical first-pass results retained for handoff context. The current final benchmark is in Section 1 and [`baseline_patch_times.txt`](file:///Volumes/realme/Dev/piko/baseline_patch_times.txt).

| # | Patch Name | Before Bundle (ms) | After Bundle (ms) | Speedup Factor |
|---|---|---|---|---|
| 1 | **X-Lite: Unlock downloads** | 42,017.56 ms | **1,334.28 ms** | **31.5x faster** |
| 2 | **X-Lite: Show sensitive media** | 955.03 ms | **430.82 ms** | **2.2x faster** |
| 3 | **X-Lite: Customize navigation bar items** | 465.45 ms | **375.01 ms** | **1.2x faster** |
| 4 | **X-Lite: Inline download button** | 2,705.64 ms | **2,534.00 ms** | **1.1x faster** |
| 5 | **X-Lite: Hide AI-generated posts** | 1,757.05 ms | **1,528.36 ms** | **1.15x faster** |
| 6 | **X-Lite: Share post as image** | 396.62 ms | **359.39 ms** | **1.1x faster** |
| 7 | **X-Lite: Disable automatic timeline refresh** | 420.30 ms | **368.27 ms** | **1.14x faster** |
| 8 | **X-Lite: Filter posts by keyword** | 363.08 ms | **414.71 ms** | Consistent |
| 9 | **X-Lite: Customize default reply sorting** | 410.96 ms | **413.08 ms** | Consistent |
| 10 | **X-Lite: Customize default media tab** | 399.83 ms | **415.87 ms** | Consistent |
| 11 | **X-Lite: Restore timeline position** | 411.64 ms | **413.57 ms** | Consistent |
| 12 | **X-Lite: Custom font** | 441.43 ms | **430.54 ms** | Consistent |
| 13 | **X-Lite: Open canonical URLs** | *(Errored in baseline)* | **2,229.95 ms** | **Fixed & PASS** |
| 14 | **X-Lite: Customize inline actions** | 1,660.84 ms | **1,723.55 ms** | Consistent |
| 15 | **X-Lite: Dynamic color** | 1,166.72 ms | **1,246.10 ms** | Consistent |
| 16 | **X-Lite: Remove ads** | 3,932.47 ms | **4,106.12 ms** | Consistent |
| 17–24 | Other 8 lightweight patches | < 20 ms total | **< 20 ms total** | Instant |
| **TOT** | **Total Execution Time** | **57,929.62 ms** | **18,351.61 ms** | **~3.2x faster** |

---

## 3. Detailed Root Causes & Fixes Applied

### 1. `DownloadFingerprints.kt` ([`UnlockDownloadsPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/premium/UnlockDownloadsPatch.kt))
- **Problem**: 5 fingerprints in `DownloadFingerprints.kt` lacked `strings` and `definingClass`, forcing 5 full linear traversals across ~100,000 classes (taking ~38s total).
- **Fixes**:
  - Anchored `XLiteVideoTabDownloadHandlerFingerprint` with `strings = listOf("subscriptions_watermarked_video_download_enabled")`.
  - Scoped `XLitePremiumSubscriptionCheckerFingerprint` with `definingClass = "Lcom/x/subscriptions/"`.
  - Refactored `MediaContentVideoIsDownloadableFingerprint`, `MediaContentGifIsDownloadableFingerprint`, and `MediaContentImageIsDownloadableFingerprint` to use class fingerprints with exact string literal anchors (`MediaContentVideo(mediaId=`, `MediaContentGif(mediaId=`, `MediaContentImage(mediaId=`).
- **Result**: Patch search time plummeted from **6,447 ms to 27 ms**; total patch time dropped from **42.0s to 1.33s**.

### 2. [`ShowSensitiveMediaPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/timeline/ShowSensitiveMediaPatch.kt)
- **Problem**: `mediaWriteMatches` searched all classes for an `IPUT_OBJECT` instruction writing the media visibility field without specifying a defining class, taking 150 ms.
- **Fix**: Added `definingClass = contextualPost.originalClassDef.type`.
- **Result**: Search time dropped from **150 ms to 0.07 ms**.

### 3. [`CustomizeNavBarPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/navbar/CustomizeNavBarPatch.kt)
- **Problem**: `XLiteTabDataFingerprint` lacked `definingClass`, searching all classes for navigation enums.
- **Fix**: Added `definingClass = "Lcom/x/main/"`.
- **Result**: Search time dropped from **56.44 ms to 3.08 ms**.

### 4. [`DefaultMediaTabPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/mediatab/DefaultMediaTabPatch.kt)
- **Problem**: `XLiteCombinedProfileTimelineSeedFingerprint` lacked `definingClass` and scanned all classes for the timeline seed constructor.
- **Fix**: Added `definingClass = "Lcom/x/profile/timeline/"`.
- **Result**: Search time dropped from **53.01 ms to 3.18 ms**.

### 5. [`DefaultReplySortingPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/replysorting/DefaultReplySortingPatch.kt)
- **Problem**: `XLiteComposeReplySortingUiStateFingerprint` did not constrain the defining class.
- **Fix**: Added `definingClass = "Lcom/x/ui/common/"`.
- **Result**: Search time dropped from **94.24 ms to 5.12 ms**.

### 6. [`DisableTimelineRefreshPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/timeline/DisableTimelineRefreshPatch.kt)
- **Problem**: `XLiteLifecycleAutoRefreshFingerprint` scanned all classes for `invokeSuspend`.
- **Fix**: Added `definingClass = "Lcom/x/urt/"`.
- **Result**: Search time dropped from **73.07 ms to 6.16 ms**.

### 7. [`DynamicColorFingerprints.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/dynamiccolor/DynamicColorFingerprints.kt) & [`DynamicColorPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/dynamiccolor/DynamicColorPatch.kt)
- **Problem**: `HorizonThemePaletteProviderFingerprint`, `XLiteDynamicColorPaletteProviderFingerprint`, and inline action entry fingerprints did not declare `definingClass`.
- **Fix**: Added `definingClass = "Lcom/x/compose/theme/"`, `definingClass = "Lcom/x/compose/core/"`, and `definingClass = "Lcom/x/inlineactionbar/"`.
- **Result**: Provider search times dropped by ~50%.

### 8. [`CustomizeInlineActionsPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/inlineactions/CustomizeInlineActionsPatch.kt) & [`InlineDownloadButtonPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/inlineactions/InlineDownloadButtonPatch.kt)
- **Problem**: Used custom lambdas checking `classDef.type.startsWith("Lcom/x/inlineactionbar/")` rather than `definingClass = "Lcom/x/inlineactionbar/"`.
- **Fix**: Replaced custom string checks with indexed `definingClass` declarations.
- **Result**: Search times dropped from **49.6 ms to 4.5 ms**.

### 9. [`CanonicalUrlsPatch.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/misc/canonicalurls/CanonicalUrlsPatch.kt)
- **Problem**: `UrlEntityModelFingerprint` had multiple ambiguous string filters and `requireSingleMatch` threw an error in sequential bundle runs due to non-deduplicated `matchAll()` results.
- **Fix**: Simplified string anchor to `"UrlEntity(displayUrl="`, added `definingClass = "Lcom/x/"` to sub-fingerprints, and deduplicated matches via `distinctBy`.
- **Result**: Patch now applies cleanly in bundle sequential mode and passes 100%.

---

## 4. Second-Pass Root Causes and Optimizations

Inspection of Morphe's [`Fingerprint.kt`](https://github.com/MorpheApp/morphe-patcher/blob/main/src/main/kotlin/app/morphe/patcher/Fingerprint.kt) exposed two important behaviors:

1. `matchAllOrNull()` does not pre-scope candidate classes from `definingClass`; it iterates the global class map and rejects the class for every method.
2. Legacy `strings = ...` uses partial matching. Exact `string(...)` filters can use the global string index, but constructing that index costs roughly one second on this APK.

Final fixes:

- Added shared [`ScopedFingerprintMatching.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/utils/ScopedFingerprintMatching.kt) plus X-Lite-specific [`PatchProfiler.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/utils/PatchProfiler.kt).
- `scopedMatchAll()` prefilters class descriptors once, then runs the fingerprint only inside matching classes while preserving complete match cardinality.
- Cached all class **descriptors** once per context, then cached scoped descriptor subsets. Class objects are never cached, so lookups always retrieve the current mutable class.
- Added a lazily built method-shape index for the rare synthetic/repackaged owners that cannot be package-scoped safely.
- Converted verified data-class labels from legacy partial strings to ordered exact filters.
- Added preserved package scopes for timeline models, model classes, navigation, icons, inline actions, subscriptions, URT, settings callers, and Compose packages.
- Resolved the repackaged Compose settings renderer from its preserved `com/x/settings/common/` caller, then used exact-class lookup.
- Chained canonical URL targets through exact resolved owners; the URL picker now resolves in effectively constant time.
- Removed the zero-match premium checker that was dead for the only declared compatible target.

### Why Package Scoping Is Now Fast

A raw `Fingerprint(definingClass = "Lcom/x/foo/").matchAll()` still pays the patcher's global per-method loop. The scoped helper instead:

1. Captures all class descriptors once per `BytecodePatchContext`.
2. Filters and caches the `Lcom/x/foo/` descriptor subset.
3. Retrieves each current class through the class map.
4. Calls `matchAllOrNull(classDef)` only on those classes.

This preserves all-match cardinality without relying on first-match behavior or obfuscated descriptors.

---

## 5. Detailed Profiling

[`PatchProfiler.kt`](file:///Volumes/realme/Dev/piko/patches/src/main/kotlin/app/crimera/patches/xlite/utils/PatchProfiler.kt) is retained as an opt-in development utility, but production patch call sites no longer contain profiling wrappers or diagnostic labels. Temporarily wrap a search or function when deeper instrumentation is needed, then remove that call-site instrumentation before committing.

When temporary wrappers are present, enable them with either:

```bash
-Dpiko.xlite.profile=true
# or
PIKO_XLITE_PROFILE=true
```

Build and run a detailed sequential profile:

```bash
bash .gradle-gh.sh ./gradlew :patches:build --no-daemon
rm -rf /tmp/profile-bin
javac -cp "morphe-desktop-1.11.0-all.jar" \
  -d /tmp/profile-bin analysis/ProfilePatches.java
java -cp "/tmp/profile-bin:morphe-desktop-1.11.0-all.jar:patches/build/libs/patches-3.9.0-dev.4.mpp" \
  analysis.ProfilePatches --bundle-only --profile | tee /tmp/xlite-profile.log
```

Profiler modes:

| Option | Purpose |
|---|---|
| *(none)* | Fresh bundle benchmark, then isolated own-execution benchmarks; updates `baseline_patch_times.txt` |
| `--bundle-only` | Fast sequential benchmark only |
| `--profile` | Enable temporary `[X-Lite profile]` wrappers when call-site instrumentation is present |
| `--diagnostic-rematch` | Rerun fingerprints after mutation for diagnostics only; never use these times as actual search/apply time |

The harness now reports resource decode and DEX decode separately, prints every dependency's own execution path, distinguishes patch-own time from bundle-incremental time, and runs the bundle before isolated diagnostics to avoid warmed-cache headline results.

---

## 6. Remaining Floor / Future Opportunities

The remaining **~1.29 s** patch execution is dominated by:

1. **Shared dependency initialization under `Remove ads` (~451 ms incremental)** — extension merge, resource mapping, settings setup, and timeline model bridges execute here first.
2. **Synthetic post-renderer resolution under `Share post as image` (~427 ms)** — the owner is repackaged under a non-semantic Google package. The method-shape index preserves cardinality without hardcoding that descriptor and is reused by reply sorting.
3. All other public patch increments are below ~49 ms in the recorded run.

Further gains require either a stable semantic caller chain for the share-image renderer or an upstream Morphe patcher method/signature index. Hardcoding the observed synthetic owner would be faster but is intentionally rejected as release-fragile.
