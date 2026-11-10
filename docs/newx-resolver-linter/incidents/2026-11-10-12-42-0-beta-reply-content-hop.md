# 12.42.0-beta.02 reply renderer gains a composed content hop

## Frozen target and failure

- `com.twitter.android` 12.42.0-beta.02 / 312420102, beta; input SHA-256 `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Input: `artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`.
- Source `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e` plus local drawer/gallery repairs; failing MPP SHA-256 `b961560c3328a6af39b448cbf367d97f8b959ead058fe577c0a9ea08866bf594`.
- Extension `extensions/newx/build/outputs/apk/debug/newx-debug.apk`, SHA-256 `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`.
- CLI `artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar`. Wrapper: `PATCHER_MAX_HEAP_MB=3072 MORPHE_CLI_JAR=<CLI> PATCH_OUTPUT_APK=<unsigned-output> ./patch-twitter.sh <input> --unsigned -r <report>`.
- Patch `NewX: Hide post reply bar`: `Expected exactly one NewX post-detail reply bar renderer, found 0: []`. Full stack and invocation retained in `artifacts/x-update-20261110/reply-renderer-failure.json` / `.log`; diagnostic repeat in `diagnostic-only-result.json` / `.log` was never an install candidate.

## Bytecode proof and repair

In classes6.dex the stable `post-detail-reply-text-field` tag is in minimal/e1.invoke. The instantiator is y1.i. Previously the matching String/Haze/Composer renderer directly called that instantiator. In this target y1.j allocates q1 (instruction 545), constructs it (566), and passes the exact value to Compose's internal content wrapper (568). q1 implements Function2; its erased invoke calls y1.i. The outer y1.h calls y1.j and does not allocate that content, so recursively expanding callers would make selection ambiguous.

Keep the original direct call path and admit exactly one composed Function2 hop. Require the erased callback body to call the anchored instantiator and all-path value provenance from the allocation to the Kotlin Function argument of a Compose/Composer wrapper. Scope discovery to the anchored renderer candidates and minimal-composer allocations. Unused, overwritten and ambiguous callbacks fail closed. Container resolution and typed hooks remain shared. No version or obfuscated name is used to select behavior.

`NewXResolverLinterTest` exercises the real caller resolver with direct and composed callbacks plus unused, clobbered and ambiguous negatives. `reply-red.log` records the expected failure before the repair; `premium-red-reply-green.log` and `premium-green.log` show the reply fixture passing afterwards. Final new/old/alpha patch results, DEX validation and installation records are in the artifact directory. UI acceptance remains pending user interaction.

Cause: APK contract drift and resolver logic; high confidence from exact call/value flow. No linter or cardinality-helper change.

## Complete frozen errors

### diagnostic-only-result.json

```text
app.morphe.patcher.patch.PatchException: Expected exactly one NewX post-detail reply bar renderer, found 0: []

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)

	at app.crimera.patches.newx.misc.postdetails.HidePostReplyBarPatchKt.resolvePostDetailReplyBarRenderer(HidePostReplyBarPatch.kt:433)

	at app.crimera.patches.newx.misc.postdetails.HidePostReplyBarPatchKt.newXHidePostReplyBarPatch$lambda$0$0(HidePostReplyBarPatch.kt:1342)

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
