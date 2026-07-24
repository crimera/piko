/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.profile;

import android.content.Context;
import android.view.View;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.crimera.PikoUtils;
import static app.morphe.extension.shared.StringRef.str;

import app.morphe.extension.twitter.entity.Debug;
import app.morphe.extension.twitter.entity.TwitterUser;
import app.morphe.extension.twitter.Pref;

import com.twitter.ui.tweet.TweetStatView;

public class MoreProfileInfo {

    private static void setTweetStatViewValue(Context context,TweetStatView tweetStatView,String name,int value){
        // The code will be injected in patching.
    }

    private static String headerComponentViewFieldName(){
        return "c";
    }

    private static String headerComponentContextFieldName(){
        return "a";
    }

    private static void setTweetStatView(Context context,View rootView, String resourceName, String text, int count){
        int targetId = ResourceUtils.getIdentifier(ResourceType.ID, resourceName);
        TweetStatView tweetStatView = (TweetStatView)  rootView.findViewById(targetId);

        setTweetStatViewValue(context, tweetStatView, text, count);
        if(count>0)
            tweetStatView.setVisibility(View.VISIBLE);
    }

    public static void addTweetStatView(Object headerComponent, Object TwitterUserObject){
        try {
            if(Pref.moreInfoOnProfile()) {
                TwitterUser twitterUserEntity = new TwitterUser(TwitterUserObject);
                Debug headerComponentEntity = new Debug(headerComponent);

                View rootView = (View) headerComponentEntity.getField(headerComponentViewFieldName());
                Context context = (Context) headerComponentEntity.getField(headerComponentContextFieldName());

                int count = twitterUserEntity.getFastFollowersCount();
                String text = str("piko_fast_follower");
                setTweetStatView(context, rootView, "fast_follower_stat", text, count);

                count = twitterUserEntity.getStatusCount();
                text = str("profile_tab_title_posts");
                setTweetStatView(context, rootView, "tweet_stat", text, count);

                count = twitterUserEntity.getArticleCount();
                text = str("profile_tab_title_articles");
                setTweetStatView(context, rootView, "article_stat", text, count);

                count = twitterUserEntity.getMediaCount();
                text = str("profile_tab_title_media");
                setTweetStatView(context, rootView, "media_stat", text, count);

                count = twitterUserEntity.getLikesCount();
                text = str("profile_tab_title_favorites");
                setTweetStatView(context, rootView, "likes_stat", text, count);
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }

    }

    public static String addUserId(String userName, Object TwitterUserObject){
        try{
            if(Pref.moreInfoOnProfile()) {
                TwitterUser twitterUserEntity = new TwitterUser(TwitterUserObject);
                long userId = twitterUserEntity.getId();
                return userName + "\n#" + Long.valueOf(userId);
            }
            return userName;
        } catch (Exception e) {
            PikoUtils.logger(e);
            return userName;
        }
    }



}