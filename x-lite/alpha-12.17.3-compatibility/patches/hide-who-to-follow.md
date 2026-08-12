# Hide Who to Follow

## Why it broke

Module detection, module-item unwrapping, and module reconstruction directly referenced unobfuscated timeline stubs. Those model descriptors changed in the alpha.

## Fix

- Added the timeline model adapter dependency.
- Use `Object` and generic lists in extension code.
- Inject release-specific module/module-item checks, field reads, and constructors at patch time.
- Continue identifying recommendation modules by the stable `who-to-follow` entry-ID prefix.

Header-text fallback matching was removed with the unstable typed header boundary; entry-ID matching remains.

## Verification

- Build passes.
- User runtime-verified who-to-follow filtering on 12.17.3-alpha.01: working.
- Final-DEX inspection and regression tests on 12.15.1 and 12.14.0 remain pending.
