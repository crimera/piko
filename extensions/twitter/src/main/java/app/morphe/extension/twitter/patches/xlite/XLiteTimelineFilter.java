/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.xlite;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.patches.TimelineEntry;
import app.morphe.extension.twitter.patches.postfilter.PostFilterMatcher;

import com.x.models.ClientEventInfo;
import com.x.models.PostIdentifier;
import com.x.models.timelinemodule.ModuleDisplayType;
import com.x.models.timelines.items.UrtTimelineItem;
import com.x.models.timelines.items.UrtTimelineModule;
import com.x.models.timelines.items.UrtTimelineModuleItem;
import com.x.models.timelines.items.UrtTimelinePost;

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

public final class XLiteTimelineFilter {
    private XLiteTimelineFilter() {}

    public static Object filter(Object originalItems) {
        if (originalItems == null) return null;

        boolean filterAds = TimelineEntry.hideAds;
        boolean filterKeywords = PostFilterMatcher.isActive();
        if (!filterAds && !filterKeywords) return originalItems;
        if (!(originalItems instanceof Iterable<?>)) return originalItems;

        FilterContext context = new FilterContext(filterAds, filterKeywords, isLoggingEnabled());
        try {
            List<Object> output = new ArrayList<>();
            boolean changed = false;

            for (Object originalItem : (Iterable<?>) originalItems) {
                FilterResult result = filterObject(originalItem, context);
                context.logTopLevel(originalItem, result);
                if (result.remove) {
                    changed = true;
                    continue;
                }

                output.add(result.item);
                changed |= result.item != originalItem;
            }

            context.flushLog();
            if (!changed) return originalItems;
            return wrapAsImmutableList(output, originalItems);
        } catch (RuntimeException exception) {
            context.logException("top-level filtering", exception);
            context.flushLog();
            return originalItems;
        }
    }

    private static FilterResult filterObject(Object original, FilterContext context) {
        if (original == null) return FilterResult.keep(null);

        try {
            if (original instanceof UrtTimelineModuleItem) {
                return filterModuleItem((UrtTimelineModuleItem) original, context);
            }
            if (original instanceof UrtTimelineItem) {
                return filterItem((UrtTimelineItem) original, context);
            }
            return FilterResult.keep(original);
        } catch (RuntimeException exception) {
            context.logException("item " + original.getClass().getSimpleName(), exception);
            return FilterResult.keep(original);
        }
    }

    private static FilterResult filterModuleItem(
            UrtTimelineModuleItem wrapper,
            FilterContext context) {
        UrtTimelineItem innerItem = wrapper.getItem();
        FilterResult innerResult = filterItem(innerItem, context);
        if (innerResult.remove) return FilterResult.remove(innerResult.reason);
        if (innerResult.item == innerItem) return FilterResult.keep(wrapper);

        UrtTimelineModuleItem rebuilt = wrapper.copy(
                (UrtTimelineItem) innerResult.item,
                wrapper.isDispensable());
        return FilterResult.replace(rebuilt, innerResult.reason);
    }

    private static FilterResult filterItem(UrtTimelineItem item, FilterContext context) {
        if (item == null) return FilterResult.keep(null);

        String adReason = context.filterAds ? adReason(item) : null;
        if (adReason != null) return FilterResult.remove(adReason);

        if (item instanceof UrtTimelinePost) {
            if (!context.filterKeywords) return FilterResult.keep(item);
            String keywordReason = PostFilterMatcher.findMatchReason((UrtTimelinePost) item);
            return keywordReason == null
                    ? FilterResult.keep(item)
                    : FilterResult.remove(keywordReason);
        }

        if (item instanceof UrtTimelineModule) {
            return filterModule((UrtTimelineModule) item, context);
        }

        return FilterResult.keep(item);
    }

    private static FilterResult filterModule(
            UrtTimelineModule module,
            FilterContext context) {
        List<UrtTimelineModuleItem> originalChildren = module.getInnerContent();
        if (originalChildren == null || originalChildren.isEmpty()) return FilterResult.keep(module);

        List<UrtTimelineModuleItem> filteredChildren = new ArrayList<>(originalChildren.size());
        Set<PostIdentifier> removedPostIds = new HashSet<>();
        boolean changed = false;

        for (UrtTimelineModuleItem originalChild : originalChildren) {
            if (originalChild == null) {
                filteredChildren.add(null);
                continue;
            }

            UrtTimelineItem originalInnerItem = originalChild.getItem();
            FilterResult childResult;
            try {
                childResult = filterItem(originalInnerItem, context);
            } catch (RuntimeException exception) {
                context.logException("module child", exception);
                filteredChildren.add(originalChild);
                continue;
            }

            if (childResult.remove) {
                changed = true;
                if (originalInnerItem instanceof UrtTimelinePost) {
                    PostIdentifier id = ((UrtTimelinePost) originalInnerItem).getId();
                    if (id != null) removedPostIds.add(id);
                }
                context.logChild(originalInnerItem, childResult.reason);
                continue;
            }

            if (childResult.item == originalInnerItem) {
                filteredChildren.add(originalChild);
                continue;
            }

            try {
                filteredChildren.add(originalChild.copy(
                        (UrtTimelineItem) childResult.item,
                        originalChild.isDispensable()));
                changed = true;
            } catch (RuntimeException exception) {
                context.logException("module item reconstruction", exception);
                filteredChildren.add(originalChild);
            }
        }

        if (!changed) return FilterResult.keep(module);
        if (filteredChildren.isEmpty()) return FilterResult.remove("EMPTY_FILTERED_MODULE");

        try {
            ModuleDisplayType displayType = repairDisplayType(module.getDisplayType(), removedPostIds);
            UrtTimelineModule rebuilt = module.copy(
                    filteredChildren,
                    module.getModuleHeader(),
                    module.getModuleFooter(),
                    displayType,
                    module.getSortIndex(),
                    module.getEntryId(),
                    module.getClientEventInfo());
            return FilterResult.replace(rebuilt, "MODULE_CHILD_FILTERED");
        } catch (RuntimeException exception) {
            context.logException("module reconstruction", exception);
            return FilterResult.keep(module);
        }
    }

    private static ModuleDisplayType repairDisplayType(
            ModuleDisplayType displayType,
            Set<PostIdentifier> removedPostIds) {
        if (removedPostIds.isEmpty()) return displayType;
        if (!(displayType instanceof ModuleDisplayType.VerticalConversation)) return displayType;

        ModuleDisplayType.VerticalConversation vertical =
                (ModuleDisplayType.VerticalConversation) displayType;
        List<PostIdentifier> originalIds = vertical.getAllTweetIds();
        if (originalIds == null || originalIds.isEmpty()) return displayType;

        List<PostIdentifier> filteredIds = new ArrayList<>(originalIds.size());
        for (PostIdentifier id : originalIds) {
            if (!removedPostIds.contains(id)) filteredIds.add(id);
        }
        if (filteredIds.size() == originalIds.size()) return displayType;
        return vertical.copy(filteredIds);
    }

    private static String adReason(UrtTimelineItem item) {
        String entryId = item.getEntryId();
        if (entryId != null && TimelineEntry.shouldHideUrtEntryId(entryId)) {
            return "AD_ENTRY_ID";
        }

        if (item instanceof UrtTimelinePost
                && ((UrtTimelinePost) item).getPromotedMetadata() != null) {
            return "AD_PROMOTED_METADATA";
        }

        ClientEventInfo eventInfo = item.getClientEventInfo();
        if (eventInfo != null
                && eventInfo.toString().toLowerCase(Locale.ROOT).contains("promoted")) {
            return "AD_CLIENT_EVENT";
        }
        return null;
    }

    private static Object wrapAsImmutableList(List<Object> filtered, Object original) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = original.getClass();
        while (current != null) {
            for (Class<?> type : current.getInterfaces()) collectInterfaces(type, interfaces);
            current = current.getSuperclass();
        }
        if (interfaces.isEmpty()) return Collections.unmodifiableList(filtered);

        List<Object> readOnly = Collections.unmodifiableList(new ArrayList<>(filtered));
        InvocationHandler handler = (proxy, method, args) -> invokeListMethod(
                proxy,
                method,
                args,
                readOnly,
                original);
        return Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                handler);
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> output) {
        if (!output.add(type)) return;
        for (Class<?> parent : type.getInterfaces()) collectInterfaces(parent, output);
    }

    private static Object invokeListMethod(
            Object proxy,
            Method method,
            Object[] args,
            List<Object> readOnly,
            Object original) throws Throwable {
        String name = method.getName();
        if ("equals".equals(name) && args != null && args.length == 1) {
            return proxy == args[0] || readOnly.equals(args[0]);
        }
        if ("hashCode".equals(name) && (args == null || args.length == 0)) {
            return readOnly.hashCode();
        }
        if ("toString".equals(name) && (args == null || args.length == 0)) {
            return readOnly.toString();
        }

        Method listMethod = List.class.getMethod(name, method.getParameterTypes());
        Object result = listMethod.invoke(readOnly, args);
        if ("subList".equals(name) && result instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> subList = (List<Object>) result;
            return wrapAsImmutableList(subList, original);
        }
        return result;
    }

    private static boolean isLoggingEnabled() {
        try {
            return Pref.serverResponseLogging();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final class FilterResult {
        final Object item;
        final boolean remove;
        final String reason;

        private FilterResult(Object item, boolean remove, String reason) {
            this.item = item;
            this.remove = remove;
            this.reason = reason;
        }

        static FilterResult keep(Object item) {
            return new FilterResult(item, false, null);
        }

        static FilterResult replace(Object item, String reason) {
            return new FilterResult(item, false, reason);
        }

        static FilterResult remove(String reason) {
            return new FilterResult(null, true, reason);
        }
    }

    private static final class FilterContext {
        final boolean filterAds;
        final boolean filterKeywords;
        final StringBuilder log;

        FilterContext(boolean filterAds, boolean filterKeywords, boolean loggingEnabled) {
            this.filterAds = filterAds;
            this.filterKeywords = filterKeywords;
            this.log = loggingEnabled ? new StringBuilder() : null;
        }

        void logTopLevel(Object original, FilterResult result) {
            if (log == null) return;
            UrtTimelineItem item = unwrap(original);
            log.append("[XLite Timeline] ")
                    .append(result.remove ? "REMOVED" : "KEPT")
                    .append(": type=")
                    .append(original == null ? "null" : original.getClass().getSimpleName())
                    .append(", entryId=")
                    .append(item == null ? "null" : item.getEntryId());
            if (result.reason != null) log.append(", reason=").append(result.reason);
            log.append('\n');
        }

        void logChild(UrtTimelineItem item, String reason) {
            if (log == null) return;
            log.append("[XLite Timeline] REMOVED_CHILD: type=")
                    .append(item == null ? "null" : item.getClass().getSimpleName())
                    .append(", entryId=")
                    .append(item == null ? "null" : item.getEntryId())
                    .append(", reason=")
                    .append(reason)
                    .append('\n');
        }

        void logException(String operation, RuntimeException exception) {
            if (log == null) return;
            log.append("[XLite Timeline] ERROR: operation=")
                    .append(operation)
                    .append(", error=")
                    .append(exception.getClass().getSimpleName())
                    .append('\n');
            PikoUtils.logger(exception);
        }

        void flushLog() {
            if (log == null || log.length() == 0) return;
            String text = log.toString();
            PikoUtils.logger(text);
            PikoUtils.pikoWriteFile("XLite-Timeline-Log.txt", text, true);
            log.setLength(0);
        }

        private UrtTimelineItem unwrap(Object value) {
            if (value instanceof UrtTimelineItem) return (UrtTimelineItem) value;
            if (value instanceof UrtTimelineModuleItem) {
                return ((UrtTimelineModuleItem) value).getItem();
            }
            return null;
        }
    }
}
