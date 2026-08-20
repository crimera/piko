package app.morphe.extension.newx.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds the searchable view of the frozen NewX settings catalog. */
final class SettingsSearchIndex {
    private static final String HIERARCHY_SEPARATOR = " \u2192 ";
    private static List<Result> cachedResults;

    private SettingsSearchIndex() {
    }

    static synchronized List<Result> results() {
        if (cachedResults == null) {
            cachedResults = Collections.unmodifiableList(buildResults());
        }
        return cachedResults;
    }

    private static List<Result> buildResults() {
        List<Result> results = new ArrayList<>();
        for (SettingsNode.Category category : SettingsRegistry.catalog()) {
            collectChildren(
                    category.children,
                    category.title.toString(),
                    results
            );
        }
        return results;
    }

    private static void collectChildren(
            List<SettingsNode> children,
            String path,
            List<Result> results
    ) {
        for (SettingsNode child : children) {
            if (child instanceof SettingsNode.Group group) {
                collectChildren(
                        group.children,
                        appendPath(path, group.title.toString()),
                        results
                );
                continue;
            }
            if (child instanceof SettingsNode.Item item) {
                results.add(createResult(item, path));
            }
        }
    }

    private static Result createResult(SettingsNode.Item item, String path) {
        String title = item.title.toString();
        String summary = item.summary == null ? "" : item.summary.toString();
        StringBuilder keywords = new StringBuilder();
        appendSearchText(keywords, title);
        appendSearchText(keywords, summary);
        appendSearchText(keywords, path);
        appendSearchText(keywords, item.id);
        if (item instanceof SettingsNode.SingleChoice choice) {
            appendChoiceTitles(keywords, choice.options);
        } else if (item instanceof SettingsNode.MultiChoice choice) {
            appendChoiceTitles(keywords, choice.options);
        }
        return new Result(item, title, summary, path, keywords.toString());
    }

    private static void appendChoiceTitles(
            StringBuilder keywords,
            List<SettingsNode.ChoiceOption> options
    ) {
        for (SettingsNode.ChoiceOption option : options) {
            appendSearchText(keywords, option.title.toString());
        }
    }

    private static void appendSearchText(StringBuilder builder, String value) {
        if (value == null || value.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(value);
    }

    static String appendPath(String parent, String child) {
        if (parent == null || parent.isEmpty()) return child == null ? "" : child;
        if (child == null || child.isEmpty()) return parent;
        return parent + HIERARCHY_SEPARATOR + child;
    }

    static final class Result {
        final SettingsNode.Item item;
        final String title;
        final String summary;
        final String path;
        final String keywords;

        Result(
                SettingsNode.Item item,
                String title,
                String summary,
                String path,
                String keywords
        ) {
            this.item = item;
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
            this.path = path == null ? "" : path;
            this.keywords = keywords == null ? "" : keywords;
        }
    }
}
