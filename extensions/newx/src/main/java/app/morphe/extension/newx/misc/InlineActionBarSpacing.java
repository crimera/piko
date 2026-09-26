package app.morphe.extension.newx.misc;

import java.util.List;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;

/**
 * Replicates the pre-12.28.0-alpha.04 inline-action bar layout ("classic" spacing) inside the newer
 * packed-slot measure policy.
 *
 * <p>Classic layout ({@code Lcom/x/inlineactionbar/f;->a(...)} on 12.27.0-prod.01 and
 * 12.28.0-alpha.01): every counted action received an equal share of the row width and the
 * icon-only actions were measured at their intrinsic size, stepped by a fixed 8dp. Hiding an
 * action therefore widened the counted shares instead of opening a gap.
 *
 * <p>Packed-slot layout (12.28.0-alpha.04 and later): every child gets its own slot and the
 * leftover width is distributed into every gap ({@code iMax}), so removing an entry widens all
 * gaps — including the trailing icon-only pair. The measure passes us the slot list it just built
 * plus its own gap floor and the counted slot floor; counted slots are the ones at or above that
 * floor because the app's slot formula is {@code max(content, floor)} for counted actions and a
 * smaller constant or half-width for the rest.
 *
 * <p>Slots are rewritten in place, no list is reallocated, and nothing is touched when the row
 * cannot fit or when the app's own packed spacing is selected.
 */
public final class InlineActionBarSpacing {
    /** Classic fixed step between actions, in dp (the pre-12.28.0-alpha.04 layout used 8dp). */
    private static final float CLASSIC_GAP_DP = 8.0f;

    /** Setting that keeps the app's own packed-slot spacing. */
    private static final String NATIVE_SPACING_SETTING =
            "newx.appearance.inline_action_native_spacing";

    private InlineActionBarSpacing() {
    }

    /** 8dp in the measure's own density, as a raw float bit pattern for the injected smali. */
    @SuppressWarnings("unused")
    public static float classicGapDp() {
        return CLASSIC_GAP_DP;
    }

    /**
     * @param slots per-child slot widths built by the measure (mutated in place)
     * @param available row width available to the children
     * @param minGap the measure's own minimum gap, in pixels
     * @param countedFloor the measure's counted slot floor, in pixels
     * @param classicGap the classic fixed step, in pixels
     */
    @SuppressWarnings("unused")
    public static void applyClassicSpacing(
            List<?> slots,
            int available,
            int minGap,
            int countedFloor,
            int classicGap
    ) {
        if (slots == null || countedFloor <= 0) return;
        try {
            if (SettingsRegistry.getBooleanOrDefault(NATIVE_SPACING_SETTING, false)) return;

            final int count = slots.size();
            if (count < 2) return;

            int counted = 0;
            int otherSlots = 0;
            for (int index = 0; index < count; index++) {
                final int slot = slotAt(slots, index);
                if (slot >= countedFloor) {
                    counted++;
                } else {
                    otherSlots += slot;
                }
            }
            // Without counted actions the classic layout left the icon-only actions intrinsic,
            // which is what the app already does; nothing to stretch.
            if (counted == 0) return;

            final int gap = Math.max(minGap, classicGap);
            // One extra gap is reserved as headroom for the app's fitting loop, which tests every
            // child with its own start offset added on top of the accumulated widths.
            final long budget =
                    (long) available - otherSlots - (long) (count - 1) * gap - gap;
            if (budget <= 0) return;

            int share = (int) (budget / counted);
            if (share < countedFloor) share = countedFloor;

            for (int index = 0; index < count; index++) {
                if (slotAt(slots, index) >= countedFloor) {
                    setSlot(slots, index, share);
                }
            }
        } catch (RuntimeException exception) {
            NewXLogger.printException(
                    () -> "Failed to apply classic inline action spacing", exception);
        }
    }

    /**
     * The slot list holds boxed integers but is only known as {@code List<?>}; the measure built it
     * for its own {@code Integer} values, so writing an int back is safe.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setSlot(List<?> slots, int index, int value) {
        ((List) slots).set(index, value);
    }

    private static int slotAt(List<?> slots, int index) {
        Object slot = slots.get(index);
        return slot instanceof Number ? ((Number) slot).intValue() : 0;
    }
}
