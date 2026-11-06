package app.morphe.extension.instagram.patches.notification.state;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the renderer should show for one chat. The renderer builds the MessagingStyle from this
 * and is the only place that posts; the plan itself holds no Android types.
 */
public final class RenderPlan {
    /** Messages to show, oldest first. Own messages have a null {@code senderId}. */
    public final List<Message> messages;
    /** Neutral "N more" line: digest count minus messages held, or 0. */
    public final int moreLine;
    /** Name Instagram gave the digest sender (for the "more" line), or null. */
    public final String moreSenderName;
    /** True only when a message from someone else is newer than what was already shown. */
    public final boolean alert;
    /** True when the chat's record should be removed instead of rendered. */
    public final boolean clear;

    public RenderPlan(List<Message> messages, int moreLine, String moreSenderName,
                      boolean alert, boolean clear) {
        this.messages = Collections.unmodifiableList(messages);
        this.moreLine = moreLine;
        this.moreSenderName = moreSenderName;
        this.alert = alert;
        this.clear = clear;
    }

    public static RenderPlan clearPlan() {
        return new RenderPlan(Collections.<Message>emptyList(), 0, null, false, true);
    }

    public RenderPlan withAlert(boolean alert) {
        return new RenderPlan(messages, moreLine, moreSenderName, alert, clear);
    }

    /** Same visible content, ignoring the alert flag. */
    public boolean sameContent(RenderPlan o) {
        return o != null && clear == o.clear && moreLine == o.moreLine && messages.equals(o.messages);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenderPlan)) return false;
        RenderPlan p = (RenderPlan) o;
        return sameContent(p) && alert == p.alert && Objects.equals(moreSenderName, p.moreSenderName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, moreLine, alert, clear);
    }

    @Override
    public String toString() {
        return (clear ? "CLEAR" : "PLAN") + "{alert=" + alert + ", more=" + moreLine + ", " + messages + "}";
    }
}
