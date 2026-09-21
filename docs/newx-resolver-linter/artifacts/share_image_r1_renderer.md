# R1 — NewX post renderer (share image)

Recon only. APK: `apks/12.27.0-prod.01.apk` (NewX 12.27.0-prod.01).

Tooling note: the task said `dexscape`; only `dexscope`
(`~/.local/bin/dexscope`) is installed, so all commands below use `dexscope
anchors`.

## 1. Anchor

```text
$ dexscope anchors apks/12.27.0-prod.01.apk --string 'AvailablePost(entryId=' --limit 10
Lcom/x/urt/items/post/k5;->toString()Ljava/lang/String; @ 0x2 CONST_STRING
```

One hit: the post model owns the label.

## 2. `k5` toString confirmation

```text
$ dexscope inspect-class apks/12.27.0-prod.01.apk 'Lcom/x/urt/items/post/k5;'
# class Lcom/x/urt/items/post/k5; (public final)
# super: Ljava/lang/Object;
# interfaces: Lcom/x/media/playback/e;, Lcom/x/urt/items/post/p5;
# dex: classes4.dex

$ ... --methods | grep toString
public final toString()Ljava/lang/String; [registers: 6, instructions: 261]

$ ... inspect-method 'Lcom/x/urt/items/post/k5;->toString()Ljava/lang/String;'
[0002] (01) const-string v1, "AvailablePost(entryId="
[000c] (05) const-string v1, ", postId="
```

`k5` is the individual timeline post state; `toString` starts with
`AvailablePost(entryId=`. Confirmed.

## 3. Incoming type refs (intended step)

```text
$ dexscope xref apks/12.27.0-prod.01.apk 'Lcom/x/urt/items/post/k5;' \
    --direction in --kind type --limit 500
```

91 lines / 52 unique owners. Only one referenced method has `k5` as first
parameter **and** `Landroidx/compose/runtime/Composer;`
(`k5` as a *direct* parameter is not an instruction type reference, so plain
`--kind type` cannot see the other renderers):

```text
Lcom/x/mappers/subscriptions/a;->a(
  Lcom/x/urt/items/post/k5;
  Landroidx/compose/foundation/layout/c3;
  Lcom/x/urt/items/post/q5;
  Landroidx/compose/ui/Modifier;
  Landroidx/compose/runtime/Composer;
  I)V
```

Cross-checking against `pointerInput` callers is what proves it (the
`--kind type` list alone would also admit non-renderers):

```text
$ dexscope dry-run apks/12.27.0-prod.01.apk --param '...k5;' --param '...c3;' \
    --param '...q5;' --param '...Modifier;' --param '...Composer;' --param 'I' \
    --return 'V' --call '.../pointer/f0;->b(...)'
MATCH_UNIQUE matches=1
```

## 4. Renderer

```text
Lcom/x/mappers/subscriptions/a;->a(Lcom/x/urt/items/post/k5;Landroidx/compose/foundation/layout/c3;Lcom/x/urt/items/post/q5;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
```

- dex: `classes4.dex` (class `Lcom/x/mappers/subscriptions/a;`)
- registers: total=63 params=6 locals=57
- params: `p0` k5, `p1` layout c3, `p2` post `q5`, `p3` Modifier, `p4` Composer, `p5` int
- matches the `ShareImagePatch` fingerprint shape
  `(timelinePostStateType, "L", "L", "L", COMPOSER, "I")V`
  (`patches/src/main/kotlin/app/crimera/patches/newx/misc/shareimage/ShareImagePatch.kt`).

### pointerInput match

```text
[01f2] (266) invoke-static v0, v15, v5,
  Landroidx/compose/ui/input/pointer/f0;->b(Landroidx/compose/ui/Modifier;Ljava/lang/Object;Landroidx/compose/ui/input/pointer/PointerInputEventHandler;)Landroidx/compose/ui/Modifier;
[01f5] (267) move-result-object v0
```

- owner/ref: `Landroidx/compose/ui/input/pointer/f0;->b(Landroidx/compose/ui/Modifier;Ljava/lang/Object;Landroidx/compose/ui/input/pointer/PointerInputEventHandler;)Landroidx/compose/ui/Modifier;`
- instruction index **266**, offset `0x01f2`; `MOVE_RESULT_OBJECT` immediately after at index 267.
- Modifier result register: `v0`; callback (`PointerInputEventHandler`) register: `v5`.

## 5. onPositioned hook

Backward scan from the pointerInput call for the first
`INVOKE_STATIC (Modifier, Function1) -> Modifier`:

```text
[01ca] (246) invoke-static v0, v5,
  Landroidx/compose/ui/layout/n;->s(Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function1;)Landroidx/compose/ui/Modifier;
[01cd] (247) move-result-object v0
```

- ref: `Landroidx/compose/ui/layout/n;->s(Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function1;)Landroidx/compose/ui/Modifier;`
- instruction index **246**, offset `0x01ca` (immediately before the
  `pointerInput` call after two `Composer` bookkeeping instructions).
- `s` is a `public static synthetic` default-argument bridge; it forwards to
  `Landroidx/compose/ui/layout/n;->r(Landroidx/compose/ui/Modifier;JJLkotlin/jvm/functions/Function1;)Landroidx/compose/ui/Modifier;`
  with `0L` and `0x40L`, which builds node
  `Landroidx/compose/ui/layout/n1;` backed by `androidx.compose.ui.spatial.d`
  (new Compose position/rect tracking). This is the "on-positioned" modifier
  the share-image patch later feeds a synthesized `Function1`.

Decoy: there is an earlier `(Modifier, Function1) -> Modifier` call at index 198
(`Landroidx/compose/ui/semantics/r;->a(...)`). The patch scans backward from the
pointerInput call, so index 246 wins. Do not anchor on the semantics call.

The `ShareImagePatch` post-pointer filter also expects a
`(Composer, Modifier) -> Modifier` call, present at index 280:

```text
[020c] (280) invoke-static v6, v0, Landroidx/compose/ui/a;->c(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;
```

## 6. Cardinality

- Renderer candidates for this APK: **1** (`MATCH_UNIQUE` with the exact
  `(k5, layout c3, post q5, Modifier, Composer, I)V` + pointerInput-call
  fingerprint). No second candidate.
- Other pointerInput callers exist but none carries `k5` first:
  `Lcom/x/video/tab/k;->t(k5, haze s, Z, Function0, friendship b, Z, Function0, F, Modifier, Composer, I)V`
  is a video-tab renderer (11 params, does not match the 6-param fingerprint).
- onpositioned `(Modifier, Function1) -> Modifier` candidates before the
  pointerInput call: 2 in-method (index 198 semantics, index 246 `layout/n->s`);
  the patch's backward-first rule resolves index 246 deterministically.

## Result

- Renderer: `Lcom/x/mappers/subscriptions/a;->a(Lcom/x/urt/items/post/k5;Landroidx/compose/foundation/layout/c3;Lcom/x/urt/items/post/q5;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V` (classes4.dex, 63 regs)
- pointerInput: `Landroidx/compose/ui/input/pointer/f0;->b(Modifier,Object,PointerInputEventHandler)Modifier` @ index 266
- onPositioned: `Landroidx/compose/ui/layout/n;->s(Modifier,Function1)Modifier` @ index 246
- cardinality: 1
