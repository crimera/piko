# 12.42 beta: canonical card callback moved to another merged owner

## Frozen failure

- APKM: `com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`.
- APKM SHA-256: `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Base APK SHA-256: `03ca41bcbeb755e396a814ea0e008004f04829c91d9be37ffa4b625351bf7a59`.
- Version: `com.twitter.android` / `12.42.0-beta.02` / `312420102`.
- Source baseline: `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e`, with the drawer/gallery repairs in the working tree.
- Failing MPP SHA-256: `b961560c3328a6af39b448cbf367d97f8b959ead058fe577c0a9ea08866bf594`.
- Extension SHA-256: `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`.
- CLI: `morphe-desktop-1.19.1-all.jar`; wrapper: `./patch-twitter.sh EXACT_APKM --unsigned -r patch-result.json`.
- Failure: `NewX: Open canonical URLs`, `Expected exactly one inlined card navigation callback, found 0: []`.
- Category: resolver logic depended on a merged callback's assigned package. This is not a cardinality-helper or linter failure.

## Exact bytecode

The old scope was `Landroidx/compose/animation/core/`. On 12.41 the callback was
`Landroidx/compose/animation/core/a;->f(Object)Object`; on 12.42 it is
`Lcom/x/urt/items/post/h5;->invoke(Object)Object`. Its card URL arm retains the same contract:

| Instruction index, old / new | Operation |
| --- | --- |
| 125 / 456 | `instance-of` the formatter-resolved card URL action |
| 126 / 457 | `if-eqz` skips the card arm |
| 128 / 458 | cast the checked receiver; the old shape first copies it |
| 129 / 459 | read the card's String URL |
| 130 / 460 | native navigation helper consumes that String |
| 132 / 462 | read the contextual post's ID source |
| 133 / 463 | call `getId()` |

The new skip target is instruction 501, past the card path. Its navigation hook is at
code-unit offset 941, outside both try regions `[33,37)` and `[42,50)`. No incoming branch targets
the insertion point. The URL/post registers are v0/v9; a four-bit scratch register is available.

The card type test has six occurrences in each exact APK. A straight-line guarded URL/navigation/ID
contract isolates one site on each release; other card reads either lack `getId()` or overwrite the URL.
The package of an R8 merged callback is therefore discarded as an anchor. Navigation arity and the
unrelated downstream post-push signature are also unnecessary for this proof.

## Repair and regression

Resolve candidates from the card action type test. Prove its guarded cast/read, unchanged URL passed
to navigation, and contextual post read feeding `getId()`. Derive the consumed registers and hook at
the native navigation call. Keep typed emission, setting-off behavior and fail-closed cardinality.

`NewXResolverLinterTest` exercises the actual resolver with the old receiver-copy shape and the new
owner/direct-cast shape, plus missing ID, overwritten URL, reversed guard, wrong post and ambiguous
anchor negatives. `canonical-red.log` freezes the owner-scope failure before its removal.

Final build, three-target patch reports and DEX inspection are recorded with the integration artifacts.
Interactive behavior remains for user acceptance; no device UI was launched by the agent.

## Complete frozen errors

### diagnostic-only-result.json

```text
app.morphe.patcher.patch.PatchException: Expected exactly one inlined card navigation callback, found 0: []

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)

	at app.crimera.patches.newx.misc.canonicalurls.CanonicalUrlsPatchKt.patchCardNavigation(CanonicalUrlsPatch.kt:1042)

	at app.crimera.patches.newx.misc.canonicalurls.CanonicalUrlsPatchKt.newXCanonicalUrlsPatch$lambda$0$0(CanonicalUrlsPatch.kt:171)

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
