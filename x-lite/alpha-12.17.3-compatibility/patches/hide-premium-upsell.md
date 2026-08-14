## Status

**Ported and verified on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/premium/HidePremiumUpsellPatch.kt`

## Breakage

On 12.17.3-alpha.01, the home top bar upsell logic moved from `SubscriptionsFeatures` into the Compose home tabbed scaffold. The legacy `XLiteHomeNavUpsellTypeFingerprint` and `XLiteHomeNavUpsellEnabledFingerprint` did not match because `SubscriptionsFeatures` was obfuscated to `Lcom/x/subscriptions/i;` and the home nav upsell method was inlined.

## Findings and Fix

1. **Compose Home Scaffold Fingerprint (`XLiteHomeNavUpsellComposableFingerprint`)**:
   - Class anchor: `XLiteHomeTabbedScaffoldClassFingerprint` matching `scaffold_home_tabbed` in `com/x/home/tabbed/a.smali`.
   - Method fingerprint: matches outer header `@Composable` static method `i(Lcom/x/profilepicture/b;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;ZLcom/x/home/c;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V`.
2. **Hook**:
   - Injected `hidePremiumUpsell.injectRead(...)` at index 0.
   - When enabled, sets `p4` (`Lcom/x/home/c;`) to `null` (`const/16 v$p4Reg, 0x0`).
   - With `p4 == null`, `a.i` takes its native `if (p4 != null)` false branch to the empty Compose group `0x7c61066f`, skipping the inner `a.c` composable entirely and preventing `NoWhenBranchMatchedException`.

## Verification Evidence

- `:patches:build`: Clean build.
- `morphe patch --exclusive -e "X-Lite: Hide premium upsell"`:
  - Successfully applied against `com.twitter.android` `12.17.3-alpha.01`.
  - Verified at runtime on alpha: home top bar renders cleanly without premium upsell chip or crash.
  - Output APK signed and saved at `/tmp/twitter-12.17.3-alpha.01-hide-upsell.apk`.
