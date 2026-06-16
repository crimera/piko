/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.translator;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.Translate;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.GTranslate;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.GTranslateV2;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.providers.FxTwitterTranslate;
import app.morphe.extension.twitter.Pref;

import app.morphe.extension.twitter.entity.Tweet;

public class NativeTranslator {

    public static void translate(Context activity, Object tweetObj) throws Exception {
        try{
            Tweet tweet = new Tweet(tweetObj);
            String text = tweet.getText();
            Long tweetId = tweet.getTweetId();
            // If text is empty.
            if(text == ""){
                PikoUtils.logger(str("piko_native_translator_zero_text"));
                return;
            }

            String toLang = Pref.translatorLanguage();
            String tweetLang = tweet.getTweetLang();

            // If both the tweet language and requested language are same.
            if(tweetLang.toLowerCase() == toLang.toLowerCase()){
                PikoUtils.logger(str("translate_tweet_same_language",toLang));
                return;
            }
            int providerCode = Pref.natveTranslatorProvider();

            Translate translator;
            switch (providerCode){
                case 1: {
                    translator = new GTranslateV2();
                    break;
                }
                case 2: {
                    translator = new FxTwitterTranslate();
                    break;
                }
                default: {
                    translator = new GTranslate();
                }
            }
            translator.translate(tweetId, text, toLang, new Translate.TranslationCallback() {
                @Override
                public void onTranslationComplete(String translatedText) {
                    // FxTwitter has multiple sources, so provider name is set after API call.
                    String providerName = translator.getProviderName();
                    String header = str("translate_tweet_link_label",tweetLang,providerName);

                    // Update UI with translated text
                    DialogBox dialogBox = new DialogBox(activity,header,translatedText);
                    dialogBox.show();
                }

                @Override
                public void onError(Exception e) {
                    // Handle translation error
                    PikoUtils.logger("Translation failed: " + e.getMessage());
                    }
                });
        }catch (Exception ex){
            PikoUtils.logger(ex);
        }
    }
}