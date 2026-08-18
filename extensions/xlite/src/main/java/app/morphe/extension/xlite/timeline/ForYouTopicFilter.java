package app.morphe.extension.xlite.timeline;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ForYouTopicFilter {
    private ForYouTopicFilter() {
    }

    @Nullable
    public static List<String> resolveForYouTopicIds(
            @Nullable List<String> originalTopicIds,
            boolean enabled,
            @Nullable Set<String> configuredTopicIds
    ) {
        if (!enabled || configuredTopicIds == null || configuredTopicIds.isEmpty()) {
            return originalTopicIds;
        }
        return new ArrayList<>(configuredTopicIds);
    }
}
