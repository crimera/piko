package app.morphe.extension.xlite.settings;

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

    SettingsNode(String id, StringRef title, @Nullable StringRef summary, int order) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.summary = summary;
        this.order = order;
    }

    public static class Group extends SettingsNode {
        public final List<SettingsNode> children;

        Group(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                List<SettingsNode> children
        ) {
            super(id, title, summary, order);
            this.children = Collections.unmodifiableList(List.copyOf(children));
        }
    }

    public static final class Category extends Group {
        Category(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                List<SettingsNode> children
        ) {
            super(id, title, summary, order, children);
        }
    }

    public abstract static class Item extends SettingsNode {
        Item(String id, StringRef title, @Nullable StringRef summary, int order) {
            super(id, title, summary, order);
        }
    }

    public abstract static class ValueItem<T> extends Item {
        public final Setting<T> setting;

        ValueItem(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                Setting<T> setting
        ) {
            super(id, title, summary, order);
            this.setting = Objects.requireNonNull(setting);
        }
    }

    public static final class Toggle extends ValueItem<Boolean> {
        Toggle(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                BooleanSetting setting
        ) {
            super(id, title, summary, order, setting);
        }
    }

    public enum InputKind {
        TEXT,
        MULTILINE,
    }

    public static final class TextInput extends ValueItem<String> {
        public final InputKind inputKind;

        TextInput(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                StringSetting setting,
                InputKind inputKind
        ) {
            super(id, title, summary, order, setting);
            this.inputKind = Objects.requireNonNull(inputKind);
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

    public static final class MultiChoice extends ValueItem<java.util.Set<String>> {
        public final List<ChoiceOption> options;

        MultiChoice(
                String id,
                StringRef title,
                @Nullable StringRef summary,
                int order,
                StringSetSetting setting,
                List<ChoiceOption> options
        ) {
            super(id, title, summary, order, setting);
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
                String handlerClassDescriptor
        ) {
            super(id, title, summary, order);
            this.handlerClassDescriptor = Objects.requireNonNull(handlerClassDescriptor);
        }
    }
}
