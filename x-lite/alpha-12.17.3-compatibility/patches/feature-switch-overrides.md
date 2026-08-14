# Feature switch overrides

## Status

**Patch-applied and final-Dex verified on 12.17.3-alpha.01. Runtime verification is pending because no ADB device is connected.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/featureflags/FeatureFlagPatch.kt`

## Port journey

The feature began as the typed override implementation in `be1e8bca1` and received import/export actions in `9f2329012`. The alpha port and fingerprint hardening were completed in `f85fcfe8`.

The pre-port repository fingerprint was not stable for the alpha. It searched globally for a method with return type `J` containing the unordered string anchor `android_system_dns_timeout_ms`, then required `getBoolean` and `getString` signatures in a `com/x/featureswitches` class. Exact alpha smali showed that the DNS string was not in the feature-switch repository implementation. The alpha also exposes both `get*` and `peek*` accessors for every supported value type, while the old patch expected only one accessor for most non-Boolean types.

The alpha-only compatibility declaration is intentional. The 12.14.0 artifact uses a different obfuscated repository owner, so this port does not claim cross-version support.

## Resolution and mutation strategy

The alpha contains the preserved owner:

```text
Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;
```

The patch uses `mutableClassDefBy` for indexed owner lookup instead of a global fingerprint. It then matches exact public, non-static `get*` and `peek*` names together with complete parameter and return descriptors. Cardinality is asserted as one `get` and one `peek` accessor for each of Boolean, Float, Int, Long, Double, String, and List.

Each matched method is cloned with preserved parameter registers. Every reachable return is wrapped with the corresponding typed `FeatureSwitchStore.resolve*` call. Register bounds are checked before emitting the `invoke-static` instruction. Runtime code remains typed through the stable extension store; no release-specific X-Lite model type or runtime reflection is required.

## Validation evidence

- Package/version: `com.twitter.android` `12.17.3-alpha.01`
- Input APK: `/Users/steven/Downloads/twitter_12.17.3-alpha.01.apk`
- Input APK SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256: `40ad73f7d79731f7ff2c422f8a37f6406f2f675f58eca2e144a431716b917480`
- Reviewed output: `/tmp/twitter-12.17.3-alpha.01-feature-switch-commit.apk`
- Output SHA-256: `5ab6cdf2f3b45ffc64caa0c629f5038492fbd9e48caa02ff7b2b676638bc1772`
- `:patches:build`: passed.
- Exclusive patch application: passed.
- Final owner smali: 28 resolver calls with 28 paired `move-result*` instructions, covering all 14 accessors and their reachable return paths.
- Final APK decoding: passed.

## Runtime status

No ADB device was connected during the session. Therefore the feature-switch settings screen, override persistence, import/export behavior, and actual runtime value replacement were **not** verified on-device. The evidence supports patch application and final-Dex correctness only; it does not support claiming that the UI or overrides work at runtime yet.
