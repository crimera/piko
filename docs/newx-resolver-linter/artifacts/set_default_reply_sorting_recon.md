# NewX: Set default reply sorting recon — Twitter/X 12.27 alpha

## Outcome

The reported failure is caused by the first fingerprint requiring the compiler-generated
`"rankingMode"` parameter-name string. That string is present in the 12.26 control but is
absent from the 12.27 APK. The semantic repository initializer is still present and has the
same role; it moved from `Lcom/x/postdetail/l;->invokeSuspend(...)` to
`Lcom/x/postdetail/j;->invokeSuspend(...)`, and its ranking enum changed from
`Lcom/x/models/ze` to `Lcom/x/models/af`.

The three fingerprints behave as follows against the supplied APKs:

| Fingerprint | 12.26 control | 12.27 target | Finding |
| --- | --- | --- | --- |
| `NewXComposeReplySortingFingerprint` | One candidate: `Lcom/x/postdetail/l;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;` | Zero candidates as written | `rankingMode` was removed by Kotlin lowering; `Relevance` and `timelineRepository` remain in the semantic path. |
| `NewXComposeReplySortingSelectionFingerprint` | One candidate: `Lcom/x/payments/transaction/p2ptransfer/i1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;` | One candidate: `Lcom/x/payments/transaction/external/entityselection/steps/h;->invoke(Ljava/lang/Object;)Ljava/lang/Object;` | The current predicate still matches 12.27. The callback branch and enum descriptor changed, but the FunctionReference shape and both string anchors remain. |
| `NewXComposeReplySortingUiStateFingerprint` | One candidate: `Lcom/x/ui/common/b2;->invoke()Ljava/lang/Object;` | Zero candidates as written | The equivalent shared `Function0` moved to `Lcom/x/urt/items/post/u0;->invoke()Ljava/lang/Object;`, outside the hardcoded `ui/common` scope. |

The first failure prevents the current patch from reaching the selection and UI-state checks.
Bytecode evidence nevertheless shows that the selection resolver is already viable on 12.27,
while the UI-state resolver needs a separate scope-independent semantic repair.

No source patch, MPP rebuild, APK patch, installation, or runtime/device test was performed in
this recon. This document is the requested evidence and fix proposal.

## Frozen inputs

Analysis date: 2026-09-14. The worktree was already dirty before this recon; only this artifact
was added.

| Role | APK | Package / version | Version code | Size | SHA-256 |
| --- | --- | --- | ---: | ---: | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android` / `12.26.0-alpha.03` | `312260203` | `123674627` bytes | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android` / `12.27.0-alpha.01` | `312270201` | `116310120` bytes | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

Other manifest facts: both target SDK 36; the control has min SDK 28 and the target has min SDK
32. The source checkout is at commit
`d6efb26d263000ed9960069dd6fdc93348f12d90`. The reported sweep is also recorded in
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## Current source predicates

The investigated source is
`patches/src/main/kotlin/app/crimera/patches/newx/misc/replysorting/DefaultReplySortingPatch.kt`.

### Repository initializer — lines 49–60

```kotlin
private object NewXComposeReplySortingFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT, name = "Relevance"),
        string("rankingMode"),
        string("timelineRepository"),
    ),
)
```

The `fieldAccess` and `timelineRepository` anchors are semantic support for the repository
initialization. `rankingMode` is only the Kotlin parameter name passed to
`Intrinsics.checkNotNullParameter`; it is not a runtime behavior contract.

### Selection callback — lines 66–82

The predicate requires a method under the semantic payments-transaction package with:

```text
return type: Object
parameters: (Object)
class superclass: kotlin.jvm.internal.FunctionReferenceImpl
class interface: kotlin.jvm.functions.Function1
method string: timelineRepository
method string: defaultUrtTimelineComponent
```

This shape remains present in the target. The target's concrete owner and switch branch are
different, but neither is used by the predicate.

### UI state initializer — lines 88–106

The predicate requires a `Function0` method under `Lcom/x/ui/common/`, with an
`SGET_OBJECT` of the self-typed field `Relevance` and a static call into
`Landroidx/compose/runtime/`. The target keeps the latter two semantic properties but moves
the shared synthetic function class to `Lcom/x/urt/items/post/`.

The failure is thrown at line 146, before the selection and UI-state fingerprints are evaluated.

## Anchor and cardinality evidence

The key commands were run against both explicit APK paths with `~/.local/bin/dexscope`.

### `rankingMode`

```text
12.26:
Lcom/x/postdetail/l;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object; @ 0x245 CONST_STRING

12.27:
no result
```

The 12.26 instruction is the parameter-name check:

```smali
[0245] const-string v5, "rankingMode"
[0247] invoke-static v8, v5,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
```

There is no `rankingMode` string anchor anywhere in the target APK, so the current first
fingerprint must return zero even though its other anchors remain.

### `timelineRepository`

Relevant results from `dexscope anchors --string timelineRepository --limit 100`:

```text
12.26:
Lcom/x/postdetail/l;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object; @ 0x2bc CONST_STRING
Lcom/x/payments/transaction/p2ptransfer/i1;->invoke(Ljava/lang/Object;)Ljava/lang/Object; @ 0x44d CONST_STRING

12.27:
Lcom/x/postdetail/j;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object; @ 0x283 CONST_STRING
Lcom/x/payments/transaction/external/entityselection/steps/h;->invoke(Ljava/lang/Object;)Ljava/lang/Object; @ 0x4c5 CONST_STRING
```

The target therefore still has one initializer-side property guard and one callback-side
property guard. The target's Kotlin null-check lowering did not remove this property name.

### `defaultUrtTimelineComponent`

Relevant results from `dexscope anchors --string defaultUrtTimelineComponent --limit 100`:

```text
12.26:
Lcom/x/payments/transaction/p2ptransfer/i1;->invoke(Ljava/lang/Object;)Ljava/lang/Object; @ 0x463 CONST_STRING

12.27:
Lcom/x/payments/transaction/external/entityselection/steps/h;->invoke(Ljava/lang/Object;)Ljava/lang/Object; @ 0x4d6 CONST_STRING
```

The callback fingerprint consequently has one target method satisfying both string anchors and
the declared class/method shape.

### Self-typed `Relevance` field reads

The enum owner changed, but the field contract did not:

| APK | Ranking enum fields | Repository initializer read | UI-state read |
| --- | --- | --- | --- |
| 12.26 | `Likes`, `Recency`, `Relevance` in `Lcom/x/models/ze;` | `Lcom/x/postdetail/l;->invokeSuspend(...)` `SGET_OBJECT` offset 572 / byte offset `0x23c` | `Lcom/x/ui/common/b2;->invoke()` `SGET_OBJECT` offset 63 / byte offset `0x3f` |
| 12.27 | `Likes`, `Recency`, `Relevance` in `Lcom/x/models/af;` | `Lcom/x/postdetail/j;->invokeSuspend(...)` `SGET_OBJECT` offset 558 / byte offset `0x22e` | `Lcom/x/urt/items/post/u0;->invoke()` `SGET_OBJECT` offset 168 / byte offset `0xa8` |

The relevant `xref` results were:

```text
12.26:
Lcom/x/postdetail/l;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
  [SGET_OBJECT @ offset 572 (#318)] -> Lcom/x/models/ze;->Relevance:Lcom/x/models/ze;
Lcom/x/ui/common/b2;->invoke()Ljava/lang/Object;
  [SGET_OBJECT @ offset 63 (#37)] -> Lcom/x/models/ze;->Relevance:Lcom/x/models/ze;

12.27:
Lcom/x/postdetail/j;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
  [SGET_OBJECT @ offset 558 (#310)] -> Lcom/x/models/af;->Relevance:Lcom/x/models/af;
Lcom/x/urt/items/post/u0;->invoke()Ljava/lang/Object;
  [SGET_OBJECT @ offset 168 (#96)] -> Lcom/x/models/af;->Relevance:Lcom/x/models/af;
```

The target field-read list contains no `Lcom/x/ui/common/...` method. The other target reads are
enum/class initializers, the repository initializer, or unrelated code and do not satisfy the
`Function0` plus Compose-state shape.

## 1. Repository initializer trace

### 12.26 control

`dexscope inspect-class` reports:

```text
class: Lcom/x/postdetail/l;
super: Lkotlin/coroutines/jvm/internal/SuspendLambda;
interfaces: Lkotlin/jvm/functions/Function2;
method: invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
registers: 54 total, 2 parameter registers, 52 locals
receiver: p0 (v52)
parameter: p1 (v53): Object
```

The exact cache-miss and repository-construction path is:

```smali
[021f] iget-object v10, v0, Lcom/x/postdetail/l;->A:Lcom/arkivanov/decompose/d;
[0221] invoke-interface v10,
       Lcom/arkivanov/decompose/d;->c()Landroidx/compose/foundation/text/input/internal/selection/z;
[0224] move-result-object v1
[0225] const-class v3, Lcom/x/repositories/post/t;
[0227] invoke-static v3, Lkotlin/jvm/internal/Reflection;->c(Ljava/lang/Class;)Lkotlin/reflect/KType;
[022a] move-result-object v3
[022b] invoke-virtual v1, v3,
       Landroidx/compose/foundation/text/input/internal/selection/z;->e(Ljava/lang/Object;)Lcom/arkivanov/essenty/instancekeeper/a;
[022e] move-result-object v8
[0231] if-nez v8, :cond_32

[0233] invoke-virtual v2, Lcom/x/postdetail/n;->A()Lcom/x/models/z5;
[0236] move-result-object v8
[023a] iget-wide v4, v8, Lcom/x/models/z5;->a:J
[023c] sget-object v8, Lcom/x/models/ze;->Relevance:Lcom/x/models/ze;
[023e] move-wide/from16 v24, v4
[0240] iget-object v4, v0, Lcom/x/postdetail/l;->D:Lcom/x/repositories/post/g;
[0242] invoke-virtual v4, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0245] const-string v5, "rankingMode"
[0247] invoke-static v8, v5,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
[024a] iget-object v4, v4, Lcom/x/repositories/post/g;->a:Lcom/x/profile/about/r;

[028c] const-string/jumbo v5, "urtTimelineRepositoryFactory"
[028f] invoke-static v4, v5,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
[0292] new-instance v21, Lcom/x/repositories/post/t;
[0294] move-object/from16 v28, v4
[0296] move-object/from16 v22, v6
[0298] move-object/from16 v23, v7
[029a] move-object/from16 v26, v8
[029c] move-object/from16 v27, v14
[029e] invoke-direct/range {v21 .. v28},
       Lcom/x/repositories/post/t;-><init>(
           Lcom/x/featureswitches/FeatureSwitchesRepository;
          Ljavax/inject/a;J;
          Lcom/x/models/ze;
          Lcom/x/googlessp/c1;
          Lcom/x/repositories/urt/w;)V
[02a1] move-object/from16 v8, v21
[02a3] invoke-virtual v1, v3, v8,
       Landroidx/compose/foundation/text/input/internal/selection/z;->p(
           Ljava/lang/Object;Lcom/arkivanov/essenty/instancekeeper/a;)V

[02b1] check-cast v8, Lcom/x/repositories/post/t;
[02b3] iput-object v8, v2, Lcom/x/postdetail/n;->p:Lcom/x/repositories/post/t;
[02bc] const-string v3, "timelineRepository"
```

The `SGET_OBJECT` at `0x23c` is the default value consumed by the repository constructor. It is
inside the instance-keeper cache-miss branch, before the constructor call, and its value stays
live in the constructor argument range.

### 12.27 target

`dexscope inspect-class` reports:

```text
class: Lcom/x/postdetail/j;
super: Lkotlin/coroutines/jvm/internal/SuspendLambda;
interfaces: Lkotlin/jvm/functions/Function2;
method: invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
registers: 50 total, 2 parameter registers, 48 locals
receiver: p0 (v48)
parameter: p1 (v49): Object
```

The corresponding path is:

```smali
[0212] iget-object v14, v0, Lcom/x/postdetail/j;->A:Lcom/arkivanov/decompose/d;
[0214] invoke-interface v14,
       Lcom/arkivanov/decompose/d;->c()Landroidx/compose/foundation/text/input/internal/selection/z;
[0217] move-result-object v1
[0218] const-class v3, Lcom/x/repositories/post/s;
[021a] invoke-static v3, Lkotlin/jvm/internal/Reflection;->c(Ljava/lang/Class;)Lkotlin/reflect/KType;
[021d] move-result-object v3
[021e] invoke-virtual v1, v3,
       Landroidx/compose/foundation/text/input/internal/selection/z;->e(Ljava/lang/Object;)Lcom/arkivanov/essenty/instancekeeper/a;
[0221] move-result-object v8
[0222] if-nez v8, :cond_31

[0224] invoke-virtual v2, Lcom/x/postdetail/l;->B()Lcom/x/models/b6;
[0227] move-result-object v8
[022c] iget-wide v4, v8, Lcom/x/models/b6;->a:J
[022e] sget-object v29, Lcom/x/models/af;->Relevance:Lcom/x/models/af;
[0230] iget-object v8, v0, Lcom/x/postdetail/j;->D:Lcom/x/repositories/post/g;
[0232] invoke-virtual/range {v29}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0235] iget-object v8, v8, Lcom/x/repositories/post/g;->a:Lcom/x/profile/about/settings/m;

[0255] iget-object v4, v8, Lcom/x/profile/about/settings/m;->d:Ljavax/inject/a;
[0257] invoke-interface v4, Ljavax/inject/a;->get()Ljava/lang/Object;
[025a] move-result-object v4
[025b] invoke-virtual v4, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0260] check-cast v31, Lcom/x/repositories/urt/w;
[0265] new-instance v24, Lcom/x/repositories/post/s;
[0267] move-object/from16 v26, v15
[0269] invoke-direct/range {v24 .. v31},
       Lcom/x/repositories/post/s;-><init>(
           Lcom/x/featureswitches/FeatureSwitchesRepository;
          Ljavax/inject/a;J;
          Lcom/x/models/af;
          Lcom/x/googlessp/a1;
          Lcom/x/repositories/urt/w;)V
[026c] move-object/from16 v8, v24
[026e] invoke-virtual v1, v3, v8,
       Landroidx/compose/foundation/text/input/internal/selection/z;->p(
           Ljava/lang/Object;Lcom/arkivanov/essenty/instancekeeper/a;)V

[0276] check-cast v8, Lcom/x/repositories/post/s;
[0278] iput-object v8, v2, Lcom/x/postdetail/l;->o:Lcom/x/repositories/post/s;
[0283] const-string v1, "timelineRepository"
```

The target's `SGET_OBJECT` is at byte offset `0x22e`, instruction `#310`, and uses `v29`
(shown as `p?` only for parameter registers in other methods; here it is an ordinary local
register). The later constructor range `{v24 .. v31}` consumes that same register. The
compiler-generated non-null operation is now:

```smali
[0232] invoke-virtual/range {v29}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
```

There is no `const-string "rankingMode"` and no `Intrinsics.checkNotNullParameter` at this
point. The direct `getClass()` call and the self-typed enum read prove that the parameter
contract was not removed from the behavior merely because its diagnostic string disappeared.

### Initializer comparison

| Semantic step | 12.26 | 12.27 |
| --- | --- | --- |
| Coroutine owner | `postdetail/l` | `postdetail/j` |
| Controller receiver | `postdetail/n` | `postdetail/l` |
| Instance-keeper type | `repositories/post/t` | `repositories/post/s` |
| Ranking enum | `models/ze` | `models/af` |
| Default read | `SGET_OBJECT v8` at `0x23c` | `SGET_OBJECT v29` at `0x22e` |
| Null-check lowering | named `rankingMode` `Intrinsics` call | `Object.getClass()` |
| Constructor | `post/t.<init>(..., ze, googlessp/c1, urt/w)` | `post/s.<init>(..., af, googlessp/a1, urt/w)` |
| Cache store | `selection/z.p(...)` at `0x2a3` | `selection/z.p(...)` at `0x26e` |
| Property guard | `timelineRepository` at `0x2bc` | `timelineRepository` at `0x283` |

The invariant is therefore not a class or descriptor identity. It is a self-typed ranking enum
read in the instance-keeper miss path, followed by a repository construction whose constructor
consumes that enum, followed by the repository cache store.

### Safe initializer mutation point

The existing mutation is inserted immediately after the resolved `Relevance` read:

```smali
const-class v<resolved-sget-register>, <resolved-enum-owner>
invoke-static/range {v<resolved-sget-register> .. v<resolved-sget-register>},
    <resolver>->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
move-result-object v<resolved-sget-register>
check-cast v<resolved-sget-register>, <resolved-enum-owner>
```

This preserves the register used by the constructor. The owner, register, constructor
descriptor, and cache-store call must be resolved from the matched instructions; neither
`v8`/`v29` nor `ze`/`af` should be hardcoded.

## 2. Selection callback trace

### 12.26 control

Class shape:

```text
class: Lcom/x/payments/transaction/p2ptransfer/i1;
super: Lkotlin/jvm/internal/FunctionReferenceImpl;
interfaces: Lkotlin/jvm/functions/Function1;
method: invoke(Ljava/lang/Object;)Ljava/lang/Object;
registers: 31 total
receiver: p0 (v29)
parameter: p1 (v30)
reply branch: :pswitch_11
```

Exact branch around the two fingerprint strings:

```smali
[043e] move-object/from16 v1, p1
[0440] check-cast v1, Lcom/x/models/ze;
[0442] invoke-static v1, v15,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
[0445] iget-object v2, v0, Lkotlin/jvm/internal/CallableReference;->receiver:Ljava/lang/Object;
[0447] check-cast v2, Lcom/x/postdetail/n;
[0449] iget-object v3, v2, Lcom/x/postdetail/n;->p:Lcom/x/repositories/post/t;
[044b] if-nez v3, :cond_48
[044d] const-string v3, "timelineRepository"
[044f] invoke-static v3,
       Lkotlin/jvm/internal/Intrinsics;->throwUninitializedPropertyAccessException(Ljava/lang/String;)V
[0453] invoke-virtual v3, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0456] const-string v4, "rankingMode"
[0458] invoke-static v1, v4,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
[045b] iget-object v3, v3, Lcom/x/repositories/post/t;->b:Lcom/x/payments/paymentmethod/list/tray/steps/k;
[045d] iput-object v1, v3, Lcom/x/payments/paymentmethod/list/tray/steps/k;->b:Ljava/lang/Object;
[045f] iget-object v1, v2, Lcom/x/postdetail/n;->q:Lcom/x/urt/z;
[0461] if-nez v1, :cond_49
[0463] const-string v1, "defaultUrtTimelineComponent"
[0465] invoke-static v1,
       Lkotlin/jvm/internal/Intrinsics;->throwUninitializedPropertyAccessException(Ljava/lang/String;)V
[046a] sget-object v1, Lcom/x/urt/w0;->a:Lcom/x/urt/w0;
[046c] invoke-virtual v13, v1, Lcom/x/urt/z;->i(Lcom/x/urt/s1;)V
[046f] sget-object v1, Lkotlin/Unit;->a:Lkotlin/Unit;
[0471] return-object v1
```

The callback receives the selected ranking enum in `p1`/`v30`, stores it into the repository,
then emits the URT refresh event. The current patch's remember hook belongs immediately after
the `check-cast` at `0x440`, before the repository field write.

### 12.27 target

Class shape:

```text
class: Lcom/x/payments/transaction/external/entityselection/steps/h;
super: Lkotlin/jvm/internal/FunctionReferenceImpl;
interfaces: Lkotlin/jvm/functions/Function1;
method: invoke(Ljava/lang/Object;)Ljava/lang/Object;
registers: 30 total
receiver: p0 (v28)
parameter: p1 (v29)
reply branch: :pswitch_16
```

Exact target branch:

```smali
[04b6] move-object/from16 v1, p1
[04b8] check-cast v1, Lcom/x/models/af;
[04ba] invoke-virtual v1, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[04bd] iget-object v0, v0, Lkotlin/jvm/internal/CallableReference;->receiver:Ljava/lang/Object;
[04bf] check-cast v0, Lcom/x/postdetail/l;
[04c1] iget-object v2, v0, Lcom/x/postdetail/l;->o:Lcom/x/repositories/post/s;
[04c3] if-nez v2, :cond_52
[04c5] const-string v2, "timelineRepository"
[04c7] invoke-static v2,
       Lkotlin/jvm/internal/Intrinsics;->throwUninitializedPropertyAccessException(Ljava/lang/String;)V
[04cb] invoke-virtual v2, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[04ce] iget-object v2, v2, Lcom/x/repositories/post/s;->b:Lcom/x/nfl/game/chat/h;
[04d0] iput-object v1, v2, Lcom/x/nfl/game/chat/h;->b:Ljava/lang/Object;
[04d2] iget-object v0, v0, Lcom/x/postdetail/l;->p:Lcom/x/urt/y;
[04d4] if-nez v0, :cond_53
[04d6] const-string v0, "defaultUrtTimelineComponent"
[04d8] invoke-static v0,
       Lkotlin/jvm/internal/Intrinsics;->throwUninitializedPropertyAccessException(Ljava/lang/String;)V
[04dd] sget-object v0, Lcom/x/urt/v0;->a:Lcom/x/urt/v0;
[04df] invoke-virtual v9, v0, Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V
[04e2] sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
[04e4] return-object v0
```

The target parameter register is dynamically `registerCount - parameterWidth = 30 - 1 = v29`.
The compiler moves it to `v1` before the concrete enum cast. The existing data-flow logic in
the patch correctly searches for this moved-parameter cast rather than assuming the receiver or
the original parameter register is the cast destination. The current remember hook belongs
immediately after `[04b8]`, using `v1`.

### Selection conclusion

The target callback satisfies every current selection fingerprint condition:

- its defining class is under `Lcom/x/payments/transaction/`;
- `invoke` returns `Ljava/lang/Object;` and accepts one `Ljava/lang/Object;` parameter;
- the class extends `FunctionReferenceImpl` and implements `Function1`;
- `timelineRepository` occurs at `0x4c5`; and
- `defaultUrtTimelineComponent` occurs at `0x4d6`.

The owner leaf (`i1` versus `h`), switch branch (`pswitch_11` versus `pswitch_16`), enum owner,
repository owner, field owner, URT event owner, and method names are all churn and must not be
used as compatibility routes or hardcoded anchors.

## 3. UI state initializer trace

### 12.26 control

Class shape:

```text
class: Lcom/x/ui/common/b2;
super: Ljava/lang/Object;
interfaces: Lkotlin/jvm/functions/Function0;
method: invoke()Ljava/lang/Object;
registers: 5 total
receiver: p0 (v4)
```

The class is a shared synthetic packed-switch `Function0` with 29 cases. The reply-sorting case
is the self-typed enum read followed immediately by Compose state creation:

```smali
:pswitch_19
[003f] sget-object v0, Lcom/x/models/ze;->Relevance:Lcom/x/models/ze;
[0041] invoke-static v0, Landroidx/compose/runtime/i;->w(Ljava/lang/Object;)Landroidx/compose/runtime/v1;
[0044] move-result-object v0
[0045] return-object v0
```

This is the `mutableStateOf(Relevance)`-equivalent initializer targeted by the current UI
fingerprint. The static Compose-state helper is `i.w(Object)` in the control.

### 12.27 target

The target has no `Relevance` field read in the `Lcom/x/ui/common/` package. The equivalent
candidate is:

```text
class: Lcom/x/urt/items/post/u0;
super: Ljava/lang/Object;
interfaces: Lkotlin/jvm/functions/Function0;
method: invoke()Ljava/lang/Object;
registers: 3 total
receiver: p0 (v2)
```

It is also a shared synthetic packed-switch `Function0` with 29 cases. Its reply-sorting case
is:

```smali
:pswitch_0
[00a8] sget-object p0, Lcom/x/models/af;->Relevance:Lcom/x/models/af;
[00aa] invoke-static p0, Landroidx/compose/runtime/i;->r(Ljava/lang/Object;)Landroidx/compose/runtime/v1;
[00ad] move-result-object p0
[00ae] return-object p0
[00af] nop
```

The `p0` spelling is the method's receiver register (`v2`) as reported by dexscope; the patch
must use the actual `OneRegisterInstruction.registerA` value rather than textual parameter
names. The Compose-state helper changed from `i.w` to `i.r`, but its semantic shape remains a
static `(Object) -> Compose state` call with the same result type.

### UI cardinality conclusion

The target `xref` for `Lcom/x/models/af;->Relevance:Lcom/x/models/af;` has these relevant
non-enum reads:

```text
Lcom/x/postdetail/j;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
Lcom/x/urt/items/post/u0;->invoke()Ljava/lang/Object;
```

The first is the repository initializer and implements `SuspendLambda`/`Function2`, not
`Function0`. The second is the only target method that is both a `Function0` and contains a
static Compose-runtime call after the `Relevance` read. The remaining target reads in the xref
list are enum/class initializers or unrelated non-`Function0` methods.

Therefore the current UI fingerprint has zero matches solely because of
`definingClass = "Lcom/x/ui/common/"`; the semantic candidate set is one after removing that
owner-scope assumption and tying the field type to the enum resolved from the repository
initializer.

## Proposed resolver repair

The repair should use one capability resolver and one common mutation for both APK shapes. It
must not branch on version, APK name, obfuscated owner, one-character method name, switch-case
number, or hardcoded register.

### A. Resolve the repository initializer by shape

The minimal repair for these two APKs is to remove only `string("rankingMode")` from the first
fingerprint. The remaining `Relevance` plus `timelineRepository` anchors are unique in the
semantic `postdetail` scope in both APKs. That is sufficient for an immediate old/target
compatibility repair.

The durable resolver should make the behavioral contract explicit instead of retaining a
compiler string as a required anchor:

1. Discover `invokeSuspend(Object) -> Object` methods in the semantic post-detail capability
   scope, without selecting an obfuscated owner leaf.
2. Collect self-typed `SGET_OBJECT` instructions whose field name is `Relevance`.
3. Keep a candidate only when it is in an instance-keeper cache-miss path that obtains a
   post-timeline repository type, constructs that repository, and stores the constructed object
   through the instance-keeper API.
4. Require the candidate enum reference to occur in the selected repository constructor's
   parameter list and in the constructor argument range after the `SGET_OBJECT`.
5. Resolve the enum owner, sget index/register, constructor reference, and cache-store call from
   the matched instructions. Require exactly one method and exactly one qualifying sget.

The `timelineRepository` string may remain a supporting diagnostic anchor while it exists, but
the constructor/cache-store shape is the required contract. If the property-name anchor is
used in an interim fingerprint, it must be documented as optional compiler evidence, not as the
behavior selector.

Use the shared `requireExactlyOne` helper for the method and instruction selections, with
semantic labels and candidate descriptions. A zero, ambiguous, or unexpected constructor shape
must raise `PatchException`.

### B. Keep the selection resolver, but cross-check the resolved enum

The current selection fingerprint already matches the target. Keep its semantic
`FunctionReferenceImpl`/`Function1` and two-string shape, then:

- require exactly one callback method and exactly one `defaultUrtTimelineComponent` instruction;
- derive the callback parameter register from `registerCount` and parameter width;
- resolve the moved-parameter `CHECK_CAST` by data flow;
- require exactly one concrete enum cast whose type equals the enum owner resolved in step A; and
- inject the remember call after that cast and before the repository field store.

This is already the approach represented by the current source after the selection match. The
cardinality checks should use `requireExactlyOne` rather than independent size/single calls when
the repair is implemented.

### C. Resolve the UI state initializer from the enum and dataflow

Replace the hardcoded `Lcom/x/ui/common/` owner scope with a package-independent method/instruction
resolver. Starting from the enum owner resolved in step A:

1. Search `Function0` `invoke() -> Object` methods for a self-typed `SGET_OBJECT` of that enum's
   `Relevance` field.
2. Require the same register to feed an immediately associated static call into the Compose
   runtime, followed by `MOVE_RESULT_OBJECT` and a returned state object. Accept the resolved
   Compose method reference (`i.w` in 12.26 and `i.r` in 12.27) by shape, not by owner/name.
3. Require exactly one qualifying state initializer. Do not select a switch case by numeric
   label; select the `SGET_OBJECT` instruction and its result-flow pair.
4. Inject the default resolver immediately after that sget, reusing the resolved enum owner and
   exact sget register.

This yields `b2.invoke()` on the control and `u0.invoke()` on the target without naming either
class, without assuming the UI package, and without confusing the repository coroutine's own
`Relevance` read with the UI state read.

### Expected resolved candidates

| Capability | 12.26 resolved shape | 12.27 resolved shape |
| --- | --- | --- |
| Repository default | `postdetail/l.invokeSuspend`, enum `models/ze`, sget `v8`, constructor `post/t` | `postdetail/j.invokeSuspend`, enum `models/af`, sget `v29`, constructor `post/s` |
| Selection callback | `payments/transaction/p2ptransfer/i1.invoke`, cast `v1 -> models/ze`, remember after `0x440` | `payments/transaction/external/entityselection/steps/h.invoke`, cast `v1 -> models/af`, remember after `0x4b8` |
| UI state default | `ui/common/b2.invoke`, sget `v0`, Compose `i.w` | `urt/items/post/u0.invoke`, sget receiver `v2`, Compose `i.r` |

These are observed outputs, not compatibility branches. The implementation should resolve the
same shapes at patch time and then share the downstream mutation.

## Validation plan

After implementing the resolver repair:

1. Run the full focused build/test commands, including
   `./gradlew :patches:test :patches:build --no-daemon` and
   `./gradlew :patches:lintNewxResolvers --no-daemon`.
2. Build the real MPP from the current source commit and record its checksum.
3. Patch both frozen APKs exclusively with `NewX: Set default reply sorting`; confirm
   `Applied` and `Saved to` for both. Do not use the prior continued-run artifact.
4. Inspect the final DEX to confirm all three injections preserve the original enum type,
   invoke/result pairing, register widths, and reachability.
5. Perform runtime/control-path testing only after the artifact is produced. Device interaction
   remains user-controlled under `AGENTS.md`; ask the user to exercise post detail and report
   any runtime issue or provide logs/screenshot.

The incident classification for this recon is resolver logic / APK contract drift plus Kotlin
compiler/R8 shape churn, not linter or cardinality-helper behavior and not runtime behavior.
