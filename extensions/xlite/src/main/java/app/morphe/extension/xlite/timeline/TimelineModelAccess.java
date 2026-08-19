package app.morphe.extension.xlite.timeline;

import java.util.List;
import java.util.Locale;

abstract class TimelineModelAccess {
    boolean isModuleItem(Object value) {
        return false;
    }

    boolean isPost(Object value) {
        return false;
    }

    boolean isModule(Object value) {
        return false;
    }

    boolean isRtbImageAd(Object value) {
        return false;
    }

    Object getModuleItem(Object wrapper) {
        return null;
    }

    boolean isModuleItemDispensable(Object wrapper) {
        return false;
    }

    Object copyModuleItem(Object wrapper, Object item, boolean dispensable) {
        return wrapper;
    }

    List<?> getModuleChildren(Object module) {
        return null;
    }

    Object getModuleDisplayType(Object module) {
        return null;
    }

    Object copyModule(Object module, List<?> children, Object displayType) {
        return module;
    }

    Object getPostId(Object post) {
        return null;
    }

    boolean isVerticalConversation(Object displayType) {
        return false;
    }

    List<?> getVerticalConversationPostIds(Object displayType) {
        return null;
    }

    Object copyVerticalConversation(Object displayType, List<?> postIds) {
        return displayType;
    }

    String getModuleEntryId(Object module) {
        return null;
    }

    Object getModuleClientEventInfo(Object module) {
        return null;
    }

    String getPostEntryId(Object post) {
        return null;
    }

    Object getPostClientEventInfo(Object post) {
        return null;
    }

    Object getPostPromotedMetadata(Object post) {
        return null;
    }

    boolean isPromotedClientEventInfo(Object eventInfo) {
        return eventInfo != null
                && eventInfo.toString().toLowerCase(Locale.ROOT).contains("promoted");
    }

    String getPostText(Object post) {
        return null;
    }

    List<?> getPostMentions(Object post) {
        return null;
    }

    int getMentionStartIdx(Object mention) {
        return 0;
    }

    int getMentionEndIdx(Object mention) {
        return 0;
    }

    String getMentionScreenName(Object mention) {
        return null;
    }

    String getPostAuthorScreenName(Object post) {
        return null;
    }

    String getPostTextForFilter(Object post) {
        String text = getPostText(post);
        if (text == null || text.isEmpty()) return text;

        List<?> mentions = getPostMentions(post);
        if (mentions == null || mentions.isEmpty()) return text;

        int cursor = 0;
        for (Object mention : mentions) {
            int start = getMentionStartIdx(mention);
            int end = getMentionEndIdx(mention);
            if (start < cursor || end <= start || end > text.length()) break;
            if (start > cursor && !isWhitespaceRegion(text, cursor, start)) break;
            cursor = end;
        }
        if (cursor <= 0) return text;

        int bodyStart = cursor;
        while (bodyStart < text.length() && Character.isWhitespace(text.charAt(bodyStart))) {
            bodyStart++;
        }
        if (bodyStart >= text.length()) return "";
        return text.substring(bodyStart);
    }

    private static boolean isWhitespaceRegion(String text, int from, int to) {
        for (int index = from; index < to; index++) {
            if (!Character.isWhitespace(text.charAt(index))) return false;
        }
        return true;
    }

    Object getContentDisclosure(Object post) {
        return null;
    }

    boolean hasAiGeneratedDisclosure(Object disclosure) {
        return false;
    }

    Object getAiDetectionSource(Object disclosure) {
        return null;
    }
}
