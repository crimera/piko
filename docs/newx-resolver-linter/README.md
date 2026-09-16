# NewX resolver safety experiment

## Purpose

NewX is obfuscated and changes frequently. Resolver cardinality helpers and a
source linter add code, rules, and patch-development friction. This experiment
measures whether that cost is justified by fewer incorrect, ambiguous, silent,
or difficult-to-diagnose patches.

The claim under test is:

> Fail-closed cardinality checks and resolver linting improve patch correctness
> and safety more than they increase development cost and compatibility friction.

This is not a claim of application security in the broad sense. The scope is
patch-time resolver safety and preservation of intended behavior.

## What counts as evidence

### Benefits

- A resolver change is rejected before producing a misleading or broken APK.
- Zero or ambiguous candidates produce a useful diagnostic.
- A patch no longer selects an unintended method, field, or call site.
- Optional probes preserve their fallback behavior.
- A real APK regression is detected earlier or diagnosed faster.

### Costs

- Added resolver/helper code and review complexity.
- Linter false positives or false negatives.
- Extra fixture maintenance.
- Longer lint/build/patch cycles.
- APKs rejected even though the patch could have applied safely.
- More difficult diagnosis caused by helper or linter behavior.

## Required validation layers

1. **Unit fixtures** — safe and unsafe source patterns with expected findings.
2. **Historical comparison** — baseline and candidate linter on the same source.
3. **Real APK matrix** — old, production, and alpha APKs from known versions.
4. **Failure isolation** — reproduce with linter/helper changes isolated or
   reverted before assigning causality.
5. **Runtime/control paths** — verify both modified behavior and fallback or
   negative paths where practical.

## Incident log

### Active 12.27.0-alpha.01 recon/repair session

Target APK: `apks/twitter_12.27.0-alpha.01.apk` (`com.twitter.android`, alpha;
SHA-256 `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d`).
Control APK: `apks/twitter_12.26.0-alpha.03.apk` (SHA-256
`4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e`).
Source started at commit `d6efb26d`; the repairs below are currently
uncommitted. The script was run after every rebuilt MPP; no device install was
performed.

| Patch/error | Bytecode drift | Repair/status |
| --- | --- | --- |
| `NewX: Custom font` | Compose paragraph intrinsics moved from `ui/text/platform/d` to `ui/text/c`; constructor gained a boolean. | Widened to the stable `ui/text` scope, constructor role/return filters, arity `6..7`; applied. Detailed incident: `incidents/2026-09-13-customfont-12-27.md`. |
| `NewX: Custom sharing domain` | Share-sheet status-prefix branch now reaches the shared builder through a `goto`; old layout fell through directly. | Follow one resolved `goto` while retaining static-builder, register-consumption, and result checks; applied. |
| `NewX: Customize drawer items` | Drawer content moved from string-bearing `drawer/o` to `drawer/a`; generated forwarding lambdas call the row renderer with a pre-resolved title. | Resolve the content renderer by semantic parameter shape; ignore forwarding calls without a local resource lookup and require resources across the aggregate call set; applied. |
| `NewX: Disable automatic timeline refresh` | `requestType`/`event` Kotlin null-check strings disappeared; URT implementation moved `v.i`/`z.i` to the same semantic roles with changed obfuscated owners. | Resolve the request handler by timeline enum/item signature and the event handler by `AUTO_REFRESH` plus repository request flow; applied. |
| `NewX: Disable video player scrolling` | Compose Pager ABI changed (18 params with page-size `Float` → 17 params with layout-direction object); R8 holder moved from `com/google/android/play/core/appupdate/b` to `com/google/firebase/b`. | Resolve both ABI shapes under the Google holder scope using the `Vertical` orientation enum; applied. |
| `NewX: Dynamic color` | Palette lazy caches changed from `LazyKt.b(Function0)` static wrapping to direct `kotlin/d.<init>(Function0)`; factory moved from `compose/theme/c` to `compose/core/f1`. The old per-entry inline-action Compose renderer was also removed. | Direct-lazy factory flow is repaired and applies. **Current failure:** `patchInlineActionTints` still targets the removed per-entry renderer; the 12.27 action-state/composable path needs a separate capability adapter. |

With `--continue-on-error`, the same target produced this additional failure
catalog. These are recorded before repair so the catalog does not hide failures
behind the first abort:

| Patch | Observed failure | Resolution & Status |
| --- | --- | --- |
| `NewX: Filter For You by topic` | `requestType` null-check string disappeared from the request builder. | Removed stale string anchor; stable `FOR_YOU` enum read + resolved HomeTimeline constructor applies. |
| `NewX: Hide AI-generated posts` | No free register for the setting read at index 0. | Relocated setting read to safe insertion point with available register; applies. |
| `NewX: Hide Discover more` | No free register for the setting read at index 0. | Relocated setting read to safe insertion point with available register; applies. |
| `NewX: Hide Spaces bar` | Zero semantic renderer matches. | Resolver accepts 12.27 renderer extra callback and no longer requires removed compiler null-check strings; applies. |
| `NewX: Hide compose button` | Fingerprint match failure (`onClick` stripped). | Replaced with Compose modifier-to-`AnimatedVisibility` data flow tracing; applies. |
| `NewX: Hide post dividers` | Thread-connector fingerprint match failure (`$this$threadConnector` stripped). | Replaced with canvas draw call structure and thread collection traversal; applies. |
| `NewX: Hide post reply bar` | Zero minimal reply-bar composition callers (`inlineComposer` stripped). | Ordered 8-parameter Compose ABI resolver and dynamic photo-viewer resolver; applies. |
| `NewX: Inline download button` | Dependency could not resolve Coil memory-cache getter. | Adapted to direct provider return class, hoisted icon size `iget`, and 4-bit scratch registers; applies. |
| `NewX: Open canonical URLs` | Semantic URL entity constructor unresolved; inlined URL picker. | Shape adapter for extracted vs inlined URL picker; wire serializer property indexing for `UrlEntity`; applies. |
| `NewX: Restore timeline position` | Timeline-position restore registers could not be allocated. | Structurally reserved 5 scratch registers from original count before cloning; handles high register moves via `/from16`; applies. |
| `NewX: Set default reply sorting` | Zero Compose reply-sorting initializer matches (`rankingMode` stripped). | Removed `rankingMode`, dynamic ranking enum resolution (`ze`/`af`), package-independent `Function0` shape resolution; applies. |

All 40 patches now apply cleanly in standard `./patch-twitter.sh` runs producing a valid, signed APK.


The first five failures are not all Compose UI changes: drawer, sharing, and
URT are product/generated-code refactors exposed by the same release. The
video and dynamic-color failures are genuine Compose/compiler/library-shape
drift. This is evidence for a capability resolver bridge, but not for routing
all failures through one Compose-only adapter.

Each real failure or reported bug gets a file under this directory, for
example:

```text
docs/newx-resolver-linter/incidents/2026-09-10-drawer-footer.md
```

Use this template:

```markdown
# Short incident title

- Date:
- Reporter/session:
- APK package/version/build type:
- APK path and checksum:
- Source commit:
- MPP path/checksum:
- Extension artifact paths/checksums:
- Command:
- Failing patch:
- Severity:
- Confidence: confirmed / likely / unknown

## Symptom

## Complete error

## Reproduction

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [ ] resolver logic
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior
- [ ] unrelated

## Isolation comparison

Describe the result with the linter/helper change reverted or bypassed, without
using stale artifacts. Record what changed and what did not.

## Fix and validation

## Fixture or test added

## Lessons
```

## Metrics to maintain

For each benchmark entry, record:

- baseline and candidate commits;
- fixture corpus revision and safe/unsafe fixture counts;
- findings by rule;
- known unsafe cases, detected unsafe cases, false positives, and false
  negatives;
- lint, test, compile, and patch runtime;
- APK versions tested and patch success/failure;
- incidents prevented, incidents caused, and incidents unrelated;
- time to diagnose and fix failures.

Finding count alone is not a success metric. A lower count can mean missed
unsafe cases; a higher count can mean false positives. The desired result is
better unsafe-case detection with no unacceptable false-positive or fallback
regressions.

## Artifact rules

- Keep generated reports in `build/reports/` unless deliberately promoted.
- Keep committed fixtures, incident notes, and benchmark summaries under this
  directory or the linked test source.
- Record checksums or exact paths for APKs, MPPs, and extensions when an
  artifact affects the result.
- Never mix outputs from different source commits, versions, or toolchains.
