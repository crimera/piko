# NewX 12.29 navigation bar high-register field read

- Date: 2026-09-24
- Reporter/session: user-reported patch warnings; pi repair session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh --continue-on-error`
- Failing patch: `NewX: Customize navigation bar` (warning, not an abort)
- Severity: high (silent bytecode corruption; the injected instruction was dropped)
- Cause: emitted-bytecode bug — a fresh receiver local above v15 was used in `iget-object`
- Confidence: high; before/after bytecode and `verify-method` inspected

## Symptom

```text
INFO: Applied: NewX: Customize inline actions
[6,8] Invalid register: v22. Must be between v0 and v15, inclusive.
[12,0] Invalid register: v22. Must be between v0 and v15, inclusive.
[16,0] Invalid register: v22. Must be between v0 and v15, inclusive.
INFO: Applied: NewX: Customize navigation bar
```

## Root cause

`NavBarCustomizationPatch.injectReplacementOverride` preserves the renderer receiver at method
entry (the compiler reuses the parameter registers before the renderer call):

```kotlin
thisRegister = originalRegisterCount          // fresh local, may exceed v15
method.addInstructions(0, "move-object/from16 v$thisRegister, p0")
```

The injected snippet then read the tab field directly:

```smali
iget-object v$workRegister, v$thisRegister, $navigationField
```

`iget-object` is Dalvik format 22c, whose destination **and object** registers are four bits.
On 12.29 `R0->invoke` has 22 original registers, so `thisRegister = v22`. The smali assembler
emitted `Invalid register: v22` three times and dropped the three `iget-object` instructions,
leaving `v$workRegister` stale before `overrideIcon`, `overrideLabel`, and `shouldClearBadge`.

## Fix

Move the preserved receiver into the already-allocated four-bit work register before each field
read, then read the field through that register:

```smali
move-object/from16 v$workRegister, v$thisRegister
iget-object v$workRegister, v$workRegister, $navigationField
```

`move-object/from16` is format 22x (8-bit destination, 16-bit source), so `v$workRegister` stays
four-bit and `v$thisRegister` may be any register. The rest of the snippet is unchanged.

## Evidence

Patched `Lcom/x/android/r0;->invoke` (12.29, after the fix):

```smali
[00af] (94) move-object/from16 v6, v22
[00b1] (95) iget-object v6, v6, Lcom/x/android/r0;->b:Ljava/lang/Object;
[00b3] (96) invoke-static v6, v2, Lapp/.../NavBarReplacement;->overrideIcon(...)
```

Before the fix the three `iget-object` instructions were absent and the assembler printed the
`Invalid register` warnings. After the fix the warnings are gone (`0`), and
`dexscope verify-method` on the patched method exits 0 with no errors.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- 12.29.0-alpha.04, 12.28.0-prod.01, 12.27.0-prod.01 — full `./patch-twitter.sh
  --continue-on-error` runs report `Invalid register` count `0`, failure count `0`, and `Saved to`.

## Notes

A focused unit fixture was not added because the snippet is emitted from a
`NavBarItemContentTarget` assembled from the target APK; the invariant is instead enforced at the
single emission site and checked by the patched-bytecode verification above.
