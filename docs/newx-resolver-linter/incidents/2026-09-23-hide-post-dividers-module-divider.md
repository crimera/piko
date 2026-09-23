# NewX 12.28 hide post dividers module-divider regression

- Date: 2026-09-23
- Reporter/session: user report; pi repair session
- APK package/version/build type: `com.twitter.android` 12.28.0-prod.01 production
- APK path and checksum: `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
- Additional validated targets:
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
  - `apks/12.28.0-alpha.01.apk` / `23c9a96109161ba904387e0fbeec8e8c13d94b26149058bcb5c801b2df9064ad`
  - `apks/12.28.0-alpha.04.apk` / `2faeabfc103f2104d7bfb698078680f06dadbc0afdaefefbf934dfa191aa978b`
- Source commit: `5fb32a59bfe768cfe74707084f0a8486414ee4e5` plus working-tree resolver repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `9fff62e652b8619da3d05b2b6e2751baa108bfcf3d4a4da6d76e144e7ef7df4d`
- Command: `./patch-twitter.sh apks/12.28.0-prod.01.apk`
- Failing patch: none (patch applied); the failure is runtime behavior
- Severity: medium (feature no-op; also hid the pagination indicator)
- Cause: resolver logic — obfuscated lambda-adapter owner drift plus a misdiagnosed hoisted fallback
- Confidence: high; selection verified in the patched bytecode of all four targets

## Symptom

With `NewX: Hide post dividers` enabled, 12.28 production still showed the
full-width horizontal lines between timeline cards/modules. The same setting
appeared to work on 12.27 production and 12.28.0-alpha.01.

## Root cause

`NewXTimelineModuleBuilderFingerprint` locates the per-module zero-key lazy
item that renders the divider. The original matcher accepted an inline content
lambda built from a `Landroidx/compose/foundation/**` adapter constructor plus
the `androidx/compose/runtime/internal/**` wrapper constructor.

R8 moved the divider's lambda adapter out of the foundation package:

| Release | Divider item adapter | Match result |
| --- | --- | --- |
| 12.27.0-prod.01 | `Landroidx/compose/foundation/m1;` | correct (foundation inline shape) |
| 12.28.0-alpha.01 | `Landroidx/compose/foundation/m1;` | correct (foundation inline shape) |
| 12.28.0-alpha.04 | `Lcom/x/settings/privacy/chats/passcode/a;` | wrong (fell through to hoisted branch) |
| 12.28.0-prod.01 | `Lcom/x/search/query/s0;` | wrong (fell through to hoisted branch) |

Commit `513d36bc` added a hoisted fallback that accepted any zero-key lazy item
whose content register was loaded by an `sget-object` of a
`runtime/internal/**` field, in the belief that newer releases hoisted the
divider lambda into `Lurt/ui/a->e`. In 12.28 that static holder is actually the
pagination indicator (`Lurt/ui/z;->b` on 12.28.0-prod.01, `Lurt/ui/y;->b` on
12.28.0-alpha.04). The patch therefore skipped the pagination indicator and
left the real module divider in place.

The real module divider item is built inline in every release. Its adapter
class and discriminator are the only volatile parts; the discriminator selects
an `invoke` case block that draws the Material3 divider:

- 12.27.0-prod.01: `foundation/m1` discriminator `0x9` -> `material3/x;->f(...)`.
- 12.28.0-alpha.01: `foundation/m1` discriminator `0xa` -> `material3/x;->f(...)`.
- 12.28.0-alpha.04: `passcode/a` discriminator `0xd` -> `material3/y;->f(...)`.
- 12.28.0-prod.01: `search/query/s0` discriminator `0xe` (default block) -> `material3/y;->f(...)`.

Neighboring zero-key lazy items (the pagination indicator and the Spaces
reconnect prompt) also construct lambdas, so owner-based or shape-only matching
cannot separate them. The discriminator must be resolved before the case block
is inspected.

## Fix

Replaced the foundation-package and hoisted heuristics with a semantic
resolver in `HidePostDividersPatch.kt`:

1. Select zero-key lazy items.
2. Trace the content register back to its `runtime/internal` wrapper
   constructor and then to the obfuscated adapter constructor; reject the
   wrapper if the content register is overwritten before the lazy call (this
   rejects the pagination item, whose content register is clobbered by a later
   `sget-object`).
3. Resolve the adapter's `invoke` and map the discriminator to its case block
   (or the fallthrough block when the discriminator is not a switch key).
4. Require the block to draw a Material3 divider
   (`Landroidx/compose/material3/**` `(F,I,I,J,Composer,Modifier)V`).
5. `requireExactlyOne` over the resolved indices.

`packedSwitchCases` (previously navigation-specific) is reused and its error
messages were made neutral.

## Evidence

Patched bytecode selection after the fix (injected
`SettingsRegistry.getBooleanOrDefault("newx.timeline.hide_post_dividers", false)`
immediately precedes the divider lazy item and jumps over it):

- 12.28.0-prod.01: hook at the `search/query/s0` disc `0xe` item; the
  `Lurt/ui/a->e` pagination item is untouched.
- 12.28.0-alpha.04: hook at the `passcode/a` disc `0xd` item.
- 12.28.0-alpha.01 / 12.27.0-prod.01: hook at the `foundation/m1` item; the
  Spaces reconnect item is untouched.

## Regression fixture

`NewXResolverLinterTest.module divider selection accepts non-foundation inline
adapter and scopes discriminator` builds a module-builder method whose divider
item uses the non-foundation `Lfixture/DividerAdapter;` and asserts that only
that item is selected. The fixture also contains a sibling item on the same
adapter with a non-divider discriminator, a hoisted zero-key item whose content
register reuses a stale wrapper register, and an unselected control. The second
test asserts that a lookup that never returns a divider selects nothing.

## Validation

- `./gradlew :patches:lintNewxResolvers` — passed.
- `./gradlew :patches:build` (clean `:patches:clean` first) — full NewX test
  suite passed.
- Patched `12.27.0-prod.01`, `12.28.0-alpha.01`, `12.28.0-alpha.04`, and
  `12.28.0-prod.01`; each reported `Applied: NewX: Hide post dividers` and
  `Saved to`.
- Runtime/device behavior not exercised by the agent; user asked to verify the
  timeline and thread/comment views with the setting both on and off.
