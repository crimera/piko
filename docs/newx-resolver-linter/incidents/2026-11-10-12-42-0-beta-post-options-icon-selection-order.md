# 12.42.0 beta: post-options icon selection moved after the feedback lookup

- Date: 2026-11-10
- Package: `com.twitter.android`, `12.42.0-beta.02`, version code `312420102`, ARM64 APKM.
- Input: `../../../../artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm`.
- Input SHA-256: `6e95bdcdea386051c68f09c27e5a9a363ffe781619ef448ce5e6f8f9416f21f1`.
- Initial source: `5758c6eab82d6d67beed95b6d8c9d2c478d6b93e` with the existing generated Chinese locale overlay.
- Initial MPP and extension for the failed diagnostic run: the same `patches/build/libs/patches-3.11.0-dev.1.mpp` (SHA-256 `aa54e066033a3dc2985a4d54f759058aa32f7b8503379a9dcdee02d1bf0538e5`) and `extensions/newx/build/outputs/apk/debug/newx-debug.apk` (SHA-256 `e2f955b19b9a147ea736b7181d057bfd859c06ddf8e5d1effb03ba288099b85e`) recorded for the same APK in `2026-11-10-12-42-0-beta-drawer-parameter-order.md`.
- CLI: `../../../../artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar`.
- Failing patch: `NewX: Customize post menu items`. It throws from `Patch.finalize`, so the report's `failedPatches` entry is named `Unknown` even though the patch is also listed under applied patches.
- Complete report: `../../../../artifacts/x-update-20261110/diagnostic-only-result.json` (the run also wrote `diagnostic-only-patch.log` beside it).

## Reproduction

Build the current checkout, then run `patch-twitter.sh` on the exact input above with the diagnostic output path:

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g' :patches:build --no-daemon
$env:PATCHER_MAX_HEAP_MB='3072'
$env:MORPHE_CLI_JAR='D:/Backup/Desktop/Android/artifacts/x-12372-20261026/tools/morphe-desktop-1.19.1-all.jar'
$env:PATCH_OUTPUT_APK='D:/Backup/Desktop/Android/artifacts/x-update-20261110/diagnostic-only-do-not-install.apk'
& 'C:\Program Files\Git\bin\bash.exe' ./patch-twitter.sh ../artifacts/x-update-20261110/downloads/com.twitter.android_12.42.0-beta.02-312420102_1arch_2dpi_24lang_d1e925b83dbbda57f3fc2e5e0574dcff_apkmirror.com.apkm --unsigned -r ../artifacts/x-update-20261110/diagnostic-only-result.json
```

The failure is deterministic on that APK: the label renderer is found, `cloneParameters` runs, the feedback
lookup is found, and the icon scan then returns no candidates.

## Complete patch error

```text
app.morphe.patcher.patch.PatchException: The patch "BytecodePatch@1993936880" raised an exception:
app.morphe.patcher.patch.PatchException: Expected NewX post-options icon assignments, found none
	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.injectLabelsAndIcons(PostOptionsPatch.kt:409)
	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.newXPostOptionsPatch$lambda$0$1(PostOptionsPatch.kt:187)
	at app.morphe.patcher.patch.Patch.finalize(Patch.kt:164)
	at app.morphe.patcher.patch.BytecodePatch.finalize$morphe_patcher(Patch.kt:295)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:168)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:151)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	...
Caused by: app.morphe.patcher.patch.PatchException: Expected NewX post-options icon assignments, found none
	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.injectLabelsAndIcons(PostOptionsPatch.kt:409)
	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.newXPostOptionsPatch$lambda$0$1(PostOptionsPatch.kt:187)
	at app.morphe.patcher.patch.Patch.finalize(Patch.kt:164)
	at app.morphe.patcher.patch.BytecodePatch.finalize$morphe_patcher(Patch.kt:295)
	... 13 more
```

The other `failedPatches` entries (post reply bar, premium upsell, canonical URLs, native download redirect) are
unrelated resolvers and are not analysed here.

## Evidence and cause

Cause category: APK contract drift. The option sheet's icon `when` moved from before the feedback lookup to after
it, the icon selection now ends in a register-merge cluster instead of a single merge label, and a second
null-icon option sheet constructor was added. No linter or cardinality-helper behaviour is involved; the failure
is the resolver's own instruction-order assumption.

The renderer is unchanged in identity and shape: `Landroidx/compose/material/d0;->invoke(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;`,
zero try blocks, 65 registers in 12.41 and 64 in 12.42. The state class kept its fields:
`Lcom/x/urt/items/post/options/j0;` with `a:Z`, `b:Lcom/x/models/oh;` (`Lcom/x/models/nh;` in 12.42),
`c:Ljava/util/List;`, and the feedback map field `d` (`Ljava/util/LinkedHashMap;` in 12.41,
`Lkotlin/collections/builders/MapBuilder;` in 12.42).

### Old flow (12.41)

| Index | Instruction | Role |
| --- | --- | --- |
| 76-82 | `sget-object v19/v20/v21/v23/v24/v25/v26, Lcom/x/icons/a;->…` | seven icon literals preloaded; `v25` holds the no-match default |
| 102 | `move-object/from16 v25, v19` + `goto :goto_d2` | first icon arm merges into `v25` |
| 117/145/149/169/173 | `move-object/from16 v25, …` + `goto :goto_d2` | remaining icon arms |
| 194-196 | `iget-object v0, v12, …j0;->d:…; get; move-result-object v0` | feedback label lookup, action `v10`, label/result `v0` |
| 441 | `move-object/from16 v26, v0` | label into constructor slot |
| 451 | `move-object/from16 v24, v10` | action into constructor slot |
| 452 | `invoke-direct/range {v23..v29}, Lcom/x/ui/common/sheets/a;-><init>(Object,Icon,String,String,Boolean,I)V` | single option sheet constructor |

All six icon arms converge on `:goto_d2` at index 102, so the icon is already merged into `v25` before the lookup
at 194. This is why the old resolver's "object move followed by `goto`, before the lookup" scan found exactly six
assignments.

### New flow (12.42)

| Index | Instruction | Role |
| --- | --- | --- |
| 70-73 | `iget-object v6, v15, …j0;->d:MapBuilder; get; move-result-object v6; check-cast v6 String` | feedback label lookup now comes first, action `v7`, label `v6` |
| 84-88 | `sget-object v16, …n0;->a:[I; ordinal; aget; packed-switch v16` | shared enum dispatch (label fallback + icon selection) |
| 357-369 | `sget-object v2, Lcom/x/models/y5;->SendToGrokBot; if-ne v7, v2` … `const/16 v25, 0x0` … `invoke-direct/range {v23..v29}, sheets/a-><init>` | new `SendToGrokBot` branch with a **null** icon |
| 405-416 | six `sget-object` icon literals into `v17/v19/v20/v21/v24/v26`, `sget-object v25, …q7` as the default, then `packed-switch v16` | icon selection preloads and dispatch |
| 435/454/489/494/519/524 | `move-object/from16 v25, <icon register>` + `goto` | icon arms now *after* the lookup |
| 553 | `move-object/from16 v26, v6` | label into constructor slot |
| 554 | `move-object/from16 v24, v7` | action into constructor slot |
| 555 | `invoke-direct/range {v23..v29}, Lcom/x/ui/common/sheets/a;-><init>(Object,Icon,String,String,Boolean,I)V` | real option sheet constructor |

The grok branch exits through `goto/16 :goto_4b9` and never reaches 555. The general constructor is at smali line
2213, the grok one at 1813, and the arms at 1953-2200.

### Why the scan reports "found none"

`injectLabelsAndIcons` filtered `MOVE_OBJECT*` instructions whose next instruction is `goto` **and** whose index is
`< lookup.callIndex` (71 in 12.42). Every real icon arm is at 435-524, so the candidate list was empty and the
resolver threw before it inspected fields. Widening only the bound is also wrong: the scan then picks up unrelated
pairs:

| Index | Instruction | What it actually is |
| --- | --- | --- |
| 119 | `move-object v6, v2` + `goto` | label fallback result |
| 399/444/528 | `move-object/from16 v62, v4` + `goto` | Compose group bookkeeping (`v62` frame register) |
| 351/380 | `move-object/from16 v25, v12/v13` | grok-region register reuse, no icon field |
| 566 | `move-object/from16 v25, v2` | after the consumer, unrelated |

The "object move that ends a branch arm" shape alone is not the contract.

### Why fixing detection alone is not enough

The icon rewrite used to be emitted at `lookup.resultIndex + 1` = 73. On 12.42 that point is before the icon
selection: `sget-object v25, Lcom/x/icons/a;->q7` at 414 and the six arms at 435-524 all overwrite `v25` afterwards.
A detection-only fix would write the contribution icon and then let the app replace it, silently. The icon
override has to move to the instruction that actually consumes the selected icon.

### How the consumer is identified

The only invokes in either renderer that take an `Lcom/x/icons/b;` parameter are the option sheet constructors
`sheets/a-><init>(Object,Icon,String,String,Boolean,I)`: 452 in 12.41, 369 and 555 in 12.42. Slot mapping is
`v24` = action, `v25` = icon, `v26` = label in the range invoke `{v23..v29}`.

- 12.41: `v25` merges at 102 and is untouched until 452, so the icon is the same value the old hook produced.
- 12.42: the grok site at 369 receives `const/16 v25, 0x0` at 365, a deliberately null icon. The general site at
  555 receives the arm-merged icon; `v25` has no write between the last arm (524) and the invoke.
- The label/action values at both 12.42 sites trace back to the lookup: `v26` from `v6` (367/553) and `v24` from
  `v7` (368/554). The action slot therefore cannot separate the two sites; only the icon producer can.

## Repair and verification

`PostOptionsPatch.kt` now resolves the icon as a `(cluster, consumer)` tuple and derives both hook points from it.

`PostOptionsIconSite` is `(iconRegister, iconType, consumerIndex, consumerActionSlot, consumerLabelSlot)`, and
`resolvePostOptionsIconSite` (now `PostOptionsPatch.kt:382`) works as follows:

1. The action definition is the last register write before `lookup.callIndex` that writes `lookup.actionRegister`.
2. Candidate arms are `MOVE_OBJECT*` instructions whose successor is `GOTO`/`GOTO_16`/`GOTO_32`, whose source is
   resolved through the existing `resolveFieldReadOrNull(register, untilIndex)`. Grouping by destination keeps
   only clusters with at least two arms.
3. Exactly one cluster must exist; its arms must resolve to exactly one field owner and one field type. This
   rejects the single-move decoys and the `v62` Compose bookkeeping.
4. A consumer is an invoke after the last arm whose object parameter slot is the cluster destination, where
   exactly one slot is proven to carry the lookup action value by all-path `provesValueFromInstruction` from the
   action definition, and exactly one `String` slot is tied to `lookup.labelRegister` either by identity or by
   exactly one object move inside `(lastArmIndex, callIndex)` with no later write to that slot.
5. A candidate consumer is rejected if any instruction writes the icon register between the last arm and the
   call, because it then does not read the cluster's value (`PostOptionsPatch.kt:436`).
6. `requireExactlyOne("NewX post-options icon consumer", …)` fails closed with candidate evidence.

The label slot deliberately does not use all-path provenance: releases that map the label through its own register
rewrite it on other paths, which would make a valid consumer look unproven. The identity-or-one-copy tie is
narrower but chosen consciously, and the limitation is called out here instead of hidden behind a weaker proof.

`injectLabelsAndIcons` (`PostOptionsPatch.kt:544`) now emits two hooks, the later one first so the earlier index
stays valid:

- icon hook at `site.consumerIndex`, `excludedRegisters = (listOf(actionRegister, iconCheckRegister) + invocation.registersUsed).filter { it != iconRegister }`,
  `relocateBranchTargets = true`, body unchanged: `usesIcon(actionRegister)` → `move-result` → `if-eqz` →
  `sget(site.iconRegister, …)`;
- label hook at `lookup.resultIndex + 1`, with the icon block removed and `excludedRegisters` reduced to the
  action, label and icon registers.

`iconType` comes from the cluster and feeds the existing `resolveIconField(name, iconType)`. No resource, model,
version or reflection contract changed, and no linter or cardinality-helper behaviour changed.

Behaviour preservation: on 12.41 the icon merge (102) precedes the lookup (195) and 452 consumes the same `v25`,
so moving the icon hook from 197 to 452 is equivalent. On 12.42 the icon argument is live at 555 and last written
at 524. `iconCheckRegister` is the pre-`cloneParameters` register count, and the cloned method's extra parameter
slots start there, so the scratch register is outside the original body at both insertion points; the typed
`Block` would fail closed on `RegisterLimit` rather than emit a verifier error. The grok branch keeps its native
null icon, which is also what the old code produced there.

### Fixture evidence

`NewXResolverLinterTest` gained two focused tests through the real resolver entry:

- `post options icon site follows the arm cluster into the sheet consumer across switch order` builds the 12.41
  and 12.42 renderer shapes, including the grok null-icon constructor before the cluster and the `p0/p1/p2`
  Compose parameter restores, and asserts `consumerIndex`, `iconRegister`, `iconType`, `consumerActionSlot` and
  `consumerLabelSlot`. The 12.41 shape also rewrites `v6` through `trim` before the label copy to document the
  label-tie limitation.
- `post options icon site fails closed on ambiguous clusters and consumers` covers two clusters, one cluster with
  two field types, a consumer that does not carry the action value, and a write to the icon register between the
  last arm and the consumer.

RED (pre-factoring detection restored, final fixture in place):

```text
> Task :patches:test FAILED
NewXResolverLinterTest > post options icon site follows the arm cluster into the sheet consumer across switch order FAILED
    app.morphe.patcher.patch.PatchException at NewXResolverLinterTest.kt:1169
83 tests completed, 1 failed
BUILD FAILED in 38s
```

Log: `../../../../artifacts/x-update-20261110/postoptions-red.log`.

GREEN (resolver restored):

```text
> Task :patches:test
BUILD SUCCESSFUL in 31s
```

Log: `../../../../artifacts/x-update-20261110/postoptions-green.log`; the JUnit report records
`tests="83" skipped="0" failures="0" errors="0"`. The scoped command was:

```powershell
& .\gradlew.bat ':patches:test' '--tests' 'app.crimera.tools.newx.NewXResolverLinterTest' '--no-daemon' '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8'
```

Process-scoped `ANDROID_HOME` and GitHub build credentials were used as described by the local build setup; the
credential was never printed, and the probe used for RED was removed before the GREEN run.

## Known limits

- The consumer must take the cluster destination itself as the icon argument. A release that copies the merged
  icon into a second register before the constructor fails closed instead of guessing.
- The label tie does not prove all-path provenance; it accepts identity or one intervening copy with no later
  write. A release that rewrites the label slot more than once between the last arm and the call fails closed.
- Two qualifying clusters or consumers raise `PatchException`; they are not merged or ordered by position.

Runtime acceptance (post menu renders, the custom icon shows on the real sheet, the `SendToGrokBot` row keeps its
native null icon) remains with the user. This session ran no full build, no patch, no install and no device action.

## Complete frozen errors

### diagnostic-only-result.json

```text
app.morphe.patcher.patch.PatchException: The patch "BytecodePatch@1993936880" raised an exception: app.morphe.patcher.patch.PatchException: Expected NewX post-options icon assignments, found none

	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.injectLabelsAndIcons(PostOptionsPatch.kt:409)

	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.newXPostOptionsPatch$lambda$0$1(PostOptionsPatch.kt:187)

	at app.morphe.patcher.patch.Patch.finalize(Patch.kt:164)

	at app.morphe.patcher.patch.BytecodePatch.finalize$morphe_patcher(Patch.kt:295)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:168)

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



	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:181)

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

Caused by: app.morphe.patcher.patch.PatchException: Expected NewX post-options icon assignments, found none

	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.injectLabelsAndIcons(PostOptionsPatch.kt:409)

	at app.crimera.patches.newx.misc.postoptions.PostOptionsPatchKt.newXPostOptionsPatch$lambda$0$1(PostOptionsPatch.kt:187)

	at app.morphe.patcher.patch.Patch.finalize(Patch.kt:164)

	at app.morphe.patcher.patch.BytecodePatch.finalize$morphe_patcher(Patch.kt:295)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:168)

	... 13 more
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
