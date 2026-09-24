# NewX 12.29 hide-post-reply-bar: photo-screen pill overlap root cause + timeline gap regression

- Date: 2026-09-24
- Reporter/session: user report (action bar overlaps gesture pill; blank gap below timeline inline
  action bar after commit `b46d10dc`)
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` /
  `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets: `apks/12.28.0-prod.01.apk`, `apks/12.27.0-prod.01.apk`
- Source commit: `febded09` (revert of `b46d10dc`) plus this working-tree repair
- MPP path: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh` (12.29 default), `./patch-twitter.sh apks/12.28.0-prod.01.apk
  --continue-on-error`, `./patch-twitter.sh apks/12.27.0-prod.01.apk --continue-on-error`
- Failing patch: `NewX: Hide post reply bar` (runtime layout; no patch-time abort)
- Severity: medium (gesture-pill overlap; timeline layout regression)
- Cause: resolver logic — mutation-site selection deleted an inset the app gates itself; plus a
  regression from the forced media fallback (`b46d10dc`, reverted here)
- Confidence: high on bytecode and patch output; runtime layout awaits device confirmation

## Symptoms

1. **Pill overlap (original bug, pre-existing):** with `newx.post_actions_media.hide_post_reply_bar`
   enabled, the fullscreen immersive photo screen's inline action bar overlaps the Android gesture
   navigation pill. With the setting disabled, the "Post your reply" composer supplies the bottom
   space and the layout is correct.
2. **Timeline gap (regression `b46d10dc`):** a blank gap appeared below ordinary timeline posts'
   inline action bar. User bisection: introduced by `b46d10dc`, present only with the setting
   enabled as a patch-attributable delta (a pre-existing empty area also exists with the setting
   disabled, which is stock layout behavior).

## Root cause (symptom 1)

`postdetailsheet/a->a` (12.29; `postdetailsheet/a->b` on 12.27/12.28) is the post-detail reply-bar
container. It resolves the `composer/minimal/r` model from the decompose slot state and renders
`composer/minimal/b;->h` inside a Box whose modifier conditionally applies the navigation-bar
inset:

```smali
sget-object v2, Lcom/x/ui/common/h2;->a:Landroidx/compose/runtime/x;   ; composition-local flag
invoke-static v6, v2, Landroidx/compose/runtime/m0;->l(...)Ljava/lang/Object;  ; Composer.getLocal
check-cast v2, Ljava/lang/Boolean; booleanValue
if-eqz v2, :cond_15                    ; app's own "bottom-anchored immersive" gate
  sget-object c4;->w / w2;->f(Composer)          ; WindowInsetsHolder
  iget-object v2, c4;->e                          ; = navigationBars (verified in holder <init>)
  invoke-static v3, v2, Landroidx/compose/foundation/layout/f;->s(Modifier, b4)Modifier
:cond_15
... Box(modifier) { composer/minimal/b->h(...) }
```

The gated reservation is exactly the fullscreen photo screen's gesture-area reservation. The patch
removed it twice over: `returnVoidIfEnabled(postDetailSheetContainer, 0)` deleted the Box and its
inset, and `branchIfEnabled(postDetailNavigationInsetsHook)` skipped the inset call. With the
composer hidden (`minimal/b;->h` guard) nothing remained below the action bar, so it dropped into
the pill. `minimal/b;->h` already hides the reply bar, the vertical gradient scrim, and the Haze
blur box (see `docs/newx-hide-post-reply-bar.md`), so the container-level guards were redundant for
their stated purpose and only deleted the reservation.

This also explains why the bug is specific to the 12.29 "new look" immersive photo screen and why
older photo-viewer fixes (media-controls composer→spacer fallback) were orthogonal.

## Root cause (symptom 2)

`b46d10dc` added `branchIfEnabled` at the composer gate of `com/x/media/e0;->a` (its only runtime
delta on 12.29). `e0->a` is the shared media block used by timeline media elements
(`jetfuel/v2/element/s1` → `media/q1->h` → `media/q1->i` → `media/c1` → `media/e0->b` → `media/e0->a`),
so the forced branch rendered the `:cond_62` navigation spacer (`Spacer(Modifier.a2(c4.n))`,
`c4.n` = `navigationBarsIgnoringVisibility`) mid-feed — the gap. Per the reporter it did not fix the
photo screen either, so it was net-negative and is reverted (`febded09`).

## Fix

- `git revert b46d10dc` restores the pre-existing single photo-viewer fallback hook
  (`media/q1->a`) and the exact pre-`b46d10dc` runtime on all targets.
- `HidePostReplyBarPatch` now classifies the post-detail container's inset application:
  - **GATED** (12.27/12.28/12.29 verified: composition-local read → conditional skip branch →
    straight-line `f;->s(holder.navigationBars)` apply arm → goto merge; skip arm leaves the
    modifier register untouched): no container-level guards. The app's own gate keeps reserving
    the gesture area on bottom-anchored immersive surfaces only.
  - **UNCONDITIONAL** (legacy containers): previous behavior preserved exactly
    (`branchIfEnabled` inset skip + `returnVoidIfEnabled` container).
  - Ambiguous shapes fail closed with `PatchException`.
- Regression guard: `patches/src/test/kotlin/app/crimera/patches/newx/misc/postdetails/HidePostReplyBarPatchTest.kt`
  (named failure: misclassifying GATED reintroduces the pill overlap; misclassifying UNCONDITIONAL
  reintroduces the legacy empty inset space). Not a `NewXResolverLinterTest` fixture: the failure
  class is mutation-site selection at patch time, not a resolver call-pattern the linter models.

## Validation

- `./gradlew :patches:test :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- Full runs through `./patch-twitter.sh` on 12.29.0-alpha.04, 12.28.0-prod.01, 12.27.0-prod.01 —
  `NewX: Hide post reply bar` applied and `Saved to` on each, zero failures.
- Final DEX on the patched 12.29 artifact: setting reads exist only at `MainActivity->I`,
  `composer/minimal/b->e`, `composer/minimal/b->h`, `media/q1->a` (plus settings registration);
  `postdetailsheet/a->a` contains none — the gated reservation is intact. `dexscope verify-method`
  on `postdetailsheet/a->a` exits 0.
- Runtime layout (pill overlap gone, timeline restored) awaits device confirmation from the
  reporter; the agent does not drive the device.

## Known limits / follow-ups

- 12.20–12.25 containers were not re-examined (APKs not in `./apks`); their unconditional shapes
  keep the legacy mutation by construction, but a stored-shape check is worth doing when an APK is
  available.
- `media/e0->a`'s fallback spacer reads `c4.n` (`navigationBarsIgnoringVisibility`) unlike the
  proven legacy `c4.e` (`navigationBars`). If a future report shows a media-controls-only surface
  still overlapping, revisit that fallback (and `com/x/ui/immersive/chrome/` inset ownership)
  instead of re-forcing unconditional branches.
