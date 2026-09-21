# NewX inline download long press

## Behavior

The injected inline download button now distinguishes a tap from a long press:

| Gesture | Single media | Multiple media |
| --- | --- | --- |
| Tap | download the media | open the media picker |
| Long press | download the media | download every media item directly |

Long press is the "download everything" shortcut and never opens the picker. The picker's own
`Download all` and `Download and merge` actions are unchanged.

## Native contract

The inline action bar renders every entry through `combinedClickable`. The presenter
(`InlineActionBarPresenter`) receives the gesture as a distinct event:

```text
tap        -> event(actionEntry, Resources)      -> presenter handler, carries Resources
long press -> event(actionEntry)                 -> presenter handler, no Resources
```

Both events carry the same `InlineActionEntry`, so the extension previously could not tell them
apart and long press behaved like a tap. The tap event additionally carries `Resources` for the
presenter's limited-action messaging; the long-press event does not. The obfuscated event names
differ per release:

| Target | Presenter handler | Long-press event | Tap event |
| --- | --- | --- | --- |
| 12.27.0-prod.01 | `Lcom/x/inlineactionbar/i0;->e(...)` | `Lcom/x/inlineactionbar/r;` (`l4` only) | `Lcom/x/inlineactionbar/n;` (`l4`, `Resources`) |
| 12.28.0-alpha.01 | `Lcom/x/inlineactionbar/i0;->e(...)` | `Lcom/x/inlineactionbar/r;` (`l4` only) | `Lcom/x/inlineactionbar/n;` (`l4`, `Resources`) |
| 12.28.0-alpha.04 | `Lcom/x/inlineactionbar/m0;->e(...)` | `Lcom/x/inlineactionbar/u;` (`k4` only) | `Lcom/x/inlineactionbar/q;` (`k4`, `Resources`) |

Gesture direction was pinned from the compiled `combinedClickable` chain:
`l0.L` is `SemanticsProperties.OnLongClick` (`androidx/compose/ui/semantics/n;->c`), `l0.L` is
`h0.i`, `h0.i` is `e0.e` param 6, `e0.e` param 6 is `q0.g`, and `q0.g` is the second `Function0`
argument of `v0.b`. In `p0.a` that second `Function0` wraps the second `Function1` argument of
`p0.a`, which is the `a1` lambda (index 7) that constructs the no-Resources event.

## Resolver and mutation contract

- Reuse the existing presenter event-handler fingerprint.
- Resolve the long-press event semantically. Among the `INSTANCE_OF` types the handler inspects,
  select the one that implements the event interface, exposes a no-arg getter returning the action
  entry, and exposes no no-arg getter returning `android.content.res.Resources`. The missing
  Resources getter is the discriminator that excludes the tap event. Require exactly one match and
  fail closed otherwise.
- Inject at the handler entry:

```text
move-object/from16  presenter, p0
move-object/from16  event,     p<eventParameter>
instance-of         longPress, event, <resolvedLongPressEvent>
invoke-static       {presenter, event, longPress}, InlineDownloadButton->handleEvent(Ljava/lang/Object;Ljava/lang/Object;Z)Z
move-result         presenter
if-eqz              presenter, :continue
return-void
```

- The extension runs `enqueueAllDownloads()` when `longPress` is true, otherwise keeps the existing
  picker/`enqueueSingleDownload()` routing.
- Three 4-bit scratch registers are allocated at index 0; `FreeRegisterProvider` supplies `v1`, `v0`
  and `v2` in that order for every declared target.

## Validation

Source baseline: commit `66e2e1c3dac43b52e197ac88c2d7878a7fd215ad` plus the working-tree inline
download change. The MPP also contains unrelated pre-existing working-tree edits to
`TimelineScrollPositionStore`; those do not touch this patch.

Build artifact:

- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- SHA-256: `5931626fc1507863664e16dafd61a439383572154f85bb73b4e6cf20b04759ae`
  (the MPP embeds timestamps and is not byte-reproducible; every validation run below used
  this build from the same source revision)
- extension: `extensions/newx/build/morphe/extensions/newx.mpe`
- SHA-256: `a2d3c029a8ffcf5863c9fcdf0b286c2c33ad09c6df60b63d2bd7c6333f083b82`

Commands passed:

```text
./gradlew :extensions:newx:test :patches:test :patches:lintNewxResolvers
./gradlew :patches:build
```

`./patch-twitter.sh` reported `Applied: NewX: Inline download button` and `Saved to` for:

| Target | APK SHA-256 | Output | Output SHA-256 |
| --- | --- | --- | --- |
| 12.27.0-prod.01 | `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc` | `/tmp/piko-inline-longpress-12.27.0-prod.01.apk` | `754cfb5e0fe16ce89d78fd6a5bf2f60977b47d900d19246d5efdf511bc88d559` |
| 12.28.0-alpha.04 | `2faeabfc103f2104d7bfb698078680f06dadbc0afdaefefbf934dfa191aa978b` | `/tmp/piko-inline-longpress-12.28.0-alpha.04.apk` | `164e49d8a501f10c8bfc523c32e6fc9c4cfff171ac93730f6c27a34e36f0e3bf` |
| 12.28.0-alpha.01 | `23c9a96109161ba904387e0fbeec8e8c13d94b26149058bcb5c801b2df9064ad` | `/tmp/piko-inline-longpress-12.28.0-alpha.01.apk` | `9318613cf0e93c50810b60285e102ffd541a1a6eef5d65f4bf9d2027420b1794` |

The full `./patch-twitter.sh` run currently aborts before completion on 12.28.0-alpha.01 in the
unrelated `NewX: Gallery profile Photos tab` patch
(`Expected exactly one NewX bottom paginator state-flow getter, found 0`). The alpha.01 inline
download artifact above was produced with a focused `-e "NewX: Inline download button"` run using
the same MPP and keystore.

`dexscope` confirmed exactly one caller of the new 3-arg `handleEvent`, and the emitted entry
sequence for each target:

```text
move-object/from16 v1, p0
move-object/from16 v0, p6
instance-of v2, v0, <longPressEvent>    # r on 12.27/12.28.0-alpha.01, u on 12.28.0-alpha.04
invoke-static v1, v0, v2, InlineDownloadButton->handleEvent(Ljava/lang/Object;Ljava/lang/Object;Z)Z
move-result v1
if-eqz v1, :cond_0
return-void
:cond_0
... native handler ...
```

`verify-method` returns `INCONCLUSIVE` (modeling warnings only, exit 0) for the modified handler
and the extension entry point. No device was launched or controlled. Runtime confirmation requires
user-driven long press on a patched build.
