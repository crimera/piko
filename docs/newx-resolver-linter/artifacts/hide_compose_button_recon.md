# Hide compose button recon: Twitter/X 12.27.0-alpha.01

## Outcome

The compose/new-post button renderer still exists in 12.27.0-alpha.01. Its
source-level contract and patch point did not change. The failure is caused by
the removal of the Kotlin parameter-name string `"onClick"`, which the current
fingerprint treats as mandatory.

Exact renderer descriptors:

- 12.26.0-alpha.03:
  `Lcom/x/ui/common/m;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V`
- 12.27.0-alpha.01:
  `Lcom/x/ui/common/l;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V`

The declaring class moved from the obfuscated leaf `m` to `l`. The descriptive
package remains `Lcom/x/ui/common/`, the obfuscated method name happens to
remain `c`, and the complete parameter and return signature is unchanged. No
fingerprint should encode either one-character name.

The preferred repair is a package-independent semantic resolver over this
Compose ABI, the no-argument boolean `isVisible()` interface call, and the data
flow from the method's `Modifier` parameter into the AnimatedVisibility-shaped
call. It resolves exactly one method in both APKs and distinguishes the close
recommended-list false candidate. A minimal short-term repair is to remove the
`string("onClick")` filter; that is also unique in both APKs under the existing
top-level `Lcom/x/ui/common/` scope, but the package-independent resolver better
matches the repository's R8/refactor policy.

## Frozen inputs

Analysis was performed from source commit
`d6efb26d263000ed9960069dd6fdc93348f12d90` with an already-dirty worktree.
Only this artifact was added by this recon.

| Role | Path | Manifest identity | SHA-256 |
| --- | --- | --- | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, versionCode `312260203`, versionName `12.26.0-alpha.03` | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, versionCode `312270201`, versionName `12.27.0-alpha.01` | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

The existing incident record is
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## Current fingerprint and reproduced cardinality

`NewXNewPostButtonFingerprint` currently requires all of the following:

1. A direct member of `Lcom/x/ui/common/`.
2. Parameters exactly
   `(I, Composer, Modifier, Function0)` and return type `V`.
3. The string `"onClick"`.
4. An `INVOKE_INTERFACE` named `isVisible` with no parameters and boolean
   return type.

The equivalent dexscope query reproduces the important cardinality change:

```text
12.26.0-alpha.03  MATCH_UNIQUE  1
  Lcom/x/ui/common/m;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V

12.27.0-alpha.01  NO_MATCH      0
```

When only `"onClick"` is removed while retaining the current package scope,
complete signature, and visibility-call condition, the intended result is one
method per APK. Dexscope directly confirms that the package plus complete
signature alone already has cardinality one in each APK:

```text
12.26.0-alpha.03  MATCH_UNIQUE  1  Lcom/x/ui/common/m;->c(...)V
12.27.0-alpha.01  MATCH_UNIQUE  1  Lcom/x/ui/common/l;->c(...)V
```

The `isVisible()` invocation is independently present in both matches, as
shown below. Therefore the missing string, not a missing renderer or changed
signature, is the direct reason for the fingerprint failure.

## Bytecode evidence

### 12.26.0-alpha.03 control

Method:

```text
Lcom/x/ui/common/m;->c(
    I,
    Landroidx/compose/runtime/Composer;,
    Landroidx/compose/ui/Modifier;,
    Lkotlin/jvm/functions/Function0;
)V
```

Registers: 14 total, 4 parameter registers. `p0` is the Compose changed mask,
`p1` is `Composer`, `p2` is `Modifier`, and `p3` is the click callback.

Relevant instructions:

```smali
[0000] const-string v0, "onClick"
[0002] invoke-static p3, v0,
       Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

[0008] const p1, -0x53995a21
[000b] invoke-virtual v7, p1,
       Landroidx/compose/runtime/m0;->k0(I)Landroidx/compose/runtime/Composer;

[003f] sget-object v1, Lcom/x/ui/common/o1;->a:Landroidx/compose/runtime/x;
[0041] invoke-virtual v7, v1,
       Landroidx/compose/runtime/m0;->l(Landroidx/compose/runtime/g2;)Ljava/lang/Object;
[0045] check-cast v1, Lcom/x/ui/common/m1;
[0052] iget-object v1, v1, Lcom/x/ui/common/m1;->d:Lcom/x/ui/common/l1;
[0056] invoke-interface v1, Lcom/x/ui/common/l1;->isVisible()Z
[0059] move-result v1

[0070] new-instance v0, Landroidx/compose/foundation/a0;
[0073] invoke-direct v0, p3, v2,
       Landroidx/compose/foundation/a0;-><init>(Lkotlin/jvm/functions/Function0;I)V
[0076] const v2, 0x7fb87ab7

[0087] move-object v2, p2
[0088] invoke-static/range {v1 .. v9},
       Landroidx/compose/animation/l2;->f(
           Z,
           Landroidx/compose/ui/Modifier;,
           Landroidx/compose/animation/l1;,
           Landroidx/compose/animation/m1;,
           Ljava/lang/String;,
           Lkotlin/jvm/functions/Function3;,
           Landroidx/compose/runtime/Composer;,
           I,
           I
       )V
```

The decompiled expression makes the consumed value clear: the renderer calls
AnimatedVisibility with a boolean equivalent to
`bottomAppBarState == null && buttonVisibility.isVisible()`, the supplied
`modifier`, and content wrapping the supplied `function0` click callback.

### 12.27.0-alpha.01 target

Method:

```text
Lcom/x/ui/common/l;->c(
    I,
    Landroidx/compose/runtime/Composer;,
    Landroidx/compose/ui/Modifier;,
    Lkotlin/jvm/functions/Function0;
)V
```

Registers and parameter roles remain identical: 14 total, with `p0` through
`p3` carrying the same values.

Relevant instructions:

```smali
[0000] invoke-virtual p3, Ljava/lang/Object;->getClass()Ljava/lang/Class;

[0006] const p1, -0x53995a21
[0009] invoke-virtual v6, p1,
       Landroidx/compose/runtime/m0;->j0(I)Landroidx/compose/runtime/m0;

[003d] sget-object v1, Lcom/x/ui/common/o1;->a:Landroidx/compose/runtime/x;
[003f] invoke-virtual v6, v1,
       Landroidx/compose/runtime/m0;->l(Landroidx/compose/runtime/f2;)Ljava/lang/Object;
[0043] check-cast v1, Lcom/x/ui/common/m1;
[0050] iget-object v1, v1, Lcom/x/ui/common/m1;->d:Lcom/x/ui/common/l1;
[0054] invoke-interface v1, Lcom/x/ui/common/l1;->isVisible()Z
[0057] move-result v1

[006e] new-instance v1, Landroidx/compose/foundation/a0;
[0070] invoke-direct v1, p3, v9,
       Landroidx/compose/foundation/a0;-><init>(Lkotlin/jvm/functions/Function0;I)V
[0073] const v4, 0x7fb87ab7

[0084] move-object v1, p2
[0085] invoke-static/range {v0 .. v8},
       Landroidx/compose/animation/p;->i(
           Z,
           Landroidx/compose/ui/Modifier;,
           Landroidx/compose/animation/y1;,
           Landroidx/compose/animation/z1;,
           Ljava/lang/String;,
           Lkotlin/jvm/functions/Function3;,
           Landroidx/compose/runtime/Composer;,
           I,
           I
       )V
```

The old named Kotlin null check became `function0.getClass()`. This retains the
runtime non-null check but contains no parameter-name string. A complete APK
string-anchor query finds `"onClick"` only in an unrelated Jetfuel element
method in 12.27; it is absent from this renderer.

The following semantic elements survive unchanged:

- Exact `(I, Composer, Modifier, Function0)V` Compose ABI.
- `Lcom/x/ui/common/m1;->d:Lcom/x/ui/common/l1;` state relationship.
- `INVOKE_INTERFACE Lcom/x/ui/common/l1;->isVisible()Z`.
- Boolean combination of bottom-app-bar absence and visibility state.
- The supplied modifier flowing to the second argument of an
  AnimatedVisibility-shaped static call.
- The supplied `Function0` wrapped as the animation content/click callback.
- Compose group keys `-0x53995a21` and `0x7fb87ab7`.
- Multiple analogous consumers across home/drawer, notifications or NFL hub,
  search, profile, Explore, and Communities.

The Compose runtime and animation implementation descriptors churned between
the APKs (`k0` to `j0`, `Z` to `Y`, `l2.f` to `p.i`, and animation transition
types changed). These are evidence of the same regenerated Compose body, not
good exact fingerprint anchors.

## Caller evidence

Incoming references confirm that these two methods occupy the same shared UI
role. Representative pairs are:

| 12.26 caller | 12.27 caller | Relationship |
| --- | --- | --- |
| `Landroidx/activity/compose/z;->invoke(...)` | same owner/signature | direct call to renderer |
| `Lcom/x/search/result/c;->invoke(...)` | same owner/signature | direct call to renderer |
| `Lcom/x/main/drawer/e;->invoke(...)` | `Lcom/x/main/drawer/g;->invoke(...)` | generated recomposition callback |
| `Lcom/x/profile/n1;->k(...)` | `Lcom/x/profile/p1;->k(...)` | profile composition |
| `Lcom/x/explore/p;->invoke(...)` | same owner/signature | Explore composition |
| `Lcom/x/communities/impl/detail/w;->q(...)` | `Lcom/x/communities/impl/detail/q;->q(...)` | Communities detail composition |

There are seven incoming renderer references in each APK. Their owners exhibit
ordinary obfuscation churn, while their feature distribution and call shape
remain equivalent.

## Close false candidate and distinguishing invariant

A package-independent scan must not stop at the exact ABI plus `isVisible()`.
There is one close false candidate in each APK:

```text
Lcom/x/list/recommended/a;->a(
    I,
    Landroidx/compose/runtime/Composer;,
    Landroidx/compose/ui/Modifier;,
    Lkotlin/jvm/functions/Function0;
)V
```

It also reads `m1.d`, invokes `l1.isVisible()Z`, and calls the same
AnimatedVisibility implementation. In the control it even retains the same
`"onClick"` parameter string. Therefore ABI plus `isVisible()` has cardinality
two among the relevant `l1.isVisible()` consumers in each APK.

The stable distinction is modifier data flow:

- New-post renderer: the AnimatedVisibility call's second argument is an
  alias of the method's `p2` `Modifier` parameter (`move-object local, p2`).
- Recommended-list renderer: it ignores the supplied `p2` for that call and
  passes a new modifier returned by a helper with semantic shape
  `(Composer, Modifier) -> Modifier`.

This is preferable to distinguishing the methods by any obfuscated owner,
method, synthetic lambda class, instruction offset, or Compose group-key
literal.

## Recommended resolver

Use one semantic fingerprint/resolver shared by both releases:

1. Match `static` methods returning `V` with exact parameters
   `(I, Composer, Modifier, Function0)`.
2. Require exactly one `INVOKE_INTERFACE` whose reference name is
   `isVisible`, has no parameters, and returns `Z`. Do not require its
   obfuscated declaring class.
3. Find exactly one AnimatedVisibility-shaped static invoke by its public
   boundary shape, not its obfuscated method name:
   - returns `V`;
   - nine parameters;
   - parameter 0 is `Z`;
   - parameter 1 is `Landroidx/compose/ui/Modifier;`;
   - parameter 4 is `Ljava/lang/String;`;
   - parameter 5 is `Lkotlin/jvm/functions/Function3;`;
   - parameter 6 is `Landroidx/compose/runtime/Composer;`;
   - parameters 7 and 8 are `I`;
   - declaring class is under the stable AndroidX Compose animation namespace.
     Do not encode `l2.f` or `p.i`, nor the obfuscated transition types in
     parameters 2 and 3.
4. Trace the second invoke argument through object-move aliases and require it
   to originate at this static method's `Modifier` parameter. Reject a value
   originating from an invoke result. This removes the recommended-list
   candidate.
5. Collect all qualifying methods and resolve with the shared
   `requireExactlyOne(label = "NewX new-post button renderer", candidates =
   ...)`. Let zero and ambiguity throw `PatchException` with candidate
   descriptions. Do not use `single()` or silently skip.
6. Keep the existing entry mutation: `returnVoidIfEnabled(method, 0)` is the
   latest safe value-independent point and remains valid because both targets
   are static void renderers with the unchanged ABI.

Illustrative shape only (adapt to the repository's instruction helpers):

```kotlin
private object NewXNewPostButtonCandidateFingerprint : Fingerprint(
    parameters = listOf(
        "I",
        COMPOSER_DESCRIPTOR,
        MODIFIER_DESCRIPTOR,
        FUNCTION_ZERO_DESCRIPTOR,
    ),
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            name = "isVisible",
            parameters = emptyList(),
            returnType = "Z",
        ),
    ),
    custom = { method, _ -> method.hasDirectModifierAnimatedVisibilityFlow() },
)

val renderer = requireExactlyOne(
    label = "NewX new-post button renderer",
    candidates = NewXNewPostButtonCandidateFingerprint.scopedMatchAll(),
)
hideNewPostButton.returnVoidIfEnabled(renderer.method, 0)
```

`hasDirectModifierAnimatedVisibilityFlow()` should validate unique call shape
and register provenance. It must not compare against `m`, `l`, `c`, `l2`, `p`,
or fixed instruction indexes.

### Minimal lower-risk port

If a deep resolver is out of scope for the immediate release, deleting only
this filter is sufficient for the two supplied APKs:

```kotlin
string("onClick")
```

Keep the current complete ABI, `isVisible()` filter, direct
`Lcom/x/ui/common/` membership check, and exact-one failure. This produces one
match in both 12.26 and 12.27 and never names the one-character owner. It is a
safe compatibility repair for these targets, but its package constraint is
less resilient than the recommended semantic resolver if R8 later moves the
class.

Do not add release-version routing or a second owner-specific fingerprint.
There is only one behavioral contract here.

## Validation required with the implementation

This recon did not alter patch code, build an MPP, patch an APK, install an
APK, or perform runtime/device interaction.

When implementing the repair:

1. Add a focused fixture to the existing `NewXResolverLinterTest.kt` corpus for
   the real false-negative pattern: old `checkNotNullParameter(...,
   "onClick")` versus new `Object.getClass()`, both retaining the semantic
   visibility and modifier-to-animation flow. Do not create a new test file.
2. Include the recommended-list shape as the negative/control fixture so the
   resolver cannot accept both lookalikes.
3. Run `./gradlew :patches:test :patches:build --no-daemon` and
   `./gradlew :patches:lintNewxResolvers --no-daemon`.
4. Build the real MPP and patch both exact APKs independently. Confirm
   `NewX: Hide compose button` reports `Applied`, each run reports `Saved to`,
   and record output artifact paths/checksums.
5. Runtime validation remains a user action under the repository's device
   policy. Ask the user to verify the button is visible with the setting off,
   hidden with it on, and that compose buttons on home/search/profile/Explore/
   Communities do not regress outside the intended global behavior.

## Dexscope commands used

Representative reproducible commands:

```sh
~/.local/bin/dexscope manifest \
  --apk ./apks/twitter_12.26.0-alpha.03.apk \
  --apk ./apks/twitter_12.27.0-alpha.01.apk --package

~/.local/bin/dexscope dry-run \
  ./apks/twitter_12.26.0-alpha.03.apk \
  ./apks/twitter_12.27.0-alpha.01.apk \
  --class 'Lcom/x/ui/common/' --return V \
  --param I \
  --param 'Landroidx/compose/runtime/Composer;' \
  --param 'Landroidx/compose/ui/Modifier;' \
  --param 'Lkotlin/jvm/functions/Function0;' \
  --string onClick --limit 20

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.26.0-alpha.03.apk \
  'Lcom/x/ui/common/m;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V' \
  --limit 240

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.27.0-alpha.01.apk \
  'Lcom/x/ui/common/l;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V' \
  --limit 240

~/.local/bin/dexscope anchors \
  --apk ./apks/twitter_12.26.0-alpha.03.apk \
  --apk ./apks/twitter_12.27.0-alpha.01.apk \
  --method 'Lcom/x/ui/common/l1;->isVisible()Z' --limit 30

~/.local/bin/dexscope xref ./apks/twitter_12.27.0-alpha.01.apk \
  'Lcom/x/ui/common/l;->c(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;)V' \
  --direction in --kind method --limit 50
```
