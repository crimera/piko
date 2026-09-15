# Hide post dividers recon: Twitter/X 12.27.0-alpha.01

## Outcome

The thread connector modifier still exists in 12.27.0-alpha.01. Its method
signature, parameter order, return type, semantic body, and enclosing
`Lcom/x/ui/common/` package are unchanged. The declaring class's obfuscated
leaf changed from `m` to `l`.

Exact descriptors:

- 12.26.0-alpha.03 control:
  `Lcom/x/ui/common/m;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;`
- 12.27.0-alpha.01 target:
  `Lcom/x/ui/common/l;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;`

The direct cause of the fingerprint failure is the loss of the Kotlin
parameter-name string `"$this$threadConnector"`. In 12.26, Kotlin emits a
`const-string` followed by `Intrinsics.checkNotNullParameter`. In 12.27, the
same non-null contract is lowered to `p0.getClass()`, so no parameter-name
string remains in the method or APK string-anchor result.

`Instruction::isDrawModifierCall` remains valid. Each target method contains
exactly one static call with the semantic shape
`(Modifier, Function1) -> Modifier`. Its AndroidX owner was obfuscated from
`Landroidx/compose/ui/draw/j;` to `Landroidx/compose/ui/draw/h;`, but the helper
already ignores owner and method name, so that churn does not cause the
failure.

The recommended repair is one package-independent semantic resolver shared by
both versions:

1. Select static methods with the exact
   `(Modifier, Composer, int) -> Modifier` ABI.
2. Require exactly one static `(Modifier, Function1) -> Modifier` draw call.
3. Validate that the draw call result is combined with the method's incoming
   `Modifier` by an interface call shaped `(Modifier) -> Modifier`, and that
   the combined result is returned.
4. Resolve the qualifying method with the shared `requireExactlyOne` helper.

This produces one semantic candidate in each supplied APK without relying on
`m`, `l`, `C`, a parameter-name string, a release number, or an obfuscated
AndroidX draw owner. A smaller immediate repair—remove only the string check
while retaining `Lcom/x/ui/common/`, the exact ABI, the draw-call count, and
the existing `requireExactlyOne`—is also unique in both APKs, but is less
resilient to future package movement.

## Frozen inputs

Analysis was performed from source commit
`d6efb26d263000ed9960069dd6fdc93348f12d90`. The worktree was already dirty;
this recon only adds this artifact.

| Role | Path | Manifest identity | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, versionCode `312260203`, versionName `12.26.0-alpha.03` | 123,674,627 bytes | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, versionCode `312270201`, versionName `12.27.0-alpha.01` | 116,310,120 bytes | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

The sweep incident is recorded at
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## Current fingerprint and reproduced failure

`NewXThreadConnectorFingerprint` currently requires:

1. A defining class under `Lcom/x/ui/common/`.
2. Return type `Landroidx/compose/ui/Modifier;`.
3. Exact parameters `(Modifier, Composer, I)`.
4. A string reference equal to `"$this$threadConnector"`.
5. Exactly one `isDrawModifierCall` instruction.

Dexscope finds the required string only in the control:

```text
12.26.0-alpha.03
  Lcom/x/ui/common/m;->C(Modifier,Composer,I)Modifier @ 0x0 CONST_STRING

12.27.0-alpha.01
  no match
```

Removing the string constraint while retaining the common-UI scope and exact
ABI gives exact cardinality one in each APK:

```text
12.26.0-alpha.03  MATCH_UNIQUE  matches=1
  Lcom/x/ui/common/m;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

12.27.0-alpha.01  MATCH_UNIQUE  matches=1
  Lcom/x/ui/common/l;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;
```

The class declarations also agree on the method contract:

```text
12.26: public static final C(Modifier,Composer,I)Modifier
       registers=7, instructions=38
12.27: public static final C(Modifier,Composer,I)Modifier
       registers=6, instructions=36
```

The one-character owner change is ordinary R8/compiler churn. The method name
`C` happens to remain the same, but it is not a permissible invariant.

## Bytecode evidence

### 12.26.0-alpha.03 control

Method:

```text
Lcom/x/ui/common/m;->C(
    Landroidx/compose/ui/Modifier;,
    Landroidx/compose/runtime/Composer;,
    I
)Landroidx/compose/ui/Modifier;
```

Registers: 7 total, 3 parameters, 4 locals. Relevant instructions:

```smali
[0000] const-string p2, "$this$threadConnector"
[0002] invoke-static p0, p2,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(
           Ljava/lang/Object;Ljava/lang/String;
       )V

[0005] const/4 p2, 2
[0006] int-to-float p2, p2
[0008] invoke-static p1, v0,
       Lcom/google/android/gms/dynamite/e;->G(
           Landroidx/compose/runtime/Composer;I
       )Lcom/x/compose/theme/b;
[000c] iget-wide v0, v0, Lcom/x/compose/theme/b;->e:J

[002f] new-instance v3, Landroidx/compose/material3/b5;
[0032] invoke-direct v3, p2, v0, v1, v2,
       Landroidx/compose/material3/b5;-><init>(FJI)V
[0038] check-cast v3, Lkotlin/jvm/functions/Function1;
[003a] sget-object p1,
       Landroidx/compose/ui/t;->a:Landroidx/compose/ui/t;
[003c] invoke-static p1, v3,
       Landroidx/compose/ui/draw/j;->d(
           Landroidx/compose/ui/Modifier;,
           Lkotlin/jvm/functions/Function1;
       )Landroidx/compose/ui/Modifier;
[003f] move-result-object p1
[0040] invoke-interface p0, p1,
       Landroidx/compose/ui/Modifier;->n0(
           Landroidx/compose/ui/Modifier;
       )Landroidx/compose/ui/Modifier;
[0043] move-result-object p0
[0044] return-object p0
```

The decompiled terminal expression is equivalent to:

```text
return modifier.n0(drawModifier(singletonModifier, cachedDrawLambda));
```

The draw lambda captures a `2f` width and theme color `b.e:J`.

### 12.27.0-alpha.01 target

Method:

```text
Lcom/x/ui/common/l;->C(
    Landroidx/compose/ui/Modifier;,
    Landroidx/compose/runtime/Composer;,
    I
)Landroidx/compose/ui/Modifier;
```

Registers: 6 total, 3 parameters, 3 locals. Relevant instructions:

```smali
[0000] invoke-virtual p0,
       Ljava/lang/Object;->getClass()Ljava/lang/Class;

[0003] const/4 p2, 0
[0004] invoke-static p1, p2,
       Lcom/bumptech/glide/e;->y(
           Landroidx/compose/runtime/Composer;I
       )Lcom/x/compose/theme/b;
[0008] iget-wide v0, p2, Lcom/x/compose/theme/b;->e:J
[000d] const/high16 v2, 0x40000000  # 2.0f

[002d] new-instance v2, Landroidx/compose/foundation/text/c;
[0031] invoke-direct v2, v0, v1, p2,
       Landroidx/compose/foundation/text/c;-><init>(JI)V
[0037] check-cast v2, Lkotlin/jvm/functions/Function1;
[0039] sget-object p1,
       Landroidx/compose/ui/q;->a:Landroidx/compose/ui/q;
[003b] invoke-static p1, v2,
       Landroidx/compose/ui/draw/h;->d(
           Landroidx/compose/ui/Modifier;,
           Lkotlin/jvm/functions/Function1;
       )Landroidx/compose/ui/Modifier;
[003e] move-result-object p1
[003f] invoke-interface p0, p1,
       Landroidx/compose/ui/Modifier;->n0(
           Landroidx/compose/ui/Modifier;
       )Landroidx/compose/ui/Modifier;
[0042] move-result-object p0
[0043] return-object p0
```

This is the same behavior: derive the theme connector color, memoize a draw
lambda, create one draw modifier, combine it with the incoming modifier, and
return the combined modifier.

The following changes are implementation churn and should not be anchors:

- Parameter null check:
  `Intrinsics.checkNotNullParameter(..., "$this$threadConnector")` became
  `Object.getClass()`.
- Theme accessor: `dynamite/e.G` became `glide/e.y`.
- Compose cache calls: `m0.U/t0` became `m0.T/r0`.
- Draw-lambda implementation: `material3/b5(FJI)` became
  `foundation/text/c(JI)`; the `2.0f` is now internalized by the lambda shape.
- Modifier singleton: `androidx/compose/ui/t.a` became
  `androidx/compose/ui/q.a`.
- Draw helper owner: `androidx/compose/ui/draw/j.d` became
  `androidx/compose/ui/draw/h.d`.

The stable public/framework shapes survive all of these changes.

### Draw-lambda confirmation

The captured Function1 bodies confirm that this is specifically the vertical
thread connector, not merely an arbitrary draw modifier.

In 12.26, `material3/b5` is constructed with discriminator `1`, width `2f`,
and theme color `J`. Its default `invoke(Object)` branch casts the argument to
the Compose draw scope, computes the avatar-aligned x coordinate with RTL/LTR
handling, builds points from y=`0` to the scope height, and invokes the draw
line helper with the captured color and stroke width.

In 12.27, `foundation/text/c` is constructed with discriminator `11` and the
same theme color. Packed-switch case 11 performs the same operation: it casts
to the draw scope, computes the RTL/LTR x coordinate from `20f` plus half of a
`2f` stroke, builds top-to-bottom endpoints, and invokes the draw-line helper
with the captured color and a `2f` stroke. The synthetic class, constructor
shape, draw-scope interface names, and helper names all churned, so this
deeper body is confirmation rather than a recommended fingerprint boundary.

## Caller evidence for semantic identity

Incoming references corroborate that the two descriptors occupy the same
threaded-post path.

The control descriptor has four incoming calls: three from
`Lcom/x/jetfuel/v2/element/attribute/m;->a(...)V` and one from
`Lcom/x/urt/items/post/t1;->a(...)V`. The target descriptor also has four:
three from `Lcom/x/media/playback/e;->a(...)V` and one from
`Lcom/x/urt/items/post/s1;->a(...)V`. These owners are reconnaissance evidence,
not proposed anchors.

A representative callsite pair has the same control-flow and consumption:

```smali
# 12.26, element/attribute/m, offset 0x26c
invoke-static v3, v6, v4, Lcom/x/ui/common/m;->C(Modifier,Composer,I)Modifier
move-result-object v3
invoke-static v3, v6, v4, Landroidx/compose/foundation/layout/t;->a(Modifier,Composer,I)V

# 12.27, media/playback/e, offset 0x285
invoke-static v0, v6, v3, Lcom/x/ui/common/l;->C(Modifier,Composer,I)Modifier
move-result-object v0
invoke-static v0, v6, v3, Landroidx/compose/foundation/layout/t;->a(Modifier,Composer,I)V
```

In both releases, the caller first applies padding and width, invokes the
connector modifier, and immediately consumes its returned modifier in the
same Compose layout helper. The surrounding Compose group key
`-0x550e2438` is also unchanged, but numeric group keys should not be required
because generated Compose code can regenerate them.

## App-wide cardinality and false candidates

Without a package restriction, the exact ABI has two matches in the control
and three in the target:

| APK | Exact-ABI candidate | Static `(Modifier, Function1) -> Modifier` draw calls | Semantic result |
| --- | --- | ---: | --- |
| 12.26 | `Lcom/x/ui/common/m;->C(...)Modifier` | 1 | connector |
| 12.26 | `Lcom/x/payments/ui/g;->a(...)Modifier` | 0 | shared-transition modifier |
| 12.27 | `Lcom/x/ui/common/l;->C(...)Modifier` | 1 | connector |
| 12.27 | `Lcom/x/payments/ui/f;->a(...)Modifier` | 0 | shared-transition modifier |
| 12.27 | `Lcom/google/android/gms/internal/mlkit_vision_face/y7;->c(...)Modifier` | 0 | autoplay modifier wrapper |

Dexscope was used to inspect all five complete method bodies. The payments
methods build a `SharedTransitionScope` modifier and have no Function1 draw
helper. The 12.27 ML Kit-owned candidate delegates to a media-autoplay helper
with parameters `(Modifier, autoplay-state, String, Composer, I)` and likewise
has no draw helper.

Consequently, the existing `isDrawModifierCall` shape reduces the app-wide
exact-ABI set to exactly one method per supplied APK. The modifier-combination
data-flow check provides additional fail-closed protection if another method
with this ABI and a draw call appears later.

## Recommended resolver

### Preferred invariant

Use a broad candidate fingerprint based on the stable ABI, then a semantic
shape predicate or resolver:

```kotlin
private object NewXThreadConnectorCandidateFingerprint : Fingerprint(
    returnType = MODIFIER_DESCRIPTOR,
    parameters = listOf(MODIFIER_DESCRIPTOR, COMPOSER_DESCRIPTOR, INSETS_DESCRIPTOR),
    custom = { method, _ -> method.hasThreadConnectorModifierFlow() },
)
```

`hasThreadConnectorModifierFlow()` should require all of the following:

1. The method is static, so the first declared parameter is the incoming
   modifier and not an instance receiver.
2. There is exactly one `INVOKE_STATIC` or `INVOKE_STATIC_RANGE` whose method
   reference returns `Modifier` and has exact parameters
   `(Modifier, Function1)`. This is the existing `isDrawModifierCall`; retain
   it because it already ignores the churned owner/name.
3. That invoke is followed by its required `MOVE_RESULT_OBJECT`; record the
   produced register.
4. Exactly one later `INVOKE_INTERFACE` has declaring type `Modifier`, returns
   `Modifier`, and takes one `Modifier`. Do not require the obfuscated name
   `n0`.
5. The interface-call receiver is the method's incoming modifier register and
   its argument is the draw result register (allow only explicitly traced
   `MOVE_OBJECT` aliases if needed).
6. The interface-call result is the object returned by the method.

Collect all methods satisfying that predicate, then preserve the current
fail-closed boundary:

```kotlin
val connector = requireExactlyOne(
    label = "NewX thread connector",
    candidates = NewXThreadConnectorCandidateFingerprint.scopedMatchAll(),
)
```

Do not add separate 12.26/12.27 fingerprints or route on version. Both APKs
implement one contract. Do not use the observed leaf classes `m`/`l`, method
name `C`, draw owners `j`/`h`, lambda owners, caller owners, fixed offsets, or
Compose group-key literals.

The existing entry mutation remains the smallest safe patch point. Returning
the incoming `p0` when the setting is enabled bypasses only construction of
the connector draw modifier and leaves callers and unrelated draw helpers
unchanged.

### Minimal compatibility repair

For a lower-complexity immediate port, remove only the parameter-string
condition:

```kotlin
custom = { method, _ ->
    method.instructions.count(Instruction::isDrawModifierCall) == 1
}
```

Keep the existing common-UI scope, exact ABI, and outer `requireExactlyOne`.
Dexscope confirms this yields one match in each supplied APK. This is safe for
the two declared targets, but the preferred resolver is more robust if R8 or
a product refactor later relocates the helper outside `Lcom/x/ui/common/`.

## Validation required when implementing

This recon did not change patch code, build an MPP, patch an APK, install an
APK, or interact with a device.

The implementation should:

1. Add the real false-negative to the existing
   `NewXResolverLinterTest.kt` fixture corpus: the old named Intrinsics null
   check and the new `Object.getClass()` lowering should both retain the same
   semantic draw flow, and the resolver should match both.
2. Add the exact-ABI payments/autoplay shapes as non-matching controls in that
   existing suite so an ABI-only regression becomes visible. Do not create a
   new test file.
3. Run the full NewX tests and
   `./gradlew :patches:lintNewxResolvers`; compare against the committed
   linter baseline as required by the repository rules.
4. Build the real MPP and patch both frozen APKs independently. Confirm
   `NewX: Hide post dividers` reports `Applied`, each run reports `Saved to`,
   and record artifact paths and checksums.
5. Ask the user to runtime-check normal posts, threaded replies, and reply
   facepiles with the setting both off and on. Runtime/device interaction is a
   user action unless separately authorized.

## Representative dexscope commands

```sh
~/.local/bin/dexscope anchors \
  --apk ./apks/twitter_12.26.0-alpha.03.apk \
  --apk ./apks/twitter_12.27.0-alpha.01.apk \
  --string '$this$threadConnector' --limit 50

~/.local/bin/dexscope dry-run \
  ./apks/twitter_12.26.0-alpha.03.apk \
  ./apks/twitter_12.27.0-alpha.01.apk \
  --class 'Lcom/x/ui/common/' \
  --return 'Landroidx/compose/ui/Modifier;' \
  --param 'Landroidx/compose/ui/Modifier;' \
  --param 'Landroidx/compose/runtime/Composer;' \
  --param 'I' --limit 100

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.26.0-alpha.03.apk \
  'Lcom/x/ui/common/m;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;' \
  --limit 220

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.27.0-alpha.01.apk \
  'Lcom/x/ui/common/l;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;' \
  --limit 220

~/.local/bin/dexscope xref \
  ./apks/twitter_12.27.0-alpha.01.apk \
  'Lcom/x/ui/common/l;->C(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;' \
  --direction in --kind method --limit 100
```
