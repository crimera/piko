package app.morphe.extension.xlite.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.xlite.postfilter.PostFilterMatcher;
import app.morphe.extension.xlite.postfilter.PostFilterRuleStore;

public final class XLiteTimelineFilter {

    private static final String AI_SOURCE_USER_MARKED = "UserMarked";
    private static final String AI_SOURCE_AUTO_DETECTED = "AutoDetected";
    private static final String AI_SOURCE_NOT_IDENTIFIED = "SourceNotIdentified";
    private static final TimelineModelAccess PRODUCTION_MODEL_ACCESS = new TimelineModelAccess() {
        @Override boolean isModuleItem(Object value) { return isTimelineModuleItem(value); }
        @Override boolean isPost(Object value) { return isTimelinePost(value); }
        @Override boolean isModule(Object value) { return isTimelineModule(value); }
        @Override boolean isRtbImageAd(Object value) { return isTimelineRtbImageAd(value); }
        @Override Object getModuleItem(Object wrapper) { return XLiteTimelineFilter.getModuleItem(wrapper); }
        @Override boolean isModuleItemDispensable(Object wrapper) {
            return XLiteTimelineFilter.isModuleItemDispensable(wrapper);
        }
        @Override Object copyModuleItem(Object wrapper, Object item, boolean dispensable) {
            return XLiteTimelineFilter.copyModuleItem(wrapper, item, dispensable);
        }
        @Override List<?> getModuleChildren(Object module) { return getModuleInnerContent(module); }
        @Override Object getModuleDisplayType(Object module) { return XLiteTimelineFilter.getModuleDisplayType(module); }
        @Override Object copyModule(Object module, List<?> children, Object displayType) {
            return XLiteTimelineFilter.copyModule(
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
        @Override Object getPostId(Object post) { return XLiteTimelineFilter.getPostId(post); }
        @Override boolean isVerticalConversation(Object displayType) {
            return XLiteTimelineFilter.isVerticalConversation(displayType);
        }
        @Override List<?> getVerticalConversationPostIds(Object displayType) {
            return XLiteTimelineFilter.getVerticalConversationPostIds(displayType);
        }
        @Override Object copyVerticalConversation(Object displayType, List<?> postIds) {
            return XLiteTimelineFilter.copyVerticalConversation(displayType, postIds);
        }
        @Override String getModuleEntryId(Object module) { return XLiteTimelineFilter.getModuleEntryId(module); }
        @Override Object getModuleClientEventInfo(Object module) {
            return XLiteTimelineFilter.getModuleClientEventInfo(module);
        }
        @Override String getPostEntryId(Object post) { return XLiteTimelineFilter.getPostEntryId(post); }
        @Override Object getPostClientEventInfo(Object post) {
            return XLiteTimelineFilter.getPostClientEventInfo(post);
        }
        @Override Object getPostPromotedMetadata(Object post) {
            return XLiteTimelineFilter.getPostPromotedMetadata(post);
        }
        @Override String getPostText(Object post) { return XLiteTimelineFilter.getPostText(post); }
        @Override Object getContentDisclosure(Object post) { return XLiteTimelineFilter.getContentDisclosure(post); }
        @Override boolean hasAiGeneratedDisclosure(Object disclosure) {
            return XLiteTimelineFilter.hasAiGeneratedDisclosure(disclosure);
        }
        @Override Object getAiDetectionSource(Object disclosure) {
            return XLiteTimelineFilter.getAiDetectionSource(disclosure);
        }
    };

    private XLiteTimelineFilter() {
    }

    public static Object filterPromotedItems(Object timelineItems, boolean enabled) {
        return filterPromotedItems(timelineItems, enabled, PRODUCTION_MODEL_ACCESS);
    }

    static Object filterPromotedItems(Object timelineItems, boolean enabled, TimelineModelAccess modelAccess) {
        return filterTimelineItems(timelineItems, enabled, false, null, modelAccess);
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
        return filterTimelineItems(timelineItems, false, false, snapshot, modelAccess);
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
        return filterTimelineItems(timelineItems, false, false, null, sources, modelAccess);
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
                modelAccess
        );
    }

    private static Object filterTimelineItems(
            Object timelineItems,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide,
            TimelineModelAccess modelAccess
    ) {
        if (timelineItems == null) return null;
        if (!filterPromotedItems
                && !hideWhoToFollow
                && (ruleSnapshot == null || !ruleSnapshot.hasEnabledRules())
                && (aiSourcesToHide == null || aiSourcesToHide.isEmpty())) {
            return timelineItems;
        }
        if (!(timelineItems instanceof Iterable<?> iterable)) return timelineItems;

        try {
            List<Object> filtered = new ArrayList<>();
            boolean changed = false;
            for (Object original : iterable) {
                FilterResult result = filterObject(
                        original,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide,
                        modelAccess
                );
                if (result.remove) {
                    changed = true;
                    continue;
                }
                filtered.add(result.item);
                changed |= result.item != original;
            }
            if (!changed) return timelineItems;
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
                        modelAccess
                );
            }
            return filterItem(
                    original,
                    filterPromotedItems,
                    hideWhoToFollow,
                    ruleSnapshot,
                    aiSourcesToHide,
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
            TimelineModelAccess modelAccess
    ) {
        Object originalItem = modelAccess.getModuleItem(wrapper);
        FilterResult result = filterItem(
                originalItem,
                filterPromotedItems,
                hideWhoToFollow,
                ruleSnapshot,
                aiSourcesToHide,
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
            TimelineModelAccess modelAccess
    ) {
        if (item == null) return FilterResult.keep(null);
        if (filterPromotedItems && isPromoted(item, modelAccess)) return FilterResult.remove();
        if (modelAccess.isPost(item)) {
            if (PostFilterMatcher.findMatchReason(modelAccess.getPostText(item), ruleSnapshot) != null) {
                return FilterResult.remove();
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
        Object disclosure = modelAccess.getContentDisclosure(post);
        if (disclosure == null || !modelAccess.hasAiGeneratedDisclosure(disclosure)) return false;

        Object source = modelAccess.getAiDetectionSource(disclosure);
        if (source == null) return aiSourcesToHide.contains(AI_SOURCE_NOT_IDENTIFIED);
        if (!(source instanceof Enum<?> enumSource)) return false;
        return aiSourcesToHide.contains(enumSource.name());
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
            TimelineModelAccess modelAccess
    ) {
        if (hideWhoToFollow && isWhoToFollowEntryId(modelAccess.getModuleEntryId(module))) {
            return FilterResult.remove();
        }

        List<?> originalChildren = modelAccess.getModuleChildren(module);
        if (originalChildren == null || originalChildren.isEmpty()) {
            return FilterResult.keep(module);
        }

        List<Object> filteredChildren = new ArrayList<>(originalChildren.size());
        Set<Object> removedPostIds = new HashSet<>();
        boolean changed = false;
        for (Object originalChild : originalChildren) {
            if (originalChild == null) {
                filteredChildren.add(null);
                continue;
            }

            Object originalItem = modelAccess.getModuleItem(originalChild);
            FilterResult result;
            try {
                result = filterItem(
                        originalItem,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide,
                        modelAccess
                );
            } catch (RuntimeException exception) {
                logFailure("timeline module child", exception);
                filteredChildren.add(originalChild);
                continue;
            }

            if (result.remove) {
                changed = true;
                if (modelAccess.isPost(originalItem)) {
                    Object postId = modelAccess.getPostId(originalItem);
                    if (postId != null) removedPostIds.add(postId);
                }
                continue;
            }
            if (result.item == originalItem) {
                filteredChildren.add(originalChild);
                continue;
            }

            try {
                filteredChildren.add(modelAccess.copyModuleItem(
                        originalChild,
                        result.item,
                        modelAccess.isModuleItemDispensable(originalChild)
                ));
                changed = true;
            } catch (RuntimeException exception) {
                logFailure("timeline module child reconstruction", exception);
                filteredChildren.add(originalChild);
            }
        }

        if (!changed) return FilterResult.keep(module);
        if (filteredChildren.isEmpty()) return FilterResult.remove();

        try {
            Object displayType = repairDisplayType(
                    modelAccess.getModuleDisplayType(module),
                    removedPostIds,
                    modelAccess
            );
            return FilterResult.replace(modelAccess.copyModule(module, filteredChildren, displayType));
        } catch (RuntimeException exception) {
            logFailure("timeline module reconstruction", exception);
            return FilterResult.keep(module);
        }
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

        return eventInfo != null
                && eventInfo.toString().toLowerCase(Locale.ROOT).contains("promoted");
    }

    private static boolean isPromotedEntryId(String entryId) {
        if (entryId == null) return false;
        if (entryId.contains("promoted")) return true;
        if (entryId.startsWith("ad-") || entryId.contains("-ad-")) return true;
        String[] components = entryId.split("-");
        return components.length == 3 && "conversationthread".equals(components[0]);
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
        return Collections.unmodifiableList(new ArrayList<>(filtered));
    }

    private static void logFailure(String operation, Throwable exception) {
        Logger.printException(() -> "Failed X-Lite " + operation, exception);
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
