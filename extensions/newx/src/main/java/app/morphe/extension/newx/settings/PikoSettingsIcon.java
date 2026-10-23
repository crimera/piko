package app.morphe.extension.newx.settings;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Supplies the Piko settings icon to the rows NewX injects into the app's settings list and drawer.
 *
 * <p>Resources the patch adds are only assigned ids when the APK is rebuilt, so the drawable is
 * resolved here at runtime. The patch adds a cached {@code get()} accessor to this class that wraps
 * the id in the app's own icon type, which Java code cannot name.
 */
public final class PikoSettingsIcon {
    private static final String DRAWABLE_NAME = "piko_ic_vector_settings_stroke";

    private static int drawableId;

    private PikoSettingsIcon() {
    }

    /** Injection point: drawable resource id of the Piko settings icon. */
    public static int getDrawableId() {
        int id = drawableId;
        if (id == 0) {
            id = ResourceUtils.getIdentifierOrThrow(ResourceType.DRAWABLE, DRAWABLE_NAME);
            drawableId = id;
        }
        return id;
    }
}
