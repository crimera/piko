package app.morphe.extension.xlite.misc;

/**
 * Existing PostActionType values used as carriers for custom X-Lite post-menu options.
 *
 * These are enum names from the target app, not user-facing labels. They must match the
 * action names declared by the corresponding post-option patch contribution. Update
 * compatibility when a target version removes or renames one of these carriers.
 */
public final class XLitePostOptionActions {
    public static final String BROWSE_OBJECT_ACTION = "None";
    public static final String SHARE_IMAGE_ACTION = "ViewDebugDialog";

    private XLitePostOptionActions() {
    }
}
