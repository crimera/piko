package app.morphe.extension.twitter.patches.nativeFeatures.translator;

import android.content.Context;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.Translate;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.GTranslate;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.GTranslateV2;
import app.morphe.extension.twitter.Pref;

import app.morphe.extension.twitter.entity.Tweet;

public class NativeTranslator {

    public static void translate(Context activity, Object tweetObj) throws Exception {
        try{
        String text = "";
        Tweet tweet = new Tweet(tweetObj);
        text = tweet.getText();
        // If text is empty.
        if(text == ""){
            Utils.logger(StringRef.str("piko_native_translator_zero_text"));
            return;
        }

        String toLang = Pref.translatorLanguage();
        String tweetLang = tweet.getTweetLang();

        // If both the tweet language and requested language are same.
        if(tweetLang.toLowerCase() == toLang.toLowerCase()){
            Utils.logger(StringRef.str("translate_tweet_same_language",toLang));
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
                Utils.logger("Translation failed: " + e.getMessage());
            }
        });
    }catch (Exception ex){
            Utils.logger(ex);
        }
    }
}