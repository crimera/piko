# 12.42.0-beta.02 gallery content captures the timeline type

## Frozen artifacts

- Package/version: `com.twitter.android`, `12.42.0-beta.02` / `312420102`, beta.
- Input: `artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`.
- Input SHA-256: `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Base source: `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e`, with the local drawer repair from the adjacent incident.
- First failing MPP: `b7d66aee82f156d6d2fb007b1b216b2aac62df46616593088d6868974e4be495`.
- Second failing MPP: `239b6ac8b9f802762549d5c07056efbfdedb0639ce2b4c347fde9daaf647fc95` (body matcher repaired; callback matcher still duplicated the obsolete constraint).
- Extension: `extensions/newx/build/outputs/apk/debug/newx-debug.apk`, SHA-256 `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`.
- CLI: `artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar`.
- Wrapper: `PATCHER_MAX_HEAP_MB=3072 MORPHE_CLI_JAR=<above> PATCH_OUTPUT_APK=artifacts/x-update-20261110/x-piko-localized-unsigned.apk ./patch-twitter.sh <input-above> --unsigned -r artifacts/x-update-20261110/patch-result.json`.
- Failing patch: `NewX: Gallery profile Photos tab`. Complete failures are retained in `gallery-failure.json` / `.log` and `gallery-state-failure.json` / `.log` in the artifact directory.

## Bytecode evidence and cause

`classes7.dex`, `Lcom/x/urt/ui/g0;->e(...)V` still has the required immutable list, timeline enum, Modifier and Composer, and ends in `(Composer,I)`. Its list/paging content lambda is `Lcom/x/urt/ui/d0;`: allocation at instruction 491, constructor at 510. The constructor now captures `Lcom/x/models/timelines/u;` as well as Function3, list, paging and Function1 values. It does not capture Modifier. The full composable restart lambda still captures Modifier and remains excluded.

In scoped smali `inspect/timeline-smali/com/x/urt/ui/g0.smali`, the captured timeline argument is `v10` from `p2`; constructor arguments occupy `v5..v21` at line 3120. The constructed object is checked/cast to Function1 and passed to the lazy timeline host `g0.c` at line 3157. The two Function1 capture slots still carry item renderer and item click in native order. The latter is `v16`, copied from renderer parameter `p13`; callback resolution must continue tracing this value instead of taking the renderer's first Function1.

Both the body fingerprint and callback-state resolver independently rejected any captured timeline enum. Repairing only the fingerprint moved the failure to the duplicated check. This is APK contract drift and resolver logic, with high confidence from exact bytecode and focused red tests; cardinality helpers and linter behavior are unchanged.

## Repair and regression

Share the list/paging content-constructor contract between body discovery and callback-state discovery. Permit the auxiliary timeline capture, preserve the Modifier exclusion, unique list requirement, callback requirements and fail-closed candidate selection. Keep dynamically traced item callback and existing typed hook emission. There is no version-based behavior branch.

`NewXResolverLinterTest` reproduces both entry points: old/new timeline captures, restart-lambda rejection, missing list/callback rejection, and callback tracing to renderer parameter 2 despite a preceding item-renderer callback. `gallery-red.log` records the body test failing before repair. `gallery-state-red.log` records the callback entry failing before the shared-contract repair.

Final full-patch, DEX and installation validation is recorded in the deployment notes and provenance under `artifacts/x-update-20261110/`. Interactive acceptance remains with the user; no target UI was opened by the agent.

## Complete frozen errors

### gallery-failure.json

```text
app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: app.crimera.patches.newx.misc.mediatab.NewXTimelineBodyFingerprint@12a6e53c

	at app.morphe.patcher.Fingerprint.patchException(Fingerprint.kt:1407)

	at app.crimera.patches.common.ScopedFingerprintMatchingKt.scopedMatchAll(ScopedFingerprintMatching.kt:121)

	at app.crimera.patches.newx.misc.mediatab.ProfilePhotosGalleryPatchKt.newXProfilePhotosGalleryPatch$lambda$0$1(ProfilePhotosGalleryPatch.kt:1741)

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

### gallery-state-failure.json

```text
app.morphe.patcher.patch.PatchException: Expected exactly one NewX timeline state class, found 0: []

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)

	at app.crimera.patches.newx.misc.mediatab.ProfilePhotosGalleryPatchKt.resolveTimelineItemCallbackIndex(ProfilePhotosGalleryPatch.kt:823)

	at app.crimera.patches.newx.misc.mediatab.ProfilePhotosGalleryPatchKt.newXProfilePhotosGalleryPatch$lambda$0$1(ProfilePhotosGalleryPatch.kt:1748)

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
