# Remove Ads

## Why it broke

Ad filtering entered `XLiteTimelineFilter`, whose Java bytecode directly referenced readable timeline classes. Alpha lacks those descriptors, producing `NoClassDefFoundError` before filtering could run.

## Fix

- Added the timeline model adapter dependency.
- Changed filter internals to generic `Object` values.
- Moved post/module/ad recognition and property extraction to patch-time-injected bridges.
- Resolve promoted metadata, entry IDs, event info, module children, and reconstruction against each target APK.

## Verification

- Build passes.
- User runtime-verified ad filtering on 12.17.3-alpha.01: working without `NoClassDefFoundError`.
- Final-DEX inspection and regression tests on 12.15.1 and 12.14.0 remain pending.
