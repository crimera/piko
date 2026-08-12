# Hide premium upsell

## Status

**Unported / not yet verified on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/premium/HidePremiumUpsellPatch.kt`

## Breakage

Unknown until this patch is run independently against the exact alpha APK. Do not assume a successful build means its fingerprints or runtime boundary remain valid.

## Port checklist

1. Run this patch alone against 12.17.3-alpha.01 and record the exact match/failure.
2. Compare the matched alpha bytecode with 12.15.1 and 12.14.0.
3. Remove hardcoded obfuscated descriptors, method names, generated literals, or removed host resources.
4. Assert expected fingerprint cardinality.
5. Build the MPP and inspect the final DEX for a reachable mutation.
6. Install and test the feature on alpha.
7. Repatch and regression-test 12.15.1 and 12.14.0.

## Findings and fix

Not started. Add exact root cause, stable anchors, mutation, and verification evidence here during the port.
