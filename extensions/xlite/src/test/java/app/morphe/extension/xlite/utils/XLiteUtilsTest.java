package app.morphe.extension.xlite.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class XLiteUtilsTest {
    @Test
    public void findsPresenterValueInPresenterHierarchy() throws IllegalAccessException {
        XLiteUtils.PresenterData data =
                XLiteUtils.findPresenterData(new Presenter(), String.class.getName());

        assertEquals("post", data.getValue());
    }

    private static class BasePresenter {
        private final String post = "post";
    }

    private static final class Presenter extends BasePresenter {
    }
}
