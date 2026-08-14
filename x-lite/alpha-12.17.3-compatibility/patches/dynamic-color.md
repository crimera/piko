# Dynamic color

## Status

**Ported, patch-tested, and runtime-confirmed on 12.17.3-alpha.01; full behavior matrix pending.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/dynamiccolor/DynamicColorPatch.kt`

## Breakage

The alpha merges the STANDARD and DIM palette factories into one synthetic `Function0.invoke()` method, so the old one-allocation-per-factory assumption failed. The inline-action entry and action-type models are now obfuscated, their renderer reads fields directly instead of preserved getters, the like composable's Compose state descriptor changed, and one accent constructor now loads packed colors into `v0`.

## Port checklist

1. Run this patch alone against 12.17.3-alpha.01 and record the exact match/failure.
2. Compare the matched alpha bytecode with 12.15.1 and 12.14.0.
3. Remove hardcoded obfuscated descriptors, method names, generated literals, or removed host resources.
4. Assert expected fingerprint cardinality.
5. Build the MPP and inspect the final DEX for a reachable mutation.
6. Install and test the feature on alpha.
7. Repatch and regression-test 12.15.1 and 12.14.0.

## Findings and fix

- Resolve theme-variant selector values from the synthetic enum mapping initializer, then map each provider branch to its cache field instead of relying on cache-read order.
- Resolve each palette branch by the constructor's `isLight` argument, not by assuming one palette allocation per synthetic factory method.
- Assert three distinct `(method, allocation index)` branches and mutate shared methods from the highest instruction index downward.
- Validate the full 130-color palette as ten reversed 13-tone scales before replacing the primary accent scale.
- Resolve `InlineActionEntry` from ordered stable data-class labels, then derive its action-type and enabled fields from the corresponding `toString()` value reads.
- Match the renderer using the resolved fields and public Compose ABI parameters; no alpha model descriptor is hardcoded.
- Require exactly one like composable constructor whose owner exposes the expected Compose lambda invoke shape.
- Anchor the Lottie fallback to the exact activated-like color field.
- Permit valid wide color registers starting at `v0`.

## Verification

- `:patches:build`: passes.
- Exclusive alpha patch reports `Applied: X-Lite: Dynamic color`.
- Initial runtime-confirmed output: `/tmp/twitter-12.17.3-alpha.01-dynamic-color.apk`.
- Review-hardened exclusive output: `/tmp/twitter-12.17.3-alpha.01-dynamic-color-reviewed.apk`.
- User confirmed the dynamic-color feature works on-device.
- The full light/dark/AMOLED, inline-action tint, dynamic-like, and restart-persistence matrix remains pending.
