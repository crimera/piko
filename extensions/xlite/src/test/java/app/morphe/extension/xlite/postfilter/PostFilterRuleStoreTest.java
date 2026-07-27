package app.morphe.extension.xlite.postfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public class PostFilterRuleStoreTest {
    @Test
    public void serializationRoundTripsVersionedRules() throws JSONException {
        List<PostFilterRule> source = List.of(
                new PostFilterRule("one", " First phrase ", true, false, true),
                new PostFilterRule("two", "@User", false, true, false)
        );

        String serialized = PostFilterRuleStore.serialize(source);
        List<PostFilterRule> restored = PostFilterRuleStore.deserialize(serialized);

        assertTrue(serialized.contains("\"version\":1"));
        assertEquals(2, restored.size());
        assertEquals("First phrase", restored.get(0).getPhrase());
        assertTrue(restored.get(0).matchesContent());
        assertFalse(restored.get(0).matchesUsernames());
        assertFalse(restored.get(1).isEnabled());
    }

    @Test
    public void rejectsUnknownSchemaVersionAndInvalidScopes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PostFilterRuleStore.deserialize("{\"version\":2,\"rules\":[]}")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PostFilterRuleStore.deserialize(
                        "{\"version\":1,\"rules\":[{" +
                                "\"id\":\"1\",\"phrase\":\"word\"," +
                                "\"content\":false,\"usernames\":false,\"enabled\":true}]}"
                )
        );
    }

    @Test
    public void newRulesDefaultToCallerSelectedScopesAndSnapshotsAreImmutable() {
        FakePersistence persistence = new FakePersistence();
        PostFilterRuleStore store = new PostFilterRuleStore(persistence);

        PostFilterRule rule = store.add("phrase", true, false);

        assertTrue(rule.matchesContent());
        assertFalse(rule.matchesUsernames());
        assertThrows(
                UnsupportedOperationException.class,
                () -> store.snapshot().getRules().clear()
        );
    }

    @Test
    public void validatesBlankMissingScopeAndNormalizedDuplicates() {
        PostFilterRuleStore store = new PostFilterRuleStore(new FakePersistence());
        assertValidation(
                PostFilterRuleStore.ValidationError.BLANK_PHRASE,
                () -> store.add("  ", true, false)
        );
        assertValidation(
                PostFilterRuleStore.ValidationError.NO_SCOPE,
                () -> store.add("phrase", false, false)
        );
        store.add("ＦＩＲＳＴ", true, false);
        assertValidation(
                PostFilterRuleStore.ValidationError.DUPLICATE_PHRASE,
                () -> store.add("first", false, true)
        );
    }

    @Test
    public void editsRemovalsAndEnableChangesPublishFreshSnapshots() {
        FakePersistence persistence = new FakePersistence();
        PostFilterRuleStore store = new PostFilterRuleStore(persistence);
        PostFilterRule added = store.add("first", true, false);
        PostFilterRuleStore.Snapshot beforeEdit = store.snapshot();

        store.update(added.getId(), "second", false, true);
        PostFilterRule edited = store.snapshot().getRules().get(0);
        assertEquals("first", beforeEdit.getRules().get(0).getPhrase());
        assertEquals("second", edited.getPhrase());
        assertTrue(edited.matchesUsernames());

        store.setRuleEnabled(edited.getId(), false);
        assertFalse(store.snapshot().hasEnabledRules());
        store.remove(edited.getId());
        assertTrue(store.snapshot().getRules().isEmpty());
    }

    @Test
    public void masterEnablePersistsWithoutRestart() {
        FakePersistence persistence = new FakePersistence();
        PostFilterRuleStore store = new PostFilterRuleStore(persistence);
        assertTrue(store.isEnabled());

        store.setEnabled(false);

        assertFalse(store.isEnabled());
        assertFalse(persistence.enabled);
    }

    private static void assertValidation(
            PostFilterRuleStore.ValidationError expected,
            Runnable operation
    ) {
        PostFilterRuleStore.ValidationException exception = assertThrows(
                PostFilterRuleStore.ValidationException.class,
                operation::run
        );
        assertEquals(expected, exception.getError());
    }

    private static final class FakePersistence implements PostFilterRuleStore.Persistence {
        String structured;
        boolean enabled = true;

        @Override public String readStructuredRules() { return structured; }
        @Override public boolean readEnabled() { return enabled; }

        @Override
        public void writeStructuredRules(String value) {
            structured = value;
        }

        @Override public void writeEnabled(boolean value) { enabled = value; }
    }
}
