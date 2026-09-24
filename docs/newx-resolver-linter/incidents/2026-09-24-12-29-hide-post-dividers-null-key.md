# NewX 12.29 hide post dividers null-key regression

- Date: 2026-09-24
- Reporter/session: 12.29 port session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated target:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` /
  `0effcffb0a00d38fb5070c59ec8cd4ef5010e798ee3fbc00ff7b84ed0cd82631`
- Command: `./patch-twitter.sh` (12.29), `./patch-twitter.sh apks/12.28.0-prod.01.apk --continue-on-error`
- Failing patch: `NewX: Hide post dividers`
- Severity: medium (patch abort; no APK produced)
- Cause: resolver logic — 12.29's Kotlin lowering propagates the null lazy-item key through `move-object` aliases, and `isZeroKeyLazyItem` only scanned a fixed 4-instruction window for a direct zero constant
- Confidence: high; selection verified in the patched bytecode of both targets

## Symptom

```text
SEVERE: FAILED: NewX: Hide post dividers
app.morphe.patcher.patch.PatchException: Expected exactly one NewX timeline module divider item, found 0: []
    at app.crimera.patches.newx.utils.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
    at app.crimera.patches.newx.timeline.HidePostDividersPatchKt.resolveTimelineModuleDividerCallIndex(HidePostDividersPatch.kt:483)
```

## Root cause

The module-divider resolver selects zero-key lazy items whose inline adapter case
block draws a Material3 divider (see
`docs/newx-resolver-linter/incidents/2026-09-23-hide-post-dividers-module-divider.md`).
`isZeroKeyLazyItem` proved the key was zero with a 4-instruction backward scan for
a `const` writing the key register.

In 12.29 the divider item's key is a null reference lowered to a zero constant
that reaches the call through object-move aliases:

```smali
[002f] const/4 v6, 0x0        # shared zero / null
...
[0084] move-object/from16 v25, v6
...
[02ca] new-instance v2, Lcom/x/profile/about/v;
[02cc] const/16 v3, 0x12      # discriminator 0x12 (not a switch key -> default block)
[02ce] invoke-direct {v2, v15, v3}, Lcom/x/profile/about/v;-><init>(Ljava/lang/Object;I)V
[02d1] new-instance v3, Landroidx/compose/runtime/internal/f;
[02d3] const v4, 0x1f78647f
[02d6] invoke-direct {v3, v2, v5, v4}, Landroidx/compose/runtime/internal/f;-><init>(Ljava/lang/Object;ZI)V
[02d9] move-object/from16 v4, v25   # key register v4 aliases the null
[02db] const/4 v2, 3
[02dc] invoke-static v1, v4, v3, v2, Landroidx/compose/foundation/lazy/k;->u(...)V
```

The old window saw `const v4, 0x1f78647f` (the Compose group key) as the last
writing `const` and rejected the item, so `timelineModuleDividerItemIndices`
returned zero candidates.

## Fix

- Added `List<Instruction>.resolveConstantOnCurrentPath` in
  `patches/src/main/kotlin/app/crimera/patches/newx/utils/InstructionDataFlow.kt`.
  It follows integer **and** object moves to a single integer literal and stops
  when the tracked register is overwritten by another value.
- `isZeroKeyLazyItem` now resolves the key through that helper and requires `0`.
- Added the regression fixture
  `module divider selection follows null key through object moves` to the existing
  `NewXResolverLinterTest` suite.

## Target anchors and cardinality

| Release | Builder | Divider item adapter | Discriminator | Case block |
| --- | --- | --- | --- | --- |
| 12.27.0-prod.01 | `Lcom/x/urt/ui/y;` | `Landroidx/compose/foundation/m1;` | `0x9` | `material3/x;->f(FI I J Composer Modifier)V` |
| 12.28.0-alpha.01 | `Lcom/x/urt/ui/w;` | `Landroidx/compose/foundation/m1;` | `0xa` | `material3/x;->f(...)V` |
| 12.28.0-alpha.04 | `Lcom/x/urt/ui/w;` | `Lcom/x/settings/privacy/chats/passcode/a;` | `0xd` | `material3/y;->f(...)V` |
| 12.28.0-prod.01 | `Lcom/x/urt/ui/x;` | `Lcom/x/search/query/s0;` | `0xe` | `material3/y;->f(...)V` (default block) |
| 12.29.0-alpha.04 | `Lcom/x/urt/ui/w;` | `Lcom/x/profile/about/v;` | `0x12` | `material3/v;->f(...)V` (default block) |

The adapter owner, discriminator, and Material3 leaf are volatile and are not
anchors. The Compose group key `0x1f78647f` is shared by 12.28-alpha/prod and
12.29 but is `0x71b7770b` in 12.27, so it is reconnaissance evidence only.
`requireExactlyOne` remains the fail-closed boundary.

## Discarded anchors

- Adapter owner (`foundation/m1`, `passcode/a`, `search/query/s0`,
  `profile/about/v`) and discriminator value.
- Direct-const key window (the regression cause).
- Compose group-key literal and call offset.
- The `Lurt/ui/a->e` hoisted singleton (pagination indicator, not the divider).

## Before/after hook

Injected `SettingsRegistry.getBooleanOrDefault("newx.timeline.hide_post_dividers", false)`
immediately before the divider lazy `u` call and `if-nez`-jumped over it.
Final DEX inspection confirms:

- 12.29.0-alpha.04: hook at `profile/about/v` disc `0x12`; the pagination item
  (`foundation/m1` disc `0x7`) is untouched.
- 12.28.0-prod.01: hook at `search/query/s0` disc `0xe`; the pagination item
  (`search/query/s0` disc `0xc`) is untouched.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- `./patch-twitter.sh` — `Applied: NewX: Hide post dividers`; the run then aborts
  on the unrelated `NewX: Open canonical URLs` fingerprint
  (`docs/newx-resolver-linter/incidents/2026-09-24-12-29-open-canonical-urls.md`).
- `./patch-twitter.sh --continue-on-error` — saved the patched APK and the hook
  was verified in the final DEX.
- `./patch-twitter.sh apks/12.28.0-prod.01.apk --continue-on-error` — `Applied`
  and `Saved to`; hook verified on the divider item.

## Known limits

`resolveConstantOnCurrentPath` follows the curated move/literal/write opcode sets
already used by `resolveIntegerLiteralOnCurrentPath`; it does not model arithmetic
clobbers or exception edges. A future release that computes the key is expected
to fail loudly through `requireExactlyOne` rather than silently select the wrong
item.
