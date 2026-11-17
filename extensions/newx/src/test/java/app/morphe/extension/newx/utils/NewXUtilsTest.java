package app.morphe.extension.newx.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class NewXUtilsTest {
    @Test
    public void findsPresenterValueInPresenterHierarchy() throws IllegalAccessException {
        NewXUtils.PresenterData data =
                NewXUtils.findPresenterData(new Presenter(), String.class.getName());

        assertEquals("post", data.getValue());
    }

    @Test
    public void identifierFallbackCachesMissingAccessorsAndUsesLaterAccessor() throws Exception {
        assertEquals("id-123", NewXUtils.identifierToString(new IdentifierWithA()));

        Map<?, ?> cache = methodCache();
        Map<?, ?> methods = (Map<?, ?>) cache.get(IdentifierWithA.class);
        assertNotNull(methods);
        assertTrue(methods.containsKey("getValue"));
        assertTrue(methods.containsKey("getStr"));
        assertTrue(methods.containsKey("a"));
        assertNull(resolvedMethod(methods.get("getValue")));
        assertNull(resolvedMethod(methods.get("getStr")));
        assertNotNull(resolvedMethod(methods.get("a")));
    }

    @Test
    public void inheritedAccessorResolvesAndStrictLookupStillThrowsAfterCachedMiss()
            throws Exception {
        assertEquals("inherited", NewXUtils.invokeIfPresent(new InheritedAccessor(), "getValue"));

        MissingAccessor target = new MissingAccessor();
        assertNull(NewXUtils.invokeIfPresent(target, "notPresent"));
        NoSuchMethodException error = assertThrows(
                NoSuchMethodException.class,
                () -> NewXUtils.invoke(target, "notPresent")
        );
        assertEquals(MissingAccessor.class.getName() + ".notPresent()", error.getMessage());
    }

    @Test
    public void invocationFailureDoesNotCacheMethodAsAbsent() {
        SometimesThrows target = new SometimesThrows();
        assertNull(NewXUtils.invokeIfPresent(target, "getValue"));
        assertEquals("recovered", NewXUtils.invokeIfPresent(target, "getValue"));
        assertEquals(2, target.calls.get());
    }

    @Test
    public void concurrentOptionalLookupsReusePositiveAndNegativeResolutions() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger calls = new AtomicInteger();
        try {
            Callable<Boolean> lookup = () -> {
                CountingAccessor target = new CountingAccessor(calls);
                for (int i = 0; i < 100; i++) {
                    if (!"value".equals(NewXUtils.invokeIfPresent(target, "getValue"))) return false;
                    if (NewXUtils.invokeIfPresent(target, "missing") != null) return false;
                }
                return true;
            };
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(lookup));
            for (Future<Boolean> result : results) assertTrue(result.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(800, calls.get());
        Map<?, ?> methods = (Map<?, ?>) methodCache().get(CountingAccessor.class);
        assertNotNull(methods);
        assertNull(resolvedMethod(methods.get("missing")));
        assertNotNull(resolvedMethod(methods.get("getValue")));
    }

    private static Map<?, ?> methodCache() throws Exception {
        Field field = NewXUtils.class.getDeclaredField("METHOD_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    private static Method resolvedMethod(Object resolution) throws Exception {
        assertNotNull(resolution);
        Field field = resolution.getClass().getDeclaredField("method");
        field.setAccessible(true);
        return (Method) field.get(resolution);
    }

    public static final class IdentifierWithA {
        public String a() {
            return "id-123";
        }
    }

    public static class AccessorBase {
        public String getValue() {
            return "inherited";
        }
    }

    public static final class InheritedAccessor extends AccessorBase {
    }

    public static final class MissingAccessor {
    }

    public static final class SometimesThrows {
        private final AtomicInteger calls = new AtomicInteger();

        public String getValue() {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("first call");
            return "recovered";
        }
    }

    public static final class CountingAccessor {
        private final AtomicInteger calls;

        CountingAccessor(AtomicInteger calls) {
            this.calls = calls;
        }

        public String getValue() {
            calls.incrementAndGet();
            return "value";
        }
    }

    private static class BasePresenter {
        private final String post = "post";
    }

    private static final class Presenter extends BasePresenter {
    }
}
