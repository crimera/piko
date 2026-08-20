package app.morphe.extension.newx.settings;

/** Keeps the root settings query while navigating into and back out of a group. */
final class SettingsSearchSession {
    private static String query = "";

    private SettingsSearchSession() {
    }

    static synchronized String query() {
        return query;
    }

    static synchronized void update(String value) {
        query = value == null ? "" : value;
    }

    static synchronized void reset() {
        query = "";
    }
}
