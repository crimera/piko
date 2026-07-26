package app.morphe.extension.xlite.api;

import java.util.Objects;

/** Metadata for a top-level settings category. */
public final class SettingsCategory {
    private final String id;
    private final String titleResourceName;
    private final String summaryResourceName;
    private final int order;

    public SettingsCategory(
            String id,
            String titleResourceName,
            String summaryResourceName,
            int order
    ) {
        this.id = Objects.requireNonNull(id);
        this.titleResourceName = Objects.requireNonNull(titleResourceName);
        this.summaryResourceName = summaryResourceName;
        this.order = order;
    }

    public String getId() { return id; }
    public String getTitleResourceName() { return titleResourceName; }
    public String getSummaryResourceName() { return summaryResourceName; }
    public int getOrder() { return order; }
}
