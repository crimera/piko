package app.morphe.extension.newx.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.newx.postfilter.PostFilterMatcher;
import app.morphe.extension.newx.postfilter.PostFilterRuleStore;

public final class NewXTimelineFilter {

    private static final String DEBUG_LOG_PREFIX = "[DEBUG-newx-timeline] ";
    private static final String AI_SOURCE_USER_MARKED = "UserMarked";
    private static final String AI_SOURCE_AUTO_DETECTED = "AutoDetected";
    private static final String AI_SOURCE_NOT_IDENTIFIED = "SourceNotIdentified";
    private static final String DISCOVER_MORE_ENTRY_ID = "tweetdetailrelatedtweets";
    private static final TimelineModelAccess PRODUCTION_MODEL_ACCESS = new TimelineModelAccess() {
        @Override boolean isModuleItem(Object value) { return isTimelineModuleItem(value); }
        @Override boolean isPost(Object value) { return isTimelinePost(value); }
        @Override boolean isModule(Object value) { return isTimelineModule(value); }
        @Override boolean isRtbImageAd(Object value) { return isTimelineRtbImageAd(value); }
        @Override Object getModuleItem(Object wrapper) { return NewXTimelineFilter.getModuleItem(wrapper); }
        @Override boolean isModuleItemDispensable(Object wrapper) {
            return NewXTimelineFilter.isModuleItemDispensable(wrapper);
        }
        @Override Object copyModuleItem(Object wrapper, Object item, boolean dispensable) {
            return NewXTimelineFilter.copyModuleItem(wrapper, item, dispensable);
        }
        @Override List<?> getModuleChildren(Object module) { return getModuleInnerContent(module); }
        @Override Object getModuleDisplayType(Object module) { return NewXTimelineFilter.getModuleDisplayType(module); }
        @Override Object copyModule(Object module, List<?> children, Object displayType) {
            return NewXTimelineFilter.copyModule(
                    module,
                    children,
                    getModuleHeader(module),
                    getModuleFooter(module),
                    displayType,
                    getModuleSortIndex(module),
                    getModuleEntryId(module),
                    getModuleClientEventInfo(module)
            );
        }
        @Override Object getPostId(Object post) { return NewXTimelineFilter.getPostId(post); }
        @Override boolean isVerticalConversation(Object displayType) {
            return NewXTimelineFilter.isVerticalConversation(displayType);
        }
        @Override List<?> getVerticalConversationPostIds(Object displayType) {
            return NewXTimelineFilter.getVerticalConversationPostIds(displayType);
        }
        @Override Object copyVerticalConversation(Object displayType, List<?> postIds) {
            return NewXTimelineFilter.copyVerticalConversation(displayType, postIds);
        }
        @Override String getModuleEntryId(Object module) { return NewXTimelineFilter.getModuleEntryId(module); }
        @Override Object getModuleClientEventInfo(Object module) {
            return NewXTimelineFilter.getModuleClientEventInfo(module);
        }
        @Override String getPostEntryId(Object post) { return NewXTimelineFilter.getPostEntryId(post); }
        @Override Object getPostClientEventInfo(Object post) {
            return NewXTimelineFilter.getPostClientEventInfo(post);
        }
        @Override Object getPostPromotedMetadata(Object post) {
            return NewXTimelineFilter.getPostPromotedMetadata(post);
        }
        @Override boolean isPromotedClientEventInfo(Object eventInfo) {
            return NewXTimelineFilter.isPromotedClientEventInfo(eventInfo);
        }
        @Override String getPostText(Object post) { return NewXTimelineFilter.getPostText(post); }
        @Override List<?> getPostMentions(Object post) { return NewXTimelineFilter.getPostMentions(post); }
        @Override int getMentionStartIdx(Object mention) {
            return NewXTimelineFilter.getMentionStartIdx(mention);
        }
        @Override int getMentionEndIdx(Object mention) { return NewXTimelineFilter.getMentionEndIdx(mention); }
        @Override String getMentionScreenName(Object mention) {
            return NewXTimelineFilter.getMentionScreenName(mention);
        }
        @Override String getPostAuthorScreenName(Object post) {
            return NewXTimelineFilter.getPostAuthorScreenName(post);
        }
        @Override Object getContentDisclosure(Object post) { return NewXTimelineFilter.getContentDisclosure(post); }
        @Override boolean hasAiGeneratedDisclosure(Object disclosure) {
            return NewXTimelineFilter.hasAiGeneratedDisclosure(disclosure);
        }
        @Override Object getAiDetectionSource(Object disclosure) {
            return NewXTimelineFilter.getAiDetectionSource(disclosure);
        }
    };

    private NewXTimelineFilter() {
    }

    public static Object filterPromotedItems(Object timelineItems, boolean enabled) {
        return filterPromotedItems(timelineItems, enabled, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterPromotedItems(Object timelineItems, boolean enabled, TimelineModelAccess modelAccess) {
        return filterTimelineItems(timelineItems, enabled, false, null, modelAccess);
    }

    public static Object filterDiscoverMore(Object timelineItems, boolean enabled) {
        return filterDiscoverMore(timelineItems, enabled, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterDiscoverMore(Object timelineItems, boolean enabled, TimelineModelAccess modelAccess) {
        if (!enabled) return timelineItems;
        return filterTimelineItems(timelineItems, false, false, null, Collections.emptySet(), true, modelAccess);
    }

    public static Object filterWhoToFollow(Object timelineItems, boolean enabled) {
        return filterWhoToFollow(timelineItems, enabled, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterWhoToFollow(Object timelineItems, boolean enabled, TimelineModelAccess modelAccess) {
        if (!enabled) return timelineItems;
        return filterTimelineItems(timelineItems, false, true, null, modelAccess);
    }

    public static Object filterPostsByKeyword(Object timelineItems) {
        try {
            PostFilterRuleStore store = PostFilterRuleStore.shared();
            if (!store.isEnabled()) return timelineItems;
            return filterTimelineItems(
                    timelineItems,
                    false,
                    false,
                    store.snapshot(),
                    Collections.emptySet(),
                    false,
                    PRODUCTION_MODEL_ACCESS
            );
        } catch (RuntimeException exception) {
            logFailure("post-filter rule loading", exception);
            return timelineItems;
        }
    }

    public static Object filterPostsByKeyword(
            Object timelineItems,
            boolean enabled,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        return filterPostsByKeyword(timelineItems, enabled, snapshot, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterPostsByKeyword(
            Object timelineItems,
            boolean enabled,
            PostFilterRuleStore.Snapshot snapshot,
            TimelineModelAccess modelAccess
    ) {
        if (!enabled) return timelineItems;
        return filterTimelineItems(timelineItems, false, false, snapshot, Collections.emptySet(), false, modelAccess);
    }

    public static Object filterAiGeneratedPosts(Object timelineItems, Set<String> sourcesToHide) {
        return filterAiGeneratedPosts(timelineItems, sourcesToHide, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterAiGeneratedPosts(
            Object timelineItems,
            Set<String> sourcesToHide,
            TimelineModelAccess modelAccess
    ) {
        Set<String> sources = parseAiSources(sourcesToHide);
        if (sources.isEmpty()) return timelineItems;
        return filterTimelineItems(timelineItems, false, false, null, sources, false, modelAccess);
    }

    private static Set<String> parseAiSources(Set<String> sourcesToHide) {
        if (sourcesToHide == null || sourcesToHide.isEmpty()) return Collections.emptySet();
        Set<String> sources = new HashSet<>();
        for (String source : sourcesToHide) {
            if (source != null && isSupportedAiSource(source)) sources.add(source);
        }
        return sources;
    }

    private static boolean isSupportedAiSource(String source) {
        return AI_SOURCE_USER_MARKED.equals(source)
                || AI_SOURCE_AUTO_DETECTED.equals(source)
                || AI_SOURCE_NOT_IDENTIFIED.equals(source);
    }

    private static Object filterTimelineItems(
            Object timelineItems,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            TimelineModelAccess modelAccess
    ) {
        return filterTimelineItems(
                timelineItems,
                filterPromotedItems,
                hideWhoToFollow,
                ruleSnapshot,
                Collections.emptySet(),
                false,
                modelAccess
        );
    }

    private static Object filterTimelineItems(
            Object timelineItems,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            boolean hideDiscoverMore,
            TimelineModelAccess modelAccess
    ) {
        if (timelineItems == null) return null;
        if (!filterPromotedItems
                && !hideWhoToFollow
                && (ruleSnapshot == null || !ruleSnapshot.hasEnabledRules())
                && (aiSourcesToHide == null || aiSourcesToHide.isEmpty())
                && !hideDiscoverMore) {
            return timelineItems;
        }
        if (!(timelineItems instanceof Iterable<?> iterable)) return timelineItems;

        try {
            List<Object> filtered = null;
            List<Object> unchangedPrefix = null;
            List<?> sourceList = timelineItems instanceof List<?> list ? list : null;
            int index = 0;
            for (Object original : iterable) {
                FilterResult result = filterObject(
                        original,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide,
                        hideDiscoverMore,
                        modelAccess
                );
                boolean changed = result.remove || result.item != original;
                if (!changed) {
                    if (filtered != null) filtered.add(original);
                    else if (sourceList == null) {
                        if (unchangedPrefix == null) unchangedPrefix = new ArrayList<>();
                        unchangedPrefix.add(original);
                    }
                    index++;
                    continue;
                }

                if (filtered == null) {
                    filtered = new ArrayList<>(sourceList != null ? sourceList.size() : index + 1);
                    if (sourceList != null) {
                        filtered.addAll(sourceList.subList(0, index));
                    } else if (unchangedPrefix != null) {
                        filtered.addAll(unchangedPrefix);
                        unchangedPrefix = null;
                    }
                }
                if (!result.remove) filtered.add(result.item);
                index++;
            }
            if (filtered == null) return timelineItems;
            return immutableList(filtered);
        } catch (RuntimeException exception) {
            logFailure("top-level timeline filtering", exception);
            return timelineItems;
        }
    }

    private static FilterResult filterObject(
            Object original,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            boolean hideDiscoverMore,
            TimelineModelAccess modelAccess
    ) {
        if (original == null) return FilterResult.keep(null);
        try {
            if (modelAccess.isModuleItem(original)) {
                return filterModuleItem(
                        original,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide,
                        hideDiscoverMore,
                        modelAccess
                );
            }
            return filterItem(
                    original,
                    filterPromotedItems,
                    hideWhoToFollow,
                    ruleSnapshot,
                    aiSourcesToHide,
                    hideDiscoverMore,
                    modelAccess
            );
        } catch (RuntimeException exception) {
            logFailure("timeline item " + original.getClass().getSimpleName(), exception);
            return FilterResult.keep(original);
        }
    }

    private static FilterResult filterModuleItem(
            Object wrapper,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            boolean hideDiscoverMore,
            TimelineModelAccess modelAccess
    ) {
        Object originalItem = modelAccess.getModuleItem(wrapper);
        FilterResult result = filterItem(
                originalItem,
                filterPromotedItems,
                hideWhoToFollow,
                ruleSnapshot,
                aiSourcesToHide,
                hideDiscoverMore,
                modelAccess
        );
        if (result.remove) return result;
        if (result.item == originalItem) return FilterResult.keep(wrapper);
        return FilterResult.replace(modelAccess.copyModuleItem(
                wrapper,
                result.item,
                modelAccess.isModuleItemDispensable(wrapper)
        ));
    }

    private static FilterResult filterItem(
            Object item,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            boolean hideDiscoverMore,
            TimelineModelAccess modelAccess
    ) {
        if (item == null) return FilterResult.keep(null);
        if (filterPromotedItems) {
            try {
                if (isPromoted(item, modelAccess)) return FilterResult.remove();
            } catch (RuntimeException exception) {
                logDiagnostic("promoted-item check", exception, "item=" + describeValue(item));
                throw exception;
            }
        }
        if (modelAccess.isPost(item)) {
            try {
                String textForFilter = modelAccess.getPostTextForFilter(item);
                String authorScreenName = modelAccess.getPostAuthorScreenName(item);
                if (PostFilterMatcher.findMatchReason(textForFilter, authorScreenName, ruleSnapshot) != null) {
                    return FilterResult.remove();
                }
            } catch (RuntimeException exception) {
                logDiagnostic("post keyword check", exception, "post=" + describeValue(item));
                throw exception;
            }
            if (isAiGenerated(item, aiSourcesToHide, modelAccess)) return FilterResult.remove();
        }
        if (modelAccess.isModule(item)) {
            return filterModule(
                    item,
                    filterPromotedItems,
                    hideWhoToFollow,
                    ruleSnapshot,
                    aiSourcesToHide,
                    hideDiscoverMore,
                    modelAccess
            );
        }
        return FilterResult.keep(item);
    }

    private static boolean isAiGenerated(
            Object post,
            Set<String> aiSourcesToHide,
            TimelineModelAccess modelAccess
    ) {
        if (aiSourcesToHide == null || aiSourcesToHide.isEmpty()) return false;
        Object disclosure;
        try {
            disclosure = modelAccess.getContentDisclosure(post);
        } catch (RuntimeException exception) {
            logDiagnostic("AI disclosure read", exception, "post=" + describeValue(post));
            throw exception;
        }
        if (disclosure == null) return false;

        try {
            if (!modelAccess.hasAiGeneratedDisclosure(disclosure)) return false;
            Object source = modelAccess.getAiDetectionSource(disclosure);
            if (source == null) return aiSourcesToHide.contains(AI_SOURCE_NOT_IDENTIFIED);
            if (!(source instanceof Enum<?> enumSource)) return false;
            return aiSourcesToHide.contains(enumSource.name());
        } catch (RuntimeException exception) {
            logDiagnostic(
                    "AI disclosure classification",
                    exception,
                    "post=" + describeValue(post),
                    "disclosure=" + describeValue(disclosure)
            );
            throw exception;
        }
    }

    private static Object getContentDisclosure(Object post) {
        return null;
    }

    private static boolean hasAiGeneratedDisclosure(Object disclosure) {
        return false;
    }

    private static Object getAiDetectionSource(Object disclosure) {
        return null;
    }

    private static FilterResult filterModule(
            Object module,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            boolean hideDiscoverMore,
            TimelineModelAccess modelAccess
    ) {
        String entryId = modelAccess.getModuleEntryId(module);
        if (hideWhoToFollow && isWhoToFollowEntryId(entryId)) {
            return FilterResult.remove();
        }
        if (hideDiscoverMore && isDiscoverMoreEntryId(entryId)) {
            return FilterResult.remove();
        }

        List<?> originalChildren = modelAccess.getModuleChildren(module);
        if (originalChildren == null || originalChildren.isEmpty()) {
            return FilterResult.keep(module);
        }

        List<Object> filteredChildren = null;
        Set<Object> removedPostIds = null;
        boolean changed = false;
        for (int childIndex = 0; childIndex < originalChildren.size(); childIndex++) {
            Object originalChild = originalChildren.get(childIndex);
            if (originalChild == null) {
                if (filteredChildren != null) filteredChildren.add(null);
                continue;
            }

            Object originalItem = null;
            FilterResult result;
            try {
                originalItem = modelAccess.getModuleItem(originalChild);
                result = filterItem(
                        originalItem,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide,
                        hideDiscoverMore,
                        modelAccess
                );
            } catch (RuntimeException exception) {
                logFailure(
                        "timeline module child",
                        exception,
                        childContext(
                                module,
                                childIndex,
                                originalChild,
                                originalItem,
                                filterPromotedItems,
                                hideWhoToFollow,
                                ruleSnapshot,
                                aiSourcesToHide
                        )
                );
                if (filteredChildren != null) filteredChildren.add(originalChild);
                continue;
            }

            if (result.remove) {
                if (filteredChildren == null) {
                    filteredChildren = copyChildrenPrefix(originalChildren, childIndex);
                }
                changed = true;
                if (modelAccess.isPost(originalItem)) {
                    Object postId = modelAccess.getPostId(originalItem);
                    if (postId != null) {
                        if (removedPostIds == null) removedPostIds = new HashSet<>();
                        removedPostIds.add(postId);
                    }
                }
                continue;
            }
            if (result.item == originalItem) {
                if (filteredChildren != null) filteredChildren.add(originalChild);
                continue;
            }

            try {
                Object replacement = modelAccess.copyModuleItem(
                        originalChild,
                        result.item,
                        modelAccess.isModuleItemDispensable(originalChild)
                );
                if (filteredChildren == null) {
                    filteredChildren = copyChildrenPrefix(originalChildren, childIndex);
                }
                filteredChildren.add(replacement);
                changed = true;
            } catch (RuntimeException exception) {
                logFailure(
                        "timeline module child reconstruction",
                        exception,
                        childContext(
                                module,
                                childIndex,
                                originalChild,
                                result.item,
                                filterPromotedItems,
                                hideWhoToFollow,
                                ruleSnapshot,
                                aiSourcesToHide
                        )
                );
                if (filteredChildren != null) filteredChildren.add(originalChild);
            }
        }

        if (!changed) return FilterResult.keep(module);
        if (filteredChildren == null || filteredChildren.isEmpty()) return FilterResult.remove();

        try {
            Object displayType = repairDisplayType(
                    modelAccess.getModuleDisplayType(module),
                    removedPostIds == null ? Collections.emptySet() : removedPostIds,
                    modelAccess
            );
            return FilterResult.replace(modelAccess.copyModule(module, filteredChildren, displayType));
        } catch (RuntimeException exception) {
            logFailure("timeline module reconstruction", exception);
            return FilterResult.keep(module);
        }
    }

    private static List<Object> copyChildrenPrefix(List<?> children, int endExclusive) {
        List<Object> prefix = new ArrayList<>(children.size());
        for (int index = 0; index < endExclusive; index++) {
            prefix.add(children.get(index));
        }
        return prefix;
    }

    private static Object repairDisplayType(
            Object displayType,
            Set<Object> removedPostIds,
            TimelineModelAccess modelAccess
    ) {
        if (removedPostIds.isEmpty() || !modelAccess.isVerticalConversation(displayType)) return displayType;

        List<?> originalIds = modelAccess.getVerticalConversationPostIds(displayType);
        if (originalIds == null || originalIds.isEmpty()) return displayType;

        List<Object> filteredIds = new ArrayList<>(originalIds.size());
        for (Object id : originalIds) {
            if (!removedPostIds.contains(id)) filteredIds.add(id);
        }
        if (filteredIds.size() == originalIds.size()) return displayType;
        return modelAccess.copyVerticalConversation(displayType, filteredIds);
    }

    private static boolean isWhoToFollowEntryId(String entryId) {
        return entryId != null && entryId.startsWith("who-to-follow");
    }

    private static boolean isDiscoverMoreEntryId(String entryId) {
        return entryId != null
                && (DISCOVER_MORE_ENTRY_ID.equals(entryId)
                || entryId.startsWith(DISCOVER_MORE_ENTRY_ID + "-"));
    }

    private static boolean isPromoted(Object item, TimelineModelAccess modelAccess) {
        if (modelAccess.isRtbImageAd(item)) return true;
        String entryId = null;
        Object eventInfo = null;
        if (modelAccess.isPost(item)) {
            entryId = modelAccess.getPostEntryId(item);
            eventInfo = modelAccess.getPostClientEventInfo(item);
            if (modelAccess.getPostPromotedMetadata(item) != null) return true;
        } else if (modelAccess.isModule(item)) {
            entryId = modelAccess.getModuleEntryId(item);
            eventInfo = modelAccess.getModuleClientEventInfo(item);
        }
        if (isPromotedEntryId(entryId)) return true;

        return eventInfo != null && modelAccess.isPromotedClientEventInfo(eventInfo);
    }

    private static boolean isPromotedEntryId(String entryId) {
        if (entryId == null) return false;
        if (entryId.contains("promoted")) return true;
        return entryId.startsWith("ad-") || entryId.contains("-ad-");
    }

    private static boolean isTimelineModuleItem(Object value) {
        return false;
    }

    private static boolean isTimelinePost(Object value) {
        return false;
    }

    private static boolean isTimelineModule(Object value) {
        return false;
    }

    private static boolean isTimelineRtbImageAd(Object value) {
        return false;
    }

    private static Object getModuleItem(Object wrapper) {
        return null;
    }

    private static boolean isModuleItemDispensable(Object wrapper) {
        return false;
    }

    private static Object copyModuleItem(Object ignoredWrapper, Object item, boolean dispensable) {
        return ignoredWrapper;
    }

    private static List<?> getModuleInnerContent(Object module) {
        return null;
    }

    private static Object getModuleHeader(Object module) {
        return null;
    }

    private static Object getModuleFooter(Object module) {
        return null;
    }

    private static Object getModuleDisplayType(Object module) {
        return null;
    }

    private static long getModuleSortIndex(Object module) {
        return 0L;
    }

    private static String getModuleEntryId(Object module) {
        return null;
    }

    private static Object getModuleClientEventInfo(Object module) {
        return null;
    }

    private static String getPostEntryId(Object post) {
        return null;
    }

    private static Object getPostClientEventInfo(Object post) {
        return null;
    }

    private static Object getPostPromotedMetadata(Object post) {
        return null;
    }

    private static boolean isPromotedClientEventInfo(Object eventInfo) {
        return false;
    }

    private static boolean hasPromotedClientEventInfoComponent(String component) {
        return component != null && component.toLowerCase(Locale.ROOT).contains("promoted");
    }

    private static Object getPostId(Object post) {
        return null;
    }

    private static boolean isVerticalConversation(Object displayType) {
        return false;
    }

    private static List<?> getVerticalConversationPostIds(Object displayType) {
        return null;
    }

    private static Object copyVerticalConversation(Object ignoredDisplayType, List<?> postIds) {
        return ignoredDisplayType;
    }

    private static String getPostText(Object post) {
        return null;
    }

    private static List<?> getPostMentions(Object post) {
        return null;
    }

    private static int getMentionStartIdx(Object mention) {
        return 0;
    }

    private static int getMentionEndIdx(Object mention) {
        return 0;
    }

    private static String getMentionScreenName(Object mention) {
        return null;
    }

    private static String getPostAuthorScreenName(Object post) {
        return null;
    }

    private static Object copyModule(
            Object ignoredModule,
            List<?> children,
            Object header,
            Object footer,
            Object displayType,
            long sortIndex,
            String entryId,
            Object clientEventInfo
    ) {
        return null;
    }

    private static Object immutableList(List<Object> filtered) {
        return Collections.unmodifiableList(filtered);
    }

    private static String childContext(
            Object module,
            int childIndex,
            Object child,
            Object item,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide
    ) {
        return "module=" + describeValue(module)
                + ", childIndex=" + childIndex
                + ", child=" + describeValue(child)
                + ", item=" + describeValue(item)
                + ", filters={promoted=" + filterPromotedItems
                + ", whoToFollow=" + hideWhoToFollow
                + ", keywordRules=" + hasEnabledRules(ruleSnapshot)
                + ", aiSources=" + aiSourcesToHide + "}";
    }

    private static boolean hasEnabledRules(PostFilterRuleStore.Snapshot snapshot) {
        return snapshot != null && snapshot.hasEnabledRules();
    }

    private static String describeValue(Object value) {
        if (value == null) return "null";
        String description;
        try {
            description = value.toString();
        } catch (RuntimeException exception) {
            description = "<toString failed: " + exception.getClass().getSimpleName() + ">";
        }
        if (description.length() > 400) description = description.substring(0, 400) + "…";
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value))
                + "=" + description;
    }

    private static void logFailure(String operation, Exception exception, String... context) {
        String details = context.length == 0 ? "" : " [" + String.join(", ", context) + "]";
        // Keep the toast as a visible repro signal while detailed context remains in the log.
        Logger.printException(() -> DEBUG_LOG_PREFIX + "Failed NewX " + operation + details, exception);
    }

    private static void logDiagnostic(String operation, Exception exception, String... context) {
        String details = context.length == 0 ? "" : " [" + String.join(", ", context) + "]";
        Logger.printInfo(() -> DEBUG_LOG_PREFIX + "Failed NewX " + operation + details, exception);
    }

    private static final class FilterResult {
        private final Object item;
        private final boolean remove;

        private FilterResult(Object item, boolean remove) {
            this.item = item;
            this.remove = remove;
        }

        private static FilterResult keep(Object item) {
            return new FilterResult(item, false);
        }

        private static FilterResult replace(Object item) {
            return new FilterResult(item, false);
        }

        private static FilterResult remove() {
            return new FilterResult(null, true);
        }
    }
}
