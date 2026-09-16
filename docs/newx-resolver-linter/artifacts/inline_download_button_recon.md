# NewX inline download button resolver reconnaissance

Date: 2026-09-14

Target APK: `com.twitter.android` 12.27.0-alpha.01, version code `312270201`

Control APK: `com.twitter.android` 12.26.0-alpha.03, version code `312260203`

Source under inspection: `patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/InlineDownloadButtonPatch.kt`

Analysis tool: `~/.local/bin/dexscope`
No device was launched or controlled.

Checksums:

| APK | SHA-256 |
|---|---|
| `apks/twitter_12.26.0-alpha.03.apk` | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| `apks/twitter_12.27.0-alpha.01.apk` | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

## Executive result

The failing line 177 owner restriction is the immediate 12.27 failure. The renderer contract and the two model-field filters remain present, but the renderer was moved out of the `com/x/inlineactionbar` package by the 12.27 build:

| Release | Inline-entry model | Action enum | Renderer method |
|---|---|---|---|
| 12.26 control | `Lcom/x/models/k4;` | `Lcom/x/models/u5;` | `Lcom/x/inlineactionbar/h;->d(Lcom/x/models/k4;Lcom/x/inlineactionbar/x0;JFLdev/chrisbanes/haze/s;Landroidx/compose/ui/graphics/z;JLandroidx/compose/foundation/layout/c3;Lkotlin/jvm/functions/Function1;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V` |
| 12.27 target | `Lcom/x/models/l4;` | `Lcom/x/models/w5;` | `Lcom/arkivanov/decompose/errorhandler/a;->i(Lcom/x/models/l4;Lcom/x/inlineactionbar/v0;JFLdev/chrisbanes/haze/t;Landroidx/compose/ui/graphics/z;JLandroidx/compose/runtime/Composer;I)V` |

The exact parameter shape is still unique in both APKs. `definingClass = "Lcom/x/inlineactionbar/"` excludes the 12.27 owner, so it fails before any bytecode mutation. The correct resolver must omit that owner constraint and retain the dynamically resolved entry-model descriptor and field references.

There is a second 12.27 issue that will be exposed after line 177 is corrected: the icon lambda still matches, but its size read was hoisted before the packed switch. The current `branchStart until iconAccess.index` lookup expects a branch-local size read and will find none for the 12.27 `Q9` branch. The fix therefore needs a validated branch-local/pre-switch-hoisted shape adapter.

## Commands and cardinality evidence

The principal dry runs were:

```text
dexscope dry-run twitter_12.26...apk --return V \
  --param k4 --param x0 --param J --param F --param haze/s --param graphics/z \
  --param J --param layout/c3 --param Function1 --param Modifier --param Composer --param I
  MATCH_UNIQUE matches=1
  Lcom/x/inlineactionbar/h;->d(... )V

dexscope dry-run twitter_12.27...apk --return V \
  --param l4 --param v0 --param J --param F --param haze/t --param graphics/z \
  --param J --param layout/b3 --param Function1 --param Modifier --param Composer --param I
  MATCH_UNIQUE matches=1
  Lcom/arkivanov/decompose/errorhandler/a;->i(... )V
```

The equivalent no-owner resolver is the same fingerprint shape used by `DynamicColorPatch.kt` in `patchInlineActionTints()`. DEX inspection confirms one exact method in each APK; the owner restriction is the only reason the inline-download copy rejects 12.27.

## 1. NewX inline-action entry renderer

### Model anchors

`InlineActionEntry(actionType=`, `, count=`, and `, isEnabled=` resolve to one model `toString()` method in each APK:

| Release | Model `toString()` | Action field | Enabled field |
|---|---|---|---|
| 12.26 | `Lcom/x/models/k4;->toString()Ljava/lang/String;` | `Lcom/x/models/k4;->a:Lcom/x/models/u5;` | `Lcom/x/models/k4;->c:Z` |
| 12.27 | `Lcom/x/models/l4;->toString()Ljava/lang/String;` | `Lcom/x/models/l4;->a:Lcom/x/models/w5;` | `Lcom/x/models/l4;->c:Z` |

The relevant `toString()` bytecode is structurally identical:

```text
12.26 [0007] iget-object v1, p0, Lcom/x/models/k4;->a:Lcom/x/models/u5;
      [001d] iget-boolean v2, p0, Lcom/x/models/k4;->c:Z

12.27 [0007] iget-object v1, p0, Lcom/x/models/l4;->a:Lcom/x/models/w5;
      [001d] iget-boolean p0, p0, Lcom/x/models/l4;->c:Z
```

### Renderer proof

The current fingerprint parameters are the Compose-lowered entry-renderer shape:

```text
(InlineActionEntry, object, long, float, object, object, long, object, object,
 Modifier, Composer, int) -> void
```

The exact DEX methods are:

```text
12.26 Lcom/x/inlineactionbar/h;->d(
      Lcom/x/models/k4; Lcom/x/inlineactionbar/x0; J F
      Ldev/chrisbanes/haze/s; Landroidx/compose/ui/graphics/z; J
      Landroidx/compose/foundation/layout/c3;
      Lkotlin/jvm/functions/Function1; Landroidx/compose/ui/Modifier;
      Landroidx/compose/runtime/Composer; I)V

12.27 Lcom/arkivanov/decompose/errorhandler/a;->i(
      Lcom/x/models/l4; Lcom/x/inlineactionbar/v0; J F
      Ldev/chrisbanes/haze/t; Landroidx/compose/ui/graphics/z; J
      Landroidx/compose/foundation/layout/b3;
      Lkotlin/jvm/functions/Function1; Landroidx/compose/ui/Modifier;
      Landroidx/compose/runtime/Composer; I)V
```

Both are `public static final`. The two semantic filter reads are present at the same instruction positions:

```text
12.26 [00e5] iget-object v6, v1, Lcom/x/models/k4;->a:Lcom/x/models/u5;
      [00e7] iget-boolean v7, v1, Lcom/x/models/k4;->c:Z
      [018e] invoke-static/range {...}, Lcom/x/ui/common/s0;->a(... )V

12.27 [00e5] iget-object v6, v1, Lcom/x/models/l4;->a:Lcom/x/models/w5;
      [00e7] iget-boolean v7, v1, Lcom/x/models/l4;->c:Z
      [018c] invoke-static/range {...}, Lcom/x/ui/common/q0;->a(... )V
```

The downstream tint/layout helper changed owner and descriptors as ordinary obfuscation/refactoring, but it is not used as the resolver anchor. The renderer’s first parameter remains the resolved entry model and its fourth parameter remains the float icon size (`p4`), so the existing insertion point at instruction zero is semantically valid in both targets.

## 2. NewX TwitterShare icon lambda

### Icon resource-to-field resolution

`dexscope res` reports the same resource IDs in both APKs:

```text
ic_vector_share          = 0x7f0805d6
ic_vector_incoming_stroke = 0x7f080486
```

Both literals occur once in `Lcom/x/icons/a;-><clinit>()V`, and the current four-instruction forward search resolves them as follows:

| Resource | DEX literal | Resolved field |
|---|---|---|
| `ic_vector_share` | `const v1, 0x7f0805d6` at `0x15ee` | `Lcom/x/icons/a;->Q9:Lcom/x/icons/b;` |
| `ic_vector_incoming_stroke` | `const v2, 0x7f080486` at `0x0bfb` | `Lcom/x/icons/a;->V4:Lcom/x/icons/b;` |

The surrounding proof is identical in both releases:

```text
share:    const ..., 0x7f0805d6
          invoke-direct ..., Lcom/x/icons/b;-><init>(I)V
          sput-object ..., Lcom/x/icons/a;->Q9:Lcom/x/icons/b;

incoming: const ..., 0x7f080486
          invoke-direct ..., Lcom/x/icons/b;-><init>(I)V
          sput-object ..., Lcom/x/icons/a;->V4:Lcom/x/icons/b;
```

The fields are semantically distinct. `Q9` is the `TwitterShare` icon branch; `V4` is the incoming-stroke replacement icon injected by the extension. Neither field name should be hard-coded in patch code; the existing resource-based resolver is the right contract, with `requireExactlyOne` already applied.

### Lambda cardinality and bytecode

The current icon fingerprint does match in both releases:

```text
Lcom/x/cards/impl/unified/components/appstore/h;->invoke(
    Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
```

It has the required instance float field and the `Q9` static icon read. Xrefs to `Q9` include several unrelated consumers, but only the appstore lambda combines the share read with an instance field read of type `F` owned by the lambda receiver. Thus the package restriction is not needed for these two APKs; omitting it and requiring exactly one semantic candidate is more resilient to package movement while remaining fail-closed.

The share branch is present in both:

```text
12.26 [00d2] iget p2, p0, Lcom/x/cards/impl/unified/components/appstore/h;->b:F
      [00db] sget-object v1, Lcom/x/icons/a;->Q9:Lcom/x/icons/b;
      [00e0] invoke-static/range {...}, Lcom/x/compose/core/j;->d(... )V

12.27 [000a] iget p0, p0, Lcom/x/cards/impl/unified/components/appstore/h;->b:F
      [000c] packed-switch v0, :pswitch_data_0
      [00d9] sget-object v5, Lcom/x/icons/a;->Q9:Lcom/x/icons/b;
      [00de] invoke-static/range {...}, Lcom/x/compose/core/i;->d(... )V
```

The lambda class and semantic `Q9` branch survive unchanged. The method’s register count changes from 13 to 17 because of compiler/R8 register allocation; the patch already obtains scratch registers from the free-register provider.

### Required size-read shape adapter

This is the second compatibility break in the current file. In 12.26, the `Q9` branch begins after the preceding `return-object` and contains its own `iget ... b:F` before `sget Q9`. In 12.27, `b:F` is read once before the packed switch and reused by all branches. The current code:

```kotlin
val branchStart = instructions
    .subList(0, iconAccess.index)
    .indexOfLast { it.opcode == Opcode.RETURN_OBJECT } + 1
val sizeAccess = requireExactlyOne(
    "NewX TwitterShare branch size access",
    instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf {
            index in branchStart until iconAccess.index &&
                instruction.opcode == Opcode.IGET &&
                instruction.getReference<FieldReference>()?.toString() == sizeField.toString()
        }?.let { accessIndex -> accessIndex to instruction }
    },
)
```

will find the 12.26 branch-local read, but zero candidates for the 12.27 `Q9` branch because the only read is at `0x000a`, before `packed-switch` and before `branchStart`. Removing the owner restriction alone is therefore insufficient for a complete 12.27 port.

Recommended resolver contract:

1. Resolve the share icon access by the dynamically resolved `shareIconField`.
2. Resolve the receiver’s unique non-static `F` field from the matched lambda class, or derive it from the matched `iget` and assert that all selected accesses use the same field.
3. Resolve the `Q9` branch’s size-read shape as exactly one of two validated shapes:
   - one same-field read in the selected switch branch (12.26 shape), or
   - one same-field read before the selected packed switch that dominates the `Q9` access (12.27 shape).
4. Reject zero, both shapes, or ambiguous candidates with `PatchException`. Normalize the register immediately after the selected read, then select the incoming icon immediately after the `Q9` read.

This keeps one common downstream mutation while adapting only the proven control-flow shape. Do not choose the first `iget F` by instruction order without proving its relation to the `Q9` access.

## 3. Inline presenter type and event-handler hook

`inlinePresenterType` is already resolved indirectly by `resolvedNewXInlineActionBarModels()`; it is not hard-coded in `InlineDownloadButtonPatch.kt`:

| Release | Presenter/bar class | State-builder method | Presenter post field |
|---|---|---|---|
| 12.26 | `Lcom/x/inlineactionbar/k0;` | `d(Landroidx/compose/runtime/Composer;)Lcom/x/inlineactionbar/x0;` | `k0->b:Lcom/x/models/o1;` |
| 12.27 | `Lcom/x/inlineactionbar/i0;` | `d(Landroidx/compose/runtime/Composer;)Lcom/x/inlineactionbar/v0;` | `i0->b:Lcom/x/models/p1;` |

The bar resolver’s semantic proof remains stable: the builder reads the canonical-post accessor, converts the immutable action collection, and adds inline-action entries to an `ArrayList`. The exact builder bytecode shows the model/action collection flow in both versions:

```text
12.26 k0->d: [00f1] invoke-interface ..., Lcom/x/models/s5;->d()Lkotlinx/collections/immutable/b;
      [010c] check-cast ..., Lcom/x/models/k4;
      [010e] iget-object ..., Lcom/x/models/k4;->a:Lcom/x/models/u5;

12.27 i0->d: [00d2] invoke-interface ..., Lcom/x/models/u5;->d()Lkotlinx/collections/immutable/b;
      [00ec] check-cast ..., Lcom/x/models/l4;
      [00ef] iget-object ..., Lcom/x/models/l4;->a:Lcom/x/models/w5;
```

The collection consumer is also unchanged in meaning: old `k0->d` adds the resolved entry at
`[017e]` via `ArrayList->add(Object)`, while new `i0->d` does so at `[015f]`. The conversion
back to `kotlinx.collections.immutable.b` follows at old `[0186]`/`[018a]` and new
`[0167]`/`[016b]`. These are the semantic anchors used by the separate bar-model resolver.

The event-handler fingerprint also matches in both releases after using the dynamically resolved presenter and action-model descriptors:

```text
12.26 Lcom/x/inlineactionbar/k0;->e(
  Lcom/x/inlineactionbar/k0;Lcom/x/inlineactionbar/n0;Lkotlinx/coroutines/i0;
  Ljava/lang/String;Landroidx/compose/runtime/l1;Landroidx/compose/runtime/l1;
  Lcom/x/inlineactionbar/a0;)V

12.27 Lcom/x/inlineactionbar/i0;->e(
  Lcom/x/inlineactionbar/i0;Lcom/x/inlineactionbar/l0;Lkotlinx/coroutines/g0;
  Ljava/lang/String;Landroidx/compose/runtime/l1;Landroidx/compose/runtime/l1;
  Lcom/x/inlineactionbar/y;)V
```

Evidence in both handlers:

```text
12.26 [003b] iget-object ..., Lcom/x/models/k4;->a:Lcom/x/models/u5;
      [0067] invoke-virtual ..., Ljava/lang/Enum;->ordinal()I

12.27 [0034] iget-object ..., Lcom/x/models/l4;->a:Lcom/x/models/w5;
      [0060] invoke-virtual ..., Ljava/lang/Enum;->ordinal()I
```

Both handlers are static. The existing `parameterTypes.firstOrNull() == inlinePresenterType` assertion is an important contract check, and the event register calculation correctly accounts for wide parameters. No owner or method-name change is needed here; the presenter type is already derived from the bar-model resolver.

## 4. Other hooks in `InlineDownloadButtonPatch.kt`

### Initialization

The patch adds `InlineDownloadButton.initialize(Context)` at index zero of the shared NewX application `onCreate` hook. That hook is resolved by the separate extension hook using the stable superclass `Landroid/app/Application;`, not by an obfuscated NewX class. No 12.26/12.27-specific issue was found.

### Post-model bridges

`patchPostModelBridges()` uses resolved model fields and does not route on version:

| Bridge | 12.26 evidence | 12.27 evidence |
|---|---|---|
| presenter post | `k0->b:o1` | `i0->b:p1` |
| contextual canonical post | `o1->b:r0` | `p1->b:s0` |
| canonical media | `r0->d:Lkotlinx/collections/immutable/b;` | `s0->d:Lkotlinx/collections/immutable/b;` |
| contextual repost | `o1->d:Lcom/x/models/k8;` | `p1->d:Lcom/x/models/k8;` |
| entry constructor | `(u5, Long, Z)V` | `(w5, Long, Z)V` |
| injected carrier | `u5->TwitterShare:u5` | `w5->TwitterShare:w5` |

The constructor resolver matches the resolved action enum, `Ljava/lang/Long;`, and `Z`; the `TwitterShare` field is a semantic enum constant and is present in both APKs. The bridge mutation is therefore compatible with both versions.

### Icon-field registry owner

The current `resolveIconField()` also restricts the `<clinit>` to `Lcom/x/icons/`. That owner is unchanged in these APKs and the resource literal is unique, so it is not part of the present failure. For future R8/package movement, the stronger invariant is “one `<clinit>` containing the resolved drawable ID followed within four instructions by one `SPUT_OBJECT`,” with `requireExactlyOne` over the resulting field references. Do not replace the resource anchor with `Q9`/`V4` field names.

## Recommended code change

1. Delete the private `requireSingle()` wrapper and use the shared `requireExactlyOne()` for the three resolver match collections in this file. This preserves cardinality, candidate descriptions, and the repository’s fail-closed resolver policy.
2. For the entry renderer, remove only `definingClass = "Lcom/x/inlineactionbar/"`. Keep the dynamically resolved entry-model descriptor, action field, enabled field, Compose parameter shape, and `V` return type. The expected cardinality is exactly one in both declared APKs.
3. For the icon lambda, prefer the semantic `Q9` plus receiver-owned `F`-field invariant without requiring the appstore package. Require exactly one candidate; if a future target produces more than one, fail with candidate descriptions.
4. Add the branch-local versus pre-switch-hoisted size-read adapter described above. Require exactly one proven shape and preserve the existing later-index-first mutation order.
5. Leave `inlinePresenterType` and the event handler on the resolved bar-model descriptor. Keep the presenter-first-parameter assertion and static check.

A representative resolver skeleton for the immediate renderer fix is:

```kotlin
val inlineRenderer = requireExactlyOne(
    "NewX inline-action entry renderer",
    Fingerprint(
        parameters = listOf(
            entryModels.inlineActionEntryDescriptor,
            "L", "J", "F", "L", "L", "J", "L", "L",
            MODIFIER, COMPOSER, "I",
        ),
        returnType = "V",
        filters = listOf(
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                reference = entryModels.inlineActionTypeField,
            ),
            fieldAccess(
                opcode = Opcode.IGET_BOOLEAN,
                reference = entryModels.inlineActionEnabledField,
            ),
        ),
    ).scopedMatchAll(),
).also { it.method.requireStatic("NewX inline-action entry renderer") }
```

The `L` positions remain the existing Compose object wildcards; the only removed constraint is the unstable owner package. The icon and size-read resolvers must be changed together so that a successful renderer match cannot simply advance to a deterministic zero-match failure in the 12.27 branch layout.

## Final disposition

The 12.27 renderer failure is confirmed as an owner/package move, not an action-model contract change. The icon lambda and resource fields do match in 12.27, but the icon size read changed control-flow shape. A complete port requires both the owner-agnostic renderer resolver and the explicit size-read shape adapter, with `requireExactlyOne`/`PatchException` cardinality checks retained throughout. No version-number routing or one-character obfuscated identity should be introduced.
