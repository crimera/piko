# NewX 12.29 alpha new-post renderer Compose ABI lowering

- Date: 2026-09-24
- Reporter/session: user report (`SEVERE: FAILED: NewX: Hide compose button`); pi repair session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `/Volumes/realme/Dev/piko-x-lite/apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Source commit: `d4880c32aa68aa89858f016d6cc38a70753f701b` plus this working tree
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `264a639e533c6b5bb1660c5e4be3f731c33d6be910a9c3dcfe114a8a92ddaa88`
- Extension artifact path/checksum: `extensions/newx/build/morphe/extensions/newx.mpe` / `7d0304cc9c2a77d833a0b35bc0138129c9d12b498074962eee5e8107d02e39a3`
- Output artifact path/checksum: `/Users/steven/Downloads/piko-twitter-patched.apk` / `5067c61994043d36e3b311130809067a68314e4b4be0429934fef3f7666e2bb3`
- Commands: `./patch-twitter.sh` (fails on the next patch), `./patch-twitter.sh --continue-on-error`; focused patch runs against 12.27.0-prod.01, 12.28.0-prod.01, 12.28.0-alpha.01, 12.28.0-alpha.04
- Failing patch: `NewX: Hide compose button`
- Severity: medium
- Confidence: likely; patch-time behavior and emitted bytecode confirmed, not device-tested

## Symptom

Patching aborted before any later patch could run:

```
SEVERE: FAILED: NewX: Hide compose button
app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: app.crimera.patches.newx.timeline.NewXNewPostButtonCandidateFingerprint@3cb20d
        at app.morphe.patcher.Fingerprint.patchException(Fingerprint.kt:664)
        at app.crimera.patches.utils.ScopedFingerprintMatchingKt.scopedMatchAll(ScopedFingerprintMatching.kt:142)
        at app.crimera.patches.newx.timeline.HideNewPostButtonPatchKt.hideNewPostButtonPatch$lambda$0$0(HideNewPostButtonPatch.kt:188)
        at app.morphe.patcher.patch.Patch.execute(Patch.kt:126)
```

## Reproduction

12.27/12.28 keep the renderer on `Lcom/x/ui/common/k;`:

```
Lcom/x/ui/common/k;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V
```

`dexscope dry-run` over the `Lcom/x/ui/common/` package with the legacy four-parameter shape
returns `MATCH_UNIQUE 1` for 12.27.0-prod.01, 12.28.0-prod.01, 12.28.0-alpha.01, and
12.28.0-alpha.04, but `MATCH_AMBIGUOUS 3` for 12.29 because the renderer left the four-parameter
shape. The three remaining four-parameter methods (`shimmer/f.h`, `text/k.f`, `text/k.g`) do not
call `isVisible()`.

12.29 moved the renderer to `Lcom/x/ui/common/m;` and lowered its Compose ABI to content-last
ordering with the two changed/default bitmasks:

```
Lcom/x/ui/common/m;->c(Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V
```

`registers: total=14 params=5`, so `p0 (v9)` is the `Modifier` parameter. `dexscope anchors
--method 'Lcom/x/ui/common/a2;->isVisible()Z'` lists this method at `0x60` as the only
five-parameter caller; the four-parameter `Lcom/x/list/recommended/a;->a(...)` lookalike still
calls the same interface at `0x39`. The interface itself was repackaged (`m1`/`i1`/`n1` →
`a2`, implemented by `Lcom/x/ui/common/k;`), but the no-argument `isVisible()Z` contract and the
`Landroidx/compose/animation/p;->j(...)` animated-visibility call are unchanged.

The provenance shape also changed. The renderer conditionally overwrites its `Modifier`
parameter slot with the Compose companion default before aliasing it into the animated-visibility
argument:

```
and-int/lit8 v0, p4, 0x2
if-eqz v0, :cond_5
sget-object p0, Landroidx/compose/ui/p;->a   # overwrites the v9 Modifier parameter
:cond_5
move-object v1, p0
invoke-static/range {v0 .. v8}, Landroidx/compose/animation/p;->j(...)V
```

The previous linear provenance walk stopped at the conditional `sget-object` and rejected the
true renderer. The recommended-list lookalike is still distinguished because it never aliases
the parameter and passes a companion-created modifier (`sget-object v1, ...`) instead.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [x] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior

## Fix

- Added `NewXNewPostButtonComposeFlagCandidateFingerprint` for the 12.29
  `(Modifier, Function0, Composer, I, I)V` shape and kept
  `NewXNewPostButtonCandidateFingerprint` for the legacy `(I, Composer, Modifier, Function0)V`
  shape. Both reuse one visibility filter and one custom predicate, and the two candidate lists
  are unioned under the shared `requireExactlyOne("NewX new-post button renderer", ...)` mutation
  (`returnVoidIfEnabled(method, 0)`), so there is no version routing or hardcoded owner.
- `isNewPostButtonRendererCandidate` now derives the `Modifier` parameter index from the method's
  own parameter list instead of a fixed index, so it applies to both shapes.
- `originatesFromObjectParameter` returns as soon as the tracked register reaches the `Modifier`
  parameter register, so a conditional default assignment to that slot no longer defeats
  provenance. The change only makes the resolver more permissive for the true renderer; the
  lookalike is still rejected because its animated-visibility argument is written by
  `sget-object`, not by an object move from the parameter.
- No obfuscated owner, method name, or fixed instruction offset is encoded.

## Validation

- `./gradlew :patches:test :patches:build :patches:lintNewxResolvers --no-daemon` (pass)
- Added the `new-post resolver survives 12-29 compose-flag ABI and conditional default modifier`
  fixture to `NewXResolverLinterTest.kt` (five-parameter renderer true, companion-modifier
  lookalike false).
- `./patch-twitter.sh` — `Applied: NewX: Hide compose button`; the run then aborts on the
  separate, pre-existing `NewX: Hide post dividers` failure (see that patch's own incident).
- `./patch-twitter.sh --continue-on-error` — `Applied: NewX: Hide compose button`,
  `Saved to /Users/steven/Downloads/piko-twitter-patched.apk`.
- Focused patch runs (`-e "NewX: Hide compose button"`) on 12.27.0-prod.01, 12.28.0-prod.01,
  12.28.0-alpha.01, and 12.28.0-alpha.04 — all `Applied`, all `Saved to`.
- `dexscope inspect-method` on each patched APK confirms the guard
  (`newx.timeline.hide_new_post_button` + `return-void`) is injected at the renderer entry:
  `Lcom/x/ui/common/k;->c(...)` on 12.27/12.28 and `Lcom/x/ui/common/m;->c(...)` on 12.29.

## Known limits

- 12.29 is not added to `Constants.COMPATIBILITY_NEW_X`; the CLI run used `--force` /
  `--continue-on-error`.
- Other NewX patches still fail on 12.29 (`Hide post dividers`, `Set default profile post
  sorting`); those are tracked separately.
- Runtime behavior (button hidden with the setting on, visible with it off) was not
  device-tested.
