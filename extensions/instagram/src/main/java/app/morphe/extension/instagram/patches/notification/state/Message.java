package app.morphe.extension.instagram.patches.notification.state;

import java.util.Objects;

/**
 * One chat message. Immutable.
 *
 * <p>{@code serverTime} is the real server timestamp in ms, or 0 when unknown (a digest's own
 * message). Per R1 only a real server time may be compared against {@code readUpTo}; {@code
 * arrivalTime} (phone clock) is used only to order messages that have no server time.
 */
public final class Message {
    /** Instagram's item id, or null (digest text, some REST items). */
    public final String itemId;
    /** Null for own messages inside a {@link RenderPlan} (they render without a sender). */
    public final String senderId;
    public final boolean senderIsViewer;
    public final String text;
    public final long serverTime;
    public final long arrivalTime;
    public final boolean edited;
    public final Kind kind;

    /** Item type, coarsely. Only MEDIA matters to the rules: a digest's "Sent a photo" may match it. */
    public enum Kind { TEXT, MEDIA, OTHER }

    /**
     * The one place that maps an Instagram item type (plus its text, when known) to a {@link Kind}.
     * Attachment-like types are MEDIA so a digest's "Sent a photo"/"Sent an attachment" can match
     * them (one-to-one). "link" carries text and stays TEXT; action_log, video_call_event and
     * unknown types are OTHER unless their text is generic "Sent a ..." wording.
     */
    public static Kind kindOfType(String type, String text) {
        if (type == null) type = "";
        if (type.equals("text") || type.equals("link")) return Kind.TEXT;
        if (type.equals("media") || type.equals("raven_media") || type.equals("voice_media")
                || type.equals("animated_media") || type.equals("store_sticker") || type.startsWith("xma_") || type.equals("generic_xma")
                || type.equals("clip") || type.equals("reel_share") || type.equals("story_share")
                || type.equals("media_share")) return Kind.MEDIA;
        if (text != null && text.matches("^Sent an? [A-Za-z ]{1,30}$")) return Kind.MEDIA;
        return Kind.OTHER;
    }

    /** What an item without text and with no better label shows. */
    public static final String ATTACHMENT = "Sent an attachment";

    private static final java.util.Set<String> LABELS = new java.util.HashSet<>(java.util.Arrays.asList(
            ATTACHMENT, "Sent a photo", "Sent a video", "Sent a photo or video", "Sent a disappearing photo",
            "Sent a disappearing video", "Sent a disappearing message", "Sent a voice message", "Sent a sticker",
            "Sent a reel", "Sent a post", "Sent a story", "Replied to a story", "Sent a song", "Sent a link",
            "Sent a location", "Sent a profile", "Sent a live video", "Video chat", "❤️"));

    /**
     * The patch's own wording for an item that has no text, by item type. Used only when Instagram's
     * notification did not word the message itself (history, fetched pages); Instagram's wording
     * always wins over it, see {@link #isLabel}. {@code video} is null when unknown.
     */
    public static String labelOfType(String type, Boolean video) {
        if (type == null) return ATTACHMENT;
        switch (type) {
            case "media":
                return video == null ? "Sent a photo or video" : video ? "Sent a video" : "Sent a photo";
            case "raven_media":
            case "visual_media":
                return video == null ? "Sent a disappearing message"
                        : video ? "Sent a disappearing video" : "Sent a disappearing photo";
            case "voice_media": return "Sent a voice message";
            case "animated_media": case "store_sticker": return "Sent a sticker";
            case "clip": case "xma_clip": return "Sent a reel";
            case "media_share": case "xma_media_share": return "Sent a post";
            case "story_share": case "xma_story_share": return "Sent a story";
            case "reel_share": case "xma_reel_share": return "Replied to a story";
            case "music": return "Sent a song";
            case "link": case "xma_link": return "Sent a link";
            case "location": return "Sent a location";
            case "profile": return "Sent a profile";
            case "live_viewer_invite": case "live_video_share": return "Sent a live video";
            case "video_call_event": return "Video chat";
            case "like": return "❤️";
            default: return ATTACHMENT;
        }
    }

    /** True for wording the patch made up ({@link #labelOfType}), which loses to Instagram's own. */
    public static boolean isLabel(String text) {
        return text != null && LABELS.contains(text);
    }

    /**
     * Instagram's history writes your own messages as "<username>: text". Strips a leading token
     * without whitespace and a colon, when something remains, so both copies of a message match.
     */
    public static String stripOwnPrefix(String text) {
        if (text == null) return "";
        int colon = text.indexOf(": ");
        if (colon <= 0 || colon + 2 >= text.length()) return text;
        for (int i = 0; i < colon; i++) if (Character.isWhitespace(text.charAt(i))) return text;
        return text.substring(colon + 2);
    }

    public Message(String itemId, String senderId, boolean senderIsViewer, String text,
                   long serverTime, long arrivalTime, boolean edited) {
        this(itemId, senderId, senderIsViewer, text, serverTime, arrivalTime, edited, Kind.TEXT);
    }

    public Message(String itemId, String senderId, boolean senderIsViewer, String text,
                   long serverTime, long arrivalTime, boolean edited, Kind kind) {
        this.itemId = itemId;
        this.senderId = senderId;
        this.senderIsViewer = senderIsViewer;
        this.text = text == null ? "" : text;
        this.serverTime = Math.max(0, serverTime);
        this.arrivalTime = arrivalTime;
        this.edited = edited;
        this.kind = kind == null ? Kind.TEXT : kind;
    }

    public Message(String itemId, String senderId, boolean senderIsViewer, String text,
                   long serverTime, long arrivalTime) {
        this(itemId, senderId, senderIsViewer, text, serverTime, arrivalTime, false);
    }

    public boolean hasServerTime() {
        return serverTime > 0;
    }

    /** Time used for display order only: the server time if known, otherwise arrival. */
    public long displayTime() {
        return serverTime > 0 ? serverTime : arrivalTime;
    }

    public Message withText(String newText, boolean markEdited) {
        return new Message(itemId, senderId, senderIsViewer, newText, serverTime, arrivalTime,
                markEdited || edited, kind);
    }

    /** Copy for rendering: own messages carry no sender. */
    Message forRender() {
        return senderIsViewer && senderId != null
                ? new Message(itemId, null, true, text, serverTime, arrivalTime, edited, kind) : this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message m = (Message) o;
        return senderIsViewer == m.senderIsViewer && serverTime == m.serverTime
                && arrivalTime == m.arrivalTime && edited == m.edited && kind == m.kind
                && Objects.equals(itemId, m.itemId) && Objects.equals(senderId, m.senderId)
                && text.equals(m.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, senderId, senderIsViewer, text, serverTime, arrivalTime, edited, kind);
    }

    @Override
    public String toString() {
        return (senderIsViewer ? "me" : senderId) + "@" + serverTime + "/" + arrivalTime
                + (itemId != null ? "#" + itemId : "") + ": " + text;
    }
}
