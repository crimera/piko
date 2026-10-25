# Runtime optimization resolver failure and restore performance attribution

## Reported performance concern

On 2026-10-26 the user reported that people were attributing scrolling performance
loss to timeline restoration. No particular external APK/patch revision or crash
stack was supplied. The preceding device baseline compared installed Piko12.33
with official/current12.37.2 and did not isolate restoration. Its old/current
HWUI medians were 2.51%/2.69% jank and 19/20.5 ms P95; versions/content/features
and temperature differ. This report is not proof that restoration caused the gap.

Source audit plus exact12.37.2/12.36.1 DEX traces identified lifecycle/paging save
producers, not a proven per-pixel save observer. Separately, a Success-state Compose
SideEffect retries post-load restore while its applied flag is false; the old
extension bridge resolves account/settings/key information even without a pending
custom anchor. The audit is preserved locally under
`build/reports/newx-restore-perf/recon.md`.

The working change adds an empty-pending-map fast path, preserving original native
restore fallback, and a cheap scope/setting/account gate before native anchor
construction. Native map updates and extension account tracking remain intact.
Classification: reported runtime/performance behavior, attribution unconfirmed.
No linter/cardinality-helper behavior change is involved.

## Actual patch failure during typed-media implementation

- APK: `com.twitter.android`,12.37.2-prod.01, versionCode312372001, production,
  arm64-v8a APKM:
  `apks/com.twitter.android_12.37.2-prod.01-312372001_1arch_2dpi_24lang_40077bd4311e4f25683ffac3caf0472b_apkmirror.com.apkm`.
- Source base: `bda61e6b` (rebased optimization commit) plus the uncommitted typed-media resolver/emitter and
  restore guards. This failure predates the GIF accessor-chain correction.
- Built actual MPP: `patches/build/libs/patches-3.11.0-dev.1.mpp`.
  SHA-256 `63982deb017132c13e4369b0dfb3fecc8cb553b3d3ba6bc343d4ee425b66ab87`.
- Embedded `extensions/newx.mpe` SHA-256:
  `f4f4e65021fc03dec6f672be37109a42b8020dc0b6bc756a6c928c50f22204f5`.
- Patcher: script's `../piko/morphe-desktop-1.19.0-all.jar`,2 GiB heap.
- Command: `./patch-twitter.sh apks/com.twitter.android_12.37.2-prod.01-312372001_1arch_2dpi_24lang_40077bd4311e4f25683ffac3caf0472b_apkmirror.com.apkm`.
- Failing patch: `NewX: Inline download button`.
- No new final output APK was saved or installed. The old Downloads artifact must
  not be treated as this attempt's result. Full log/MPP/provenance are under
  `build/reports/newx-runtime-optimization/20261026-034923/patch-failure-1/`.

Cause: the newly added application resolver asked the shared `fieldForToStringLabel`
helper to follow a packed builder's terminal `, variants=` argument. In12.37.2
`p4.toString` passes that tag as the final argument to a helper returning a
StringBuilder, then reads the actual variants field and appends it later. The
shared helper cannot connect this shape and correctly reports zero candidates.
The new resolver strategy was unsuitable for that shape. This is not a linter
regression or a cardinality-helper regression, nor evidence of the reported
runtime performance issue. No shared helper/linter code changed. Prior source
`9e518d8b` patched this exact APK successfully without requesting this new lookup;
that baseline command/output is in the preceding device benchmark evidence.
Confidence: high from the exact failing call stack and native instructions;
not inferred from elapsed patch time.

Correction: resolve the video's variants field normally, then follow the linked
GIF/video variants accessor/interface capability. Resolve actual member references
from field-read/return data flow and shared signature; never use observed obfuscated
names, raw first-match selection, or catch-and-skip. Variant contentType uses a
separately validated two-String/boxed-bitrate toString schema because its terminal
packed label has the same helper limitation.

Regression coverage is in `InlineMediaPredicatePatchTest`: the emitted predicate
receiver must be initialized from the input before typed field reads; loop/result
pairing and false exits are checked, and ambiguous/changed field schemas fail.
The GIF producer-chain case must exercise the real field-selection entry point.
An uninitialized scratch receiver was also caught in source review before any
APK emission and corrected with an argument-to-work-register move. It was not
shipped and did not fail on the user's phone.

## Complete patch error

```text
ERROR: FAILED: NewX: Inline download button
INFO: Patching took 18.7 seconds
ERROR: Patching aborted: Patching failed: Patch "NewX: Inline download button" failed: NewX Expected exactly one model field for ', variants=' in Lcom/x/models/p4;->toString()Ljava/lang/String;, found 0: []
ERROR: app.morphe.patcher.patch.PatchException: NewX Expected exactly one model field for ', variants=' in Lcom/x/models/p4;->toString()Ljava/lang/String;, found 0: []
	at app.crimera.patches.newx.models.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:193)
	at app.crimera.patches.newx.models.PostModelsKt.resolveNewXInlineMediaModels(PostModels.kt:811)
	at app.crimera.patches.newx.models.PostModelsKt.access$resolveNewXInlineMediaModels(PostModels.kt:1)
	at app.crimera.patches.newx.models.PostModelResolutionState.compositionMediaModels(PostModels.kt:224)
	at app.crimera.patches.newx.models.PostModelsKt.resolvedNewXInlineMediaModels(PostModels.kt:318)
	at app.crimera.patches.newx.misc.inlineactions.InlineDownloadButtonPatchKt.newXInlineDownloadButtonPatch$lambda$0$1(InlineDownloadButtonPatch.kt:266)
	at app.morphe.patcher.patch.Patch.execute(Patch.kt:141)
	at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:292)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:122)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:147)
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
Caused by: app.morphe.patcher.patch.PatchException: Expected exactly one model field for ', variants=' in Lcom/x/models/p4;->toString()Ljava/lang/String;, found 0: []
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.requireSingleToStringField(SemanticIntrospection.kt:110)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:101)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:42)
	at app.crimera.patches.newx.models.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:56)
	... 22 more

INFO: Use --continue-on-error to skip failed patches and continue patching
```

## Follow-up validation

Build, exact old/new APK results, final DEX checks, and same-version device controls
will be recorded with the runtime optimization report. The original baseline APK
is retained separately for comparison.

## Second attempt: framework collection admission

After the GIF accessor correction, the exact same command/APK reached the next
new guard and failed: the app resolver requested assignability to `java.lang.Iterable`.
The shared helper walks direct DEX interfaces and superclasses, with explicit
framework support for List/Set/Map; it cannot infer the framework List-to-Iterable
parent relation when framework definitions are absent from DEX. Native
`kotlinx/collections/immutable/b` directly implements `java.util.List`, as the
exact APK proves. The correction validates the actual variants List contract via
the existing helper and then safely emits Iterable iteration. No descriptor
equality, version routing, shared helper fork, or swallowed exception is used.

Classification: new resolver admission assumption exceeded existing helper
capability, not a linter/cardinality behavior change or restore runtime issue.
MPP SHA-256 `6ab66c8b33e72c787e9fc9b72bcb86e4e7e1af8492a53e651b2d2a8504fba085`. Embedded extension source unchanged from first attempt.
Full MPP/log/provenance: `build/reports/newx-runtime-optimization/20261026-034923/patch-failure-2/`.
No new final APK was saved or installed. The emitted fixture uses List-typed
variant references and checks Iterable dispatch; exact old/new APK patch fixtures
cover the resolver admission and generated branch contract.

```text
ERROR: FAILED: NewX: Inline download button
INFO: Patching took 19.8 seconds
ERROR: Patching aborted: Patching failed: Patch "NewX: Inline download button" failed: NewX inline GIF variants are not Iterable: Lcom/x/models/p4;->d:Lkotlinx/collections/immutable/b;
ERROR: app.morphe.patcher.patch.PatchException: NewX inline GIF variants are not Iterable: Lcom/x/models/p4;->d:Lkotlinx/collections/immutable/b;
	at app.crimera.patches.newx.models.PostModelsKt.resolveNewXInlineMediaModels(PostModels.kt:841)
	at app.crimera.patches.newx.models.PostModelsKt.access$resolveNewXInlineMediaModels(PostModels.kt:1)
	at app.crimera.patches.newx.models.PostModelResolutionState.compositionMediaModels(PostModels.kt:224)
	at app.crimera.patches.newx.models.PostModelsKt.resolvedNewXInlineMediaModels(PostModels.kt:318)
	at app.crimera.patches.newx.misc.inlineactions.InlineDownloadButtonPatchKt.newXInlineDownloadButtonPatch$lambda$0$1(InlineDownloadButtonPatch.kt:266)
	at app.morphe.patcher.patch.Patch.execute(Patch.kt:141)
	at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:292)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:122)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:147)
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

INFO: Use --continue-on-error to skip failed patches and continue patching
```

Provenance correction: the current checkout is `bda61e6b`, a rebase of the earlier
`9e518d8b` optimization commit onto the upstream Clone fix. Their only tree
difference is `ClonePatch.kt`; Clone is not selected by these benchmark commands.
The saved baseline APK remains the exact earlier `9e518d8b` artifact.
