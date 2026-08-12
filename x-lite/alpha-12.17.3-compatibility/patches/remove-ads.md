# Remove Ads

## Why it broke

Ad filtering entered `XLiteTimelineFilter`, whose Java bytecode directly referenced readable timeline classes. Alpha lacks those descriptors, producing `NoClassDefFoundError` before filtering could run.

## Fix

- Added the timeline model adapter dependency.
- Changed filter internals to generic `Object` values.
- Moved post/module/ad recognition and property extraction to patch-time-injected bridges.
- Resolve promoted metadata, entry IDs, event info, module children, and reconstruction against each target APK.

## Verification

Build passes. Isolated alpha patching, final-DEX inspection, and runtime ad filtering remain pending.
