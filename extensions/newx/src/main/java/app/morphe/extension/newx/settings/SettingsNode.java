package app.morphe.extension.newx.settings;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;

public abstract class SettingsNode {
    public final String id;
    public final StringRef title;
    @Nullable public final StringRef summary;
    public final int order;
    public final boolean visible;

    SettingsNode(
            String id,
            StringRef title,
            @Nullable StringRef summary,
            int order,
            boolean visible
    ) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.summary = summary;
        this.order = order;
        this.visible = visible;
    }

    public static class Group extends SettingsNode {
        @Nullable public final String iconResourceName;
        public final List<SettingsNode> children;

        Group(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                @Nullable String iconResourceName,
                int order,
                List<SettingsNode> children
        ) {
            super(id, title, summary, order, true);
            this.iconResourceName = iconResourceName;
            this.children = Collections.unmodifiableList(List.copyOf(children));
        }
    }

    public static final class Category extends Group {
        Category(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                @Nullable String iconResourceName,
                int order,
                List<SettingsNode> children
        ) {
            super(id, title, summary, iconResourceName, order, children);
        }
    }

    public abstract static class Item extends SettingsNode {
        Item(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                boolean visible
        ) {
            super(id, title, summary, order, visible);
        }
    }

    public abstract static class ValueItem<T> extends Item {
        public final Setting<T> setting;

        ValueItem(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                Setting<T> setting,
                boolean visible
        ) {
            super(id, title, summary, order, visible);
            this.setting = Objects.requireNonNull(setting);
        }
    }

    public static final class Toggle extends ValueItem<Boolean> {
        Toggle(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                BooleanSetting setting,
                boolean visible
        ) {
            super(id, title, summary, order, setting, visible);
        }
    }

    public enum InputKind {
        TEXT,
        MULTILINE,
    }

    public static final class TextInput extends ValueItem<String> {
        public final InputKind inputKind;
        @Nullable public final String validatorClassDescriptor;

        TextInput(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                StringSetting setting,
                InputKind inputKind,
                @Nullable String validatorClassDescriptor,
                boolean visible
        ) {
            super(id, title, summary, order, setting, visible);
            this.inputKind = Objects.requireNonNull(inputKind);
            this.validatorClassDescriptor = validatorClassDescriptor;
        }
    }

    public static final class ChoiceOption {
        public final String id;
        public final StringRef title;

        ChoiceOption(String id, StringRef title) {
            this.id = Objects.requireNonNull(id);
            this.title = Objects.requireNonNull(title);
        }
    }

    public static final class SingleChoice extends ValueItem<String> {
        public final List<ChoiceOption> options;

        SingleChoice(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                StringSetting setting,
                List<ChoiceOption> options,
                boolean visible
        ) {
            super(id, title, summary, order, setting, visible);
            this.options = Collections.unmodifiableList(List.copyOf(options));
        }
    }

    public static final class MultiChoice extends ValueItem<java.util.Set<String>> {
        public final List<ChoiceOption> options;

        MultiChoice(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                StringSetSetting setting,
                List<ChoiceOption> options,
                boolean visible
        ) {
            super(id, title, summary, order, setting, visible);
            this.options = Collections.unmodifiableList(List.copyOf(options));
        }
    }

    public static final class Action extends Item {
        public final String handlerClassDescriptor;

        Action(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                String handlerClassDescriptor,
                boolean visible
        ) {
            super(id, title, summary, order, visible);
            this.handlerClassDescriptor = Objects.requireNonNull(handlerClassDescriptor);
        }
    }

    public static final class CustomScreen extends Item {
        @Nullable public final String iconResourceName;
        public final String fragmentClassDescriptor;

        CustomScreen(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                @Nullable String iconResourceName,
                String fragmentClassDescriptor
        ) {
            super(id, title, summary, order, true);
            this.iconResourceName = iconResourceName;
            this.fragmentClassDescriptor = Objects.requireNonNull(fragmentClassDescriptor);
        }
    }
}
