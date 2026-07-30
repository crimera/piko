package app.morphe.extension.xlite.featureswitches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class FeatureSwitchStoreTest {
    @Test
    public void overrideWinsAndPersistsWithItsType() {
        MemoryPersistence persistence = new MemoryPersistence();
        FeatureSwitchStore first = new FeatureSwitchStore(persistence);

        assertEquals(
                Integer.valueOf(3),
                first.resolve("media_limit", FeatureSwitchStore.ValueType.INT, 3, Integer.class)
        );
        first.setOverride("media_limit", FeatureSwitchStore.ValueType.INT, 7);
        assertEquals(
                Integer.valueOf(7),
                first.resolve("media_limit", FeatureSwitchStore.ValueType.INT, 3, Integer.class)
        );

        FeatureSwitchStore restored = new FeatureSwitchStore(persistence);
        assertEquals(
                Integer.valueOf(7),
                restored.resolve("media_limit", FeatureSwitchStore.ValueType.INT, 3, Integer.class)
        );
        FeatureSwitchStore.Entry entry = restored.snapshot("media").get(0);
        assertTrue(entry.isOverridden());
        assertEquals(3, entry.getObservedValue());
        assertEquals(7, entry.getEffectiveValue());
    }

    @Test
    public void supportsEveryEditableValueType() {
        MemoryPersistence persistence = new MemoryPersistence();
        FeatureSwitchStore store = new FeatureSwitchStore(persistence);
        store.setOverride("boolean", FeatureSwitchStore.ValueType.BOOLEAN, true);
        store.setOverride("int", FeatureSwitchStore.ValueType.INT, 4);
        store.setOverride("long", FeatureSwitchStore.ValueType.LONG, 5L);
        store.setOverride("float", FeatureSwitchStore.ValueType.FLOAT, 1.5f);
        store.setOverride("double", FeatureSwitchStore.ValueType.DOUBLE, 2.5d);
        store.setOverride("string", FeatureSwitchStore.ValueType.STRING, "value");
        store.setOverride("list", FeatureSwitchStore.ValueType.STRING_LIST, List.of("a", "b"));

        FeatureSwitchStore restored = new FeatureSwitchStore(persistence);
        assertEquals(true, restored.resolve(
                "boolean", FeatureSwitchStore.ValueType.BOOLEAN, false, Boolean.class));
        assertEquals(Integer.valueOf(4), restored.resolve(
                "int", FeatureSwitchStore.ValueType.INT, 0, Integer.class));
        assertEquals(Long.valueOf(5L), restored.resolve(
                "long", FeatureSwitchStore.ValueType.LONG, 0L, Long.class));
        assertEquals(Float.valueOf(1.5f), restored.resolve(
                "float", FeatureSwitchStore.ValueType.FLOAT, 0f, Float.class));
        assertEquals(Double.valueOf(2.5d), restored.resolve(
                "double", FeatureSwitchStore.ValueType.DOUBLE, 0d, Double.class));
        assertEquals("value", restored.resolve(
                "string", FeatureSwitchStore.ValueType.STRING, null, String.class));
        assertEquals(List.of("a", "b"), restored.resolve(
                "list", FeatureSwitchStore.ValueType.STRING_LIST, null, List.class));
    }

    @Test
    public void customAndDetectedOverridesSortBeforeUnmodifiedSwitches() {
        FeatureSwitchStore store = new FeatureSwitchStore(new MemoryPersistence());
        store.resolve("z_detected", FeatureSwitchStore.ValueType.BOOLEAN, false, Boolean.class);
        store.resolve("y_overridden", FeatureSwitchStore.ValueType.INT, 1, Integer.class);
        store.setOverride("y_overridden", FeatureSwitchStore.ValueType.INT, 2);
        store.setOverride("a_custom", FeatureSwitchStore.ValueType.STRING, "enabled");

        List<FeatureSwitchStore.Entry> entries = store.snapshot("");
        assertEquals(List.of("a_custom", "y_overridden", "z_detected"), entries.stream()
                .map(FeatureSwitchStore.Entry::getKey)
                .toList());
        assertTrue(entries.get(0).isOverridden());
        assertTrue(entries.get(1).isOverridden());
        assertFalse(entries.get(2).isOverridden());
        assertTrue(store.hasEntry("a_custom"));
        assertEquals("a_custom", store.snapshot("custom").get(0).getKey());
    }

    @Test
    public void mismatchedOverrideTypeDoesNotCorruptGetterValue() {
        FeatureSwitchStore store = new FeatureSwitchStore(new MemoryPersistence());
        store.setOverride("flag", FeatureSwitchStore.ValueType.STRING, "wrong type");

        assertFalse(store.resolve(
                "flag", FeatureSwitchStore.ValueType.BOOLEAN, false, Boolean.class));
        FeatureSwitchStore.Entry entry = store.snapshot("").get(0);
        assertFalse(entry.isOverridden());
        assertEquals(FeatureSwitchStore.ValueType.BOOLEAN, entry.getType());
    }

    @Test
    public void listValuesAreDefensivelyCopied() {
        FeatureSwitchStore store = new FeatureSwitchStore(new MemoryPersistence());
        List<String> override = new ArrayList<>(List.of("first"));
        store.setOverride("list", FeatureSwitchStore.ValueType.STRING_LIST, override);
        override.add("mutated");

        List<?> resolved = store.resolve(
                "list",
                FeatureSwitchStore.ValueType.STRING_LIST,
                List.of("default"),
                List.class
        );
        assertEquals(List.of("first"), resolved);
    }

    @Test
    public void exportedOverridesCanReplaceAnotherStoresOverrides() throws Exception {
        FeatureSwitchStore source = new FeatureSwitchStore(new MemoryPersistence());
        source.setOverride("enabled", FeatureSwitchStore.ValueType.BOOLEAN, true);
        source.setOverride("limit", FeatureSwitchStore.ValueType.INT, 8);

        FeatureSwitchStore target = new FeatureSwitchStore(new MemoryPersistence());
        target.setOverride("stale", FeatureSwitchStore.ValueType.STRING, "remove me");
        target.importOverrides(source.exportOverrides());

        assertFalse(target.hasEntry("stale"));
        assertTrue(target.resolve(
                "enabled", FeatureSwitchStore.ValueType.BOOLEAN, false, Boolean.class));
        assertEquals(Integer.valueOf(8), target.resolve(
                "limit", FeatureSwitchStore.ValueType.INT, 0, Integer.class));
    }

    @Test
    public void invalidImportDoesNotReplaceCurrentOverrides() {
        FeatureSwitchStore store = new FeatureSwitchStore(new MemoryPersistence());
        store.setOverride("enabled", FeatureSwitchStore.ValueType.BOOLEAN, true);

        assertThrows(org.json.JSONException.class, () -> store.importOverrides("not json"));
        assertTrue(store.resolve(
                "enabled", FeatureSwitchStore.ValueType.BOOLEAN, false, Boolean.class));
    }

    private static final class MemoryPersistence implements FeatureSwitchStore.Persistence {
        private String value = "";

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String updatedValue) {
            value = updatedValue;
        }
    }
}
