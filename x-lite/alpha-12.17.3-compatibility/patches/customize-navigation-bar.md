# Customize navigation bar items

## Status

**Ported and runtime-tested on 12.17.3-alpha.01: working.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/navbar/CustomizeNavBarPatch.kt`

## Breakage

The original fingerprint hardcoded the unobfuscated tab enum descriptor and required the old 16-parameter State constructor with a specific `ProfileUser` parameter. The alpha obfuscated the enum descriptor and changed the constructor contract.

## Findings and fix

- Matched `getEntries`, `COMMUNITIES`, and `SPACES` without embedding the alpha enum descriptor.
- Validated that all three anchors resolve to the same enum type at patch time.
- Accepted the alpha State constructor variants and located tab data through stable list/map parameter positions instead of the old model descriptor.
- Added match and reference validation so ambiguous or inconsistent matches fail during patching.

The alpha hardening was implemented in commit `2aad3537b` (`fix(x-lite): harden tab navbar fingerprint for 12.17.3-alpha.01`).

## Verification

- User applied the ported patch on `12.17.3-alpha.01`.
- Navigation bar customization works successfully.
