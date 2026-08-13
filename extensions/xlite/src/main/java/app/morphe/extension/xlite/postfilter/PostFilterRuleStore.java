package app.morphe.extension.xlite.postfilter;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;

public final class PostFilterRuleStore {
    static final String ENABLED_KEY = "xlite.content.post_filtering.enabled";
    static final String STRUCTURED_RULES_KEY = "xlite.content.post_filtering.rules";
    private static final int SCHEMA_VERSION = 1;

    public enum ValidationError {
        BLANK_PHRASE,
        NO_SCOPE,
        DUPLICATE_PHRASE,
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final ValidationError error;

        ValidationException(ValidationError error) {
            super(error.name());
            this.error = error;
        }

        public ValidationError getError() {
            return error;
        }
    }

    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.emptyList());
        private final List<PostFilterRule> rules;
        private final List<String> contentPhrases;
        private final List<String> usernamePhrases;

        private Snapshot(List<PostFilterRule> source) {
            rules = Collections.unmodifiableList(new ArrayList<>(source));
            List<String> content = new ArrayList<>();
            List<String> usernames = new ArrayList<>();
            for (PostFilterRule rule : rules) {
                if (!rule.isEnabled()) continue;
                if (rule.matchesContent()) content.add(rule.getNormalizedPhrase());
                if (rule.matchesUsernames()) usernames.add(rule.getNormalizedPhrase());
            }
            contentPhrases = Collections.unmodifiableList(content);
            usernamePhrases = Collections.unmodifiableList(usernames);
        }

        public List<PostFilterRule> getRules() {
            return rules;
        }

        List<String> contentPhrases() {
            return contentPhrases;
        }

        List<String> usernamePhrases() {
            return usernamePhrases;
        }

        public boolean hasEnabledRules() {
            return !contentPhrases.isEmpty() || !usernamePhrases.isEmpty();
        }
    }

    interface Persistence {
        @Nullable String readStructuredRules();
        boolean readEnabled();
        void writeStructuredRules(String value);
        void writeEnabled(boolean enabled);
    }

    private final Object lock = new Object();
    private final Persistence persistence;
    private volatile Snapshot cachedSnapshot;
    private volatile boolean cachedEnabled;
    private volatile boolean loaded;

    PostFilterRuleStore(Persistence persistence) {
        this.persistence = persistence;
    }

    public static PostFilterRuleStore shared() {
        return Holder.INSTANCE;
    }

    public static Snapshot snapshotOf(List<PostFilterRule> rules) {
        return rules.isEmpty() ? Snapshot.EMPTY : new Snapshot(rules);
    }

    public Snapshot snapshot() {
        ensureLoaded();
        return cachedSnapshot;
    }

    public boolean isEnabled() {
        ensureLoaded();
        return cachedEnabled;
    }

    public void setEnabled(boolean enabled) {
        ensureLoaded();
        synchronized (lock) {
            if (cachedEnabled == enabled) return;
            persistence.writeEnabled(enabled);
            cachedEnabled = enabled;
        }
    }

    public PostFilterRule add(String phrase, boolean matchContent, boolean matchUsernames) {
        return save(null, phrase, matchContent, matchUsernames);
    }

    public PostFilterRule update(
            String id,
            String phrase,
            boolean matchContent,
            boolean matchUsernames
    ) {
        return save(id, phrase, matchContent, matchUsernames);
    }

    public void remove(String id) {
        ensureLoaded();
        synchronized (lock) {
            List<PostFilterRule> updated = new ArrayList<>(cachedSnapshot.rules);
            if (!updated.removeIf(rule -> rule.getId().equals(id))) return;
            publish(updated);
        }
    }

    public void setRuleEnabled(String id, boolean enabled) {
        ensureLoaded();
        synchronized (lock) {
            List<PostFilterRule> updated = new ArrayList<>(cachedSnapshot.rules);
            for (int index = 0; index < updated.size(); index++) {
                PostFilterRule rule = updated.get(index);
                if (!rule.getId().equals(id)) continue;
                if (rule.isEnabled() == enabled) return;
                updated.set(index, rule.withEnabled(enabled));
                publish(updated);
                return;
            }
        }
    }

    private PostFilterRule save(
            @Nullable String id,
            String phrase,
            boolean matchContent,
            boolean matchUsernames
    ) {
        ensureLoaded();
        String trimmedPhrase = phrase == null ? "" : phrase.trim();
        synchronized (lock) {
            validate(trimmedPhrase, matchContent, matchUsernames, id);
            List<PostFilterRule> updated = new ArrayList<>(cachedSnapshot.rules);
            int existingIndex = id == null ? -1 : indexOf(updated, id);
            if (id != null && existingIndex < 0) {
                throw new IllegalArgumentException("Unknown post-filter rule: " + id);
            }
            boolean enabled = existingIndex < 0 || updated.get(existingIndex).isEnabled();
            PostFilterRule saved = new PostFilterRule(
                    id == null ? UUID.randomUUID().toString() : id,
                    trimmedPhrase,
                    matchContent,
                    matchUsernames,
                    enabled
            );
            if (existingIndex < 0) {
                updated.add(saved);
            } else {
                updated.set(existingIndex, saved);
            }
            publish(updated);
            return saved;
        }
    }

    private void validate(
            String phrase,
            boolean matchContent,
            boolean matchUsernames,
            @Nullable String editedId
    ) {
        if (PostFilterRule.normalize(phrase).isEmpty()) {
            throw new ValidationException(ValidationError.BLANK_PHRASE);
        }
        if (!matchContent && !matchUsernames) {
            throw new ValidationException(ValidationError.NO_SCOPE);
        }
        String normalized = PostFilterRule.normalize(phrase);
        for (PostFilterRule rule : cachedSnapshot.rules) {
            if (rule.getId().equals(editedId)) continue;
            if (rule.getNormalizedPhrase().equals(normalized)) {
                throw new ValidationException(ValidationError.DUPLICATE_PHRASE);
            }
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            cachedEnabled = persistence.readEnabled();
            cachedSnapshot = loadRules();
            loaded = true;
        }
    }

    private Snapshot loadRules() {
        String structured = persistence.readStructuredRules();
        if (structured != null && !structured.isEmpty()) {
            try {
                return new Snapshot(deserialize(structured));
            } catch (JSONException | IllegalArgumentException exception) {
                Logger.printException(() -> "Failed to read X-Lite post-filter rules", exception);
                return Snapshot.EMPTY;
            }
        }

        return Snapshot.EMPTY;
    }

    private void publish(List<PostFilterRule> rules) {
        String serialized = serialize(rules);
        persistence.writeStructuredRules(serialized);
        cachedSnapshot = rules.isEmpty() ? Snapshot.EMPTY : new Snapshot(rules);
    }

    static String serialize(List<PostFilterRule> rules) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            JSONArray serializedRules = new JSONArray();
            for (PostFilterRule rule : rules) {
                JSONObject serializedRule = new JSONObject();
                serializedRule.put("id", rule.getId());
                serializedRule.put("phrase", rule.getPhrase());
                serializedRule.put("content", rule.matchesContent());
                serializedRule.put("usernames", rule.matchesUsernames());
                serializedRule.put("enabled", rule.isEnabled());
                serializedRules.put(serializedRule);
            }
            root.put("rules", serializedRules);
            return root.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not serialize post-filter rules", exception);
        }
    }

    static List<PostFilterRule> deserialize(String serialized) throws JSONException {
        JSONObject root = new JSONObject(serialized);
        int version = root.getInt("version");
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported post-filter schema version: " + version);
        }

        JSONArray serializedRules = root.getJSONArray("rules");
        List<PostFilterRule> rules = new ArrayList<>();
        Set<String> normalizedPhrases = new LinkedHashSet<>();
        for (int index = 0; index < serializedRules.length(); index++) {
            JSONObject value = serializedRules.getJSONObject(index);
            PostFilterRule rule = new PostFilterRule(
                    value.getString("id"),
                    value.getString("phrase").trim(),
                    value.getBoolean("content"),
                    value.getBoolean("usernames"),
                    value.getBoolean("enabled")
            );
            if (rule.getId().isEmpty() || rule.getNormalizedPhrase().isEmpty()) {
                throw new IllegalArgumentException("Invalid post-filter rule");
            }
            if (!rule.matchesContent() && !rule.matchesUsernames()) {
                throw new IllegalArgumentException("Post-filter rule has no scope");
            }
            if (!normalizedPhrases.add(rule.getNormalizedPhrase())) {
                throw new IllegalArgumentException("Duplicate post-filter phrase");
            }
            rules.add(rule);
        }
        return Collections.unmodifiableList(rules);
    }

    private static int indexOf(List<PostFilterRule> rules, String id) {
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).getId().equals(id)) return index;
        }
        return -1;
    }

    private static final class Holder {
        private static final PostFilterRuleStore INSTANCE =
                new PostFilterRuleStore(new SharedPreferencesPersistence());
    }

    private static final class SharedPreferencesPersistence implements Persistence {
        private static final BooleanSetting ENABLED_SETTING = new BooleanSetting(ENABLED_KEY, true);
        private static final StringSetting STRUCTURED_RULES_SETTING =
                new StringSetting(STRUCTURED_RULES_KEY, "");

        @Override
        public String readStructuredRules() {
            String serializedRules = STRUCTURED_RULES_SETTING.get();
            return serializedRules.isEmpty() ? null : serializedRules;
        }

        @Override
        public boolean readEnabled() {
            return ENABLED_SETTING.get();
        }

        @Override
        public void writeStructuredRules(String value) {
            STRUCTURED_RULES_SETTING.save(value);
        }

        @Override
        public void writeEnabled(boolean enabled) {
            ENABLED_SETTING.save(enabled);
        }
    }
}
