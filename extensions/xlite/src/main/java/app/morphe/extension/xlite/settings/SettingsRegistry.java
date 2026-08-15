package app.morphe.extension.xlite.settings;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;

public final class SettingsRegistry {
    private enum ItemType {
        TOGGLE,
        TEXT_INPUT,
        SINGLE_CHOICE,
        MULTI_CHOICE,
        ACTION,
        CUSTOM_SCREEN,
    }

    private static final Comparator<NodeBuilder> NODE_COMPARATOR =
            Comparator.comparingInt((NodeBuilder node) -> node.order).thenComparing(node -> node.id);
    private static final Map<String, NodeBuilder> NODES = new LinkedHashMap<>();
    private static final Map<String, GroupBuilder> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, Setting<?>> SETTINGS = new LinkedHashMap<>();
    private static List<SettingsNode.Category> catalog = Collections.emptyList();
    private static boolean frozen;

    private SettingsRegistry() {
    }

    public static synchronized void registerCategory(
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            @Nullable String iconResourceName,
            int order
    ) {
        registerGroupInternal(
                null,
                id,
                titleResourceName,
                summaryResourceName,
                iconResourceName,
                order,
                true
        );
    }

    public static synchronized void registerGroup(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            @Nullable String iconResourceName,
            int order
    ) {
        registerGroupInternal(
                parentId,
                id,
                titleResourceName,
                summaryResourceName,
                iconResourceName,
                order,
                false
        );
    }

    public static synchronized void registerToggle(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.TOGGLE);
    }

    public static synchronized void configureToggle(String id, boolean defaultValue, boolean rebootApp) {
        ItemBuilder item = requireItem(id, ItemType.TOGGLE);
        requireUnconfigured(item);
        item.defaultValue = defaultValue;
        item.rebootApp = rebootApp;
        item.configured = true;
    }

    public static synchronized void registerTextInput(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.TEXT_INPUT);
    }

    public static synchronized void configureTextInput(
            String id,
            String defaultValue,
            int inputKind,
            boolean rebootApp
    ) {
        ItemBuilder item = requireItem(id, ItemType.TEXT_INPUT);
        requireUnconfigured(item);
        SettingsNode.InputKind[] inputKinds = SettingsNode.InputKind.values();
        if (inputKind < 0 || inputKind >= inputKinds.length) {
            throw failure("Invalid input kind for " + id + ": " + inputKind);
        }
        item.defaultValue = Objects.requireNonNull(defaultValue);
        item.inputKind = inputKinds[inputKind];
        item.rebootApp = rebootApp;
        item.configured = true;
    }

    public static synchronized void registerSingleChoice(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.SINGLE_CHOICE);
    }

    public static synchronized void configureSingleChoice(
            String id,
            String defaultValue,
            boolean rebootApp
    ) {
        ItemBuilder item = requireItem(id, ItemType.SINGLE_CHOICE);
        requireUnconfigured(item);
        item.defaultValue = Objects.requireNonNull(defaultValue);
        item.rebootApp = rebootApp;
        item.configured = true;
    }

    public static synchronized void registerMultiChoice(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.MULTI_CHOICE);
    }

    public static synchronized void configureMultiChoice(String id, boolean rebootApp) {
        ItemBuilder item = requireItem(id, ItemType.MULTI_CHOICE);
        requireUnconfigured(item);
        item.rebootApp = rebootApp;
        item.configured = true;
    }

    public static synchronized void registerChoiceOption(
            String settingId,
            String optionId,
            String titleResourceName,
            boolean selectedByDefault
    ) {
        requireMutable();
        NodeBuilder node = NODES.get(settingId);
        if (!(node instanceof ItemBuilder item) || (item.type != ItemType.SINGLE_CHOICE && item.type != ItemType.MULTI_CHOICE)) {
            throw failure("Unknown X-Lite choice setting: " + settingId);
        }
        if (item.options.containsKey(optionId)) {
            throw failure("Duplicate choice option for " + settingId + ": " + optionId);
        }
        item.options.put(
                Objects.requireNonNull(optionId),
                new ChoiceBuilder(optionId, titleResourceName, selectedByDefault)
        );
    }

    public static synchronized void registerAction(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.ACTION);
    }

    public static synchronized void configureAction(String id, String handlerClassDescriptor) {
        ItemBuilder item = requireItem(id, ItemType.ACTION);
        requireUnconfigured(item);
        validateClassDescriptor(id, handlerClassDescriptor);
        item.handlerClassDescriptor = handlerClassDescriptor;
        item.configured = true;
    }

    public static synchronized void registerCustomScreen(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order
    ) {
        registerItem(parentId, id, titleResourceName, summaryResourceName, order, ItemType.CUSTOM_SCREEN);
    }

    public static synchronized void configureCustomScreen(String id, String fragmentClassDescriptor) {
        ItemBuilder item = requireItem(id, ItemType.CUSTOM_SCREEN);
        requireUnconfigured(item);
        validateClassDescriptor(id, fragmentClassDescriptor);
        item.fragmentClassDescriptor = fragmentClassDescriptor;
        item.configured = true;
    }

    /** Patches inject contribution registration calls at the start of this method. */
    public static synchronized void load() {
        if (frozen) return;

        Object register0 = null;
        Object register1 = null;
        Object register2 = null;
        Object register3 = null;
        Object register4 = null;
        Object register5 = null;
        reserveInjectionRegisters(register0, register1, register2, register3, register4, register5);

        try {
            BuiltInSettings.register();
            freeze();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to initialize X-Lite settings", exception);
            throw exception;
        }
    }

    public static synchronized boolean isLoaded() {
        return frozen;
    }

    public static synchronized List<SettingsNode.Category> catalog() {
        requireFrozen();
        return catalog;
    }

    public static synchronized SettingsNode.Group getGroup(String id) {
        requireFrozen();
        for (SettingsNode.Category category : catalog) {
            SettingsNode.Group match = findGroup(category, id);
            if (match != null) return match;
        }
        throw failure("Unknown X-Lite settings group: " + id);
    }

    public static synchronized boolean getBoolean(String key) {
        Setting<?> setting = requireSetting(key);
        if (!(setting instanceof BooleanSetting booleanSetting)) {
            throw failure("X-Lite setting is not boolean: " + key);
        }
        return booleanSetting.get();
    }

    public static synchronized boolean getBooleanOrDefault(String key, boolean defaultValue) {
        Setting<?> setting = SETTINGS.get(key);
        if (setting == null) return defaultValue;
        if (!(setting instanceof BooleanSetting booleanSetting)) {
            throw failure("X-Lite setting is not boolean: " + key);
        }
        return booleanSetting.get();
    }

    public static synchronized String getString(String key) {
        Setting<?> setting = requireSetting(key);
        if (!(setting instanceof StringSetting stringSetting)) {
            throw failure("X-Lite setting is not a string: " + key);
        }
        return stringSetting.get();
    }

    public static synchronized Set<String> getStringSet(String key) {
        Setting<?> setting = requireSetting(key);
        if (!(setting instanceof StringSetSetting stringSetSetting)) {
            throw failure("X-Lite setting is not a string set: " + key);
        }
        return stringSetSetting.get();
    }

    private static void registerGroupInternal(
            @Nullable String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            @Nullable String iconResourceName,
            int order,
            boolean category
    ) {
        requireMutable();
        NodeBuilder existing = NODES.get(id);
        if (existing != null) {
            if (!(existing instanceof GroupBuilder group)
                    || !group.matches(
                            parentId,
                            titleResourceName,
                            summaryResourceName,
                            iconResourceName,
                            order,
                            category
                    )) {
                throw failure("Conflicting X-Lite settings group: " + id);
            }
            return;
        }

        if (category && parentId != null) {
            throw failure("X-Lite settings category cannot have a parent: " + id);
        }
        if (!category && parentId == null) {
            throw failure("X-Lite settings group must have a parent: " + id);
        }

        GroupBuilder group = new GroupBuilder(
                parentId,
                id,
                titleResourceName,
                summaryResourceName,
                iconResourceName,
                order,
                category
        );
        NODES.put(id, group);
        if (category) {
            CATEGORIES.put(id, group);
            return;
        }
        requireGroup(parentId).children.add(group);
    }

    private static void registerItem(
            String parentId,
            String id,
            String titleResourceName,
            @Nullable String summaryResourceName,
            int order,
            ItemType type
    ) {
        requireMutable();
        if (NODES.containsKey(id)) {
            throw failure("Duplicate X-Lite setting ID: " + id);
        }

        ItemBuilder item = new ItemBuilder(
                parentId,
                id,
                titleResourceName,
                summaryResourceName,
                order,
                type
        );
        NODES.put(id, item);
        requireGroup(parentId).children.add(item);
    }

    private static void freeze() {
        requireMutable();
        if (CATEGORIES.isEmpty()) {
            throw failure("X-Lite settings registry has no contributed categories");
        }

        CATEGORIES.values().forEach(SettingsRegistry::validateGroup);

        List<SettingsNode.Category> categories = new ArrayList<>();
        CATEGORIES.values().stream()
                .filter(category -> !category.children.isEmpty())
                .sorted(NODE_COMPARATOR)
                .forEach(category -> categories.add(buildCategory(category)));
        if (categories.isEmpty()) {
            throw failure("X-Lite settings registry has no contributed settings");
        }

        catalog = Collections.unmodifiableList(categories);
        frozen = true;
    }

    private static void validateGroup(GroupBuilder group) {
        validateStringResource(group.titleResourceName);
        validateStringResource(group.summaryResourceName);
        validateDrawableResource(group.iconResourceName);
        if (group.children.isEmpty()) {
            throw failure("Empty X-Lite settings group: " + group.id);
        }

        for (NodeBuilder child : group.children) {
            if (child instanceof GroupBuilder childGroup) {
                validateGroup(childGroup);
                continue;
            }
            validateItem((ItemBuilder) child);
        }
    }

    private static void validateItem(ItemBuilder item) {
        if (!item.configured) {
            throw failure("Incomplete X-Lite setting definition: " + item.id);
        }
        validateStringResource(item.titleResourceName);
        validateStringResource(item.summaryResourceName);
        if (item.type != ItemType.SINGLE_CHOICE && item.type != ItemType.MULTI_CHOICE) return;
        if (item.options.isEmpty()) {
            throw failure("Choice setting has no options: " + item.id);
        }
        item.options.values().forEach(option -> validateStringResource(option.titleResourceName));
    }

    private static SettingsNode.Category buildCategory(GroupBuilder group) {
        return new SettingsNode.Category(
                group.id,
                stringRef(group.titleResourceName),
                stringRefOrNull(group.summaryResourceName),
                group.iconResourceName,
                group.order,
                buildChildren(group)
        );
    }

    private static SettingsNode.Group buildGroup(GroupBuilder group) {
        return new SettingsNode.Group(
                group.id,
                stringRef(group.titleResourceName),
                stringRefOrNull(group.summaryResourceName),
                group.iconResourceName,
                group.order,
                buildChildren(group)
        );
    }

    private static List<SettingsNode> buildChildren(GroupBuilder group) {
        List<SettingsNode> children = new ArrayList<>();
        group.children.stream().sorted(NODE_COMPARATOR).forEach(child -> {
            if (child instanceof GroupBuilder childGroup) {
                children.add(buildGroup(childGroup));
                return;
            }
            children.add(buildItem((ItemBuilder) child));
        });
        return children;
    }

    private static SettingsNode.Item buildItem(ItemBuilder item) {
        StringRef title = stringRef(item.titleResourceName);
        StringRef summary = stringRefOrNull(item.summaryResourceName);
        return switch (item.type) {
            case TOGGLE -> {
                BooleanSetting setting = new BooleanSetting(
                        item.id,
                        (Boolean) item.defaultValue,
                        item.rebootApp
                );
                SETTINGS.put(item.id, setting);
                yield new SettingsNode.Toggle(item.id, title, summary, item.order, setting);
            }
            case TEXT_INPUT -> {
                StringSetting setting = new StringSetting(
                        item.id,
                        (String) item.defaultValue,
                        item.rebootApp
                );
                SETTINGS.put(item.id, setting);
                yield new SettingsNode.TextInput(
                        item.id,
                        title,
                        summary,
                        item.order,
                        setting,
                        item.inputKind
                );
            }
            case SINGLE_CHOICE -> {
                List<SettingsNode.ChoiceOption> options = new ArrayList<>();
                item.options.values().forEach(option -> {
                    options.add(new SettingsNode.ChoiceOption(
                            option.id,
                            stringRef(option.titleResourceName)
                    ));
                });
                StringSetting setting = new StringSetting(
                        item.id,
                        (String) item.defaultValue,
                        item.rebootApp
                );
                SETTINGS.put(item.id, setting);
                yield new SettingsNode.SingleChoice(
                        item.id,
                        title,
                        summary,
                        item.order,
                        setting,
                        options
                );
            }
            case MULTI_CHOICE -> {
                Set<String> defaults = new LinkedHashSet<>();
                List<SettingsNode.ChoiceOption> options = new ArrayList<>();
                item.options.values().forEach(option -> {
                    options.add(new SettingsNode.ChoiceOption(
                            option.id,
                            stringRef(option.titleResourceName)
                    ));
                    if (option.selectedByDefault) defaults.add(option.id);
                });
                StringSetSetting setting = new StringSetSetting(item.id, defaults, item.rebootApp);
                SETTINGS.put(item.id, setting);
                yield new SettingsNode.MultiChoice(
                        item.id,
                        title,
                        summary,
                        item.order,
                        setting,
                        options
                );
            }
            case ACTION -> new SettingsNode.Action(
                    item.id,
                    title,
                    summary,
                    item.order,
                    item.handlerClassDescriptor
            );
            case CUSTOM_SCREEN -> new SettingsNode.CustomScreen(
                    item.id,
                    title,
                    summary,
                    item.order,
                    item.fragmentClassDescriptor
            );
        };
    }

    private static void validateStringResource(@Nullable String resourceName) {
        if (resourceName == null) return;
        ResourceUtils.getIdentifierOrThrow(ResourceType.STRING, resourceName);
    }

    private static void validateDrawableResource(@Nullable String resourceName) {
        if (resourceName == null) return;
        ResourceUtils.getIdentifierOrThrow(ResourceType.DRAWABLE, resourceName);
    }

    private static StringRef stringRef(String resourceName) {
        return StringRef.sfc(resourceName);
    }

    @Nullable
    private static StringRef stringRefOrNull(@Nullable String resourceName) {
        return resourceName == null ? null : stringRef(resourceName);
    }

    @Nullable
    private static SettingsNode.Group findGroup(SettingsNode.Group group, String id) {
        if (group.id.equals(id)) return group;
        for (SettingsNode child : group.children) {
            if (!(child instanceof SettingsNode.Group childGroup)) continue;
            SettingsNode.Group match = findGroup(childGroup, id);
            if (match != null) return match;
        }
        return null;
    }

    private static GroupBuilder requireGroup(String id) {
        NodeBuilder node = NODES.get(id);
        if (node instanceof GroupBuilder group) return group;
        throw failure("Unknown X-Lite settings parent group: " + id);
    }

    private static ItemBuilder requireItem(String id, ItemType type) {
        requireMutable();
        NodeBuilder node = NODES.get(id);
        if (!(node instanceof ItemBuilder item) || item.type != type) {
            throw failure("Unknown X-Lite " + type + " setting: " + id);
        }
        return item;
    }

    private static Setting<?> requireSetting(String key) {
        requireFrozen();
        Setting<?> setting = SETTINGS.get(key);
        if (setting == null) throw failure("Unknown X-Lite setting: " + key);
        return setting;
    }

    private static void requireUnconfigured(ItemBuilder item) {
        if (item.configured) throw failure("X-Lite setting configured twice: " + item.id);
    }

    private static void validateClassDescriptor(String id, String descriptor) {
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) return;
        throw failure("Invalid class descriptor for " + id + ": " + descriptor);
    }

    private static void requireMutable() {
        if (frozen) throw failure("X-Lite settings registry is frozen");
    }

    private static void requireFrozen() {
        if (!frozen) throw failure("X-Lite settings registry is not loaded");
    }

    private static IllegalStateException failure(String message) {
        Logger.printException(() -> message);
        return new IllegalStateException(message);
    }

    @SuppressWarnings("unused")
    private static void reserveInjectionRegisters(
            Object register0,
            Object register1,
            Object register2,
            Object register3,
            Object register4,
            Object register5
    ) {
    }

    private abstract static class NodeBuilder {
        @Nullable final String parentId;
        final String id;
        final String titleResourceName;
        @Nullable final String summaryResourceName;
        final int order;

        NodeBuilder(
                @Nullable String parentId,
                String id,
                String titleResourceName,
                @Nullable String summaryResourceName,
                int order
        ) {
            this.parentId = parentId;
            this.id = Objects.requireNonNull(id);
            this.titleResourceName = Objects.requireNonNull(titleResourceName);
            this.summaryResourceName = summaryResourceName;
            this.order = order;
        }
    }

    private static final class GroupBuilder extends NodeBuilder {
        final boolean category;
        final List<NodeBuilder> children = new ArrayList<>();
        @Nullable final String iconResourceName;

        GroupBuilder(
                @Nullable String parentId,
                String id,
                String titleResourceName,
                @Nullable String summaryResourceName,
                @Nullable String iconResourceName,
                int order,
                boolean category
        ) {
            super(parentId, id, titleResourceName, summaryResourceName, order);
            this.iconResourceName = iconResourceName;
            this.category = category;
        }

        boolean matches(
                @Nullable String candidateParentId,
                String candidateTitle,
                @Nullable String candidateSummary,
                @Nullable String candidateIcon,
                int candidateOrder,
                boolean candidateCategory
        ) {
            return Objects.equals(parentId, candidateParentId)
                    && titleResourceName.equals(candidateTitle)
                    && Objects.equals(summaryResourceName, candidateSummary)
                    && Objects.equals(iconResourceName, candidateIcon)
                    && order == candidateOrder
                    && category == candidateCategory;
        }
    }

    private static final class ItemBuilder extends NodeBuilder {
        final ItemType type;
        final Map<String, ChoiceBuilder> options = new LinkedHashMap<>();
        boolean configured;
        boolean rebootApp;
        @Nullable Object defaultValue;
        @Nullable SettingsNode.InputKind inputKind;
        @Nullable String handlerClassDescriptor;
        @Nullable String fragmentClassDescriptor;

        ItemBuilder(
                String parentId,
                String id,
                String titleResourceName,
                @Nullable String summaryResourceName,
                int order,
                ItemType type
        ) {
            super(parentId, id, titleResourceName, summaryResourceName, order);
            this.type = type;
        }
    }

    private static final class ChoiceBuilder {
        final String id;
        final String titleResourceName;
        final boolean selectedByDefault;

        ChoiceBuilder(String id, String titleResourceName, boolean selectedByDefault) {
            this.id = Objects.requireNonNull(id);
            this.titleResourceName = Objects.requireNonNull(titleResourceName);
            this.selectedByDefault = selectedByDefault;
        }
    }
}
