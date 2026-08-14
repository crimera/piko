## Status

**Ported and verified on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/customfont/CustomFontPatch.kt`

## Breakage

None. The Compose AndroidParagraphIntrinsics constructor contract, MetricAffectingSpan hooks, and EmojiCompat processing sequences remain identical on 12.17.3-alpha.01.

## Findings and Fix

1. **`composeParagraphTypefaceFingerprint`**:
   - Matches `androidx/compose/ui/text/platform/d.<init>(Ljava/lang/String;Landroidx/compose/ui/text/b1;Ljava/util/List;Ljava/util/List;Landroidx/compose/ui/text/font/i;Landroidx/compose/ui/unit/c;)V` (6 parameters).
   - Replaces `invoke-virtual {vPaint, vTypeface}, Landroid/graphics/Paint;->setTypeface` with `invoke-static {vPaint, vTypeface}, Lapp/morphe/extension/xlite/misc/UpdateFont;->applyTypeface`.
2. **`ComposeSpanTypefaceFingerprint`**:
   - Matches both `updateDrawState` and `updateMeasureState` in `androidx/compose/ui/text/android/style`.
   - Replaces `Paint.setTypeface` calls with `UpdateFont.applyTypeface`.
3. **EmojiCompat processing**:
   - Locates the single `EmojiCompat.process()` invocation, the raw-text bypass branch, and downstream list empty checks in the Paragraph Intrinsics constructor.
   - Successfully hooks `UpdateFont.processComposeEmoji(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;` into both the EmojiCompat result path and raw-text fallback path.
4. **Font picker and settings actions**:
   - Retired the legacy `FontPickerFragment` which attempted to replace the non-existent `fragment_container` ID in the custom `XLiteSettingsActivity`.
   - Converted `UpdateFont` to use direct `activity.startActivityForResult` with `ACTION_OPEN_DOCUMENT` and handle results via `UpdateFont.handleActivityResult(Activity, int, int, Intent)` wired into `XLiteSettingsActivity.onActivityResult`.

## Verification Evidence

- `:patches:build`: Clean build.
- `morphe patch --exclusive -e "X-Lite: Custom font"`:
  - Applied cleanly against `com.twitter.android` `12.17.3-alpha.01`.
  - Font and emoji font file import, validation, copying, and restart dialog verified working at runtime on alpha.
  - Output APK signed and saved at `/tmp/twitter-12.17.3-alpha.01-custom-font.apk`.
