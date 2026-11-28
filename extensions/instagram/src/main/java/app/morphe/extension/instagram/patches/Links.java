/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.net.Uri;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.regex.Pattern;

import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import android.util.Log;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ShareLinkSanitizer;
import app.morphe.extension.instagram.constants.PostType;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.story.StorySeenRequestScope;
import app.morphe.extension.crimera.PikoUtils;

import app.morphe.extension.instagram.settings.ActivityHook;

@SuppressWarnings("unused")
public class Links {
    private static final Pattern VALID_DOMAIN =
            Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(:\\d{1,5})?");
    private static final boolean DISABLE_ADS;
    private static final ShareLinkSanitizer SHARE_LINK_SANITIZER = new ShareLinkSanitizer(
            "instagram.com",
            Arrays.asList("comment_id", "img_index", "open_comments", "story_media_id"),
            Arrays.asList("igsh", "igsi", "utm_source", "utm_medium", "utm_content", "fbclid", "si", "stkn", "mdxt", "cplk", "obrf", "vrfl", "rpxt")
    );

    static {
        DISABLE_ADS = Pref.disableAds() && SettingsStatus.disableAds;
    }

    public static boolean shouldSkipStorySeenRequest() {
        return Pref.viewStoriesAnonymously() && !StorySeenRequestScope.isActive();
    }

    public static boolean shouldBlockOnboardingScreen(String appId) {
        return "com.bloks.www.bloks.ig.ndx.ci.entry.screen".equals(appId)
                || "com.bloks.www.bloks.ig.ndx.ls.entry.screen".equals(appId);
    }

    public static boolean openExternally(String url) {
        try {
            if(Pref.openLinksExternally()) {
                // https://l.instagram.com/?u=<actual url>&e=<tracking id>
                String actualUrl = Uri.parse(url).getQueryParameter("u");
                if (actualUrl != null) {
                    String sanitizedUrl = sanitizeUrl(actualUrl);
                    // actualUrl comes from a bio/DM/comment/caption link anyone can put on
                    // Instagram - without this check a crafted intent:// (or other dangerous
                    // scheme) link would reach ACTION_VIEW unfiltered and could launch an
                    // arbitrary exported component on the device with attacker-chosen extras.
                    String scheme = Uri.parse(sanitizedUrl).getScheme();
                    if (scheme == null
                            || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        return false;
                    }
                    PikoUtils.openUrl(sanitizedUrl,false);
                    return true;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "openExternally failure", ex);
        }
        return false;
    }

    // Thanks to InstaEclipse and InstaMoon.
    public static void interceptUri(URI uri) throws IOException{
       boolean shouldBlockUri = false;
        try {
            if (uri != null && uri.getPath() != null) {
                String path = uri.getPath();

                if (path.contains("/api/v2/media/seen/")) {
                    shouldBlockUri = shouldSkipStorySeenRequest();
                    StorySeenRequestScope.onStorySeenRequest(shouldBlockUri);
                } else if (path.contains("/heartbeat_and_get_viewer_count/")) {
                    shouldBlockUri = Pref.viewLiveAnonymously();
                } else if (path.contains("profile_ads/get_profile_ads/")
                        || path.contains("/async_ads/")
                        || path.contains("/feed/injected_reels_media/")
                        || path.contains("/api/v1/ads/graphql/")) {
                    shouldBlockUri = DISABLE_ADS;
                }

            }

        } catch (Exception ex) {
            Logger.printException(() -> "intercept URI failed: ", ex);
        }
        // Exception is hanndled at call.
        if (shouldBlockUri && Pref.pikoDebug()) {
            Log.d("piko", "blocked uri: " + uri);
        }
        if(shouldBlockUri) {
            throw new IOException("Block uri");
        }
    }

    public static String sanitizeUrl(String url){
        try{
            return SHARE_LINK_SANITIZER.sanitize(url, Pref.sanitizeShareLinks());
        } catch (Exception e) {
            Logger.printException(() -> "sanitizeUrl failed: ", e);
        }
        return url;
    }

    public static String sanitizeSharedUrl(String url) {
        return SettingsStatus.sanitizeShareLinks ? sanitizeUrl(url) : url;
    }

    public static String processSharedUrl(String url) {
        // Clean the original Instagram URL before changing its host.
        String sanitizedUrl = sanitizeSharedUrl(url);
        return SettingsStatus.customSharingDomain ? changeDomain(sanitizedUrl) : sanitizedUrl;
    }

    public static String changeDomain(String url) {
        try {
            String domain = normalizeCustomDomain(Pref.customSharingDomain());
            if (domain.isEmpty()) return url;

            Uri uri = Uri.parse(url);
            String host = uri.getHost();

            if (host == null
                    || !(host.equalsIgnoreCase("instagram.com") || host.equalsIgnoreCase("www.instagram.com"))
                    || host.equalsIgnoreCase(domain)) {
                return url;
            }

            return uri.buildUpon().encodedAuthority(domain).build().toString();
        } catch (Exception e) {
            Logger.printException(() -> "changeDomain failed: ", e);
        }
        return url;
    }

    public static String customSharingDomainSummary(String customDomain) {
        String domain = normalizeCustomDomain(customDomain);
        return domain.isEmpty() ? str("piko_custom_sharing_domain_desc") : domain;
    }

    private static String normalizeCustomDomain(String customDomain) {
        String domain = customDomain.trim()
                .replaceFirst("^[^/?#]*://", "")
                .replaceFirst("[/?#].*", "")
                .replaceFirst("^[^@]*@", "");

        int portStart = domain.lastIndexOf(':');
        String host = portStart < 0 ? domain : domain.substring(0, portStart);
        String port = portStart < 0 ? "" : domain.substring(portStart);

        String bareHost = host.regionMatches(true, 0, "www.", 0, 4) ? host.substring(4) : host;
        if (!bareHost.isEmpty() && bareHost.indexOf('.') < 0) host += ".com";

        domain = host + port;
        return VALID_DOMAIN.matcher(domain).matches() ? domain : "";
    }

    public static String generatePostLink(Object mediaObject, int position) throws Exception {
        return generatePostLink(new MediaData(mediaObject), position);
    }

    public static String generatePostLink(MediaData mediaData, int position) throws Exception {
        String postShortCode = mediaData.getShortcode();
        PostType postType = mediaData.getPostType();

        String shortTag = "p";
        if(postType.equals(PostType.REEL)){
            shortTag = "reel";
        } else if(postType.equals(PostType.STORY)){
            shortTag = "stories";
            postShortCode = mediaData.getUserData().getUsername();
        }

        String link = String.format(Constants.INSTAGRAM_SHARE_LINK, shortTag, postShortCode);

        if(postType.equals(PostType.STORY)){
            String postID = mediaData.getPostID();
            link+=postID;
        } else if(postType.equals(PostType.CAROUSEL)){
            link+="?img_index="+String.valueOf(position+1);
        }
        return link;
    }

}
