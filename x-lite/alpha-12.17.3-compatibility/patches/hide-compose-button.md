# Hide compose button

## Status

**Runtime-tested and working on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/timeline/HideNewPostButtonPatch.kt`

## Breakage and root cause

The release-specific renderer symbols changed between the reference release and the alpha:

- 12.14.0: `Lcom/x/ui/common/g0;->a(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V`
- 12.17.3-alpha.01: `Lcom/x/ui/common/n;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V`

The owner and method names are R8-obfuscated implementation details. The alpha compatibility entry was previously only a documentation placeholder; no runtime failure was recorded. A fingerprint tied to either release-specific symbol would break when the renderer moved from `g0->a` to `n->c`.

## Fix

The fingerprint uses only the stable renderer contract:

- parameters: `I`, `Landroidx/compose/runtime/Composer;`, `Landroidx/compose/ui/Modifier;`, and `Lkotlin/jvm/functions/Function0;`;
- return type: `V`;
- a top-level class directly under `Lcom/x/ui/common/`, avoiding the obfuscated owner name;
- ordered `onClick` and `isVisible()Z` instruction anchors;
- an exact-one match assertion via `PatchException`.

No release-specific model descriptor is present in the extension boundary. The Compose and Kotlin function types are preserved public ABI types, so they remain exact rather than being weakened to `L`.

## Mutation

The alpha target method is:

```text
Lcom/x/ui/common/n;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V
```

When `xlite.timeline.hide_new_post_button` is enabled, the settings helper injects this guard at instruction index 0, before the original Compose renderer body:

```smali
const-string v0, "xlite.timeline.hide_new_post_button"
invoke-static {v0}, Lapp/morphe/extension/xlite/settings/SettingsRegistry;->getBoolean(Ljava/lang/String;)Z
move-result v0
if-eqz v0, :cond_0
return-void

:cond_0
```

The final DEX contains this guard at `com/x/ui/common/n.smali` method `c`; the branch falls through to the original renderer when the setting is disabled.

## Verification

- Target package/version: `com.twitter.android` 12.17.3-alpha.01
- Target APK: `~/Downloads/twitter_12.17.3-alpha.01.apk`
- Target SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256 at exclusive patch execution: `0f2f86609c8dde568fc450e5711baba31d63fa41658ae0fd8ad82b577270f003`
- Patched APK: `analysis/twitter-12.17.3-alpha.01/builds/twitter_12.17.3-alpha.01_hide-compose-button-current.apk`
- Patched APK SHA-256: `e48ef4e963080e88731d9472b4d66bd42c9f943322049b184f00a8336e5a8e51`
- Build: `bash .gradle-gh.sh ./gradlew :patches:build --no-daemon` passed.
- Exclusive patch run: applied successfully with exactly one matched renderer.
- Final DEX inspection: reachable settings-gated `return-void` confirmed at the target method entry point.
- Runtime: user installed and confirmed the compose button toggle works on the alpha device.
