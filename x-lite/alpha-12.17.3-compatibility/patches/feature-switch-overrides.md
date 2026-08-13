# Feature switch overrides

## Status

**Patch-applied and final-Dex verified on 12.17.3-alpha.01. Runtime verification is pending because no ADB device is connected.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/featureflags/FeatureFlagPatch.kt`

## Target evidence

- Package/version: `com.twitter.android` `12.17.3-alpha.01`
- Input APK SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256: `40ad73f7d79731f7ff2c422f8a37f6406f2f675f58eca2e144a431716b917480`
- Reviewed output: `/tmp/twitter-12.17.3-alpha.01-feature-switch-reviewed.apk`
- Output SHA-256: `5ab6cdf2f3b45ffc64caa0c629f5038492fbd9e48caa02ff7b2b676638bc1772`

## Resolution and mutation

The alpha contains the preserved owner:

```text
Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;
```

The patch now uses `mutableClassDefBy` for indexed owner lookup instead of a global fingerprint. It then matches the exact public `get*` and `peek*` names, complete parameter descriptors, and return descriptors. Cardinality is asserted as one `get` and one `peek` accessor for each of Boolean, Float, Int, Long, Double, String, and List.

Each matched method is cloned with preserved parameter registers. Every reachable return is wrapped with the corresponding typed `FeatureSwitchStore.resolve*` call. The final owner smali contains 28 resolver calls: two return paths for each of 14 accessors. The APK was decoded successfully after patching, proving the injected register and return shapes assemble.

The old `android_system_dns_timeout_ms` string anchor is not used: it is outside this repository implementation in the alpha. The 12.14.0 artifact uses a different, obfuscated repository owner and is not covered by the current alpha-only compatibility declaration.

## Validation

- `:patches:build`: passed.
- Final owner smali contains 28 resolver calls with 28 paired `move-result*` instructions.
- Runtime install/UI verification: pending; no ADB device is connected.
