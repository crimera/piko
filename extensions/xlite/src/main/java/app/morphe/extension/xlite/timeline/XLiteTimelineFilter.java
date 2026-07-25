package app.morphe.extension.xlite.timeline;

import com.x.models.timelines.items.UrtTimelineItem;
import com.x.models.timelines.items.UrtTimelineModuleItem;
import com.x.models.timelines.items.UrtTimelinePost;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;

public final class XLiteTimelineFilter {
    private XLiteTimelineFilter() {
    }

    public static Object filterPromotedItems(Object timelineItems, boolean enabled) {
        if (!enabled || timelineItems == null) return timelineItems;
        if (!(timelineItems instanceof Iterable<?> iterable)) return timelineItems;

        try {
            List<Object> filtered = new ArrayList<>();
            int originalSize = 0;
            for (Object candidate : iterable) {
                originalSize++;
                if (!isPromoted(candidate)) filtered.add(candidate);
            }
            if (filtered.size() == originalSize) return timelineItems;
            return immutableListView(filtered, timelineItems);
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to filter X-Lite promoted timeline items", exception);
            return timelineItems;
        }
    }

    private static boolean isPromoted(Object candidate) {
        if (candidate == null) return false;
        UrtTimelineItem item = unwrap(candidate);
        if (item == null) return false;

        String entryId = item.getEntryId();
        if (isPromotedEntryId(entryId)) return true;
        if (item instanceof UrtTimelinePost post && post.getPromotedMetadata() != null) return true;

        Object clientEventInfo = item.getClientEventInfo();
        return clientEventInfo != null
                && clientEventInfo.toString().toLowerCase(Locale.ROOT).contains("promoted");
    }

    private static UrtTimelineItem unwrap(Object candidate) {
        if (candidate instanceof UrtTimelineItem item) return item;
        if (candidate instanceof UrtTimelineModuleItem moduleItem) return moduleItem.getItem();
        return null;
    }

    private static boolean isPromotedEntryId(String entryId) {
        if (entryId == null) return false;
        if (entryId.contains("promoted")) return true;
        if (entryId.startsWith("ad-") || entryId.contains("-ad-")) return true;
        String[] components = entryId.split("-");
        return components.length == 3 && "conversationthread".equals(components[0]);
    }

    private static Object immutableListView(List<Object> filtered, Object originalList) {
        Class<?>[] interfaces = allInterfaces(originalList.getClass());
        if (interfaces.length == 0) return Collections.unmodifiableList(filtered);

        InvocationHandler handler = new ImmutableListInvocationHandler(filtered, originalList);
        return Proxy.newProxyInstance(originalList.getClass().getClassLoader(), interfaces, handler);
    }

    private static Class<?>[] allInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new HashSet<>();
        Class<?> current = type;
        while (current != null) {
            Collections.addAll(interfaces, current.getInterfaces());
            current = current.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[0]);
    }

    private static final class ImmutableListInvocationHandler implements InvocationHandler {
        private final List<Object> filtered;
        private final Object originalList;

        private ImmutableListInvocationHandler(List<Object> filtered, Object originalList) {
            this.filtered = Collections.unmodifiableList(new ArrayList<>(filtered));
            this.originalList = originalList;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            String name = method.getName();
            if ("equals".equals(name) && arguments != null && arguments.length == 1) {
                return proxy == arguments[0] || filtered.equals(arguments[0]);
            }
            if ("hashCode".equals(name) && noArguments(arguments)) return filtered.hashCode();
            if ("toString".equals(name) && noArguments(arguments)) return filtered.toString();

            Object result = method.invoke(filtered, arguments);
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
    }
}
