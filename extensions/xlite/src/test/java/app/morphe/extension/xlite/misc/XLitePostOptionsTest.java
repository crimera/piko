package app.morphe.extension.xlite.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class XLitePostOptionsTest {
    @Test
    public void addsOptionWithoutMutatingOriginalGroups() {
        List<Group> groups = Collections.singletonList(new Group(Collections.singletonList(Action.Native)));

        List<?> result = XLitePostOptions.addOption(groups, Action.None.name(), true);

        assertEquals(1, groups.size());
        assertEquals(2, result.size());
        assertEquals(Collections.singletonList(Action.None), ((Group) result.get(1)).actions);
    }

    @Test
    public void disabledOptionReturnsOriginalGroups() {
        List<Group> groups = Collections.singletonList(new Group(Collections.singletonList(Action.Native)));

        assertSame(groups, XLitePostOptions.addOption(groups, Action.None.name(), false));
    }

    @Test
    public void existingOptionIsNotAddedTwice() {
        List<Group> groups = Collections.singletonList(new Group(Collections.singletonList(Action.None)));

        assertSame(groups, XLitePostOptions.addOption(groups, Action.None.name(), true));
    }

    @Test
    public void missingOptionIsNotAdded() {
        List<Group> groups = Collections.singletonList(new Group(Collections.singletonList(Action.Native)));

        assertSame(groups, XLitePostOptions.addOption(groups, "Missing", true));
    }

    @Test
    public void identifiesOnlyConfiguredSentinel() {
        assertTrue(XLitePostOptions.isAction(Action.None, XLitePostOptionActions.BROWSE_OBJECT_ACTION));
        assertTrue(XLitePostOptions.isAction(Action.ViewDebugDialog, XLitePostOptionActions.SHARE_IMAGE_ACTION));
        assertFalse(XLitePostOptions.isAction(Action.Native, XLitePostOptionActions.BROWSE_OBJECT_ACTION));
        assertFalse(XLitePostOptions.isAction(null, XLitePostOptionActions.BROWSE_OBJECT_ACTION));
    }

    @Test
    public void skipsNullListFieldBeforeActionList() {
        List<Group> groups = Collections.singletonList(Group.withNullMetadata(Collections.singletonList(Action.Native)));

        List<?> result = XLitePostOptions.addOption(groups, Action.None.name(), true);

        assertEquals(2, result.size());
        assertEquals(Collections.singletonList(Action.None), ((Group) result.get(1)).actions);
    }

    private enum Action {
        Native,
        None,
        ViewDebugDialog,
    }

    private static final class Group {
        private final List<String> metadata;
        private final List<Action> actions;

        private Group(List<Action> actions) {
            this(Collections.singletonList("metadata"), actions);
        }

        private Group(List<String> metadata, List<Action> actions) {
            this.metadata = metadata;
            this.actions = actions;
        }

        private static Group withNullMetadata(List<Action> actions) {
            return new Group(null, actions);
        }
    }
}
