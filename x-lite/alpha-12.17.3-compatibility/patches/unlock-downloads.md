# Unlock downloads

## Status

**Ported to 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/premium/UnlockDownloadsPatch.kt`
Fingerprints: `patches/src/main/kotlin/app/crimera/patches/xlite/premium/DownloadFingerprints.kt`

## Breakage in 12.17.3-alpha.01

1. **`SubscriptionsFeatures` descriptor obfuscation**:
   - The unobfuscated interface descriptor `Lcom/x/subscriptions/SubscriptionsFeatures;` was obfuscated to interface `Lcom/x/subscriptions/i;` and implementation `Lcom/x/subscriptions/m;`.
   - `forceSubscriptionFeatureResults()` failed because it hardcoded `reference?.definingClass == "Lcom/x/subscriptions/SubscriptionsFeatures;"`.
2. **`SubscriptionsFeaturesHasAnyPremiumFingerprint` string changes**:
   - `com/x/subscriptions/m.smali->e()Z` checks `["feature/premium_basic", "feature/premium_plus", "feature/twitter_blue_verified"]`. The legacy `"feature/twitter_blue"` string was removed in alpha.
3. **`MediaContentVideo` & `MediaContentGif` obfuscation**:
   - `MediaContentVideo` and `MediaContentGif` inner class descriptors were obfuscated to `Lcom/x/models/x4;` and `Lcom/x/models/q4;` (extending `Lcom/x/models/b5;`).
   - `isDownloadable()` was obfuscated to `.method public final h()Z`.
4. **`XLiteVideoTabDownloadHandlerFingerprint` and inlining**:
   - Video tab download handlers (`com/x/video/tab/m` and `com/x/video/tab/legacy/j`) use obfuscated methods (`Lcom/x/models/media/l3;->h()Z` and `Lcom/x/subscriptions/m;->e()Z`).
   - The standalone `XLitePremiumSubscriptionChecker` wrapper methods were inlined into caller methods by R8 in alpha.

## Findings and Fix

1. **`forceSubscriptionFeatureResults()`**:
   - Relaxed defining class matching to `reference?.definingClass?.startsWith("Lcom/x/subscriptions/") == true && reference.returnType == "Z"`.
2. **`SubscriptionsFeaturesHasAnyPremiumFingerprint`**:
   - Updated strings to `listOf("feature/premium_basic", "feature/premium_plus", "feature/twitter_blue_verified")` with `returnType = "Z"` (preventing accidental matches on reference return methods like `f()`). Overriding `e()Z` to return `true` makes `hasAnyPremium()` return `true` across all callers.
3. **Bypass poster-disabled video/gif/image download restrictions**:
   - Post event handlers (`com/x/urt/items/post/h4.smali`) access `MediaContentVideo.i:Z`, `MediaContentGif.g:Z`, and `MediaContentImage.g:Z` directly via `IGET_BOOLEAN` rather than virtual getters.
   - `forceMediaClassDownloadable()` dynamically locates the backing `isDownloadable` boolean field from `h()Z`, overrides `h()Z` to return `true`, and injects `const/4 v$regA, 0x1` before all `IPUT_BOOLEAN` instructions in all constructors (`<init>`).
   - All media objects deserialized from the API are now permanently marked `isDownloadable = true`, unlocking downloads even on posts where the author disabled downloads.
4. **`XLiteVideoTabDownloadHandlerFingerprint`**:
   - Updated custom predicate to match video player handler methods taking `L` parameter, returning `V`, and invoking subscription feature checks and media model checks.
5. **`XLitePremiumSubscriptionCheckerFingerprint`**:
   - Used `matchAllOrNull()?.forEach` so the patch works whether standalone helper wrappers exist or were inlined by R8.

## Verification Evidence

- `:patches:build`: Passed cleanly.
- `morphe patch --exclusive -e "X-Lite: Unlock downloads"`:
  - Applied successfully against `com.twitter.android` `12.17.3-alpha.01`.
  - Bytecode verified via `dexdump`: constructor `IPUT_BOOLEAN` writes `1` to `MediaContentVideo.i` and `MediaContentGif.g`.
  - Output APK signed and generated at `/tmp/twitter-12.17.3-alpha.01-unlock-downloads.apk`.
