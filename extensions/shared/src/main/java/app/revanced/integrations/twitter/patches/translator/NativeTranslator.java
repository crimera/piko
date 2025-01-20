package app.revanced.integrations.twitter.patches.translator;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.revanced.integrations.twitter.Utils;
import app.revanced.integrations.shared.StringRef;
import app.revanced.integrations.twitter.patches.translator.providers.Translate;
import app.revanced.integrations.twitter.patches.translator.providers.GTranslate;
import app.revanced.integrations.twitter.patches.translator.providers.GTranslateV2;
import app.revanced.integrations.twitter.Pref;

public class NativeTranslator {

    private static String getLang(Object tweet){
        try{

            Field tweetInfofield = tweet.getClass().getDeclaredField(Constants.getTweetInfoField());
            tweetInfofield.setAccessible(true);
            Object tweetInfo = tweetInfofield.get(tweet);

            Field langfield = tweetInfo.getClass().getDeclaredField(Constants.getLangField());
            langfield.setAccessible(true);
            String langShort = (String) langfield.get(tweetInfo);
            return Constants.getLanguageNameFromLocale(langShort);
        }catch (Exception ex){
            Utils.toast("Language detection failed: " + ex.getMessage());
            Utils.logger(ex.getMessage());
        }
        return "xxx";

    }

    private static String getShortText(Object tweet) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object instance = tweet.getClass().getDeclaredMethod(Constants.getShortTextMethodName()).invoke(tweet);
        Object text = instance.getClass().getDeclaredMethod("getText").invoke(instance);
        return text!=null?(String) text : "";
    }

    private static String getLongText(Object tweet) throws ClassNotFoundException,NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object instance = tweet.getClass().getDeclaredMethod(Constants.getLongTextMethodName()).invoke(tweet);
        if(instance==null){
            return "";
        }
        Field field = instance.getClass().getDeclaredField(Constants.getLongTextFieldName());
        field.setAccessible(true);
        Object text = field.get(instance);
        return text!=null?(String) text : "";
    }

    private static String getText(Object tweet) throws Exception{
        String text = getLongText(tweet);
        if(text.equals("")){
            text = getShortText(tweet);
        }
        if(text.length()>0){
            int mediaIndex = text.indexOf("pic.x.com");
            if(mediaIndex>0) text = text.substring(0,mediaIndex);
        }
        return text;
    }

    public static void translate(Context activity, Object tweet) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        String text = "";
        try {
            text = getText(tweet);
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
        String tweetLang = getLang(tweet);

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
    }
}