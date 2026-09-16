# NewX navigation bar customization materializes the full APK

## Report

- Date: 2026-09-16
- Reported symptom: Android devices exceed 1 GB of patcher memory and terminate with an OOM while
  applying `NewX: Customize navigation bar`.
- Reported device, Android version, APK version, and complete exception: not supplied.
- Failing patch: `NewX: Customize navigation bar` (`customizeNewXNavBarPatch`).
- Cause classification: resolver logic / mutable-proxy materialization, not linter or cardinality
  helper behavior.
- Confidence: high. Two deterministic APK-wide scans converted every visited class to a mutable
  proxy, and the same production patch set completes after both scans are made read-only.

## Reproduction artifact

- Package/version/build type: `com.twitter.android` 12.27.0-alpha.01.
- APK: `apks/twitter_12.27.0-alpha.01.apk` (repository-pinned local artifact).
- Source base commit: `84a4a3996e4b4b7ff58d0391920195a1cffa44b6` plus the working-tree fix.
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`, built from that working tree.
- Extension provenance: built by `./gradlew :patches:build` from the same working tree and bundled
  into the MPP.
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk`.
- Validation command: `./patch-twitter.sh`.
- Heap limit: 512 MB (`java -Xmx512m`), matching the manager's minimum supported process heap.

The original Android OOM stack trace is unavailable. The committed resolver behavior that caused
the report is visible in `NavBarCustomizationPatch.kt` at the base commit above.

## Root cause

Both `resolveNavBarItemContent` and `resolveDrawerRowCalls` iterate every immutable APK class with
`classDefForEach`. Inside those global loops, the committed implementation called
`mutableClassDefBy(classDef.type)` before inspecting methods. This creates and retains mutable
class/method proxies for the whole APK even though discovery is read-only. Large NewX APKs therefore
pay the memory cost of materializing nearly every class, and the retained proxy graph survives for
the remainder of patching.

The semantic searches themselves are required and remain APK-wide because their owners are
obfuscated or drawer rows can live outside the drawer package. The defect is converting every
candidate to a mutable representation, not the cardinality checks or the global iteration alone.

## Fix

- Scan `classDef.methods` and immutable instruction lists during APK-wide discovery.
- Retain only class descriptors and immutable method metadata for matching candidates.
- Resolve a mutable class/method only after exact cardinality identifies the class that will be
  changed.
- Run `patch-twitter.sh` with a default 512 MB maximum heap. `PATCHER_MAX_HEAP_MB` can override the
  cap for diagnostics, but the default guards the manager's lowest supported heap budget.

No fingerprint anchors, expected match counts, or mutation targets changed.

## Validation

- `bash -n patch-twitter.sh`: PASS.
- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`: PASS.
- Full 41-patch `./patch-twitter.sh` run with `-Xmx512m`: PASS.
- One measured 512 MB run peaked at 1,337,688,064 bytes RSS (about 1,276 MiB) and completed.
- One diagnostic 256 MB run peaked at 773,849,088 bytes RSS (about 738 MiB). The navbar patch
  applied successfully, but final APK assembly later exhausted the Java heap in
  `InMemoryByteStorage.fromStream` via `ApkUtils.applyTo`.
- `NewX: Customize navigation bar`: `Applied`.
- Finalization: DEX and resources compiled, APK aligned and signed.
- Saved output: `/Users/steven/Downloads/piko-twitter-patched.apk`.
- Runtime/device validation: not performed; this incident concerns patch-time memory use.

The pre-fix implementation was not rerun on a device because the user report already establishes
the unsafe failure, and deliberately reproducing a device OOM adds no resolver evidence. The
post-fix constrained desktop run exercises the complete patch set below the manager's normal
adaptive defaults (640–1024 MB) and at its 512 MB floor.

RSS includes mapped DEX/APK data, native allocations, and JVM overhead in addition to the Java
heap. These are single-run diagnostics, not three-run release benchmark medians, and they measure
the complete patch process rather than the navbar patch in isolation.
