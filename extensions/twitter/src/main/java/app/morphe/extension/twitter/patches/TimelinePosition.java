/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.twitter.Pref;

/** Preserves the For You reading position across automatic updates and native view recreation. */
public final class TimelinePosition {
    public static int initialLoadCount(int configured, int normal, int type, int forYouType) {
        return type == forYouType && configured > 0 && Pref.disableAutoTimelineScroll()
                ? Math.max(configured, normal) : configured;
    }

    public static void preserveCache(List<?> instructions, Class<?> clearCache, int fetchType) {
        if (fetchType == 4 && Pref.disableAutoTimelineScroll()) instructions.removeIf(clearCache::isInstance);
    }

    private static final WeakHashMap<Object, State> STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<Object>> LISTS = new WeakHashMap<>();
    private static Binding binding;

    private TimelinePosition() {}

    private static final class State {
        boolean pending;
        boolean abandoned;
        List<?> positions;
    }

    private static Binding binding(Object controller) throws ReflectiveOperationException {
        if (binding == null) binding = new Binding(controller.getClass().getClassLoader());
        return binding;
    }

    public static boolean restore(Object controller, List<?> positions) {
        try {
            Binding b = binding(controller);
            if (!b.enabled(controller)) return false;
            Object provider = b.provider.get(controller);
            Object view = b.view.get(controller);
            View recycler = (View) b.recycler.get(b.list.get(view));
            LISTS.put(recycler, new WeakReference<>(provider));
            State state = STATES.computeIfAbsent(provider, key -> new State());
            if (state.abandoned) return false;
            state.pending = !positions.isEmpty();
            state.positions = state.pending ? positions : null;

            // Keep native row-ID priority, including the offsets of secondary visible rows.
            for (Object position : positions) {
                long row = b.id.getLong(position);
                if (row < 0) continue;
                int index = (Integer) b.lookup.invoke(controller, row);
                if (index >= 0) {
                    b.scroll.invoke(view, index, b.offset.getInt(position), false);
                    state.pending = false;
                    state.positions = null;
                    return true;
                }
            }

            Object adapter = b.adapter.invoke(view);
            int count = (Integer) b.count.invoke(adapter);
            for (Object position : positions) {
                Object metadata = b.metadata.get(position);
                if (metadata == null || b.metadataType.getInt(metadata) != b.forYouType) continue;
                String entry = (String) b.entry.get(metadata);
                String group = (String) b.group.get(metadata);
                if (!valid(entry) || !valid(group) || b.metadataRow.getLong(metadata) != b.id.getLong(position)) continue;
                long match = -1;
                boolean ambiguous = false;
                for (int i = 0; i < count; i++) {
                    Object item = b.item.invoke(adapter, i);
                    if (item == null || !entry.equals(b.itemEntry.invoke(item)) || !group.equals(b.itemGroup.invoke(item))) continue;
                    long row = b.itemRow.getLong(item);
                    if (row <= 0) continue;
                    if (match != -1) { ambiguous = true; break; }
                    match = row;
                }
                if (ambiguous || match < 0) continue;
                int index = (Integer) b.lookup.invoke(controller, match);
                if (index < 0) continue;
                b.scroll.invoke(view, index, b.offset.getInt(position), false);
                state.pending = false;
                state.positions = null;
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    public static void afterRender(Object controller) {
        try {
            Binding b = binding(controller);
            if (!b.enabled(controller)) return;
            State state = STATES.get(b.provider.get(controller));
            if (state == null || !state.pending || state.abandoned || state.positions == null) return;
            // Native startup restoration runs before later collections contain the saved row.
            restore(controller, state.positions);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static boolean keep(Object controller) {
        try {
            Binding b = binding(controller);
            if (!b.enabled(controller)) return false;
            State state = STATES.get(b.provider.get(controller));
            return state != null && (state.pending || state.abandoned);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean canSave(Object controller) {
        try {
            Binding b = binding(controller);
            if (!b.enabled(controller)) return true;
            State state = STATES.get(b.provider.get(controller));
            return state == null || !state.pending;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    public static void prepare(Object controller, List<?> positions) {
        try {
            Binding b = binding(controller);
            if (!b.enabled(controller)) return;
            Object adapter = b.adapter.invoke(b.view.get(controller));
            int count = (Integer) b.count.invoke(adapter);
            Map<Long, Object> items = new HashMap<>(positions.size());
            for (Object position : positions) {
                // An adapter index may describe new data while the bound view still shows an old row.
                // Attach identity only after matching the captured row ID in the current data.
                b.metadata.set(position, null);
                long row = b.id.getLong(position);
                if (row > 0) items.put(row, null);
            }
            int remaining = items.size();
            for (int i = 0; i < count && remaining > 0; i++) {
                Object item = b.item.invoke(adapter, i);
                if (item == null) continue;
                long row = b.itemRow.getLong(item);
                if (items.containsKey(row) && items.get(row) == null) {
                    items.put(row, item);
                    remaining--;
                }
            }
            for (Object position : positions) {
                long row = b.id.getLong(position);
                Object item = items.get(row);
                if (item == null) continue;
                String entry = (String) b.itemEntry.invoke(item);
                String group = (String) b.itemGroup.invoke(item);
                if (!valid(entry) || !valid(group)) continue;
                Object info = b.itemInfo.invoke(item);
                if (info == null) continue;
                b.metadata.set(position, b.constructor.newInstance(entry, group, b.sort.getLong(info), row, b.forYouType));
            }
            STATES.remove(b.provider.get(controller));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static void onScrollStateChanged(View recycler, int state) {
        if (state != 1) return; // A user drag supersedes a deferred startup restore.
        WeakReference<Object> reference = LISTS.get(recycler);
        Object provider = reference == null ? null : reference.get();
        State pending = provider == null ? null : STATES.get(provider);
        if (pending != null && pending.pending) {
            pending.pending = false;
            pending.abandoned = true;
            pending.positions = null;
        }
    }

    private static boolean valid(String value) {
        return value != null && !value.isEmpty() && !"unspecified".equals(value);
    }

    private static final class Binding {
        final int forYouType;
        final Field type, view, list, recycler, provider, id, offset, metadata;
        final Field entry, group, metadataRow, metadataType, itemRow, sort;
        final Method adapter, count, item, itemEntry, itemGroup, itemInfo, lookup, scroll;
        final Constructor<?> constructor;

        Binding(ClassLoader loader) throws ReflectiveOperationException {
            // Placeholders are replaced with verified native descriptors at patch time.
            forYouType = Integer.parseInt("piko_timeline_forYouType");
            type = field(loader, "piko_timeline_type");
            view = field(loader, "piko_timeline_view");
            list = field(loader, "piko_timeline_list");
            recycler = field(loader, "piko_timeline_recycler");
            provider = field(loader, "piko_timeline_provider");
            id = field(loader, "piko_timeline_id");
            offset = field(loader, "piko_timeline_offset");
            metadata = field(loader, "piko_timeline_metadata");
            entry = field(loader, "piko_timeline_entry");
            group = field(loader, "piko_timeline_group");
            metadataRow = field(loader, "piko_timeline_metadataRow");
            metadataType = field(loader, "piko_timeline_metadataType");
            itemRow = field(loader, "piko_timeline_itemRow");
            sort = field(loader, "piko_timeline_sort");
            adapter = method(loader, "piko_timeline_adapter");
            count = method(loader, "piko_timeline_count");
            item = method(loader, "piko_timeline_item", int.class);
            itemEntry = method(loader, "piko_timeline_itemEntry");
            itemGroup = method(loader, "piko_timeline_itemGroup");
            itemInfo = method(loader, "piko_timeline_itemInfo");
            lookup = method(loader, "piko_timeline_lookup", long.class);
            scroll = method(loader, "piko_timeline_scroll", int.class, int.class, boolean.class);
            constructor = metadata.getType().getConstructor(String.class, String.class, long.class, long.class, int.class);
        }

        boolean enabled(Object controller) throws IllegalAccessException {
            return type.getInt(controller) == forYouType && Pref.disableAutoTimelineScroll();
        }

        private static Class<?> type(ClassLoader loader, String descriptor) throws ClassNotFoundException {
            return Class.forName(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, loader);
        }

        private static Field field(ClassLoader loader, String descriptor) throws ReflectiveOperationException {
            int arrow = descriptor.indexOf("->");
            return type(loader, descriptor.substring(0, arrow)).getField(descriptor.substring(arrow + 2, descriptor.indexOf(':', arrow)));
        }

        private static Method method(ClassLoader loader, String descriptor, Class<?>... parameters) throws ReflectiveOperationException {
            int arrow = descriptor.indexOf("->");
            return type(loader, descriptor.substring(0, arrow)).getMethod(descriptor.substring(arrow + 2, descriptor.indexOf('(', arrow)), parameters);
        }
    }
}
