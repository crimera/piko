# Customize drawer items

## Status

**Ported to 12.17.3-alpha.01; patch-applied and final-APK verified.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/drawer/CustomizeDrawerPatch.kt`

## Breakage

1. **Footer item parameter ordering:**
   - In 12.14.0, drawer footer items (Settings, Help Center, etc.) were rendered by a Composable method with parameters `(String, Icon, Function0, Modifier, Composer, int)`.
   - In `12.17.3-alpha.01`, the Compose compiler changed parameter ordering to `(int, Composer, Modifier, Icon, String, Function0)`.
   - `XLiteDrawerFooterItemFingerprint` failed with a match count of 0.

2. **Title parameter register calculation:**
   - `injectDrawerItemGuard` assumed the title string was always `p0` (`titleParameterRegister = p0Register`).
   - In the alpha footer item renderer, `String` is parameter 4 (`p4`). Treating `p0` (an integer) as the title object would cause a DEX verifier failure at runtime.

## Findings and fix

- Updated `XLiteDrawerFooterItemFingerprint` parameter types to `listOf("I", "Landroidx/compose/runtime/Composer;", "L", "L", "Ljava/lang/String;", "Lkotlin/jvm/functions/Function0;")`.
- Updated `injectDrawerItemGuard` to resolve the `Ljava/lang/String;` parameter index dynamically and compute `titleParameterRegister` from the sum of preceding parameter widths.
- Verified that `XLiteDrawerContentClassFingerprint` (matched in `com.x.main.drawer.q`) and `XLiteDrawerMenuItemFingerprint` match with cardinality 1.

## Verification

- `:patches:build` passed.
- Patch exclusively applied against `com.twitter.android` `12.17.3-alpha.01`.
- Final APK generated and signed: `/tmp/twitter-12.17.3-alpha.01-customize-drawer.apk`.
