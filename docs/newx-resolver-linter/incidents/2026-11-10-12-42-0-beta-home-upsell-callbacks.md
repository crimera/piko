# 12.42.0-beta.02 home header drops a Function0 callback

## Frozen target and failure

- `com.twitter.android` 12.42.0-beta.02 / 312420102, beta; APKM SHA-256 `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1` in `artifacts/x-update-20261110/downloads/`.
- Source `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e` plus local drawer/gallery repairs; MPP SHA-256 `b961560c3328a6af39b448cbf367d97f8b959ead058fe577c0a9ea08866bf594`.
- Extension `extensions/newx/build/outputs/apk/debug/newx-debug.apk`, SHA-256 `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`.
- CLI `artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar`; command: `PATCHER_MAX_HEAP_MB=3072 MORPHE_CLI_JAR=<CLI> PATCH_OUTPUT_APK=<diagnostic-output> ./patch-twitter.sh <input> --unsigned --continue-on-error -r artifacts/x-update-20261110/diagnostic-only-result.json`. The diagnostic package is not an install candidate. Final runs omit continue-on-error.
- `NewX: Hide premium upsell`: `Expected one NewX home-nav upsell checker, found 0:`. Complete stack retained in `diagnostic-only-result.json` and `diagnostic-only-patch.log` in the artifact directory.

## Bytecode proof and repair

The `scaffold_home_tabbed` owner still contains the home header with `home_navigation_bar_title`. Old 12.41 header has four Function0 callbacks; new header has three. Both take a unique home state outside home/tabbed and use it as a nullable chip input. New y1-independent home/tabbed/a.i has state parameter index 4 (v25): instruction 256 branches on null, instruction 261 copies state to v5, instruction 264 passes it to a.c(state,Function0,Modifier,Composer,I)V. The null arm skips that native chip call. Native c renders the home/e premium chip. The old state index was 5; fixed arity or offsets are unsuitable.

Remove the arbitrary callback minimum. Retain the all-Function0 discriminator, anchored title, required Compose inputs and unique home state. Prove the parameter's value reaches both a null gate and exactly one native tabbed Compose call; the non-null arm reaches that call and the null arm cannot, including exception edges. Derive the state register from descriptor widths. Existing typed setting read nulls this same parameter, using the native skip branch.

`NewXResolverLinterTest` exercises old four/new three callbacks and missing title, missing null gate and wrong callback-family negatives through the actual state resolver. `premium-red-reply-green.log` is the observed failure before repair; `premium-green.log` passes afterwards. Full patch and final DEX evidence is retained with the deployment artifacts. Runtime UI acceptance remains pending user interaction.

Cause: APK contract drift and positional/cardinality assumption in resolver logic, not linter or helper behavior.

## Complete frozen errors

### diagnostic-only-result.json

```text
app.morphe.patcher.patch.PatchException: Expected one NewX home-nav upsell checker, found 0:

	at app.crimera.patches.newx.premium.HidePremiumUpsellPatchKt.hidePremiumUpsellPatch$lambda$0$0(HidePremiumUpsellPatch.kt:251)

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
