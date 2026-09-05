package app.morphe.extension.newx.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NewXUtilsTest {
    @Test
    public void findsPresenterValueInPresenterHierarchy() throws IllegalAccessException {
        NewXUtils.PresenterData data =
                NewXUtils.findPresenterData(new Presenter(), String.class.getName());

        assertEquals("post", data.getValue());
    }

    private static class BasePresenter {
        private final String post = "post";
    }

    private static final class Presenter extends BasePresenter {
    }
}
