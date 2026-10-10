package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Guards the window-rect shape gate behind "Post is no longer rendered".
 *
 * <p>12.33.0-prod.01 renamed Compose's IntRect from {@code androidx.compose.ui.unit.k} to {@code unit.l}
 * and gave {@code unit.k} a single long field. The handler compared class names, so spatial c() looked
 * unavailable, the bounds latch was set, and every share attempt showed the toast.
 */
public final class NewXShareImageHandlerTest {
    @Test
    public void windowRectIsMatchedByItsFourIntCoordinatesNotItsClassName() throws Exception {
        assertEquals(4, NewXShareImageHandler.windowRectFields(RenamedIntRect.class).length);
        assertEquals(0, NewXShareImageHandler.windowRectFields(RenamedLongValue.class).length);
        assertNull(NewXShareImageHandler.readIntRect(new RenamedLongValue()));
    }

    /** Stands in for the renamed IntRect: four int coordinates. The static field must not count. */
    static final class RenamedIntRect {
        static int instances;
        int a;
        int b;
        int c;
        int d;
    }

    /** Stands in for the 12.33.0-prod.01 {@code unit.k}: one long, so it is not a window rect. */
    static final class RenamedLongValue {
        long packed;
    }
}
