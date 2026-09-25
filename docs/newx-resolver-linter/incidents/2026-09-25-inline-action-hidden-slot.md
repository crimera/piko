# 12.29 hidden inline action keeps a slot when the entry is retained

- Date: 2026-09-25
- APK package/version: `com.twitter.android` `12.29.0-alpha.04` (primary), `12.28.0-prod.01` (comparison)
- APK paths: `apks/12.29.0-alpha.04.apk`, `apks/12.28.0-prod.01.apk`
- Source: `x-lite` checkout at `e44507aa` (patch), `inline-actions-spread` worktree at `be49f9cb` (experiment)
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- Extension: `extensions/newx` from the same checkout (`InlineActionFilter`, `InlineDownloadButton`)
- Command: `./patch-twitter.sh apks/12.29.0-alpha.04.apk` (default output `~/Downloads/piko-twitter-patched.apk`)
- Failing patch: `NewX: Customize inline actions` (applies cleanly; runtime visual defect, not a patch-time error)
- Severity: medium
- Cause: tooling/artifact setup — the report screenshot matches a build of the reverted
  retained-entry experiment; the shipped removal path cannot produce that slot. Underlying layout
  behaviour is in the app, not in the resolver, linter, or cardinality helpers.

## Symptom

With `newx.content.hidden_inline_actions` containing `ViewCount`, the report describes "a big gap
in the removed inline item, which is the views item" and the trailing icon-only group
(`bookmark`, `share`, download) drifting to the right edge.

## Evidence: which list shape produced the screenshot

`Lcom/x/inlineactionbar/l;->a(Landroidx/compose/ui/layout/e1;Ljava/util/List;J)Landroidx/compose/ui/layout/d1;`
(12.29 packed-slot measure; `Lcom/x/inlineactionbar/i;->a(...)` on 12.28) places one child per
entry left-to-right and advances the cursor by `slotWidth(child) + iMax`, where
`iMax = max(minGap, min((available - Σ slotWidths) / (visibleChildren - 1), 35dp))` and each
child's `slotWidth` comes from its kind. The kind travels as parent data
`Lcom/x/inlineactionbar/d1(hasContent, isViewCount, kind)`, built per entry in
`Lcom/x/inlineactionbar/c;->invoke(...)` from the entry's action type and count.
So a child that is *kept but drawn empty* still consumes both its slot and one gap.

Measured icon-glyph starts in the three reporter screenshots:

| Shot | File | Icon starts (px) | Interpretation |
| --- | --- | --- | --- |
| Before | `172617.png` | 176, 408, 646, 885, 1003, 1142 | 6 children, all drawn (reply, retweet, like, bookmark, share, download) |
| Before | `172625.png` | 176, 439, 708, 978, 1143 | 5 children, all drawn (no download on a media-less post) |
| Report | `172666.png` | 176, 385, 600, 1028, 1139 | 6 children: the 4th draws nothing |

The "before" shots show the removal signature: they come from the same install (75 s apart), and
`172625.png` has one child fewer than `172617.png` (no injected download action on a media-less
post), so its leading deltas are *larger* (263/269/270px vs 232/238/239px) with no localised hole
— fewer children means more leftover per gap.

The report shot is the opposite shape. Fitting it with the same per-kind slot widths gives
`iMax ≈ 35px` (leading deltas 209/215px, i.e. *packed*) plus a doubled gap after `like`
(428 ≈ 2 × 212px: one slot and two gaps that draw nothing). A five-drawn-child row predicts
≈ 264px deltas and no hole, and an unpainted `Countless` child (≈ 150px) lands ≈ 60px short of
the measured bookmark. Only **six children with the hidden entry retained** and rendered empty
fits every position: the app's slot math still counts it. That is the `inline-actions-spread`
experiment, not the shipped patch.

## Occurrence

`InlineActionFilter.filterHiddenActions` has removed hidden entries from the list since the
original X-Lite patch (`ed09499f`, and unchanged through `b1fe23a9`, `7772b6e0`, `5e657679`,
`e44507aa`). The retained-entry variant existed only in commit `3106603e` ("hide inline actions in
place"), which forced the action tint transparent in the entry renderer, and was reverted in
`be49f9cb` before the branches diverged again. The report screenshot therefore comes from a build
of that experiment: it was still the installed artifact when the shot was taken (the current
x-lite source has no code path that can retain a hidden entry — see the DEX checks below).

## Why retaining the entry cannot work

- `Lcom/x/inlineactionbar/l;->a(...)` measures one slot per entry; every kind
  (`CountedPill`, `Countless`, `IconOnly`) floors at `f0(style) + iconSize`, so a retained child
  always reserves width. Hiding its content leaves a hole and, because the child count is
  unchanged, `iMax` stays at the packed value, which pushes the trailing group right.
- Removing the entry is exactly what the app itself does for an action a post does not offer
  (`Lcom/x/inlineactionbar/r0;->d(...)` rebuilds the list from the post's supported actions and
  converts it with `Lcom/x/ui/common/post/quote/nested/c;->n(...)`). The patch hooks that
  conversion, so removal reproduces the native shape and the surviving slots redistribute.
- The 12.28 boolean-slot model (`Lcom/x/inlineactionbar/i;->a(...)`) runs the same algorithm with
  different metrics, which the surviving shapes expose as a different trailing-pair spacing. See
  "Trailing icon-only spacing is app metrics" below.

## Trailing icon-only spacing is app metrics, not the patch

Reporter screenshots of the same post with `ViewCount` hidden and no download entry, measured as
glyph-start deltas inside the action row (leading stays: `reply`, `retweet`, `like`; trailing pair:
`bookmark`, `share`):

| Screenshot | Build | Leading deltas (px) | `bookmark` → `share` (px) |
| --- | --- | --- | --- |
| `172625.png` | 12.29 patch | 263 / 269 / 270 | 165 |
| `172710.png` | 12.29 patch | 263 / 269 / 270 | 165 |
| `172617.png` | 12.29 patch, download entry (6 children) | 232 / 238 / 239 | 118 |
| `172115.png` | 12.28 patch | 296 / 303 / 288 | 90 |

The two 12.29 shots are indistinguishable (same build behaviour four hours apart), so nothing in
this session's patch changes moved the trailing spacing. Solving the 12.29 model for `172710`
gives counted slot ≈ 145px, icon-only slot ≈ 44px and gap ≈ 121px; `172115` needs counted slot
≈ 250px and gap ≈ 46px for the same row width. No single constant set satisfies both, and the
count-text widths differ by far less than 105px per slot, so the two shots cannot come from the
same app metrics.

Model difference (`dexscope diff apks/12.29.0-alpha.04.apk ~/Downloads/piko-twitter-patched.apk`
on the layout itself):

- 12.29 `Lcom/x/inlineactionbar/l;->a(...)`: one child per entry, three kinds
  (`CountedPill` / `Countless` / `IconOnly`), slot floors `f + icon`, `f - 8 + icon`, `g + icon`;
  `iMax = max(f0(h), min((avail - Σ) / (children - 1), f0(35dp)))`; the last `IconOnly` child is
  shifted right by `slot - iconSize` (`Lcom/x/inlineactionbar/j;->invoke`). Six floats from
  `androidx.media3.common.audio.g;->j(...)` (`f3 = smallestScreenWidthDp >= 376 ? 48f : 32f`,
  `f5 = spacing * 0.5f`).
- 12.28 `Lcom/x/inlineactionbar/i;->a(...)`: one `hasCount` boolean per child, counted slot
  `max(width - f0(c), f0(e) + f0(d))`, icon-only slot `(width + f0(d)) / 2`; same gap formula with
  `f0(f)` as the floor and a last-count-less-child correction. Four floats from
  `com.google.firebase.crashlytics.internal.model.v1;->h(...)` with the same two hardcoded values.
- `dexscope diff` on `l;->a(...)` and `j;->invoke(...)` between stock `12.29.0-alpha.04` and the
  patched APK reports similarity 1.0000 (delta 0 instructions); only `c;->invoke(...)` differs
  (+5 instructions: the identity-gated download kind override). The patch does not alter either
  app's bar metrics.

Consequence: on the new bar (>= `12.28.0-alpha.04`), hiding a leading action cannot tighten the
trailing pair — removing an entry removes slot width, and the freed width is redistributed into
*every* gap, so the remaining icons (including `bookmark` → `share`) necessarily spread.

### Where the tight trailing pair actually comes from

The bar implementation changed *inside* the 12.28 line, not with the patch:

| App build | Bar measure policy | Layout | Trailing pair |
| --- | --- | --- | --- |
| `12.27.0-prod.01` | `Lcom/x/inlineactionbar/f;`, 202 instr | old weighted layout | tight |
| `12.28.0-alpha.01` | `Lcom/x/inlineactionbar/f;`, 202 instr | old weighted layout | tight |
| `12.28.0-alpha.04` | `Lcom/x/inlineactionbar/g;` (194) + `i;` (393) | packed slots | spreads |
| `12.28.0-prod.01` | `g;` (194) + `i;` (393) | packed slots | spreads |
| `12.29.0-alpha.04` | `i;` (194) + `l;` (440) | packed slots (3 kinds) | spreads |

The old `f;->a(...)` splits children by a single boolean parent-data flag (`Lcom/x/inlineactionbar/v0`):
counted actions share the leftover width equally (`max((width - Σ iconOnly - (n-1)*f0(8dp)) / countedCount, 0)`),
icon-only children measure at their intrinsic size and are laid out with a fixed `f0(8dp)` step.
That is what keeps `bookmark`/`share` ~90px apart while the counted slots spread wide.

### The patch is not the variable on a fixed app build

On `12.28.0-prod.01`, the old release bundle (`40560ca7`, piko-newx v3.37.1) and the current
checkout produce identical bar code:

- `git diff 40560ca7..HEAD` for `CustomizeInlineActionsPatch.kt` and `InlineActionFilter.java`:
  no changes (the hidden-action removal is byte-identical).
- The only added inline-action code is the download kind override; `resolveInlineActionKindOverride`
  returns null for the legacy boolean kind models (`Z` / `ZZZ`) used by 12.27 / 12.28, so it is
  skipped there.
- Patched-DEX check on `12.28.0-prod.01`: no app-side caller of
  `InlineDownloadButton->isDownloadAction(...)` (only the extension's own methods), i.e. the kind
  rewrite is absent.
- `dexscope diff` between the two patched `12.28.0-prod.01` APKs (old bundle vs current bundle) on
  `Lcom/x/inlineactionbar/i;->a(...)` and on the entry renderer
  `Lcom/google/firebase/crashlytics/internal/model/v1;->g(...)`: similarity 1.0000, delta 0.

## Decision

- Hidden inline actions stay removed. No retained placeholder, no transparent-tint hide, no
  per-kind width tricks: any of those either leave a hole or change the app's slot math away from
  what the bar does for an unsupported action.
- The trailing `bookmark`/`share` spacing is the app's bar implementation. If the classic look is
  required on newer releases, it is delivered by `NewX: Classic inline action spacing`
  (`ClassicInlineActionSpacingPatch.kt`) — see below — not by changing which entries are hidden.
- Targets that already ship the classic weighted bar (`12.27.0-prod.01`, `12.28.0-alpha.01`) are
  left untouched by that patch.

## Implemented fix: classic spacing on the packed-slot bar

`NewX: Classic inline action spacing` (opt-in, `default = false`) rewrites the measure's per-child
slot list right before its fitting loop so the leftover width collapses into the counted slots:

- The measure builds `ArrayList<Integer>` slot widths per child (`max(content, floor)` for counted
  actions, a smaller constant or half-width for icon-only ones), then distributes
  `available - Σ slots` over `children - 1` gaps. Injecting a call before the fitting loop
  (`InlineActionBarSpacing.applyClassicSpacing`) rewrites every slot at or above the measure's own
  counted slot floor to an equal share of `available - iconOnlySlots - (children - 1) * gap - gap`,
  clamped to that floor. The app's own measurement, fitting loop and placement then reproduce the
  classic spacing: counted actions spread evenly, icon-only actions stay intrinsic and tight.
- `gap = max(measureMinGap, 8dp)`; the extra reserved gap is headroom for the fitting loop, which
  tests every child with its own start offset added on top of the accumulated widths.
- Anchors are derived from the method, not hardcoded: the unique `RangesKt.coerceAtLeast(II)I`
  result, the only `Math.max` before the `LinkedHashSet` allocation (the counted slot floor), the
  `Integer.valueOf`/`ArrayList.add` pair after it (the slot list), the `Math.min`/`Math.max` clamp
  after the unique `const/high16 35.0f` (the gap floor plus the density call), and the fitting loop
  head after the set allocation and `List.iterator()` seed.
- Registers: the cloned method keeps the app's parameter block above the measured locals, so the
  derived registers are read into scratch locals that sit below the parameter block. The floor
  register is reused by the measure for the accumulated width, so its value is lifted inside the
  slot loop, seeded with `-1` at the method head (the helper ignores a non-positive floor) so the
  verifier sees it initialized on every path.
- Runtime setting `newx.appearance.inline_action_native_spacing` keeps the app's packed spacing
  without repatching.

### Validation

- `:extensions:newx:test` (includes `InlineActionBarSpacingTest`: equal-share rewrite, floor clamp,
  icon-only rows untouched, uncaptured-floor sentinel) and `:patches:build` (settings aggregate +
  `lintNewxResolvers`): PASS.
- `./patch-twitter.sh`: `NewX: Classic inline action spacing` Applied on `12.29.0-alpha.04`,
  `12.28.0-prod.01` and `12.28.0-alpha.04`; on `12.27.0-prod.01` and `12.28.0-alpha.01` the patch
  applies as a no-op and their `Lcom/x/inlineactionbar/f;->a(...)` contains no injected reference.
- `dexscope verify-method` on the patched measure of `12.29.0-alpha.04` and `12.28.0-prod.01`:
  INCONCLUSIVE (reference/field assignability is not modeled), zero INVALID findings — the earlier
  invalid-register and uninitialized-register failures are gone.
- Patched artifacts: `~/Downloads/piko-twitter-patched.apk` (12.29.0-alpha.04) plus
  `…-12.29.0-alpha.04-classic.apk`, `…-12.28.0-prod.01-classic.apk`,
  `…-12.28.0-alpha.04-classic.apk`.

## Validation

- `./gradlew :patches:build --no-daemon`: PASS (this checkout).
- `./patch-twitter.sh apks/12.29.0-alpha.04.apk`: all selected patches Applied, saved to
  `~/Downloads/piko-twitter-patched.apk`.
- Patched `12.29.0-alpha.04` DEX: `Lcom/x/inlineactionbar/r0;->d(Landroidx/compose/runtime/Composer;)`
  calls `InlineActionFilter->prepareHiddenActions` / `preparePresenter` / `filter` immediately after
  the immutable-list conversion and re-converts the filtered list with the original
  `Lcom/x/ui/common/post/quote/nested/c;->n(...)` conversion, so the bar's `c1.a` state receives the
  shortened list.
- Extension tests (`InlineActionFilterTest`) assert the removal contract (`filter` returns the
  surviving entries only); the experiment had to rewrite those assertions to expect retained
  entries, which is the regression guard for this incident.
- Runtime layout validation: pending user testing. No device was controlled by the agent.

## A/B artifacts built for this report

All from this checkout (`patches-3.9.0-dev.4.mpp`), plus one from the old release worktree
(`40560ca7`, built at `/tmp/piko-v3371`):

| Artifact | App | Patch code | Expected trailing pair |
| --- | --- | --- | --- |
| `~/Downloads/piko-twitter-patched-12.28.0-alpha.01-current.apk` | 12.28.0-alpha.01 | current | tight (old weighted bar) |
| `~/Downloads/piko-twitter-patched-12.28.0-prod.01.apk` | 12.28.0-prod.01 | current | spread (packed slots) |
| `~/Downloads/piko-twitter-patched-12.28-old3371.apk` | 12.28.0-prod.01 | `40560ca7` | spread, identical to the row above |
| `~/Downloads/piko-twitter-patched.apk` | 12.29.0-alpha.04 | current | spread (packed slots, 3 kinds) |

The third row exists only to prove the patch version is not the variable; if it ever differs from
the second row, the "patch is not the variable" conclusion must be re-derived.
