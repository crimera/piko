# Hide post reply bar recon: Twitter/X 12.27.0-alpha.01

## Outcome

The reply-bar call hierarchy did not disappear or gain a new semantic layer in
12.27.0-alpha.01. The same four production methods are present, in call direction:

```text
post-detail sheet container
  -> minimal-composer container
  -> minimal-composer renderer caller
  -> tagged reply-field renderer
```

The 12.27 failure is caused by compiler-artifact strings in the current
`isMinimalComposerContainerCaller` predicate. In 12.26, the minimal container starts with
Kotlin `Intrinsics.checkNotNullParameter` calls carrying `"inlineComposer"` and
`"hazeState"`. In 12.27, the first named check is lowered to `Object.getClass()` and the
second named check is absent. Neither string exists anywhere in the 12.27 APK.

The intended 12.27 methods are:

- Tagged reply-field renderer:
  `Lcom/x/composer/minimal/b;->e(...)V`
- Minimal-composer renderer caller (`rendererCaller`):
  `Lcom/x/composer/minimal/b;->c(...)V`
- Minimal-composer container caller (`minimalContainerCaller`):
  `Lcom/x/composer/minimal/b;->h(Lcom/x/composer/minimal/q;Ldev/chrisbanes/haze/t;Landroidx/compose/ui/Modifier;ZLkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;II)V`
- Post-detail container caller:
  `Lcom/x/postdetailsheet/s;->g(FFLcom/x/postdetailsheet/a;Ldev/chrisbanes/haze/t;Lcom/x/photoeditor/tools/imageoverlay/j;Lkotlin/jvm/functions/Function1;Lcom/x/postdetailsheet/v;Landroidx/compose/runtime/Composer;I)V`

The recommended fix is to remove the two parameter-name strings from the container
predicate and identify the container by the already-resolved direct call edge plus its ordered
Compose ABI:

```text
(minimal-composer object, Haze object, Modifier, boolean, Function1, Composer, int, int) -> void
```

Resolve every caller collection and every selected callsite with the shared
`requireExactlyOne` helper. This matches exactly one method in both supplied APKs, survives the
observed R8 type renames, and fails closed if the composable contract or call graph changes.

## Frozen inputs

Analysis used dexscope against source commit
`d6efb26d263000ed9960069dd6fdc93348f12d90`. The worktree was already dirty; this recon only
adds this artifact.

| Role | APK | Manifest identity |
| --- | --- | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, versionCode `312260203`, versionName `12.26.0-alpha.03` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, versionCode `312270201`, versionName `12.27.0-alpha.01` |

The reported sweep failure is already recorded in
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## Current resolver failure

The current resolver first finds the one renderer containing the stable Compose test tag
`post-detail-reply-text-field`. It then walks incoming static calls:

1. Renderer -> `isMinimalComposerRendererCaller`
2. Renderer caller -> `isMinimalComposerContainerCaller`
3. Minimal container -> `isPostDetailReplyBarContainer`

The second predicate currently requires:

```kotlin
returnType == "V" &&
    parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
    parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
    parameters.any { it.startsWith(COMPOSER_MINIMAL_SCOPE) } &&
    parameters.any { it.startsWith(HAZE_SCOPE) } &&
    hasStrings("inlineComposer", "hazeState")
```

All structural conditions still hold in 12.27. Only `hasStrings(...)` fails.

Dexscope string stability results:

| String | 12.26.0-alpha.03 | 12.27.0-alpha.01 |
| --- | ---: | ---: |
| `post-detail-reply-text-field` | 1 match, in the intended renderer | 1 match, in the intended renderer |
| `inlineComposer` | 2 APK-wide matches | 0 APK-wide matches |
| `hazeState` | 33 APK-wide matches | 0 APK-wide matches |

`inlineComposer` and `hazeState` were never semantic identifiers: they are parameter-name
literals emitted for Kotlin null checks. Their complete APK-wide removal in 12.27 is consistent
with the same compiler/lowering change seen in other 12.27 resolver failures.

This was not a blanket removal of every string in the container. The old method also has a later
`"<this>"` null-check literal that becomes `Object.getClass()` in 12.27, while the animation label
`"hazeAlpha"` remains. The transformation specifically demonstrates why null-check parameter
names cannot be required as semantic anchors; it does not make every remaining string stable.

## Resolved methods and parameter contracts

### 1. Tagged reply-field renderer

The test tag remains unique and stays in `Lcom/x/composer/minimal/b;->e(...)V`.

12.26 descriptor:

```text
Lcom/x/composer/minimal/b;->e(
  Ldev/chrisbanes/haze/s; Z
  Landroidx/compose/ui/graphics/c1;
  Landroidx/compose/ui/graphics/o0;
  Lcom/x/composer/minimal/l0;
  Ljava/lang/String;
  Landroidx/compose/foundation/interaction/l;
  Landroidx/compose/ui/focus/b0;
  Landroidx/activity/compose/p;
  Landroidx/activity/compose/p;
  Function1 Function1 Function2 Function1
  Function0 Function0 Function0 Function0 Function0 Function1
  Composer I I
)V
```

12.27 descriptor:

```text
Lcom/x/composer/minimal/b;->e(
  Ldev/chrisbanes/haze/t; Z
  Landroidx/compose/ui/graphics/h1;
  Landroidx/compose/ui/graphics/t0;
  Lcom/x/composer/minimal/k0;
  Ljava/lang/String;
  Landroidx/compose/foundation/interaction/l;
  Landroidx/compose/ui/focus/y;
  Landroidx/activity/compose/p;
  Landroidx/activity/compose/p;
  Function1 Function1 Function2 Function1
  Function0 Function0 Function0 Function0 Function0 Function1
  Composer I I
)V
```

Here and below, unqualified `Function0`, `Function1`, `Function2`, and `Composer` abbreviate
their standard Kotlin/Compose descriptors. The descriptor has the same semantic layout, while
Haze, graphics, minimal model, and focus implementation types changed.

Relevant bytecode:

```smali
# 12.26, offset 0x3b7
const-string v4, "post-detail-reply-text-field"
invoke-static v3, v4,
    Landroidx/compose/ui/platform/t1;->p(Modifier,String)Modifier

# 12.27, offset 0x3b3
const-string v6, "post-detail-reply-text-field"
invoke-static v5, v6,
    Landroidx/compose/ui/platform/m0;->w(Modifier,String)Modifier
```

The test-tag helper owner/name churned, but the semantic string and modifier transformation
remain.

### 2. Minimal-composer renderer caller

The production caller of the tagged renderer remains `minimal/b.c`. Its complete layout is
unchanged apart from the Haze and minimal-model descriptors:

```text
12.26:
Lcom/x/composer/minimal/b;->c(
  Haze/s, String, minimal/l0, channel/m, channel/m,
  Function1, Function1, Function2, Function1,
  Function0 x5,
  Function1 x3,
  Modifier, Composer, I, I
)V

12.27:
Lcom/x/composer/minimal/b;->c(
  Haze/t, String, minimal/k0, channel/m, channel/m,
  Function1, Function1, Function2, Function1,
  Function0 x5,
  Function1 x3,
  Modifier, Composer, I, I
)V
```

Both versions use 56 registers, 21 parameter registers, and 35 locals. Each method makes one
static call to its version's tagged renderer:

```smali
# 12.26, instruction 499 / offset 0x3c7
invoke-static/range {v0 .. v22},
    Lcom/x/composer/minimal/b;->e(...Haze/s...graphics/c1...graphics/o0...minimal/l0...)V

# 12.27, instruction 498 / offset 0x3c5
invoke-static/range {v0 .. v22},
    Lcom/x/composer/minimal/b;->e(...Haze/t...graphics/h1...graphics/t0...minimal/k0...)V
```

The current `isMinimalComposerRendererCaller` predicate therefore still succeeds: the method is
`void`, has one `Composer`, one `Modifier`, and a Haze parameter. The direct call edge excludes
the generated restart callback.

### 3. Minimal-composer container caller

This is the method rejected by the current string predicate.

| Version | Descriptor | Registers |
| --- | --- | --- |
| 12.26 | `Lcom/x/composer/minimal/b;->h(Lcom/x/composer/minimal/r;Ldev/chrisbanes/haze/s;Landroidx/compose/ui/Modifier;ZLkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;II)V` | 46 total, 8 params, 38 locals |
| 12.27 | `Lcom/x/composer/minimal/b;->h(Lcom/x/composer/minimal/q;Ldev/chrisbanes/haze/t;Landroidx/compose/ui/Modifier;ZLkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;II)V` | 40 total, 8 params, 32 locals |

Parameter roles and order are identical:

| Index | Semantic role | 12.26 type | 12.27 type |
| ---: | --- | --- | --- |
| p0 | Minimal composer/controller | `Lcom/x/composer/minimal/r;` | `Lcom/x/composer/minimal/q;` |
| p1 | Haze state | `Ldev/chrisbanes/haze/s;` | `Ldev/chrisbanes/haze/t;` |
| p2 | Modifier | `Landroidx/compose/ui/Modifier;` | same |
| p3 | Boolean option | `Z` | same |
| p4 | Content/action lambda | `Lkotlin/jvm/functions/Function1;` | same |
| p5 | Composer | `Landroidx/compose/runtime/Composer;` | same |
| p6 | Compose changed mask | `I` | same |
| p7 | Compose default mask | `I` | same |

The exact entry-bytecode difference explains the zero match:

```smali
# 12.26
move-object/from16 v1, p0
move-object/from16 v2, p1
const-string v3, "inlineComposer"
invoke-static v1, v3,
    Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Object,String)V
const-string v3, "hazeState"
invoke-static v2, v3,
    Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Object,String)V

# 12.27
move-object/from16 v1, p0
move/from16 v6, p6
invoke-virtual v1, Ljava/lang/Object;->getClass()Ljava/lang/Class;
# no const-string "inlineComposer"
# no const-string "hazeState" and no corresponding named p1 check
```

The `inlineComposer` non-null operation has been lowered to `getClass()`. The bytecode proves
that the named `hazeState` check was eliminated; it does not by itself prove whether the source
parameter became nullable or whether compiler/R8 analysis removed the check. Either way, a
parameter-name string is not a behavioral contract.

The container still has the same behavior. Both versions:

- derive state from the minimal-composer object;
- create three color objects and pass their array through `CollectionsKt.listOf`;
- build the vertical-gradient/Haze treatment;
- call `minimal/b.c` exactly once; and
- install a Compose restart callback.

Representative preserved bytecode shape:

```smali
# 12.26, instructions 186-203
sget-wide ..., Landroidx/compose/ui/graphics/z;->m:J
new-instance ..., Landroidx/compose/ui/graphics/z;
sget-wide ..., Landroidx/compose/ui/graphics/z;->b:J
new-instance ..., Landroidx/compose/ui/graphics/z;
new-instance ..., Landroidx/compose/ui/graphics/z;
filled-new-array ..., [Landroidx/compose/ui/graphics/z;
invoke-static ..., Lkotlin/collections/CollectionsKt;->listOf(...)Ljava/util/List;
invoke-static ..., (...)Landroidx/compose/ui/graphics/o0;

# 12.27, instructions 176-192
sget-wide ..., Landroidx/compose/ui/graphics/z;->m:J
new-instance ..., Landroidx/compose/ui/graphics/z;
sget-wide ..., Landroidx/compose/ui/graphics/z;->b:J
new-instance ..., Landroidx/compose/ui/graphics/z;
new-instance ..., Landroidx/compose/ui/graphics/z;
filled-new-array ..., [Landroidx/compose/ui/graphics/z;
invoke-static ..., Lkotlin/collections/CollectionsKt;->listOf(...)Ljava/util/List;
invoke-static ..., (...)Landroidx/compose/ui/graphics/t0;
```

The direct renderer-caller invocation is also preserved:

```smali
# 12.26, instruction 606 / offset 0x46f
invoke-static/range {v2 .. v22}, Lcom/x/composer/minimal/b;->c(...)V

# 12.27, instruction 570 / offset 0x430
invoke-static/range {v7 .. v27}, Lcom/x/composer/minimal/b;->c(...)V
```

Dexscope exact-signature dry runs return one `minimal/b.h` method in each APK. More importantly,
walking incoming references from the already-resolved `minimal/b.c` gives two callers per APK,
of which exactly one has the container ABI above; the other is a generated `Function2.invoke`
restart callback.

### 4. Post-detail sheet container caller

The post-detail caller moved under ordinary obfuscation and one dependency type changed package:

```text
12.26:
Lcom/x/postdetailsheet/t;->f(
  F, F,
  Lcom/x/postdetailsheet/a;,
  Ldev/chrisbanes/haze/s;,
  Lcom/x/photoeditor/tools/text/c;,
  Lkotlin/jvm/functions/Function1;,
  Lcom/x/postdetailsheet/w;,
  Landroidx/compose/runtime/Composer;,
  I
)V

12.27:
Lcom/x/postdetailsheet/s;->g(
  F, F,
  Lcom/x/postdetailsheet/a;,
  Ldev/chrisbanes/haze/t;,
  Lcom/x/photoeditor/tools/imageoverlay/j;,
  Lkotlin/jvm/functions/Function1;,
  Lcom/x/postdetailsheet/v;,
  Landroidx/compose/runtime/Composer;,
  I
)V
```

Both methods use 25 registers, 9 parameter registers, and 16 locals. The post-detail host still
computes its local navigation-inset modifier immediately before calling the minimal container:

```smali
# 12.26
invoke-static v0, Landroidx/compose/foundation/layout/c4;->e(Composer)e4
move-result-object v4
iget-object v4, v4, Landroidx/compose/foundation/layout/e4;->e:layout/c
invoke-static v3, v4, Landroidx/compose/foundation/layout/e;->s(Modifier,d4)Modifier
move-result-object v3
...
invoke-static/range {v0 .. v7}, Lcom/x/composer/minimal/b;->h(...)V

# 12.27
invoke-static v0, Landroidx/compose/foundation/layout/w2;->f(Composer)c4
move-result-object v4
iget-object v4, v4, Landroidx/compose/foundation/layout/c4;->e:layout/c
invoke-static v3, v4, Landroidx/compose/foundation/layout/f;->s(Modifier,b4)Modifier
move-result-object v3
...
invoke-static/range {v0 .. v7}, Lcom/x/composer/minimal/b;->h(...)V
```

The exact static call is instruction 288 / offset `0x21d` in 12.26 and instruction 286 / offset
`0x218` in 12.27. There is exactly one incoming reference from the
`Lcom/x/postdetailsheet/` scope in each APK. The current
`isPostDetailReplyBarContainer` predicate remains valid because it relies on the Haze and
post-detail type families plus `Composer`, not the changed one-character owners or the moved
photo-editor type.

## Call-hierarchy comparison

The semantic production chain is unchanged:

```text
12.26: minimal/b.e <- minimal/b.c <- minimal/b.h <- postdetailsheet/t.f
12.27: minimal/b.e <- minimal/b.c <- minimal/b.h <- postdetailsheet/s.g
```

Incoming-reference cardinality and generated callers are:

| Target layer | 12.26 incoming callers | 12.27 incoming callers | Interpretation |
| --- | --- | --- | --- |
| Tagged renderer `minimal/b.e` | `minimal/b.c`; `minimal/p0.invoke` | `minimal/b.c`; `minimal/o0.invoke` | One production wrapper plus one renderer restart lambda in both |
| Renderer caller `minimal/b.c` | `minimal/b.h`; `minimal/n0.invoke` | `minimal/b.h`; `minimal/m0.invoke` | One production container plus one wrapper restart lambda in both |
| Minimal container `minimal/b.h` | `compose/theme/f.invoke`; `minimal/w0.invoke`; `media/g1.a`; `postdetailsheet/t.f` | `compose/theme/d.invoke`; `minimal/v0.invoke`; `media/h1.a`; `postdetailsheet/s.g` | Four corresponding surfaces/callbacks; all owners churned except the stable package families |

The 12.27 callers of `rendererCaller` (`minimal/b.c`) deserve special attention:

1. `Lcom/x/composer/minimal/b;->h(...)V` is the production minimal-composer container. It has
   the eight-parameter Compose ABI, calls `b.c` once at offset `0x430`, and is the method the patch
   must guard.
2. `Lcom/x/composer/minimal/m0;->invoke(Object,Object)Object` is a synthetic class implementing
   `Function2`. It reloads 18 captured object fields and two integer mask fields, casts its first
   invocation argument to `Composer`, recalculates the masks, and calls `b.c` once at offset
   `0x5b`. It is the Compose restart callback and must not be selected as the production container.

In 12.26 the equivalent callback is `minimal/n0.invoke`. That older synthetic class is shared
with a DM-chat callback behind a packed switch; the 12.27 `minimal/m0` class is dedicated to this
minimal-composer restart path. This is generated-class restructuring, not a new production call
layer. The callback's `(Object,Object)->Object` signature is cleanly excluded by the container
ABI predicate.

## Cardinality proof

The graph walk itself is the strongest discriminator because every layer is resolved from the
exact method reference discovered at the previous layer.

| Resolution step | 12.26 | 12.27 | Required result |
| --- | ---: | ---: | --- |
| Unique `post-detail-reply-text-field` renderer | 1 | 1 | exactly one |
| Incoming renderer references in minimal scope | 2 | 2 | one production caller after predicate |
| `isMinimalComposerRendererCaller` candidates calling renderer | 1 | 1 | exactly one, one callsite |
| Incoming `rendererCaller` references in minimal scope | 2 | 2 | one production container after predicate |
| Ordered container-ABI candidates calling `rendererCaller` | 1 | 1 | exactly one, one callsite |
| Incoming minimal-container references globally | 4 | 4 | informational; multiple surfaces expected |
| Post-detail-scope container candidates | 1 | 1 | exactly one, one callsite |

No method number, instruction position, one-character class/method name, or exact obfuscated
model descriptor is needed for production resolution.

## Recommended resolver fix

### Container invariant

Replace `isMinimalComposerContainerCaller` with the ordered composable contract. Exact ordering
is appropriate here because it is the method ABI being validated, not a positional instruction
guess:

```kotlin
private fun Method.isMinimalComposerContainerCaller(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        parameters.size == 8 &&
        parameters[0].startsWith(COMPOSER_MINIMAL_SCOPE) &&
        parameters[1].startsWith(HAZE_SCOPE) &&
        parameters[2] == MODIFIER_DESCRIPTOR &&
        parameters[3] == "Z" &&
        parameters[4] == FUNCTION1_DESCRIPTOR &&
        parameters[5] == COMPOSER_DESCRIPTOR &&
        parameters[6] == "I" &&
        parameters[7] == "I"
}
```

Why these anchors are justified:

- The direct call to the dynamically resolved `rendererCaller` proves behavior and avoids an
  independent global fingerprint.
- `Composer`, `Modifier`, `Function1`, primitives, and the two Compose mask integers are public
  ABI/shape anchors.
- The first two parameters are constrained only to semantic package families. Their exact leaves
  changed `r -> q` and `s -> t` between the two builds.
- The full eight-parameter order excludes the generated `Function2.invoke` callback.
- No Kotlin parameter-name strings or Compose group-key literals are retained.

The existing `isMinimalComposerRendererCaller` and `isPostDetailReplyBarContainer` conditions
already resolve one candidate at their respective direct-call edges in both APKs. They can remain
minimal; adding all 21 renderer-wrapper parameters or the post-detail photo-editor descriptor
would add churn without improving current cardinality.

### Shared cardinality helper

Refactor `findUniqueCaller` to use `requireExactlyOne` for both the candidate collection and the
selected edge's callsite collection:

```kotlin
context(context: BytecodePatchContext)
private fun findUniqueCaller(
    target: Method,
    scope: String,
    label: String,
    predicate: (Method) -> Boolean,
): Method {
    val candidates = buildList {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(scope)) return@classDefForEach
            classDef.methods.forEach { method ->
                if (!predicate(method)) return@forEach
                val callSites = method.callSiteIndices(target)
                if (callSites.isNotEmpty()) add(method to callSites)
            }
        }
    }

    val (caller, callSites) = requireExactlyOne(label, candidates)
    requireExactlyOne("$label callsite to $target", callSites)
    return caller
}
```

The `Pair` default `toString()` includes the method and callsite list, so the optional `describe`
argument is unnecessary. This fails on zero callers, multiple callers, or multiple invocations
from the selected caller. Import the shared helper from
`app.crimera.patches.newx.utils.requireExactlyOne`; keep `requireAtMostOne` for the unrelated
optional navigation-insets read.

Once the string predicate is removed, `hasString`, `hasStrings`, and the `StringReference` import
appear unused in this file and should be removed if no other resolver work reuses them.

### What not to anchor

Do not encode any of these observed identities:

- owner `minimal/b` or method names `e`, `c`, and `h` as the behavior selector;
- minimal-model leaves `l0`, `r`, `k0`, or `q`;
- Haze leaves `s` or `t`;
- post-detail owners `t` or `s`, or methods `f` or `g`;
- photo-editor types `tools/text/c` or `tools/imageoverlay/j`;
- generated callback leaves `p0`, `o0`, `n0`, `m0`, `w0`, or `v0`;
- Compose runtime/helper leaves that changed between the APKs;
- `"inlineComposer"`, `"hazeState"`, `"hazeAlpha"`, or Compose group keys.

The package scopes are discovery boundaries and type-family checks. The behavior identity comes
from the unique tagged renderer and dynamically followed exact method references.

## Regression requirement for implementation

This is a real resolver failure and should be represented in the existing
`NewXResolverLinterTest` fixture corpus when the fix is implemented, without creating a new test
file. The focused unsafe fixture should model a required caller predicate that depends on Kotlin
parameter-name strings even though the direct-call edge and semantic Compose ABI are sufficient.
Its expected finding should cover redundant compiler-string anchoring; a safe counterpart should
cover the ordered ABI plus `requireExactlyOne` graph walk.

For patch validation, build the real MPP and patch both the 12.27 target and the 12.26 control
exclusively with `NewX: Hide post reply bar`. The expected patch-time evidence is one match and one
callsite at every graph edge. This recon did not change patch code or run an MPP/APK patch.

## Conclusion

12.27 is compatible with the existing hide-reply-bar mutation strategy. The renderer, wrapper,
gradient/Haze container, post-detail host, local navigation-insets flow, and direct-call topology
all remain. The resolver failed because it promoted two Kotlin null-check parameter names to
mandatory behavior anchors. Remove those strings, validate the minimal container's ordered
Compose ABI, and use `requireExactlyOne` at each caller and callsite boundary.
