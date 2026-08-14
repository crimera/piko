# Customize default media tab

## Status

**Ported to 12.17.3-alpha.01; patch-applied and final-APK verified.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/mediatab/DefaultMediaTabPatch.kt`

## Target evidence

- The combined profile timeline constructor in `12.17.3-alpha.01` (`com/x/profile/timeline/b.<init>`) matched `XLiteCombinedProfileTimelineSeedFingerprint` with cardinality 1.
- In `12.17.3-alpha.01`, the tab enum is `Lcom/x/profile/r;`, which retains the expected `Photos` and `Videos` enum constants.
- The constructor builds `filled-new-array/range {p1 .. p2}, [Lcom/x/profile/r;` and seeds the `MutableStateFlow` with `v1` (`primaryType`).
- The patch dynamically injects `MediaTabResolver->getEnumDefault(Ljava/lang/Object;)Ljava/lang/Object;` with a dynamic `check-cast` to `Lcom/x/profile/r;` before the `MutableStateFlow` factory invoke.

## Verification

- `:patches:build` passed.
- Patch exclusively applied against `com.twitter.android` `12.17.3-alpha.01`.
- Final APK generated and signed: `/tmp/twitter-12.17.3-alpha.01-default-media-tab.apk`.
