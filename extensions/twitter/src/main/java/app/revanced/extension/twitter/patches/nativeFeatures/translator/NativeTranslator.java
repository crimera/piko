package app.revanced.extension.twitter.patches.nativeFeatures.translator;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.revanced.extension.twitter.Utils;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.twitter.patches.nativeFeatures.translator.providers.Translate;
import app.revanced.extension.twitter.patches.nativeFeatures.translator.providers.GTranslate;
import app.revanced.extension.twitter.patches.nativeFeatures.translator.providers.GTranslateV2;
import app.revanced.extension.twitter.Pref;

import app.revanced.extension.twitter.entity.Tweet;

public class NativeTranslator {

    public static void translate(Context activity, Object tweetObj) throws Exception {
        try{
        String text = "";
        Tweet tweet = new Tweet(tweetObj);
        try {
            text = tweet.getText();
        }catch (Exception e){
            text = e.toString();
        }
        // If text is empty.
        if(text == ""){
            String translatedText = StringRef.str("piko_native_translator_zero_text");
            DialogBox dialogBox = new DialogBox(activity,"",translatedText);
            dialogBox.show();
            return;
        }

        String toLang = Pref.translatorLanguage();
        String tweetLang = tweet.getTweetLang();

        // If both the tweet language and requested language are same.
        if(tweetLang.toLowerCase() == toLang.toLowerCase()){
            String translatedText = StringRef.str("translate_tweet_same_language",toLang);
            DialogBox dialogBox = new DialogBox(activity,"",translatedText);
            dialogBox.show();
            return;
        }
        int providerCode = Pref.natveTranslatorProvider();

        Translate translator;
        switch (providerCode){
            case 1: {
                translator = new GTranslateV2();
                break;
            }
            default: {
                translator = new GTranslate();
            }
        }
        String providerName = translator.getProviderName();
        String header = StringRef.str("translate_tweet_link_label",tweetLang,providerName);

        translator.translate(text, toLang, new Translate.TranslationCallback() {
            @Override
            public void onTranslationComplete(String translatedText) {
                // Update UI with translated text
                DialogBox dialogBox = new DialogBox(activity,header,translatedText);
                dialogBox.show();
            }

            @Override
            public void onError(Exception e) {
                // Handle translation error
                Utils.toast("Translation failed: " + e.getMessage());
            }
        });
    }catch (Exception ex){Utils.logger(ex);}
    }
}