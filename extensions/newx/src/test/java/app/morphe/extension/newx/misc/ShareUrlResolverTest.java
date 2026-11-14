package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.BooleanSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

public final class ShareUrlResolverTest {
    private static final String PERMANENT_LINKS_SETTING = "newx.links.permanent_share_links";
    private static final String REMOVE_SHARE_TRACKING_QUERY_SETTING =
            "newx.links.remove_share_tracking_query";

    @Before
    public void clearPermanentLinksSetting() throws ReflectiveOperationException {
        settings().remove(PERMANENT_LINKS_SETTING);
        settings().remove(REMOVE_SHARE_TRACKING_QUERY_SETTING);
    }

    @After
    public void removePermanentLinksSetting() throws ReflectiveOperationException {
        settings().remove(PERMANENT_LINKS_SETTING);
        settings().remove(REMOVE_SHARE_TRACKING_QUERY_SETTING);
    }

    @Test
    public void removesShareAttributionByDefaultAndKeepsOtherQueryParameters()
            throws ReflectiveOperationException {
        settings().put(
                REMOVE_SHARE_TRACKING_QUERY_SETTING,
                new BooleanSetting(REMOVE_SHARE_TRACKING_QUERY_SETTING, true, false)
        );
        String url = "https://x.com/jack/status/1234567890?a=tracking&s=19&keep=value#reply";

        assertEquals(
                "https://x.com/jack/status/1234567890?keep=value#reply",
                ShareUrlResolver.rewriteSharedUrl(url)
        );
        assertEquals(
                "https://x.com/adityaa_behera/status/2115453457998213441",
                ShareUrlResolver.rewriteSharedUrl(
                        "https://x.com/adityaa_behera/status/2115453457998213441?s=19"
                )
        );
        assertEquals(
                "https://example.com/post?a=keep&s=19",
                ShareUrlResolver.rewriteSharedUrl("https://example.com/post?a=keep&s=19")
        );
    }

    @Test
    public void settingCanKeepShareAttribution() throws ReflectiveOperationException {
        BooleanSetting setting =
                new BooleanSetting(REMOVE_SHARE_TRACKING_QUERY_SETTING, true, false);
        settings().put(REMOVE_SHARE_TRACKING_QUERY_SETTING, setting);
        setting.save(false);
        String url = "https://x.com/jack/status/1234567890?a=tracking&s=19";

        assertEquals(url, ShareUrlResolver.rewriteSharedUrl(url));
    }

    @Test
    public void permanentLinksAreOffByDefault() {
        String url = "https://x.com/jack/status/1234567890?s=20";

        assertEquals(url, ShareUrlResolver.rewriteSharedUrl(url));
    }

    @Test
    public void enabledPostLinksUseThePermanentStatusPath() throws ReflectiveOperationException {
        enablePermanentLinks();

        assertEquals(
                "https://x.com/i/status/1234567890/photo/1?s=20",
                ShareUrlResolver.rewriteSharedUrl(
                        "https://x.com/jack/status/1234567890/photo/1?s=20"
                )
        );
    }

    @Test
    public void enabledProfileLinksUseTheProfileModelAccountId() throws ReflectiveOperationException {
        enablePermanentLinks();
        Object profile = new Object() {
            @Override
            public String toString() {
                return "ProfileUser(id=1234567890, screenName=jack)";
            }
        };

        assertEquals(
                "https://x.com/i/user/1234567890",
                ShareUrlResolver.rewriteProfileUrl("https://x.com/jack", profile)
        );
    }

    @Test
    public void profileIdRoutesCanBeConvertedWithoutAProfileModel() throws ReflectiveOperationException {
        enablePermanentLinks();

        assertEquals(
                "https://x.com/i/user/1234567890",
                ShareUrlResolver.rewriteSharedUrl("https://x.com/i/user/1234567890")
        );
    }

    @Test
    public void unresolvedProfileIdsLeaveTheOriginalUrlAlone() throws ReflectiveOperationException {
        enablePermanentLinks();

        assertEquals(
                "https://x.com/jack",
                ShareUrlResolver.rewriteProfileUrl("https://x.com/jack", new Object())
        );
    }

    private static void enablePermanentLinks() throws ReflectiveOperationException {
        BooleanSetting setting = new BooleanSetting(PERMANENT_LINKS_SETTING, false, false);
        settings().put(PERMANENT_LINKS_SETTING, setting);
        setting.save(true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settings() throws ReflectiveOperationException {
        Field field = SettingsRegistry.class.getDeclaredField("SETTINGS");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }
}
