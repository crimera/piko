# Open canonical URLs

## Status

**Ported and runtime-tested on 12.17.3-alpha.01: working.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/canonicalurls/CanonicalUrlsPatch.kt`

## Target evidence

- Package/version: `com.twitter.android` `12.17.3-alpha.01`
- Input APK SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256: `a9664ce88d8e428c7266dd6ba1767c57b2e3fddefb21d296c34699d881976ee0`
- Reviewed output: `/tmp/twitter-12.17.3-alpha.01-canonical-reviewed.apk`
- Output SHA-256: `7a20a864e84965cbbc2facac036dcca275a017995ec133c134a900293a3da405`

## Resolution and mutation

The alpha no longer exposes the old model descriptors/getters used by the previous patch. The port, implemented in commit `33f9bbbf1` (`fix(xlite): port canonical URL patch to alpha`), resolves:

- URL entity from `UrlEntity(displayUrl=..., expandedUrl=..., url=...)` data-class text;
- mention entity from `MentionEntity(userId=..., startIdx=...)` text;
- contextual post from `ContextualPost(canonicalPost=..., quotedPost=...)` text;
- URL entity string fields from the target model's verified `toString()` field references.

The patch then builds release-specific fingerprints using the resolved fields and stable `android.net.Uri` calls. Cardinality is asserted for the text-entity navigation method, URL picker, and post-link click handler.

Final-Dex comparison against the unpatched alpha confirmed:

- `Lcom/x/navigation/ma;->f(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;` returns its expanded second argument when non-null, otherwise retains the original fallback path.
- The text-entity navigation fallback URL read in `Lcom/x/navigation/ma;` changed from the short URL field to the expanded URL field.
- The post-link click URL read in `Lcom/x/urt/items/post/x4;` changed from the short URL field to the expanded URL field.

The field resolution follows the URL entity constructor's semantic `displayUrl`, `expandedUrl`, and `url` parameter labels to their owner-defined `iput-object` writes. This avoids hardcoded obfuscated field names and does not depend on the alpha compiler's helper-based `toString()` implementation.

## Validation

- `:patches:build`: passed.
- Exclusive patch application: passed.
- Final APK decoded successfully with apktool.
- Original-vs-final smali comparison confirmed the three expected behavioral mutations and no model mutation.
- User applied the ported patch on `12.17.3-alpha.01` and confirmed canonical URLs open correctly.
