# 12.29 hide-reply-bar follow-up: pad the immersive-media action row

- Date: 2026-09-24
- APK: `com.twitter.android` `12.29.0-alpha.04`, `./apks/12.29.0-alpha.04.apk`
- Report: action bar still falls below/behind the gesture navigation area with
  `newx.post_actions_media.hide_post_reply_bar` enabled after the earlier inset fixes.
- Failing patch: `NewX: Hide post reply bar` (runtime layout; patch application succeeded).
- Source base: `a4c02939`, plus the uncommitted follow-up change to
  `HidePostReplyBarPatch.kt`.
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`, rebuilt from this checkout with
  `./gradlew :patches:build --no-daemon`. The NewX extension was built in the same Gradle build.
- Successful patch command: `./patch-twitter.sh ./apks/12.29.0-alpha.04.apk`
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk`; output manifest reports
  `12.29.0-alpha.04` / version code `312290204`.

## Root cause

The screenshots disproved both the app-root and post-detail-sheet diagnoses. The visible row is the
inline-action-bar call in `Lcom/x/media/e0;->a(...)`, immediately before that renderer's reply-
composer/no-composer choice. Returning from `composer/minimal/b->h` hides the composer but leaves
the action row's original modifier unchanged, so the row remains behind the gesture pill.

Forcing `e0`'s separate no-composer spacer was also wrong: it produced a gap in other media layouts
and did not move this row reliably. The renderer already resolves
`navigationBarsIgnoringVisibility` for that spacer. The fix reuses the same inset object on the
action row's own modifier before invoking the inline-action-bar renderer.

## Change

- Keep `MainActivity`, the post-detail sheet action renderer, and the immersive renderer's separate
  spacer gate untouched.
- Resolve the 12.29 immersive renderer semantically from its media, inline-action-bar,
  minimal-composer, Haze, immersive-chrome, `Modifier`, and `Composer` contracts.
- Require action-row → composer → navigation-inset call order, then derive the WindowInsets holder
  provider, `navigationBarsIgnoringVisibility` field, action-row modifier register, and Composer
  register from exact bytecode.
- When hide-reply-bar is enabled, apply the existing window-insets modifier to the row immediately
  before rendering it. No fixed dp value and no reply-editor mutation are added.

## Validation

- Focused `HidePostReplyBarPatchTest`: PASS; it verifies the emitted inset application replaces the
  action row's Modifier before the row render call.
- `./gradlew :patches:build --no-daemon`: PASS (including the project test task).
- Final 12.29 patch run applies all 43 selected patches and saves the output.
- Final DEX: `postdetailsheet/s->e` is unchanged. In `media/e0->a`, the setting check reads the
  existing `c4.n` inset, applies the existing `f.Q(Modifier, WindowInsets)` transformation to the
  action-row Modifier, and then invokes the original inline-action-bar renderer. The later composer
  and spacer branches retain their stock control flow.
- The output manifest is `12.29.0-alpha.04` / version code `312290204`; ZIP integrity check passes.
- `dexscope verify-method` is INCONCLUSIVE because reference invoke assignability is not modeled;
  it reports warnings, not INVALID.
- User runtime validation on the exact screenshot path: PASS; the action row now clears the
  navigation pill with Hide post reply bar enabled.

## Resolver/assembly failures during validation

The first resolver required the concrete inset field type to equal the modifier helper's interface
parameter, so patching failed closed with `navigation-bars field read ... found 0`. The concrete
`y3` implements the expected `b4`; the resolver now checks the foundation-layout inset contract and
register dataflow instead of descriptor equality. A subsequent probe used a non-range invoke for
Composer register `v22`, producing `Invalid register: v22`; the final injection uses
`invoke-static/range`. Both failures were isolated before the final runtime-tested artifact.

## Transient patcher failure during validation

One 12.29 full patch run aborted later in `NewX: Restore Twitter branding`, after
`NewX: Hide post reply bar` had applied. The exact exception was:

```text
SEVERE: FAILED: NewX: Restore Twitter branding
app.morphe.patcher.patch.PatchException: ZipFile invalid LOC header (bad signature)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:102)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:120)
    at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
    at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
    at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
    at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
    at app.morphe.cli.command.PatchCommand$call$3$3$1.invokeSuspend(PatchCommand.kt:794)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
    at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
    at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:100)
    at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:69)
    at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Unknown Source)
    at app.morphe.cli.command.PatchCommand.call$lambda$13$2(PatchCommand.kt:793)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:96)
Caused by: java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)
    at java.base/java.util.zip.ZipFile$ZipFileInputStream.initDataOffset(ZipFile.java:973)
    at java.base/java.util.zip.ZipFile$ZipFileInputStream.read(ZipFile.java:984)
    at java.base/java.util.zip.ZipFile$ZipFileInflaterInputStream.fill(ZipFile.java:500)
    at java.base/java.util.zip.InflaterInputStream.read(InflaterInputStream.java:164)
    at java.base/java.io.FilterInputStream.read(FilterInputStream.java:106)
    at java.base/java.io.InputStream.transferTo(InputStream.java:782)
    at java.base/java.nio.file.Files.copy(Files.java:3171)
    at app.morphe.util.ResourceUtilsKt.copyResources(ResourceUtils.kt:117)
    at app.crimera.patches.newx.misc.bringbacktwitter.RestoreTwitterBrandingPatchKt.bringBackTwitterPatch$lambda$0$0(RestoreTwitterBrandingPatch.kt:81)
    at app.morphe.patcher.patch.Patch.execute(Patch.kt:126)
    at app.morphe.patcher.patch.ResourcePatch.execute$morphe_patcher(Patch.kt:372)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:96)
```

The APK and MPP passed compressed-data checks; the failure did not reproduce on a later clean run.
The final patch run applied every selected patch and saved the version-verified output. Cause remains
unclassified/transient (tooling or archive-read state); it is not attributed to the hide-reply-bar
resolver or mutation.
