package app.morphe.extension.twitter.patches.customise.font;

import android.os.Build;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.shared.StringRef;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;

import app.morphe.extension.twitter.settings.SettingsStatus;

public class UpdateFont {
    public static final String FONT_FILE_NAME = "custom_font.ttf";
    public static final String EMOJI_FONT_FILE_NAME = "custom_emoji_font.ttf";

    private static Typeface textTypeface;
    private static Typeface emojiTypeface;
    private static final Context context;
    private static final boolean isCustomFontEnabled;
    private static final boolean isCustomEmojiFontEnabled;
    private static final Pattern emojiPattern;

    static {
        context = app.morphe.extension.shared.Utils.getContext();
        isCustomFontEnabled = SettingsStatus.customFont;
        isCustomEmojiFontEnabled = SettingsStatus.customEmojiFont;
        emojiPattern = Pattern.compile(
                "[\uD83C-\uDBFF\uDC00-\uDFFF]+" // Matches surrogate pairs for emojis
        );

        if(isCustomFontEnabled){
            loadFont(FONT_FILE_NAME,false);
        }
        if(isCustomEmojiFontEnabled){
            loadFont(EMOJI_FONT_FILE_NAME,true);
        }
    }

    public static void loadFont(String fontName,boolean isEmojiFont){
        File fontFile = new File(context.getFilesDir(), fontName);
        if (!fontFile.exists()) {
            Utils.logger("Font not found: " + fontFile.getAbsolutePath());
        }else{

            Typeface typeface = Typeface.createFromFile(fontFile);
            if(isEmojiFont){
                emojiTypeface = typeface;
            }else{
                textTypeface = typeface;
            }
            Utils.logger("Font loaded: " + fontFile.getAbsolutePath());
        }
    }

    public static void deleteFont(boolean isEmojiFont){
        String filename = isEmojiFont ? EMOJI_FONT_FILE_NAME : FONT_FILE_NAME;
        File fontFile = new File(context.getFilesDir(), filename);
        if (fontFile.exists()) {
            if (fontFile.delete()) {
                Utils.toast(StringRef.str("piko_pref_delete_font_success"));
            } else {
                Utils.toast(StringRef.str("piko_pref_delete_font_fail"));
            }
        } else {
            Utils.toast(StringRef.str("piko_pref_delete_font_warn"));
        }
    }

    public static Spannable process(CharSequence input) {
        if(input == null) return null;
        SpannableString ss = new SpannableString(input);
        if (textTypeface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if(isCustomFontEnabled) {
                ss.setSpan(
                    new TypefaceSpan(textTypeface),
                    0,
                    input.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            if(isCustomEmojiFontEnabled) {
                Matcher matcher = emojiPattern.matcher(input);

                while (matcher.find()) {
                    ss.setSpan(
                        new TypefaceSpan(emojiTypeface),
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
        }
        return ss;
    }


}