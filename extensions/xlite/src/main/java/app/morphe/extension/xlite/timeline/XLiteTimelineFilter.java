package app.morphe.extension.xlite.timeline;

import com.x.models.ClientEventInfo;
import com.x.models.PostIdentifier;
import com.x.models.timelinemodule.ModuleDisplayType;
import com.x.models.timelines.items.UrtTimelineItem;
import com.x.models.timelines.items.UrtTimelineModule;
import com.x.models.timelines.items.UrtTimelineModuleItem;
import com.x.models.timelines.items.UrtTimelinePost;
import com.x.models.timelines.items.UrtTimelineRtbImageAd;

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
import app.morphe.extension.xlite.settings.SettingsRegistry;

public final class XLiteTimelineFilter {
    private static final String BLOCKED_WORDS_SETTING_ID =
            "xlite.content.post_filtering.blocked_words";

    private XLiteTimelineFilter() {
    }

    public static Object filterPromotedItems(Object timelineItems, boolean enabled) {
        return filterTimelineItems(timelineItems, enabled, Collections.emptyList());
    }

    public static Object filterPostsByKeyword(Object timelineItems, boolean enabled) {
        if (!enabled) return timelineItems;
        return filterPostsByKeyword(
                timelineItems,
                true,
                SettingsRegistry.getString(BLOCKED_WORDS_SETTING_ID)
        );
    }

    public static Object filterPostsByKeyword(
            Object timelineItems,
            boolean enabled,
            String blockedWords
    ) {
        if (!enabled) return timelineItems;
        return filterTimelineItems(
                timelineItems,
                false,
                PostFilterMatcher.normalizedKeywords(blockedWords)
        );
    }

    private static Object filterTimelineItems(
            Object timelineItems,
            boolean filterPromotedItems,
            List<String> normalizedKeywords
    ) {
        if (timelineItems == null) return null;
        if (!filterPromotedItems && normalizedKeywords.isEmpty()) return timelineItems;
        if (!(timelineItems instanceof Iterable<?> iterable)) return timelineItems;

        try {
            List<Object> filtered = new ArrayList<>();
            boolean changed = false;
            for (Object original : iterable) {
                FilterResult result = filterObject(original, filterPromotedItems, normalizedKeywords);
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
            List<String> normalizedKeywords
    ) {
        if (original == null) return FilterResult.keep(null);
        try {
            if (original instanceof UrtTimelineModuleItem wrapper) {
                return filterModuleItem(wrapper, filterPromotedItems, normalizedKeywords);
            }
            if (original instanceof UrtTimelineItem item) {
                return filterItem(item, filterPromotedItems, normalizedKeywords);
            }
            return FilterResult.keep(original);
        } catch (RuntimeException exception) {
            logFailure("timeline item " + original.getClass().getSimpleName(), exception);
            return FilterResult.keep(original);
        }
    }

    private static FilterResult filterModuleItem(
            UrtTimelineModuleItem wrapper,
            boolean filterPromotedItems,
            List<String> normalizedKeywords
    ) {
        UrtTimelineItem originalItem = wrapper.getItem();
        FilterResult result = filterItem(originalItem, filterPromotedItems, normalizedKeywords);
        if (result.remove) return result;
        if (result.item == originalItem) return FilterResult.keep(wrapper);
        return FilterResult.replace(wrapper.copy(
                (UrtTimelineItem) result.item,
                wrapper.isDispensable()
        ));
    }

    private static FilterResult filterItem(
            UrtTimelineItem item,
            boolean filterPromotedItems,
            List<String> normalizedKeywords
    ) {
        if (item == null) return FilterResult.keep(null);
        if (filterPromotedItems && isPromoted(item)) return FilterResult.remove();
        if (item instanceof UrtTimelinePost post
                && PostFilterMatcher.findMatchReason(post, normalizedKeywords) != null) {
            return FilterResult.remove();
        }
        if (item instanceof UrtTimelineModule module) {
            return filterModule(module, filterPromotedItems, normalizedKeywords);
        }
        return FilterResult.keep(item);
    }

    private static FilterResult filterModule(
            UrtTimelineModule module,
            boolean filterPromotedItems,
            List<String> normalizedKeywords
    ) {
        List<UrtTimelineModuleItem> originalChildren = module.getInnerContent();
        if (originalChildren == null || originalChildren.isEmpty()) {
            return FilterResult.keep(module);
        }

        List<UrtTimelineModuleItem> filteredChildren = new ArrayList<>(originalChildren.size());
        Set<PostIdentifier> removedPostIds = new HashSet<>();
        boolean changed = false;
        for (UrtTimelineModuleItem originalChild : originalChildren) {
            if (originalChild == null) {
                filteredChildren.add(null);
                continue;
            }

            UrtTimelineItem originalItem = originalChild.getItem();
            FilterResult result;
            try {
                result = filterItem(originalItem, filterPromotedItems, normalizedKeywords);
            } catch (RuntimeException exception) {
                logFailure("timeline module child", exception);
                filteredChildren.add(originalChild);
                continue;
            }

            if (result.remove) {
                changed = true;
                if (originalItem instanceof UrtTimelinePost post) {
                    PostIdentifier id = post.getId();
                    if (id != null) removedPostIds.add(id);
                }
                continue;
            }
            if (result.item == originalItem) {
                filteredChildren.add(originalChild);
                continue;
            }

            try {
                filteredChildren.add(originalChild.copy(
                        (UrtTimelineItem) result.item,
                        originalChild.isDispensable()
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
            ModuleDisplayType displayType = repairDisplayType(
                    module.getDisplayType(),
                    removedPostIds
            );
            return FilterResult.replace(module.copy(
                    filteredChildren,
                    module.getModuleHeader(),
                    module.getModuleFooter(),
                    displayType,
                    module.getSortIndex(),
                    module.getEntryId(),
                    module.getClientEventInfo()
            ));
        } catch (RuntimeException exception) {
            logFailure("timeline module reconstruction", exception);
            return FilterResult.keep(module);
        }
    }

    private static ModuleDisplayType repairDisplayType(
            ModuleDisplayType displayType,
            Set<PostIdentifier> removedPostIds
    ) {
        if (removedPostIds.isEmpty()) return displayType;
        if (!(displayType instanceof ModuleDisplayType.VerticalConversation conversation)) {
            return displayType;
        }

        List<PostIdentifier> originalIds = conversation.getAllTweetIds();
        if (originalIds == null || originalIds.isEmpty()) return displayType;
        List<PostIdentifier> filteredIds = new ArrayList<>(originalIds.size());
        for (PostIdentifier id : originalIds) {
            if (!removedPostIds.contains(id)) filteredIds.add(id);
        }
        if (filteredIds.size() == originalIds.size()) return displayType;
        return conversation.copy(filteredIds);
    }

    private static boolean isPromoted(UrtTimelineItem item) {
        if (item instanceof UrtTimelineRtbImageAd) return true;
        if (isPromotedEntryId(item.getEntryId())) return true;
        if (item instanceof UrtTimelinePost post && post.getPromotedMetadata() != null) return true;

        ClientEventInfo eventInfo = item.getClientEventInfo();
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

    private static void logFailure(String operation, RuntimeException exception) {
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
