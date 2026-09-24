# NewX 12.29 immersive photo viewer navigation spacer

- Date: 2026-09-24
- Reporter/session: user report (reply bar hidden → action bar overlaps the gesture nav pill)
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh --continue-on-error`, plus exclusive `NewX: Hide post reply bar` probes
- Failing patch: `NewX: Hide post reply bar` (runtime layout regression; no patch-time abort)
- Severity: medium (action bar overlaps the gesture navigation region)
- Cause: resolver logic — false negative on a new immersive viewer shape
- Confidence: high on patch/bytecode; runtime layout not exercised by the agent

## Symptom

With `newx.post_actions_media.hide_post_reply_bar` enabled, the full-screen immersive photo
viewer hides the reply composer but its inline action bar drops into the gesture navigation
region. With the setting disabled the composer occupies that space and the layout is correct.

## Root cause

`HidePostReplyBarPatch` injects a branch that selects the media controls' existing
"no composer" fallback, a spacer sized from the navigation-bar inset. Its
`isPhotoViewerControlsRenderer` predicate used two hardcoded parameter shapes (10 and 11 params)
that only matched the legacy `Lcom/x/media/q1;->a(...)`.

12.29 added a second, immersive viewer:
`Lcom/x/media/e0;->a(...)` (23 params, including `Lcom/x/ui/immersive/chrome/v;`). It has the same
structure but a different arity, so the predicate skipped it and the fallback branch was never
injected. Hiding the reply bar therefore removed the only bottom inset source on that screen.

Relevant `media/e0->a` bytecode (original):

```smali
[03d9] (500) invoke-static/range {v6 .. v25}, Landroidx/media3/common/audio/g;->g(...)V   # inline action bar
...
[0533] (671) if-eqz p19, :cond_62        # composer gate
[0535] (672) if-eqz p4, :cond_62
...
[05a2] (730) invoke-static/range {v5 .. v12}, Lcom/x/composer/minimal/b;->h(...)V        # composer
...
:cond_62
[05b5] (739) invoke-static v14, Landroidx/compose/foundation/layout/w2;->f(Composer)c4
[05b9] (741) iget-object v2, v2, c4;->n:y3
[05bb] (742) invoke-static v3, v2, Landroidx/compose/foundation/layout/f;->Q(Modifier, b4)Modifier
[05bf] (744) invoke-static v14, v2, Landroidx/compose/foundation/layout/f;->e(Composer, Modifier)V   # nav spacer
```

## Fix

- `isPhotoViewerControlsRenderer` now matches semantically: static `void`, an inline-action-bar
  parameter, a minimal-composer parameter, a Haze parameter, at least one `Composer`, exactly one
  inline-action-bar call, and exactly one navigation-inset call. The hardcoded 10/11-param shapes
  are removed.
- `resolvePhotoViewerNavigationFallbackHook` became
  `resolvePhotoViewerNavigationFallbackHooks`, which collects every `com/x/media/` caller of the
  resolved composer container that satisfies the predicate and derives one fallback hook per
  caller. `execute` applies `branchIfEnabled` to each.
- Cardinality per hook is still `requireExactlyOne`, and an empty caller set fails closed.

Patched 12.29 `media/e0->a` gate:

```smali
[0533] (671) const-string v0, "newx.post_actions_media.hide_post_reply_bar"
[0535] (672) invoke-static v0, Lapp/.../SettingsRegistry;->getBooleanOrDefault(Ljava/lang/String;)Z
[0538] (673) move-result v0
[0539] (674) if-nez v0, :cond_62     # enabled -> existing navigation-bar spacer
[053b] (675) if-eqz p19, :cond_62
```

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- Exclusive `NewX: Hide post reply bar` on 12.29 injects the hook into both
  `Lcom/x/media/e0;->a` (immersive) and `Lcom/x/media/q1;->a` (legacy);
  `dexscope verify-method` on `media/e0->a` exits 0 with no errors.
- Full `./patch-twitter.sh --continue-on-error` on 12.29, 12.28.0-prod.01, and 12.27.0-prod.01 —
  zero failures and `Saved to` on each. On 12.27/12.28 the broadened predicate still resolves only
  the single legacy media controls method (`media/j1->a` / `media/h1->a`); the immersive viewer
  shape does not exist there.
- Runtime layout was not exercised by the agent; the user is asked to confirm on device.
