/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.dm;

import com.facebook.presence.model.upi.PresenceStatus;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class UserPresence {
    private static final String ACTIVE = "ACTIVE";
    private static final String IDLE = "IDLE";
    private static final String OFFLINE = "OFFLINE";

    /**
     * Called from the PresenceWriteRequest constructor, which every outgoing presence write
     * goes through.
     *
     * <p>The status is rewritten instead of the write being skipped because the first write of
     * a session travels as an argument of IgDgwPresenceClientImpl.establishStream, and that
     * call is what opens the stream the friends' presence arrives on.
     *
     * <p>OFFLINE and DISABLED are returned untouched: they come from the teardown path, and
     * those writes are worth keeping.
     *
     * <p>The constants are compared by enum name rather than by field because R8 renames the
     * fields (A03..A06 on 435.0.0.37.76) but keeps the names.
     */
    public static PresenceStatus overridePresenceStatus(PresenceStatus original) {
        try {
            if (original == null || !Pref.hideOnlineStatus()) {
                return original;
            }

            String name = original.name();
            if (ACTIVE.equals(name) || IDLE.equals(name)) {
                return PresenceStatus.valueOf(OFFLINE);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "overridePresenceStatus failed: ", ex);
        }
        return original;
    }

    /**
     * Decides whether "/disable_presence_reporting" goes into the MQTT subscription list, which
     * stops the server from inferring presence from the connection itself.
     *
     * @param original the app's own decision, the UPI rollout flag DA7.A03
     */
    public static boolean shouldDisablePresenceReporting(boolean original) {
        try {
            return original || Pref.hideOnlineStatus();
        } catch (Exception ex) {
            Logger.printException(() -> "shouldDisablePresenceReporting failed: ", ex);
            return original;
        }
    }

    /**
     * Decides whether the legacy Thrift foreground payload is published on "/t_fs".
     *
     * <p>False makes XplatNativeClientWrapper.setForeground receive a null payload, the path the
     * app already takes when the UPI rollout flag is on, so the native keepalive and the
     * isForeground bookkeeping still run.
     *
     * @param original the app's own decision, !DA7.A03()
     */
    public static boolean shouldSendLegacyPresence(boolean original) {
        try {
            return original && !Pref.hideOnlineStatus();
        } catch (Exception ex) {
            Logger.printException(() -> "shouldSendLegacyPresence failed: ", ex);
            return original;
        }
    }
}
