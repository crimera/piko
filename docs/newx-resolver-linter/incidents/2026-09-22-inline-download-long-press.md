# NewX inline-action long-press event resolves to the tap event

- Date: 2026-09-22
- Reporter/session: inline download long-press implementation session; user report that long press still opened the media picker
- APK package/version/build type: `com.twitter.android` 12.27.0-prod.01 production, plus 12.28.0-alpha.01 and 12.28.0-alpha.04 experimental targets
- APK path and checksum: `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`; `apks/12.28.0-alpha.01.apk` / `23c9a96109161ba904387e0fbeec8e8c13d94b26149058bcb5c801b2df9064ad`; `apks/12.28.0-alpha.04.apk` / `2faeabfc103f2104d7bfb698078680f06dadbc0afdaefefbf934dfa191aa978b`
- Source commit: `66e2e1c3dac43b52e197ac88c2d7878a7fd215ad` plus working-tree long-press change
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `7518da617d0f66356582bede3a0f3f8be1f211be2bb5dd52f74eb783b8394aa4` (failing gesture build) and `5931626fc1507863664e16dafd61a439383572154f85bb73b4e6cf20b04759ae` (final validated build). The MPP embeds timestamps and is not byte-reproducible across builds.
- Extension artifact path/checksum: `extensions/newx/build/morphe/extensions/newx.mpe` / `a2d3c029a8ffcf5863c9fcdf0b286c2c33ad09c6df60b63d2bd7c6333f083b82`
- Output artifact path/checksum: final validated outputs are listed in `docs/newx-inline-download-long-press.md`
- Command: `./patch-twitter.sh apks/12.27.0-prod.01.apk`, `./patch-twitter.sh apks/12.28.0-alpha.04.apk`, and a focused `-e "NewX: Inline download button"` run for 12.28.0-alpha.01
- Failing patch: `NewX: Inline download button`
- Severity: medium (user-visible feature did not work; fail-closed during the first iteration)
- Confidence: confirmed statically and by user report

## Symptom

Two stages were observed:

1. The first resolver matched both the tap event and the long-press event as the same semantic
   shape, so `requireExactlyOne` rejected the patch and the whole run aborted.
2. After adding a Resources-getter discriminator, the patch applied, but the resolver selected the
   **tap** event. Long press therefore still opened the media picker, and tap would have downloaded
   every media item.

## Complete error

```text
SEVERE: FAILED: NewX: Inline download button
app.morphe.patcher.patch.PatchException: Expected exactly one NewX inline-action long-press
event, found 2: [Lcom/x/inlineactionbar/n;, Lcom/x/inlineactionbar/r;]
```

No patch-time exception for stage 2; it was a runtime/control-path failure reported by the user.

## Reproduction

```text
./patch-twitter.sh apks/12.27.0-prod.01.apk
```

12.28.0-alpha.04 did not reproduce the two-candidate abort because its tap event is not referenced
by an `INSTANCE_OF` in the presenter handler, but it hit the same wrong-event defect.

## Cause classification

- [ ] linter behavior
- [x] cardinality-helper behavior
- [x] resolver logic
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [x] runtime behavior
- [ ] unrelated

## Isolation comparison

The linter and `requireExactlyOne` helper behaved as designed: the first iteration failed closed
with full candidate descriptions instead of selecting an arbitrary event.

DEX inspection of the candidates:

```text
tap event        fields: entry actionEntry, android.content.res.Resources
long-press event fields: entry actionEntry only
```

The presenter uses the tap event's `Resources` for its limited-action message. Selecting by
presence of Resources therefore selected the tap event, not long press.

Gesture direction was pinned from the compiled `combinedClickable` chain rather than guessed:

```text
androidx/compose/ui/semantics/n;->c        = "OnLongClick" (SemanticsProperties)
l0->x1()                                    sets n.c from l0.L
l0-><init>  p7                              -> l0.L
h0->a()     field h0.i                      -> l0.L
h0-><init>  p7   field h0.i
e0.e        param 6                         -> h0.i
e0.e        called from q0  param p6        = q0.g
q0-><init>  p7   field q0.g
q0-><init>  call args v20/v21               = v0.b p1 / v0.b p0
p0.a        v0.b(p1, p2)                    second Function0 = wrapper(p0.a p2)
p0.a        p2                              = v1.g second Function1 = a1(7) -> no-Resources event
```

The tap event is constructed by the `addparticipants/a` lambda (index 0x18); the long-press event
by the `a1` lambda (index 7).

## Fix and validation

- `resolveInlineLongPressEventType()` selects the event that implements the event interface, exposes
  the action-entry getter, and exposes **no** `Resources` getter.
- `./gradlew :extensions:newx:test :patches:test :patches:lintNewxResolvers` passed.
- `./gradlew :patches:build` passed.
- `./patch-twitter.sh` applied and saved on 12.27.0-prod.01 and 12.28.0-alpha.04; 12.28.0-alpha.01
  applied with a focused run (the full script aborts in the unrelated
  `NewX: Gallery profile Photos tab` patch).
- `dexscope` emitted `instance-of v2, v0, <longPressEvent>` with `Lcom/x/inlineactionbar/r;` on
  12.27.0-prod.01 and 12.28.0-alpha.01, and `Lcom/x/inlineactionbar/u;` on 12.28.0-alpha.04.

## Fixture or test added

No linter fixture was added: both defects were resolver predicate defects caught by the existing
fail-closed cardinality helper and by user runtime testing, not linter false positives or false
negatives. The feature-level documentation and gesture-direction derivation live in
`docs/newx-inline-download-long-press.md`.

## Lessons

When two events share a payload, the discriminator must be a capability unique to the intended
branch. "Carries Resources" is unique to the tap branch, so it selects tap, not long press. Derive
gesture direction from the compiled `combinedClickable` chain (semantics `OnLongClick` key down to
the constructor fields) instead of inferring it from event shape or naming.
