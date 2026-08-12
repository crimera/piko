# Disable Automatic Timeline Refresh

## Why it broke

The patch hardcoded `Lcom/x/models/timelines/TimelineType;`. The alpha obfuscated that enum to another descriptor, so matching and injected field references were invalid.

## Fix

- Locate the unique `FOR_YOU` static field reference in the matched lifecycle method.
- Derive the enum descriptor from that field's owner/type.
- Validate the preceding timeline getter against the resolved descriptor.
- Use the resolved descriptor for injected `FOR_YOU` and `FOLLOWING` references.

No alpha-specific descriptor is stored in source.

## Verification

- Patch code builds.
- User runtime-verified the patch on 12.17.3-alpha.01: working.
- Final-DEX inspection and regression tests on 12.15.1 and 12.14.0 remain pending.
