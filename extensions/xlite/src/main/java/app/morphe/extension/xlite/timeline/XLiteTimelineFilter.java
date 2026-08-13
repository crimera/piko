package app.morphe.extension.xlite.timeline;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private XLiteTimelineFilter() {
    }

    public static Object filterPromotedItems(Object timelineItems, boolean enabled) {
        return filterTimelineItems(timelineItems, enabled, false, null);
    }

    public static Object filterWhoToFollow(Object timelineItems, boolean enabled) {
        if (!enabled) return timelineItems;
        return filterTimelineItems(timelineItems, false, true, null);
    }

    public static Object filterPostsByKeyword(Object timelineItems) {
        try {
            PostFilterRuleStore store = PostFilterRuleStore.shared();
            if (!store.isEnabled()) return timelineItems;
            return filterTimelineItems(timelineItems, false, false, store.snapshot());
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
        if (!enabled) return timelineItems;
        return filterTimelineItems(timelineItems, false, false, snapshot);
    }

    public static Object filterAiGeneratedPosts(Object timelineItems, Set<String> sourcesToHide) {
        Set<String> sources = parseAiSources(sourcesToHide);
        if (sources.isEmpty()) return timelineItems;
        return filterTimelineItems(timelineItems, false, false, null, sources);
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
            PostFilterRuleStore.Snapshot ruleSnapshot
    ) {
        return filterTimelineItems(
                timelineItems,
                filterPromotedItems,
                hideWhoToFollow,
                ruleSnapshot,
                Collections.emptySet()
        );
    }

    private static Object filterTimelineItems(
            Object timelineItems,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide
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
                        aiSourcesToHide
                );
                if (result.remove) {
                    changed = true;
                    continue;
                }
                filtered.add(result.item);
                changed |= result.item != original;
            }
            if (!changed) return timelineItems;
            return immutableListView(filtered, timelineItems);
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
            Set<String> aiSourcesToHide
    ) {
        if (original == null) return FilterResult.keep(null);
        try {
            if (isTimelineModuleItem(original)) {
                return filterModuleItem(
                        original,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide
                );
            }
            return filterItem(original, filterPromotedItems, hideWhoToFollow, ruleSnapshot, aiSourcesToHide);
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
            Set<String> aiSourcesToHide
    ) {
        Object originalItem = getModuleItem(wrapper);
        FilterResult result = filterItem(
                originalItem,
                filterPromotedItems,
                hideWhoToFollow,
                ruleSnapshot,
                aiSourcesToHide
        );
        if (result.remove) return result;
        if (result.item == originalItem) return FilterResult.keep(wrapper);
        return FilterResult.replace(copyModuleItem(
                wrapper,
                result.item,
                isModuleItemDispensable(wrapper)
        ));
    }

    private static FilterResult filterItem(
            Object item,
            boolean filterPromotedItems,
            boolean hideWhoToFollow,
            PostFilterRuleStore.Snapshot ruleSnapshot,
            Set<String> aiSourcesToHide
    ) {
        if (item == null) return FilterResult.keep(null);
        if (filterPromotedItems && isPromoted(item)) return FilterResult.remove();
        if (isTimelinePost(item)) {
            if (PostFilterMatcher.findMatchReason(item, ruleSnapshot) != null) {
                return FilterResult.remove();
            }
            if (isAiGenerated(item, aiSourcesToHide)) return FilterResult.remove();
        }
        if (isTimelineModule(item)) {
            return filterModule(item, filterPromotedItems, hideWhoToFollow, ruleSnapshot, aiSourcesToHide);
        }
        return FilterResult.keep(item);
    }

    private static boolean isAiGenerated(Object post, Set<String> aiSourcesToHide) {
        if (aiSourcesToHide == null || aiSourcesToHide.isEmpty()) return false;
        Object disclosure = getContentDisclosure(post);
        if (disclosure == null || !hasAiGeneratedDisclosure(disclosure)) return false;

        Object source = getAiDetectionSource(disclosure);
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
            Set<String> aiSourcesToHide
    ) {
        if (hideWhoToFollow && isWhoToFollowEntryId(getModuleEntryId(module))) {
            return FilterResult.remove();
        }

        List<?> originalChildren = getModuleInnerContent(module);
        if (originalChildren == null || originalChildren.isEmpty()) {
            return FilterResult.keep(module);
        }

        List<Object> filteredChildren = new ArrayList<>(originalChildren.size());
        boolean changed = false;
        for (Object originalChild : originalChildren) {
            if (originalChild == null) {
                filteredChildren.add(null);
                continue;
            }

            Object originalItem = getModuleItem(originalChild);
            FilterResult result;
            try {
                result = filterItem(
                        originalItem,
                        filterPromotedItems,
                        hideWhoToFollow,
                        ruleSnapshot,
                        aiSourcesToHide
                );
            } catch (RuntimeException exception) {
                logFailure("timeline module child", exception);
                filteredChildren.add(originalChild);
                continue;
            }

            if (result.remove) {
                changed = true;
                continue;
            }
            if (result.item == originalItem) {
                filteredChildren.add(originalChild);
                continue;
            }

            try {
                filteredChildren.add(copyModuleItem(
                        originalChild,
                        result.item,
                        isModuleItemDispensable(originalChild)
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
            return FilterResult.replace(copyModule(
                    module,
                    filteredChildren,
                    getModuleHeader(module),
                    getModuleFooter(module),
                    getModuleDisplayType(module),
                    getModuleSortIndex(module),
                    getModuleEntryId(module),
                    getModuleClientEventInfo(module)
            ));
        } catch (RuntimeException exception) {
            logFailure("timeline module reconstruction", exception);
            return FilterResult.keep(module);
        }
    }

    private static boolean isWhoToFollowEntryId(String entryId) {
        return entryId != null && entryId.startsWith("who-to-follow");
    }

    private static boolean isPromoted(Object item) {
        if (isTimelineRtbImageAd(item)) return true;
        String entryId = null;
        Object eventInfo = null;
        if (isTimelinePost(item)) {
            entryId = getPostEntryId(item);
            eventInfo = getPostClientEventInfo(item);
            if (getPostPromotedMetadata(item) != null) return true;
        } else if (isTimelineModule(item)) {
            entryId = getModuleEntryId(item);
            eventInfo = getModuleClientEventInfo(item);
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

    private static Object immutableListView(List<Object> filtered, Object originalList) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = originalList.getClass();
        while (current != null) {
            for (Class<?> type : current.getInterfaces()) collectInterfaces(type, interfaces);
            current = current.getSuperclass();
        }
        if (interfaces.isEmpty()) return Collections.unmodifiableList(filtered);

        List<Object> readOnly = Collections.unmodifiableList(new ArrayList<>(filtered));
        InvocationHandler handler = (proxy, method, arguments) -> invokeListMethod(
                proxy,
                method,
                arguments,
                readOnly,
                originalList
        );
        return Proxy.newProxyInstance(
                originalList.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                handler
        );
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> output) {
        if (!output.add(type)) return;
        for (Class<?> parent : type.getInterfaces()) collectInterfaces(parent, output);
    }

    private static Object invokeListMethod(
            Object proxy,
            Method method,
            Object[] arguments,
            List<Object> readOnly,
            Object originalList
    ) throws Throwable {
        String name = method.getName();
        if ("equals".equals(name) && arguments != null && arguments.length == 1) {
            return proxy == arguments[0] || readOnly.equals(arguments[0]);
        }
        if ("hashCode".equals(name) && noArguments(arguments)) return readOnly.hashCode();
        if ("toString".equals(name) && noArguments(arguments)) return readOnly.toString();

        Method listMethod = List.class.getMethod(name, method.getParameterTypes());
        Object result = listMethod.invoke(readOnly, arguments);
        if ("subList".equals(name) && result instanceof List<?> subList) {
            @SuppressWarnings("unchecked")
            List<Object> typedSubList = (List<Object>) subList;
            return immutableListView(typedSubList, originalList);
        }
        return result;
    }

    private static boolean noArguments(Object[] arguments) {
        return arguments == null || arguments.length == 0;
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
