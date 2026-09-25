# NewX 12.29 default media tab patched but not applied at runtime

- Date: 2026-09-24 (reported), 2026-09-25 (corrected diagnosis and fix)
- Reporter/session: user report that the default media tab is not applying on 12.29 alpha 4
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated target:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
- Source commit: `5bfd0250` plus the corrected working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh ./apks/12.29.0-alpha.04.apk` (same for 12.28)
- Failing patch: `NewX: Set default media tab` (applies cleanly, runtime-ineffective)
- Severity: high (user-visible setting silently ignored on 12.29)
- Cause: resolver logic — 12.29 contract change, not R8 churn. The seed instruction is a
  branch target, so the resolver bridge inserted immediately before it was bypassed.
- Confidence: high; branch target and single-seed cardinality verified in the patched
  bytecode of 12.29, and old-shape behavior verified on 12.28.

## Symptom

`NewX: Set default media tab` reports `Applied` on 12.29.0-alpha.04, but the profile media
tab still opens on Videos when the setting selects Photos. No patch aborts.

## Root cause

12.29 refactored the combined timeline component (`Lcom/x/profile/timeline/d`). Through 12.28
the constructor takes the initial sub-tab parameter and seeds the selected-sub-tab flow
directly:

```smali
; 12.28
if-eqz p4, :cond_1
invoke-static p2, Lkotlinx/coroutines/flow/z;->c(Ljava/lang/Object;)Lkotlinx/coroutines/flow/w2;
move-result-object p2
iput-object p2, ...->p:Lkotlinx/coroutines/flow/w2;   # selected sub-tab flow
```

12.29 instead computes the seed from a `(contains ? initialSubTab : first(list))` fallback and
puts the result in flow field `q`:

```smali
; 12.29
:cond_2
move-object p1, v1
:goto_0
if-nez p1, :cond_3               # branch: initial sub-tab is in the list
invoke-interface p3, u2;->getValue()
... CollectionsKt.first(List) ... # fallback for an initial sub-tab not in the list
:cond_3                          # <-- branch target == the flow seed
invoke-static p1, Lkotlinx/coroutines/flow/z;->c(...)   # q seed
move-result-object p1
iput-object p1, ...->q:Lkotlinx/coroutines/flow/w2;
```

The previous repair inserted the `MediaTabResolver.getEnumDefault` call with a plain
`addInstructions(seedIndex, ...)`. dexlib2 keeps the `:cond_3` label attached to the original
seed instruction, so the inserted bridge landed **before the label** and the
`if-nez p1, :cond_3` branch jumped straight to the seed, never executing the bridge. The seed
therefore stayed `Videos`. (The same repair also routed a plain tab-type pending field through
the resolver, but the constructor leaves that field `null` whenever the initial sub-tab is
already in the loaded list — which is the normal case — so it was a no-op and could not
compensate.)

The tab-types collector (`Lcom/x/payments/transaction/checkdeposit/ui/g;->emit`, `pswitch_15`)
only rewrites the selected tab when the current value is **not** in the emitted tab list, so
once the seed is correctly `Photos` the collector leaves it alone. No collector mutation is
needed.

## Fix

`DefaultMediaTabPatch` now injects the seed bridge with `addInstructionsAtControlFlowLabel`,
which relocates the branch target to the injected instructions:

```kotlin
method.addInstructionsAtControlFlowLabel(seedInvokeIndex, resolverBridge)
```

Both the `:cond_3` branch (initial sub-tab in the list) and the fall-through (initial sub-tab
missing, `first(list)` used) now reach the resolver. The optional 12.29 pending-store adapter
remains as a deferred-path safeguard, but it is not what makes the common case work.

## Evidence

Patched 12.29 `Lcom/x/profile/timeline/d;-><init>`:

```smali
[006c] (54) if-nez p1, :cond_3
...
:cond_3
[007a] (61) invoke-static p1, Lapp/.../MediaTabResolver;->getEnumDefault(...)   # now the target
[007d] (62) move-result-object p1
[007e] (63) check-cast p1, Lcom/x/profile/s;
[0080] (64) invoke-static p1, Lkotlinx/coroutines/flow/z;->c(...)              # q flow
```

Patched 12.28 keeps the bridge directly before the seed (the seed is not a label target) and
stores no plain tab-type field, so its behavior is unchanged. `dexscope verify-method` reports
no errors on either constructor (only the analyzer's generic not-modeled warnings).

## Regression fixture

`media tab pending sub-tab store matches only the tab-type field write` in
`NewXResolverLinterTest` asserts `isPendingSubTabStore` accepts the tab-type `iput` and rejects
the sorting-flow and tab-list stores. It guards the optional-shape resolver, not the label
placement; the label placement is covered by patch-time bytecode inspection because it depends
on dexlib2 branch relocation.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- 12.29.0-alpha.04 — `./patch-twitter.sh ./apks/12.29.0-alpha.04.apk`: media-tab patch
  `Applied`, `Saved to`; `:cond_3` now resolves to the resolver bridge.
- 12.28.0-prod.01 — `./patch-twitter.sh ./apks/12.28.0-prod.01.apk`: patch `Applied`,
  `Saved to`; bridge before the seed, no plain tab-type field.
- Device execution was not performed; repository rules prohibit device control without explicit
  permission. Ask the reporter to confirm the Photos default holds on first open of the profile
  media tab.
