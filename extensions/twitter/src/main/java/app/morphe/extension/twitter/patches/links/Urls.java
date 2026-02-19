package app.morphe.extension.twitter.patches.links;

import com.twitter.model.json.core.JsonUrlEntity;

import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.Utils;
import java.net.URL;

public class Urls {
    private static final boolean unShortUrl;
    static {
        unShortUrl = SettingsStatus.unshortenlink && Pref.unShortUrl();
    }
    public static JsonUrlEntity unshort(JsonUrlEntity entity) {
        try {
            if(unShortUrl){
                entity.e = entity.c;
            }
        } catch (Exception ex) {
            Utils.logger(ex);
        }
        return entity;
    }

    public static String changeDomain(String urlString) {
        try {
            String customDomainName = Pref.customSharingDomain();
            // Check for domain extension
            if(!(customDomainName.matches("^[A-Za-z0-9-]{1,63}\\.[A-Za-z]{2,6}$"))) customDomainName+=".com"; //have .com as default extension just for safety reasons
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")) {
                return new URL(url.getProtocol(), customDomainName, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception ex) {
            Utils.logger(ex);
        }
        return urlString;
    }
}
