# Timeline resume-position factory inlined on 12.36.1 production

## Provenance

- Source: `c3c98b3634ce552879fb4c54dc5baead24be1b9e` plus working-tree changes in
  `RestoreTimelinePositionPatch.kt`, `InlineResumePositionFactory.kt`, and
  `NewXResolverLinterTest.kt`.
- APK: `apks/12.36.1-prod.01.apk`, package `com.twitter.android`,
  version `12.36.1-prod.01`, production.
- Fresh MPP: `patches/build/libs/patches-3.11.0-dev.1.mpp`, built with
  `./gradlew :patches:build --no-daemon` before reproduction and testing.
- Extensions: current checkout's Gradle outputs, no substituted extension APK.
- Composite library commits: piko-patches-library
  `5c992e00b12f9ee819e29423a2ca7ee2eb16887b`, morphe-bytecode
  `b8ac605cd968c31adbafb9eb83686afaf2527033`. No library code changed.
- CLI: script default `../piko/morphe-desktop-1.19.0-all.jar`.
- Reproduction: `./patch-twitter.sh ./apks/12.36.1-prod.01.apk`.
- Failing patch: `NewX: Restore timeline position`.

## Complete reproduction error

```text
ERROR: Patching aborted: Patching failed: Patch "NewX: Restore timeline position" failed: Expected exactly one NewX timeline resume-position factory call, found 0: []
ERROR: app.morphe.patcher.patch.PatchException: Expected exactly one NewX timeline resume-position factory call, found 0: []
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)
	at app.crimera.patches.newx.timeline.RestoreTimelinePositionPatchKt.restoreTimelinePositionPatch$lambda$0$0(RestoreTimelinePositionPatch.kt:969)
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

## Classification and bytecode evidence

APK/R8 contract drift: the standalone static factory is inlined into the save-event
method. Not a linter or cardinality-helper failure. Baseline before these changes
reproduces the failure; helper/linter versions are unchanged.

12.35 has a standalone factory:
`mlkit_vision_text_common/pf.g(immutable-list, ArrayList, holder, Instant)Resume`.
12.36.1's `com/x/urt/b0.l(v1)V` instead contains:

- `0x178`: clock `now()Instant`, result `v19`.
- `0x17c`: start of inlined factory, validates the Instant.
- `0x17f/0x181`: read holder offset/index.
- `0x183..0x278`: original row filtering, timeline-item mapping, anchor selection,
  sort-index extraction, item-index lookup and visible-item count.
- `0x27a..0x280`: construct `com/x/urt/features/o`.
- `0x281`: alias successful result into `v11`.
- `0x282`: null/success join, before writing the native resume store.

Names and offsets are evidence only, not production anchors.

## Adapter

Keep the existing standalone-factory path and its validations. Only when that call
is absent, require one resume-model constructor and one clock value reaching its
Instant argument. Copy the closed factory region into a static synthetic method on
the same component. The save-anchor bridge calls that method through the same
four-argument contract as before. Do not reimplement X's selection algorithm.

The copied region must have no overlapping try block, switch, unsupported instruction,
escaping branch or fallthrough. CFG live-input analysis binds the item list and
visible rows to preceding field reads, the holder to the save event's holder register,
and time to the proven clock result. Every remaining live input must be a local
whose last write before entry is literal zero (null/index seed), with no external
branch bypassing that definition. Switch-bearing source methods fail closed.
Unexpected or ambiguous shapes throw PatchException.

The 12.36 factory's inputs are `v4,v1,v2,v19` plus zero seeds `v11,v5`.
Generated method has 20 locals plus four parameters. Typed prologue moves arguments
into original registers and initializes the two seeds; all original branch offsets
and invoke/result pairs remain intact. The original join becomes `return-object v11`.

Focused fixture runs the extraction entry point, checks constructor/return emission
and the null-branch target, preserves self-read operands (`iget v2,v2`), accepts
`move/from16` and plain `move`, and rejects escaping branches, nonzero seeds and
branches bypassing the seed. The first development iteration
rejected `MOVE_FROM16` because it normalized dexlib's display name incorrectly;
using the enum spelling fixed it. A subsequent overly conservative seed check
rejected transient scratch writes from earlier patches even though X overwrote
them with zero before factory entry (`NewX inline resume-position factory has
unbound inputs: [11, 5]`). Requiring the last zero definition to dominate entry
fixes this without trusting an arbitrary earlier constant. The fixture includes
an earlier nonzero write overwritten by the required zero. Both development
failures were adapter logic, not linter behavior.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`: passed.
- NewX resolver fixture suite: 53 tests, zero failures/errors.
- `./patch-twitter.sh apks/12.35.0-prod.01.apk`: all 46 selected patches applied,
  saved successfully. Copy: `/tmp/piko-inline-12.35.apk`.
- `./patch-twitter.sh apks/12.34.0-prod.01.apk`: all 46 selected patches applied,
  saved successfully. Copy: `/tmp/piko-inline-12.34.apk`.
- `./patch-twitter.sh apks/12.36.1-prod.01.apk`: all 46 selected patches applied,
  including Restore timeline position and Set default reply sorting, saved to
  `/Users/steven/Downloads/piko-twitter-patched.apk`. No disabled patches or
  continue-on-error. Production artifact restored after regression runs.
- Final DEX: generated factory is called once by `piko$newxSaveAnchor`.
  Inspected parameter moves, zero seeds, original loops/branches, constructor and
  common nullable return. Sound liveness at generated method index 0 reports only
  parameter registers `v20..v23` live; no uninitialized local input.
- `dexscope verify-method` is INCONCLUSIVE, not acceptance: reference invoke,
  field and return assignability are not modeled. It reports no definite verifier
  error. Device verification remains pending.
- No installation, device interaction or runtime acceptance. User asked to check
  Latest/ranked Following restore and disabled-setting control paths.

## Separate alpha regression failure (not fixed here)

Target `apks/12.33.0-alpha.02.apk`, package `com.twitter.android`, alpha.
Same source, MPP and extension provenance; command:
`./patch-twitter.sh apks/12.33.0-alpha.02.apk`.

The pre-existing resume-model string fingerprint fails before the new adapter.
No artifact was saved. This is resolver/model-anchor drift, not evidence against
the inline adapter or linter; no reverted-baseline alpha run was performed, so the
historical attribution is limited to execution order. This alpha remains unsupported
by this test run; do not describe the matrix as all-green.

```text
ERROR: Patching aborted: Patching failed: Patch "NewX: Restore timeline position" failed: Failed to match the fingerprint: Fingerprint(definingClass=Lcom/x/urt/, name=toString, returnType=Ljava/lang/String;, parameters=[], strings=[TimelineResumePosition(entryId=])
ERROR: app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: Fingerprint(definingClass=Lcom/x/urt/, name=toString, returnType=Ljava/lang/String;, parameters=[], strings=[TimelineResumePosition(entryId=])
	at app.morphe.patcher.Fingerprint.patchException(Fingerprint.kt:1407)
	at app.crimera.patches.common.ScopedFingerprintMatchingKt.scopedMatchAll(ScopedFingerprintMatching.kt:121)
	at app.crimera.patches.newx.timeline.RestoreTimelinePositionPatchKt.restoreTimelinePositionPatch$lambda$0$0(RestoreTimelinePositionPatch.kt:929)
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
