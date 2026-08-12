# Hide new posts pill

## Status

**Runtime-tested and working on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/timeline/HideNewPostPillPatch.kt`

## Breakage

The alpha moved the renderer to `com.x.urt.instructions.a` and obfuscated both app-model parameter types. The old fingerprint required the removed `TimelineShowAlert` descriptor, so it matched zero methods.

## Fix

- Wildcarded the two release-specific object parameters with Morphe's `L` descriptor wildcard.
- Scoped the renderer by its stable Compose ABI parameters and the semantic `ntp` test-tag string.
- Kept the existing exact-one match assertion and early `return-void` mutation.

The alpha target method is:

```text
com.x.urt.instructions.a->a(Lcom/x/models/timelines/g0;Lcom/x/ui/common/j;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V
```

The injected instructions execute before the original renderer body:

```smali
const-string v4, "xlite.timeline.hide_new_post_pill"
invoke-static {v4}, Lapp/morphe/extension/xlite/settings/SettingsRegistry;->getBoolean(Ljava/lang/String;)Z
move-result v4
if-eqz v4, :cond_0
return-void
```

## Verification

- Target APK: `~/Downloads/twitter_12.17.3-alpha.01.apk`
- Target SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256: `23b0dda1a7b362f6efc6118bbadfc439eff748cf2b9efbab8a57448e3e12981d`
- Patched APK: `/tmp/xlite-hide-new-pill-alpha.apk`
- Patched APK SHA-256: `26cf7e80ba1e93eeab91644a72b0a23467f5643731d027ebdc968505c2cdf720`
- Exclusive patch run: applied successfully with one match.
- Final DEX inspection: reachable settings-gated `return-void` at the start of the alpha renderer.
- Runtime: installed on `com.twitter.android` 12.17.3-alpha.01; user confirmed the pill is hidden.

## Resilience check: 12.15.1

The fingerprint also matched the older 12.15.1 renderer during an isolated bytecode test. The temporary compatibility target was removed afterward; the centralized X-Lite compatibility declaration remains alpha-only.

- Target APK SHA-256: `84a56de8f88486bfcb797c418bb971850205affc1ad8927556626d976278bf58`
- Temporary test MPP SHA-256: `b348377a268e1a58a7aa8e472ed4d576ba05dd6a4469026033c391a49e497723`
- Patched APK SHA-256: `76824d7848a8a975cd8399d5f5723eef5e3b1282248bcb6dd59df7f2a22afe21`
- Patch result: applied successfully with one match.
- Matched renderer: `com.x.urt.instructions.t->a(Lcom/x/models/timelines/URTTimelineInstruction$ShowInstructions$TimelineShowAlert;Lcom/x/ui/common/x;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V`
- Final DEX inspection: settings-gated `return-void` is reachable at the start of the renderer.
- Runtime testing on 12.15.1: not performed.
