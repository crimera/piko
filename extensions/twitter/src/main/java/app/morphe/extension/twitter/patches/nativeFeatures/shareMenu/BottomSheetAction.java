/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.shareMenu;

/**
 * Describes a single tappable row in the bottom sheet.
 *
 * <p>Example:
 * <pre>
 *   new BottomSheetAction&lt;&gt;(R.drawable.ic_link, "Copy link", post -&gt; copyLink(post))
 * </pre>
 *
 * @param <T> Type of the data object that the action callback receives.
 */
public class BottomSheetAction<T> {

    /** Drawable resource ID shown as the leading 24 dp icon. */
    public final String iconRes;

    /** Label displayed next to the icon. */
    public final String label;

    /** Invoked with the bound data object when this row is tapped. */
    public final ActionCallback<T> callback;

    /**
     * @param iconRes  Drawable resource name for the row icon.
     * @param label    Text label for the row.
     * @param callback Called with the data object when the row is tapped.
     */
    public BottomSheetAction(String iconRes, String label, ActionCallback<T> callback) {
        this.iconRes  = iconRes;
        this.label    = label;
        this.callback = callback;
    }

    /**
     * Functional interface for action row callbacks.
     * Defined here (rather than using {@code java.util.function.Consumer})
     * so the code stays compatible below API 24.
     *
     * @param <T> Type of the data object delivered on tap.
     */
    public interface ActionCallback<T> {
        void onAction(T item);
    }
}