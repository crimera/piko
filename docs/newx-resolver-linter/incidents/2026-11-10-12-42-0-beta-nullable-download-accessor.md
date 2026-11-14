# 12.42.0-beta.02 native download redirect rejects the nullable-narrowed video-tab post accessor

- Date: 2026-11-10
- APK: `com.twitter.android` `12.42.0-beta.02` / `312420102`, beta, from
  `artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`,
  SHA-256 `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Diagnosis checkout: `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e` plus the local drawer repair.
  Fix branch base: `86fb221b` (`fix/newx-12.42-resolvers`).
- Failing run: `artifacts/x-update-20261110/diagnostic-only-result.json` /
  `diagnostic-only-patch.log`, 45 applied and 5 failed. The other four failures
  (`Hide post reply bar`, `Hide premium upsell`, `Open canonical URLs`, unnamed post-options icon
  assignment) are separate incidents and out of scope here.
- Tooling: Morphe CLI 1.19.1, patcher heap 3072 MB, run through `./patch-twitter.sh` on
  `5758c6ea` plus the drawer and gallery repairs. The diagnostic MPP SHA-256 is
  `b961560c3328a6af39b448cbf367d97f8b959ead058fe577c0a9ea08866bf594`.
- Bytecode source of truth: standalone dexlib2 from
  `artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar` over that exact APK. Evidence:
  `inspect/download-h-extract.txt` (handler `i0.h`, 1570 instructions), `inspect/vtab-y-dump.txt`
  (wrapper class dump), `inspect/BranchProbe.java` (code-unit/branch arithmetic),
  `inspect/ReadGate.java` (container-read gate replay), `inspect/ProvScan.java` (provenance
  replay), `inspect/FindAccessor.java` (accessor scan, also used on `inspect/old-base.apk`,
  `12.41.0-prod.01` / `312410001`).
- Comparison baseline: `artifacts/x-update-20261110/old-target-result.json` — 49 applied patches,
  `failedPatches: []`, including `NewX: Redirect downloads to chosen folder`.

## Exact failure

```
app.morphe.patcher.patch.PatchException: Expected exactly one NewX video-tab post source on
watermarked download path in Lcom/x/video/tab/i0;->h(Lcom/x/video/tab/y6;)V, found 0: []
  at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
  at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabPostTuple(NativeDownloadRedirectPatch.kt:600)
  at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabPost(NativeDownloadRedirectPatch.kt:477)
  at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabHooks(NativeDownloadRedirectPatch.kt:405)
  at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.newXNativeDownloadRedirectPatch$lambda$0$1(NativeDownloadRedirectPatch.kt:169)
```

`found 0` with an empty candidate list, so nothing was rejected for ambiguity: one gate dropped the
only real path rather than the resolver finding competitors.

## Bytecode evidence and cause

### 1. The watermarked download arm is unchanged

`i0.h` (REGISTERS 201, P0 = v199, TRIES 0). Branch targets were proved from code-unit offsets:

```
537 IF_EQZ   v3 -> 553     ; feature flag off, skip download
538 IGET_OBJECT v3,v1 i0->a0:Lcom/x/video/tab/y;
539 IF_EQZ   v3 -> 543     ; wrapper null -> null post
540 INVOKE_VIRTUAL y->a()Lcom/x/models/s1;
541 MOVE_RESULT_OBJECT v3
542 GOTO     -> 544
543 CONST_4  v3 #0          ; null arm
544 INVOKE_STATIC u9->c(Lcom/x/media/download/f;Lcom/x/models/s1;Lcom/x/models/c5;)Lcom/x/media/download/e;
545 MOVE_RESULT_OBJECT v3
546 IF_EQZ   v3 -> 553      ; metadata build failed
547-551   dispatch args + invoke d->c(...)
```

Merge is `i544`, null arm is `i543` (`const/4 #0` into the same `v3` the metadata call reads at
argument index 1), producer arm is exactly `i540` + `i541`. `v1` still holds `this`, and `a0` is a
non-static `i0` field, so the container read is a valid handler-field read.

### 2. The wrapper accessor now returns through a nullable diamond

`Lcom/x/video/tab/y;` (REGISTERS 2, P0 = v1, TRIES 0, 13 code units):

```
@ 0  IGET_OBJECT  v1,v1 y->b:Lcom/x/models/timelines/items/k1;
@ 2  IGET_OBJECT  v1,v1 k1->a:Lcom/x/models/q6;
@ 4  INSTANCE_OF  v0,v1 Lcom/x/models/s1;
@ 6  IF_EQZ       v0 -> @11
@ 8  CHECK_CAST   v1 Lcom/x/models/s1;
@10  RETURN_OBJECT v1
@11  CONST_4      v1 #0
@12  RETURN_OBJECT v1
```

That is the lowering of `(post.postResult as? s1)`. `y.toString` emits
`"ProgressEvent(event="` + `a` + `", post="` + `b`, so `b` is the wrapper post field; `k1`
stringifies as `UrtTimelinePost(postResult=…)`, so `k1->a` is `postResult`; `s1` implements
`q6`/`w5`, so it is a narrowing of the stored `q6`. The value contract is identical to 12.41 — the
accessor returns `wrapper.post.postResult` or null — but the return shape gained a type test, two
exits and a null constant.

The metadata call's second parameter is `Ls1;` while `postResultField.type` is `Lq6;`: the
accessor now performs the narrowing the caller used to perform. The call still takes `(f, s1, c5)`
with the post at index 1 in `v3`, so this is not a parameter reorder and not extra indirection.
The handler still calls `y->a()` directly on `this.a0` with a single receiver.

### 3. The 12.41 accessor was the straight variant of the same chain

`old-base.apk`, `Lcom/x/video/tab/u;->a()Lcom/x/models/s1;` (REGISTERS 1, TRIES 0, 7 code units):

```
@0 IGET_OBJECT  v0,v0 u->b:...j1;
@2 IGET_OBJECT  v0,v0 j1->a:Lcom/x/models/q6;
@4 CHECK_CAST   v0 Lcom/x/models/s1;
@6 RETURN_OBJECT v0
```

Only obfuscated names moved (`u`→`y`, `j1`→`k1`). This shape passes the predicate as written,
which is why 12.41 still applies and why the failure is a return-shape change rather than a
contract change.

### 4. Gate audit on the real method

| # | gate | real 12.42 result |
| --- | --- | --- |
| 1 | implementation present, no try blocks, no switch payloads | pass (`TRIES 0`) |
| 2 | `branchTargetIndices()` resolves every branch | pass (table above) |
| 3 | container read is a non-static handler field read | pass, 11 candidates before the dispatch |
| 4 | payload type = dispatch param 2 (`Lcom/x/media/download/e;`) | pass |
| 5 | metadata call candidates before the dispatch | pass, exactly one (`i544`) |
| 6 | wrapper `toString` carries `ProgressEvent(event=` + `, post=` | pass, yields `y->b` |
| 7 | `isCanonicalPostAccessor` for `y->a()` | **FAIL — the only failing gate** |
| 8a | `resolveVideoTabPostPaths`: merge/goto/null-arm layout | would pass (`i544` / `i542` / `i543`) |
| 8b | guard `i539` is `IF_EQZ` to the null arm | would pass |
| 8c | source-read-to-guard straight line | would pass (empty window) |
| 8d | `isCanonicalPostProducer` | would pass except for the empty accessor list |
| 8e | `isIsolatedNullableMerge` over `i539..i551` | would pass (only `i539→i543`, `i542→i544`) |
| 8f | `provesParameterValue(538, v3, p0)` | proven |
| 8g | `provesValueFromInstruction(545, v3, 551, v3)` | proven |

Gate 7 rejects the accessor at its first filter: the allowed opcode set is the object-move set plus
`IGET_OBJECT`, `CHECK_CAST`, `RETURN_OBJECT`, so `INSTANCE_OF`, `IF_EQZ` and `CONST_4` fail
closed. Even if they passed, `returns.size != 1` would reject the second `return-object`. With no
canonical accessor, `canonicalAccessors` is empty, `isCanonicalPostProducer` fails at
`accessors.none { it.toString() == callReference.toString() }`, no path is added, and
`requireExactlyOne` reports `found 0`.

`inspect/ReadGate.java` replayed the guard gates for every container read: only `i538` reaches
guard `i539` with a straight window, and only `i544` returns the metadata type. Exactly one tuple
survives the repair, so there is no new `found 2` risk.

Cause category: APK contract drift (compiler lowering of an existing nullable cast) plus resolver
logic. High confidence — every gate was reproduced individually on the frozen APK and only gate 7
fails; the linter and cardinality helpers are unchanged and not implicated.

## Repair and regression

`isCanonicalPostAccessor` accepts the nullable shape alongside the straight one; the straight
path keeps its original proof untouched, so 12.41 behavior cannot change. The nullable branch:

- selects on the presence of `INSTANCE_OF` and rejects try blocks in that shape;
- uses an opcode whitelist that adds only `INSTANCE_OF`, `IF_EQZ`, `CONST_4`;
- keeps the existing `fieldReads` equality pinning the accessor to exactly `wrapper.post` then
  `postResult`, and the existing wrapper-read/value-reaches proofs;
- delegates to `provesNullablePostAccessor`, which requires one type test on the post value with
  test type = `canonicalPostType`, its `IF_EQZ` at `testIndex+1` consuming the test register,
  `CHECK_CAST` at `guardIndex+1` and `RETURN_OBJECT` at `castIndex+1` fed by the cast, the null
  arm as the guard target at `successReturnIndex+1` and last-but-one instruction, `CONST_4 #0`
  into the null arm's own return register, and exactly two `RETURN_OBJECT` exits;
- `branchTargetIndices` gained a `label` parameter (default keeps the handler call site
  unchanged) so accessor failures name their own method.

The arm-order check carries
`// newx-resolver-lint: allow instruction-order because the compiler-emitted arm order is the contract.`
above the `INSTANCE_OF` selection. Both selections use `filter` + `size != 1` + `single()`, which
the resolver linter recognizes as a cardinality proof; `:patches:lintNewxResolvers` reports no
finding in this file.

Regression runs through the real `resolveVideoTabPostTuple` entry point. The `VideoTabShape`
corpus moved to shared test support (`VideoTabPostFixture.kt`, internal) so
`InlineMediaPredicatePatchTest` and `NewXResolverLinterTest` exercise one corpus instead of a
second hand-rolled handler fixture. New cases: `NARROWING` with and without `postResultCast` and
with unrelated handler reads must resolve; `ZERO_ARM_NONZERO`, `WRONG_TEST_TYPE` and
`NULL_ARM_OTHER_REGISTER` must each fail at their own check.

- `artifacts/x-update-20261110/download-red.log` — before the repair the accept test fails with the
  incident's exact error on the fixture (`found 0: []`), all other cases unchanged.
- `artifacts/x-update-20261110/download-green.log` — after the repair
  `InlineMediaPredicatePatchTest` 15/15 and `NewXResolverLinterTest` 77/77, 0 failures.
- `artifacts/x-update-20261110/download-refactor.log` — same two suites green after the fixture
  extraction, confirming the move was behavior-neutral.
- `artifacts/x-update-20261110/download-linter-test.log` — `NewXResolverLinterTest` 77/77 after the
  appended download-tuple fixture.
- `artifacts/x-update-20261110/download-lint.log` — `:patches:lintNewxResolvers` passes; the two
  initial `indices.singleOrNull` findings in this file were rewritten to the recognized
  cardinality-proof form (`filter` + `size != 1` + `single()`).

## Known limits

- The null arm is accepted only as `const/4 0` into the merged register. `const/16 0`, or a move
  from a shared null register, fails closed. The handler-side resolver already accepts the
  shared-null variant; the accessor side does not. Add it only if a real APK shows it.
- The success arm must stay adjacent (`check-cast` then `return-object`). R8 emits that today;
  anything else fails closed rather than being guessed.
- Acceptance is broader only in shape, not value: the existing field-read equality still pins the
  accessor to exactly `wrapper.post` then `postResult`.
- The producer gate still reports `found 0: []` for this failure class; better diagnostics there
  are a separate change.
- No version routing. The new shape is selected by bytecode evidence, so it also covers an older
  build that R8 happened to lower this way.
- This repair was validated by patch-time unit tests and lint only. A full MPP build, an APK patch
  run and device download behavior are not part of this change.

## Complete frozen errors

### diagnostic-only-result.json

```text
app.morphe.patcher.patch.PatchException: Expected exactly one NewX video-tab post source on watermarked download path in Lcom/x/video/tab/i0;->h(Lcom/x/video/tab/y6;)V, found 0: []

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)

	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)

	at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabPostTuple(NativeDownloadRedirectPatch.kt:600)

	at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabPost(NativeDownloadRedirectPatch.kt:477)

	at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.resolveVideoTabHooks(NativeDownloadRedirectPatch.kt:405)

	at app.crimera.patches.newx.misc.inlineactions.NativeDownloadRedirectPatchKt.newXNativeDownloadRedirectPatch$lambda$0$1(NativeDownloadRedirectPatch.kt:169)

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
