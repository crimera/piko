package app.revanced.extension.twitter.patches.customise.font;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;

import android.app.Fragment;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.twitter.settings.Settings;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import app.revanced.extension.twitter.Utils;
import java.io.File;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.twitter.settings.SettingsStatus;

public class UpdateFont {
    public static final String FONT_FILE_NAME = "custom_font.ttf";
    public static final String EMOJI_FONT_FILE_NAME = "custom_emoji_font.ttf";

    private static Typeface textTypeface;
    private static Typeface emojiTypeface;
    private static Context context;
    private static boolean isCustomFontEnabled;
    private static boolean isCustomEmojiFontEnabled;
    private static Pattern emojiPattern;

    static {
        context = app.revanced.extension.shared.Utils.getContext();
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
                Utils.showRestartAppDialog(context);
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
        if (!(textTypeface == null)) {
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