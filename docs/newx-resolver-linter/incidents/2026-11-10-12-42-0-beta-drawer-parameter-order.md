# 12.42.0 beta: drawer footer parameter reordering

- Date: 2026-11-10
- Package: `com.twitter.android`, `12.42.0-beta.02`, version code `312420102`, ARM64 APKM.
- Input: `../../../../artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`.
- Input SHA-256: `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Initial source: `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e` with the existing generated Chinese locale overlay.
- Initial MPP: freshly built `patches/build/libs/patches-3.11.0-dev.1.mpp`, SHA-256 `aa54e066033a3dc2985a4d54f759058aa32f7b8503379a9dcdee02d1bf0538e5`.
- Extension: `extensions/newx/build/outputs/apk/debug/newx-debug.apk`, SHA-256 `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`.
- CLI: `../../../../artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar`.
- Failing patch: `NewX: Customize drawer items`. Four patches applied before this required patch failed; no partial artifact was installed.
- Complete initial report/log: `../../../../artifacts/x-update-20261110/initial-drawer-failure.{json,log}`.

## Reproduction

Build the current checkout, then run `patch-twitter.sh` with the exact input above:

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g' :patches:build :extensions:newx:testDebugUnitTest :extensions:newx:assembleDebug :patches:lintNewxResolvers --rerun-tasks --no-daemon
$env:PATCHER_MAX_HEAP_MB='3072'
$env:MORPHE_CLI_JAR='D:/Backup/Desktop/Android/artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar'
$env:PATCH_OUTPUT_APK='D:/Backup/Desktop/Android/artifacts/x-update-20261110/x-piko-localized-unsigned.apk'
& 'C:\Program Files\Git\bin\bash.exe' ./patch-twitter.sh ../artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm --unsigned -r ../artifacts/x-update-20261110/patch-result.json
```

Use process-scoped Android SDK and GitHub build credentials as described by the local build setup. Never print the credential.

## Complete patch error

```text
app.morphe.patcher.patch.PatchException: Expected one NewX drawer content lambda for Lcom/x/main/drawer/a;->r(Ljava/lang/String;Lcom/x/icons/b;Lkotlin/jvm/functions/Function0;Landroidx/compose/ui/Modifier;ZLandroidx/compose/runtime/internal/g;Landroidx/compose/runtime/internal/g;Landroidx/compose/runtime/Composer;II)V, found 0:
    at app.crimera.patches.newx.misc.drawer.CustomizeDrawerPatchKt.resolveDrawerFooterTarget(CustomizeDrawerPatch.kt:816)
    at app.crimera.patches.newx.misc.drawer.CustomizeDrawerPatchKt.customizeNewXDrawerPatch$lambda$0$0(CustomizeDrawerPatch.kt:1467)
    at app.morphe.patcher.patch.Patch.execute(Patch.kt:141)
    at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:292)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:125)
    at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:151)
    at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
    at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
    at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
    at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
    at app.morphe.engine.PatchEngine$patch$3.invokeSuspend(PatchEngine.kt:491)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
    at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
    at kotlinx.coroutines.internal.LimitedDispatcher$Worker.run(LimitedDispatcher.kt:124)
    at kotlinx.coroutines.scheduling.TaskImpl.run(Tasks.kt:89)
    at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:586)
    at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:798)
    at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:717)
    at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:704)
```

## Evidence and cause

Cause category: resolver logic assumed a fixed parameter order in otherwise unchanged drawer behavior.
No linter or cardinality-helper implementation was changed.

Scoped disassembly of `classes6.dex` shows the main `Function3.invoke` in `Lcom/x/main/drawer/l;`
still calls the native divider `a.p(material3, Function1, Composer, I)`. The settings row immediately
following that section receives `icons/a.za`, the localized title, and the native click callback.
Its renderer is now:

```text
Lcom/x/main/drawer/a;->q(I,I,Composer,ComposableLambda,Modifier,Icon,String,Function0)V
```

The older footer renderer contract was `(String,Icon,Function0,Modifier,ComposableLambda,Composer,I,I)V`.
The fixed footer fingerprint therefore returned zero. The fallback menu renderer `a.r` still matched,
but `findDrawerFooterCalls` rejected the real footer because `isDrawerRowRenderer` required a fixed
prefix and two trailing Ints. The settings icon reaches argument 5 (`v12`), title argument 6 (`v13`),
and click argument 7 (`v14`) in the exact new APK. Composer is argument 2 (`v9`).

## Repair and verification

The footer is discovered in the already anchored drawer call graph: native divider, same renderer
owner, unique title/icon/click/Modifier/Composer contract, and exactly one call whose icon argument
comes from the resolved settings icon. Parameter presence replaces position and arity assumptions;
all accepted arguments are single-register values. Multiple footer renderers or settings-icon calls
continue to fail with candidate evidence. The resolved method receives the existing item guard once.
The hook emitter derives title/icon/click register indices from that exact descriptor and retains
the native auxiliary values and original branch targets. No version routing or runtime reflection
was added.

`NewXResolverLinterTest` contains the real divider-to-footer resolver fixture with old/reordered
parameters plus missing-divider, wrong-owner and missing/ambiguous-domain-parameter controls.
Before repair, the reordered case failed at its resolved-parameters assertion (77 fixtures, one
failure); `drawer-red.log` records this. Build, full-patch regression results and final DEX inspection
are recorded in `../../../../artifacts/x-update-20261110/` as validation completes.

Runtime acceptance of settings entry, drawer editing and ordinary use remains with the user;
the agent does not launch or navigate the device UI.

## Complete frozen errors

### initial-drawer-failure.json

```text
app.morphe.patcher.patch.PatchException: Expected one NewX drawer content lambda for Lcom/x/main/drawer/a;->r(Ljava/lang/String;Lcom/x/icons/b;Lkotlin/jvm/functions/Function0;Landroidx/compose/ui/Modifier;ZLandroidx/compose/runtime/internal/g;Landroidx/compose/runtime/internal/g;Landroidx/compose/runtime/Composer;II)V, found 0:

	at app.crimera.patches.newx.misc.drawer.CustomizeDrawerPatchKt.resolveDrawerFooterTarget(CustomizeDrawerPatch.kt:816)

	at app.crimera.patches.newx.misc.drawer.CustomizeDrawerPatchKt.customizeNewXDrawerPatch$lambda$0$0(CustomizeDrawerPatch.kt:1467)

	at app.morphe.patcher.patch.Patch.execute(Patch.kt:141)

	at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:292)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:125)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:151)

	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)

	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)

	at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)

	at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)

	at app.morphe.engine.PatchEngine$patch$3.invokeSuspend(PatchEngine.kt:491)

	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)

	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)

	at kotlinx.coroutines.internal.LimitedDispatcher$Worker.run(LimitedDispatcher.kt:124)

	at kotlinx.coroutines.scheduling.TaskImpl.run(Tasks.kt:89)

	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:586)

	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:798)

	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:717)

	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:704)
```

## Final integration verification

The fresh localized integration bundle SHA-256 is
`f04bb8cac9854d04703ef996281f38262c1669edbbf1b2bf7ffb76a3960f6ffb`.
The same unchanged bundle applied all 49 selected patches, with zero failures or exclusions,
to `12.42.0-beta.02`, `12.41.0-prod.01`, and `12.40.0-alpha.01` through `patch-twitter.sh`.
The final signed beta APK SHA-256 is `4afa7e48a2b7fc7a80e6e901cb85e18296bd9993bb4e41c86a92d818cf2454d3`.

The localized checkout passed 114 patch-side tests, 282 extension tests, resolver lint,
and extension assembly. All 627 English/Chinese resource pairs matched the catalog;
signature, ZIP CRC and 16-KiB alignment checks passed. Final DEX inspection checked
116 host methods and 328 extension callsites for register bounds, branch targets,
invoke operands and CFG reachability, with no errors. The relevant emitted hook was
also inspected in a scoped instruction dump. Interactive UI/feature acceptance remains
pending with the user; package installation is not evidence of runtime behavior.
