# NewX runtime optimization and restore audit — 2026-10-26

## Changes and acceptance contracts

### Composition media eligibility

The inline download button previously generated and parsed media model strings while
composing actions. The new composition-only Object→boolean bridge reads resolved
image URL or GIF/video variant URL and MIME fields directly. It accepts HTTP images,
and HTTP MP4 variants by MIME or the existing case-insensitive `.mp4` URL rule.
Canonical/repost precedence, unsupported-media false, and click-time selection/parser
behavior are preserved. It does not share native action instances or cache action lists.

Patch-time resolution uses unique model semantic strings, model field types, the
GIF/video shared variant getter/interface, and a validated variant schema. It derives
all native descriptors and fields from each APK; no version routing, reflection, or
obfuscated runtime constants are introduced. Zero/ambiguous/changed shapes fail closed.
Typed emission initializes the cast receiver from the input, pairs invoke/results,
handles null/unknown elements, and loops until a matching variant or exhaustion.

### Restore retry fast path

The injected post-load bridge first checks the concurrent pending-anchor map. With
no pending custom anchor it returns null before repository, account, setting, and
storage-key work; the original native restore getter remains reachable. With an
anchor pending, existing keyed lookup, relocation, consumption, and fallback remain.
This adds no negative-result cache or persistent global skip flag.

### Restore save admission

The injected save bridge checks positive account ID, supported timeline category,
and the existing restore setting before invoking the clock/native anchor factory.
Existing native map saves, position saves, and account tracking are preserved.
The gate is only necessary admission: invalid profile/per-ID identities can still
reach the factory and be rejected later by existing validation. Settings read errors
fall through to that existing save path. This optimization does not reject a valid scope.

## Restore audit findings

Exact 12.37.2 and 12.36.1 DEX tracing found lifecycle ON_PAUSE/ON_STOP and paging
completion producers. The lifecycle effect is keyed by lifecycle owner/event, not
scroll index/offset. No per-pixel save observer was proven. Paging save frequency
was not measured.

A separate successful-load Compose SideEffect can retry the restore getter while
its applied flag is false. A null result leaves it retryable. This establishes a
composition retry opportunity, not a count of calls per frame. The old empty-key
path still resolved account/settings/key data; the new fast path removes that work.
The full-list relocation scan already required an actual keyed pending anchor.

Local detailed audit: `build/reports/newx-restore-perf/recon.md`.
The reported slowdown cannot be attributed to restore from this source audit alone.

## Build and APK validation

Source: `bda61e6b` plus the working changes. The saved pre-change benchmark APK was
built from `9e518d8b`; the rebased tree differs only in ClonePatch.kt, which is not
selected in either benchmark artifact. No commit was created by this task.

Command:

```sh
./gradlew :extensions:newx:testDebugUnitTest :extensions:newx:lintRelease :patches:build :patches:lintNewxResolvers --no-daemon
```

263 extension and 81 patch tests passed, with zero failures/errors/skips. API 29
lint and resolver lint passed. The new patch tests guard initialized typed receivers,
iterator/result/branch emission, packed GIF labels, and ambiguous variant schemas.

The fresh MPP patched exact originals through `./patch-twitter.sh`:

| Target | Applied patches | Output under artifact root |
|---|---:|---|
| 12.37.2-prod.01 | 46 | `updated.apk` |
| 12.36.1-prod.01 | 46 | `updated-12.36.1.apk` |
| 12.34.0-prod.01 | 46 | `updated-12.34.apk` |
| 12.37.2-prod.01, `-d "NewX: Restore timeline position"` | 45 | `norestore.apk` |

Each successful log records `Saved to /Users/steven/Downloads/piko-twitter-patched.apk`;
only then was the output copied to its distinct artifact path. The no-restore control
has none of the generated timeline restore bridges. Final DEX media predicates were
inspected on all three targets. On 12.37.2, the restore bridge starts with the pending
check; save admission precedes clock/factory work, and null custom restore falls
through to native code. Dexscope verification is **inconclusive**, because reference
assignability is not modeled; this is not claimed as a verifier proof.

Two resolver failures caught before installation and their corrections/provenance
are recorded in [the incident log](newx-resolver-linter/incidents/2026-10-26-runtime-optimization-and-restore-performance.md).

Artifact root: `build/reports/newx-runtime-optimization/20261026-034923/`.
`artifact-provenance.json` records APK/MPP/extension hashes; `source.diff`, patch logs,
final-method listings, collectors, raw frame summaries, and Perfetto traces are local
review evidence. APKs/traces/generated reports are not committed.

## Device comparison

Same phone and 12.37.2 originals are used for pre-change, updated, and no-restore
variants; no data clear/uninstall is performed. Each block uses eight warmup forward
swipes, then three trials of 24 oscillating gestures.

Run order: baseline A, updated A, no-restore C, updated B, baseline B. All 15 trials
are valid (no restart or focus loss, healthy traces, thermal status 0, battery
31.7–33.7 °C).

| Block | HWUI jank % (per run) | HWUI P95 / P99 ms (median) | Main-thread CPU ms (median) | Frame work P99 ms (median) |
|---|---|---|---:|---:|
| Pre-change A | 2.32, 1.86, 2.03 | 18 / 31 | 4339 | 29.75 |
| Updated A | 2.84, 2.76, 2.68 | 21 / 36 | 4639 | 35.08 |
| No-restore C | 3.32, 3.21, 3.19 | 24 / 34 | 4966 | 31.82 |
| Updated B | 2.32, 2.46, 2.87 | 18 / 36 | 4723 | 32.43 |
| Pre-change B | 2.64, 2.69, 2.22 | 19 / 34 | 4788 | 30.10 |

Pooled over six runs each: pre-change 2.29% janky (235/10241), updated 2.66%
(264/9941). Medians of per-run jank: 2.27% vs 2.72%; main-thread CPU 4577 vs
4683 ms.

Conclusions:

- The A-block gap mostly reversed in block B: updated improved from 2.76% to 2.46%
  median, while pre-change worsened from 2.03% to 2.64%. The same build moves about
  0.5 points between blocks, which matches the remaining pre-change vs updated gap.
  Each reinstall reloads the feed, so content differs between blocks. These runs
  show no measurable scrolling improvement or regression from this change.
- Omitting Restore timeline position did not improve scrolling; the no-restore block
  was the jankiest of the five. The data does not support attributing the scrolling
  slowdown to restore.
- Closing the old 12.33 vs current gap needs either a fixed-content scroll target
  (for example, a pinned profile or saved list) to reduce feed noise, or a
  per-method trace (Perfetto with method sampling) to find the real hotspot.

The pre-change baseline APK is left installed on the phone after block B.

## Deferred recon proposals

Native action sharing still lacks consumer identity/item-key proof. Timeline list
caching still lacks complete settings/rules/whitelist/authorship revisions and
recording semantics. These remain deferred; this task introduces neither unsafe cache.
Feature-switch and emoji hook boundaries stay at their proven coverage points.
