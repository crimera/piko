# Translator receives the preview instead of the full note post

## Report and provenance

- Package/version: `com.twitter.android`, `12.39.0-alpha.03` (`312390203`), alpha.
- Target APK: `apks/12.39.0-alpha.03.apk`.
- Source baseline: `4aa6c7f5a0e09ae3d73a6e5e447456531681fabd`.
- User recording: `/storage/emulated/0/Movies/screen-20261030-055033-1793310623770.mp4`,
  retrieved with `adb pull` into `/tmp/` for inspection.
- Installed APK retrieved read-only into `/tmp/newx-installed-translation.apk`.
  Its `PostTranslator.showSheet` contains the baseline's selectable-text wrapper.
  The installed build's original MPP path/build provenance is unknown.
- Failing patch: `NewX: Translator`.
- Runtime symptom, without an exception: a long post's Translate menu tray ends at
  `1.For`; upward swipes do not reveal the remaining list items. The inline X
  translation visible behind it contains substantially more text.

## Cause and bytecode evidence

This is runtime source selection, not a resolver-linter or cardinality-helper
failure. No linter/helper change was isolated or reverted; neither is implicated.

In the exact alpha APK, timeline model `com/x/models/timelines/items/k1.getText`
delegates to `com/x/models/s5.getText`. Canonical model `com/x/models/r0.getText`
returns its raw `text` field (`b`). Our extension then applies that canonical
model's `displayTextRange` to this preview.

X's note-post renderer follows `s5.f()` to the note wrapper, then its
`noteTweetResult`, checks the successful body variant, and reads that variant's
`text` into `NotePostState`. It does not use the canonical preview's text/range.
In this APK the wrapper is `com/x/models/notes/c`, its result field is `b`, and
the body variant is `com/x/models/notes/j`, with `text` field `d` and
`postEntityList` field `b`. These identities are evidence only; the fix resolves
semantic labels and interface relationships at patch time.

A temporary Robolectric layout probe passed with both initially long text and
short text replaced after showing the tray: the existing wrapped selectable text
exceeds the viewport and scrolling reaches its final line. The probe was removed
because it did not reproduce the reported failure.

## Correction

Prefer a loaded successful note body's text and URL entities, bypassing the
canonical preview range for that body. Preserve canonical text/range/entities
when no usable note body exists. Keep the existing scrollable tray and copy the
complete provider result. Resolve the note models semantically and emit direct,
null-safe bridges with a result-variant check; do not add runtime reflection or
release-name routing.

## Validation

Implementation and validation results are recorded below after the current
checkout's MPP build and exact-APK patch runs. Runtime confirmation requires the
user to open the same post normally; no device UI was controlled during analysis.

### First rebuilt-bundle production checks

The MPP `patches/build/libs/patches-3.11.0-dev.1.mpp` was rebuilt from the
baseline plus this fix and the pre-existing working-tree timeline edits using
`./gradlew :patches:build --no-daemon` (successful; includes the patch tests).
The synchronized NewX extension came from that same checkout.

`PATCH_OUTPUT_APK=/tmp/piko-translator-12.38.0-prod.01.apk ./patch-twitter.sh apks/12.38.0-prod.01.apk`
first failed in unrelated `NewX: Restore Twitter branding` / `NewX: Restore Twitter splash transition`:
`Expected exactly one NewX launch reveal renderer after marker, found 0: []`.
No artifact was saved. This APK predates the declared production target
12.38.1; no claim of full-patch compatibility is made. The existing launch-reveal
fixture corpus already guards the missing-renderer fail-closed behavior.

Repeating the command with `-d 'NewX: Restore Twitter branding'` reached the
translator and exposed a new resolver failure: the body's `text` label is
appended in the caller while its value is appended by an outlined helper.
The existing label-to-field adapter cannot join those two writes. This is
resolver logic, not linter/helper behavior; no linter or library change caused
or was reverted for this comparison. The correction resolves the exactly-one
String instance field read in the semantically matched note-body `toString`.
A fixture in `NewXResolverLinterTest` covers this exact outline shape.

Complete translator failure from the second command (no output saved):

```
ERROR: FAILED: NewX: Translator
INFO: Patching took 7.6 seconds
ERROR: Patching aborted: Patching failed: Patch "NewX: Translator" failed: NewX Expected exactly one model field for ', text=' in Lcom/x/models/notes/j;->toString()Ljava/lang/String;, found 0: []
ERROR: app.morphe.patcher.patch.PatchException: NewX Expected exactly one model field for ', text=' in Lcom/x/models/notes/j;->toString()Ljava/lang/String;, found 0: []
	at app.crimera.patches.newx.models.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:193)
	at app.crimera.patches.newx.translation.TranslatorPatchKt.toStringField(TranslatorPatch.kt:492)
	at app.crimera.patches.newx.translation.TranslatorPatchKt.patchDisplayTextBridges(TranslatorPatch.kt:375)
	at app.crimera.patches.newx.translation.TranslatorPatchKt.newXTranslatorPatch$lambda$0$1(TranslatorPatch.kt:279)
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
Caused by: app.morphe.patcher.patch.PatchException: Expected exactly one model field for ', text=' in Lcom/x/models/notes/j;->toString()Ljava/lang/String;, found 0: []
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
	at app.crimera.patches.common.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.requireSingleToStringField(SemanticIntrospection.kt:110)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:101)
	at app.crimera.patches.common.semantic.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:42)
	at app.crimera.patches.newx.models.SemanticIntrospectionKt.fieldForToStringLabel(SemanticIntrospection.kt:56)
	... 20 more

INFO: Use --continue-on-error to skip failed patches and continue patching
```

### Final rebuilt-bundle results

After the outlined-body resolver correction, `./gradlew :patches:build --no-daemon`
passed again. Both APK runs below used that same newly built
`patches/build/libs/patches-3.11.0-dev.1.mpp` and synchronized extension; the
baseline plus working-tree changes are recorded above.

- `./gradlew :extensions:newx:testDebugUnitTest --tests '*PostTranslatorTest' :extensions:newx:lintRelease --no-daemon`: passed.
  The regression checks full note text against the short preview range, a null
  preview, and ordinary/missing-note text with supplementary Unicode code points.
- `./gradlew :patches:test --tests '*NewXResolverLinterTest' :patches:lintNewxResolvers --no-daemon`: passed.
  The new body-field fixture models the caller-appended text label and subsequent
  outlined formatter call. No linter/helper implementation was changed.
- `PATCH_OUTPUT_APK=/tmp/piko-translator-12.38.0-prod.01.apk ./patch-twitter.sh apks/12.38.0-prod.01.apk -d 'NewX: Restore Twitter branding'`:
  succeeded, reported `Applied: NewX: Translator` and
  `Saved to /tmp/piko-translator-12.38.0-prod.01.apk`.
- `PATCH_OUTPUT_APK=/Users/steven/Downloads/piko-translator-12.39.0-alpha.03.apk ./patch-twitter.sh apks/12.39.0-alpha.03.apk`:
  succeeded with the normal script patch set, reported `Applied: NewX: Translator`
  and `Saved to /Users/steven/Downloads/piko-translator-12.39.0-alpha.03.apk`.
- Final production/alpha DEX inspection confirms direct note-getter invocation,
  paired result reads, success-subtype guards, full-body text field reads, and null
  fallback returns. The alpha entity bridge reads the same note body's own entity
  list. The production result base descriptor differs from alpha and was resolved
  dynamically as intended.
- Read-only inspection of 12.37.0 production also confirms the note renderer and
  semantic model labels. The older currently declared 12.38.1 production APK was
  unavailable locally; 12.38.0 provided the older production patch check instead.
- The output APK was handed to the user for the same-post runtime test. It was not
  installed or launched; full-note rendering, scrolling and ordinary-post control
  behavior still require user confirmation.

`dexscope verify-method` on the two alpha note bridges returned `INCONCLUSIVE`
with warnings that reference invoke/return and field assignability are not
modeled. It did not provide an Android-verifier proof. The concrete instructions
and resolved model relationships were inspected directly as described above;
device runtime confirmation remains pending.
