package app.morphe.extension.xlite.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.x.models.ClientEventInfo;
import com.x.models.timelinemodule.ModuleDisplayType;
import com.x.models.timelinemodule.ModuleFooter;
import com.x.models.timelinemodule.ModuleHeader;
import com.x.models.timelines.items.UrtTimelineItem;
import com.x.models.timelines.items.UrtTimelineModule;
import com.x.models.timelines.items.UrtTimelineModuleItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XLiteTimelineFilterTest {

    @Test
    public void disabledToggleReturnsSameList() {
        List<Object> items = items(module("who-to-follow-123", null, item("who-to-follow-user")));
        assertSame(items, XLiteTimelineFilter.filterWhoToFollow(items, false));
    }

    @Test
    public void removesModuleByEntryIdPrefix() {
        List<Object> items = items(module("who-to-follow-123", null, item("who-to-follow-user")));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void removesNestedWhoToFollowModule() {
        UrtTimelineModule outer = module(
                "conversationthread-1",
                null,
                item(module("who-to-follow-123", null, item("who-to-follow-user"))),
                item("post-1")
        );
        List<Object> items = items(outer);
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertEquals(1, filtered.size());

        UrtTimelineModule filteredOuter = (UrtTimelineModule) filtered.get(0);
        assertNotSame(outer, filteredOuter);
        assertEquals(1, filteredOuter.getInnerContent().size());
        assertEquals("post-1", filteredOuter.getInnerContent().get(0).getItem().getEntryId());
    }

    @Test
    public void removesModuleByHeaderText() {
        List<Object> items = items(module("module-123", header("Who to follow"), item("user-1")));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void removesModuleByYouMightLikeHeader() {
        List<Object> items = items(module("module-123", header("You might like"), item("user-1")));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void keepsRegularModulesAndPosts() {
        UrtTimelineModule regular = module("topic-follow-123", header("Today's news"), item("post-1"));
        List<Object> items = items(regular, item("post-2"));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertSame(items, filtered);
        assertEquals(2, filtered.size());
    }

    @Test
    public void keepsModulesWithUnrelatedHeaders() {
        List<Object> items = items(module("module-123", header("Follow us on the web"), item("user-1")));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items, true);
        assertSame(items, filtered);
    }

    @Test
    public void promotedFilterStillWorksAfterSignatureChange() {
        List<Object> items = items(module("promoted-123", null, item("post-1")), item("post-2"));
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterPromotedItems(items, true);
        assertEquals(1, filtered.size());
    }

    private static List<Object> items(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static UrtTimelineModuleItem item(String entryId) {
        return item(new FakeItem(entryId));
    }

    private static UrtTimelineModuleItem item(UrtTimelineItem item) {
        return new FakeModuleItem(item);
    }

    private static UrtTimelineModule module(
            String entryId,
            ModuleHeader header,
            UrtTimelineModuleItem... children
    ) {
        return new FakeModule(entryId, header, new ArrayList<>(Arrays.asList(children)));
    }

    private static ModuleHeader header(String text) {
        return new FakeHeader(text);
    }

    private static final class FakeHeader extends ModuleHeader {
        private final String text;

        FakeHeader(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    private static final class FakeItem implements UrtTimelineItem {
        private final String entryId;

        FakeItem(String entryId) {
            this.entryId = entryId;
        }

        @Override
        public String getEntryId() {
            return entryId;
        }

        @Override
        public ClientEventInfo getClientEventInfo() {
            return null;
        }
    }

    private static final class FakeModuleItem extends UrtTimelineModuleItem {
        private final UrtTimelineItem item;

        FakeModuleItem(UrtTimelineItem item) {
            this.item = item;
        }

        @Override
        public UrtTimelineItem getItem() {
            return item;
        }

        @Override
        public boolean isDispensable() {
            return false;
        }

        @Override
        public UrtTimelineModuleItem copy(UrtTimelineItem item, boolean isDispensable) {
            return new FakeModuleItem(item);
        }
    }

    private static final class FakeModule extends UrtTimelineModule {
        private final String entryId;
        private final ModuleHeader header;
        private final List<UrtTimelineModuleItem> children;

        FakeModule(String entryId, ModuleHeader header, List<UrtTimelineModuleItem> children) {
            this.entryId = entryId;
            this.header = header;
            this.children = children;
        }

        @Override
        public List<UrtTimelineModuleItem> getInnerContent() {
            return children;
        }

        @Override
        public ModuleHeader getModuleHeader() {
            return header;
        }

        @Override
        public ModuleFooter getModuleFooter() {
            return null;
        }

        @Override
        public ModuleDisplayType getDisplayType() {
            return null;
        }

        @Override
        public long getSortIndex() {
            return 0;
        }

        @Override
        public UrtTimelineModule copy(
                List<UrtTimelineModuleItem> innerContent,
                ModuleHeader moduleHeader,
                ModuleFooter moduleFooter,
                ModuleDisplayType displayType,
                long sortIndex,
                String entryId,
                ClientEventInfo clientEventInfo
        ) {
            return new FakeModule(entryId, moduleHeader, innerContent);
        }

        @Override
        public String getEntryId() {
            return entryId;
        }

        @Override
        public ClientEventInfo getClientEventInfo() {
            return null;
        }
    }
}
