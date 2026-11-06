package app.morphe.extension.instagram.patches.notification.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ChatReducerTest {
    private static final String ME = "100";
    private static final String ALICE = "200";
    private static final String BOB = "300";
    private static final long MIN = 60_000L;
    /** 2026-10-28 18:00:00 in some zone; only differences matter. */
    private static final long T18 = 1_793_210_400_000L;

    private long now = T18;
    private ChatReducer r;
    private final ChatKey chat = new ChatKey(ME, "thread1");
    private int idSeq;

    @Before
    public void setUp() {
        r = new ChatReducer(() -> now);
    }

    private static long at(int minute, int second) {
        return T18 + minute * MIN + second * 1000L;
    }

    private Message item(String sender, String text, long serverTime, long arrival) {
        return new Message("id" + (++idSeq), sender, sender.equals(ME), text, serverTime, arrival);
    }

    private Message noId(String sender, String text, long serverTime, long arrival) {
        return new Message(null, sender, sender.equals(ME), text, serverTime, arrival);
    }

    private static List<String> texts(RenderPlan p) {
        List<String> out = new ArrayList<>();
        for (Message m : p.messages) out.add(m.text);
        return out;
    }

    /** Delivers an item and tells the reducer the renderer showed the result. */
    private RenderPlan itemShown(ChatKey k, Message m) {
        RenderPlan p = r.onItem(k, m);
        if (p != null) r.onDisplayed(k, p);
        return p;
    }

    private RenderPlan digestShown(int n, String text, String sender, long arrival, boolean placeholder) {
        RenderPlan p = r.onDigest(chat, n, text, sender, "Alice and others", arrival, placeholder);
        if (p != null) r.onDisplayed(chat, p);
        return p;
    }

    // ------------------------------------------------------------------ R1

    @Test
    public void r1_digestAt1816_realMessageSentBeforeButDeliveredLater_isNew() {
        // Old messages at 18:01 were shown and then read.
        itemShown(chat, item(ALICE, "Cp", at(1, 5), at(1, 6)));
        itemShown(chat, item(ALICE, "see you at half past", at(1, 20), at(1, 21)));
        r.onCleared(chat);

        // 18:16:43 a digest "2 new messages" arrives with no text and time 0.
        RenderPlan d = r.onDigest(chat, 2, "2 new messages", ALICE, "Alice and others", at(16, 43), true);
        assertNotNull(d);
        assertTrue(d.messages.isEmpty());
        assertEquals(2, d.moreLine);
        assertTrue(d.alert);
        r.onDisplayed(chat, d);
        // arrival time of the digest must not have become the "read" threshold
        assertEquals(at(1, 20), r.stateOf(chat).readUpTo());

        // 18:18:53 the real messages (sent 18:16:40 and :41) are delivered.
        RenderPlan p1 = itemShown(chat, item(ALICE, "are we going", at(16, 40), at(18, 53)));
        assertNotNull(p1);
        assertEquals(1, p1.moreLine);
        assertFalse("digest already alerted for these", p1.alert);
        RenderPlan p2 = itemShown(chat, item(ALICE, "i mentions", at(16, 41), at(18, 53)));
        assertEquals(java.util.Arrays.asList("are we going", "i mentions"), texts(p2));
        assertEquals(0, p2.moreLine);
        assertFalse(p2.alert);
    }

    @Test
    public void r1_digestArrivalNeverAdvancesReadUpTo() {
        digestShown(1, "hi", ALICE, at(16, 43), false);
        assertEquals(0, r.stateOf(chat).readUpTo());
    }

    @Test
    public void misorder_1727_digestOwnMessageWithTimeZeroIsLast() {
        itemShown(chat, item(ALICE, "earlier one", at(20, 0), at(20, 1)));
        itemShown(chat, item(BOB, "earlier two", at(26, 30), at(26, 31)));
        RenderPlan p = r.onDigest(chat, 3, "see you there", ALICE, "Alice", at(27, 0), false);
        assertEquals(java.util.Arrays.asList("earlier one", "earlier two", "see you there"), texts(p));
        assertTrue(p.alert);
        assertEquals(0, p.moreLine);
    }

    @Test
    public void misorder_1831_digestOwnMessageWithTimeZeroIsLast() {
        itemShown(chat, item(BOB, "a", at(30, 10), at(30, 11)));
        itemShown(chat, item(ALICE, "b", at(31, 20), at(31, 21)));
        itemShown(chat, item(BOB, "c", at(31, 40), at(31, 41)));
        RenderPlan p = r.onDigest(chat, 4, "when", ALICE, "Alice", at(31, 55), false);
        assertEquals("when", p.messages.get(p.messages.size() - 1).text);
        assertEquals(4, p.messages.size());
    }

    // ------------------------------------------------------------------ R6

    @Test
    public void r6_twoOkInARow_bothKept_withIds() {
        itemShown(chat, item(ALICE, "ok", at(5, 0), at(5, 1)));
        RenderPlan p = itemShown(chat, item(ALICE, "ok", at(5, 30), at(5, 31)));
        assertEquals(2, p.messages.size());
        assertTrue(p.alert);
    }

    @Test
    public void r6_twoOkInARow_bothKept_withoutIdsDifferentTimes() {
        itemShown(chat, noId(ALICE, "ok", at(5, 0), at(5, 1)));
        RenderPlan p = itemShown(chat, noId(ALICE, "ok", at(5, 30), at(5, 31)));
        assertEquals(2, p.messages.size());
    }

    @Test
    public void r6_sameDigestPostedTwice_isOneMessage_andSecondIsNoChange() {
        digestShown(1, "hello", ALICE, at(5, 0), false);
        assertNull(r.onDigest(chat, 1, "hello", ALICE, "Alice", at(5, 0), false));
        assertEquals(1, r.peek(chat).messages.size());
    }

    @Test
    public void r6_sameSenderAndMillisecondDedupes_evenWhenWordingDiffers() {
        itemShown(chat, noId(ALICE, "Sent a photo", at(7, 0), at(7, 1)));
        RenderPlan dup = r.onItem(chat, noId(ALICE, "Sent an attachment", at(7, 0), at(7, 2)));
        assertNull(dup);
        assertEquals(1, r.peek(chat).messages.size());
        assertEquals(1, r.peek(chat).messages.size());
    }

    @Test
    public void digestTextLosesSentAMessagePrefix() {
        RenderPlan p = r.onDigest(chat, 1, "Sent a message: see you there", ALICE, "Alice", at(27, 0), false);
        assertEquals("see you there", p.messages.get(0).text);
    }

    @Test
    public void twoRealPhotosInARowWithIds_bothShown() {
        itemShown(chat, media("P1", ALICE, "Sent an attachment", at(5, 0), at(5, 1)));
        RenderPlan p = itemShown(chat, media("P2", ALICE, "Sent an attachment", at(5, 2), at(5, 3)));
        assertEquals(2, p.messages.size());
    }

    @Test
    public void digestPhotoIsAbsorbedByExactlyOneRealMediaItem() {
        digestShown(1, "Sent a photo", ALICE, at(25, 22), false);
        r.onItem(chat, media("P1", ALICE, "Sent an attachment", at(25, 19), at(25, 30)));
        assertEquals(1, r.peek(chat).messages.size());
        RenderPlan p = r.onItem(chat, media("P2", ALICE, "Sent an attachment", at(25, 21), at(25, 31)));
        assertEquals("second photo is its own message", 2, p.messages.size());
    }

    @Test
    public void digestPhotoDoesNotMatchTextOrOtherKinds_orOtherSender() {
        digestShown(1, "Sent a photo", ALICE, at(25, 22), false);
        r.onItem(chat, item(ALICE, "Sent an attachment", at(25, 19), at(25, 30))); // TEXT kind
        r.onItem(chat, new Message("R1", ALICE, false, "Sent a reel", at(25, 20), at(25, 30), false, Message.Kind.OTHER));
        r.onItem(chat, media("P9", BOB, "Sent an attachment", at(25, 21), at(25, 30)));
        assertEquals(4, r.peek(chat).messages.size());
    }

    @Test
    public void realPhotoFirstThenDigestPhoto_absorbedOnce() {
        itemShown(chat, media("P1", ALICE, "Sent an attachment", at(25, 19), at(25, 20)));
        RenderPlan p = r.onDigest(chat, 1, "Sent a photo", ALICE, "Alice", at(25, 22), false);
        // The digest's wording replaces our label, silently.
        assertTrue("digest copy of a displayed real photo is silent", p == null || !p.alert);
        assertEquals(java.util.Arrays.asList("Sent a photo"), texts(r.peek(chat)));
    }

    @Test
    public void xmaClipAbsorbsDigestSentAnAttachment_oncePerItem() {
        assertEquals(Message.Kind.MEDIA, Message.kindOfType("xma_clip", "Sent an attachment"));
        assertEquals(Message.Kind.TEXT, Message.kindOfType("link", "https://x"));
        assertEquals(Message.Kind.OTHER, Message.kindOfType("video_call_event", "Video chat ended"));
        digestShown(1, "Sent an attachment", ALICE, at(25, 22), false);
        Message clip = new Message("C1", ALICE, false, "Sent an attachment", at(25, 20), at(25, 40),
                false, Message.kindOfType("xma_clip", "Sent an attachment"));
        r.onItem(chat, clip);
        assertEquals(1, r.peek(chat).messages.size());
        RenderPlan p = r.onItem(chat, new Message("C2", ALICE, false, "Sent an attachment", at(25, 21),
                at(25, 41), false, Message.Kind.MEDIA));
        assertEquals(2, p.messages.size());
    }

    // -------------------------------------------------- media wording (11-04 13:2x "Sent a sticker")

    @Test
    public void instagramWordingSurvivesTheStoredItemWithOurLabel() {
        // Instagram's post names the sticker; the stored item of the same message only has our label.
        itemShown(chat, noId(ALICE, "Sent a sticker", at(5, 0), at(5, 1)));
        RenderPlan p = r.onItem(chat, media("S1", ALICE, Message.ATTACHMENT, at(5, 0), at(5, 2)));
        assertTrue(p == null || !p.alert);
        assertEquals(java.util.Arrays.asList("Sent a sticker"), texts(r.peek(chat)));
    }

    @Test
    public void instagramWordingReplacesOurLabelWhenItArrivesSecond() {
        itemShown(chat, media("C1", ALICE, "Sent a reel", at(5, 0), at(5, 1)));
        RenderPlan p = r.onItem(chat, noId(ALICE, "Sent you a reel", at(5, 0), at(5, 2)));
        assertTrue(p == null || !p.alert);
        assertEquals(java.util.Arrays.asList("Sent you a reel"), texts(r.peek(chat)));
    }

    @Test
    public void digestMediaWordingIsKeptByTheRealItem() {
        digestShown(1, "Sent a photo", ALICE, at(25, 22), false);
        r.onItem(chat, media("P1", ALICE, Message.ATTACHMENT, at(25, 19), at(25, 30)));
        assertEquals(java.util.Arrays.asList("Sent a photo"), texts(r.peek(chat)));
    }

    @Test
    public void realTextIsNeverReplacedByALabel() {
        itemShown(chat, item(ALICE, "look at this", at(5, 0), at(5, 1)));
        r.onItem(chat, noId(ALICE, Message.ATTACHMENT, at(5, 0), at(5, 2)));
        assertEquals(java.util.Arrays.asList("look at this"), texts(r.peek(chat)));
    }

    @Test
    public void labelsByItemType() {
        assertEquals("Sent a voice message", Message.labelOfType("voice_media", null));
        assertEquals("Sent a reel", Message.labelOfType("xma_clip", null));
        assertEquals("Sent a post", Message.labelOfType("xma_media_share", null));
        assertEquals("Sent a video", Message.labelOfType("media", true));
        assertEquals("Sent a photo", Message.labelOfType("media", false));
        assertEquals("Sent a disappearing message", Message.labelOfType("raven_media", null));
        assertEquals(Message.ATTACHMENT, Message.labelOfType("generic_xma", null));
        assertEquals(Message.Kind.MEDIA, Message.kindOfType("voice_media", "Sent a voice message"));
        assertEquals(Message.Kind.OTHER, Message.kindOfType("video_call_event", "Video chat"));
    }

    // ---------------------------------------------------- queries for read markers and actions

    @Test
    public void queriesAnswerFromHeldMessagesOfOthers() {
        itemShown(chat, new Message("a1", ALICE, false, "one", at(1, 0), at(1, 1)));
        itemShown(chat, new Message("a2", ALICE, false, "two", at(2, 0), at(2, 1)));
        itemShown(chat, new Message("m1", ME, true, "mine", at(3, 0), at(3, 1)));
        assertEquals(at(1, 0), r.itemTime(chat, "a1"));
        assertEquals("own items are not someone else's", 0, r.itemTime(chat, "m1"));
        assertEquals(0, r.itemTime(chat, "unknown"));
        assertEquals("a2", r.latestFromOthers(chat).itemId);
        assertEquals(chat, r.chatOfItem(ME, "a2"));
        assertNull(r.chatOfItem("someone-else", "a2"));
        assertNull(r.chatOfItem(ME, "unknown"));
    }

    @Test
    public void swipeThenNewMessage_showsOnlyTheNewOne() {
        itemShown(chat, item(ALICE, "old1", at(1, 0), at(1, 1)));
        itemShown(chat, item(ALICE, "old2", at(1, 10), at(1, 11)));
        assertTrue(r.onDismissed(chat).clear);
        RenderPlan p = r.onItem(chat, item(ALICE, "fresh", at(9, 0), at(9, 1)));
        assertEquals(java.util.Arrays.asList("fresh"), texts(p));
        assertTrue(p.alert);
    }

    @Test
    public void digest1816_afterDismissBetween1801And1816_showsNo1801Message() {
        itemShown(chat, item(ALICE, "see you at half past", at(1, 20), at(1, 21)));
        r.onDismissed(chat);
        RenderPlan d = digestShown(2, "2 new messages", ALICE, at(16, 43), true);
        assertTrue(d.messages.isEmpty());
        assertEquals(2, d.moreLine);
    }

    @Test
    public void digest1816_afterReadElsewhereBetween1801And1816_showsNo1801Message() {
        itemShown(chat, item(ALICE, "see you at half past", at(1, 20), at(1, 21)));
        r.onReadElsewhere(chat, at(5, 0));
        RenderPlan d = digestShown(2, "2 new messages", ALICE, at(16, 43), true);
        assertTrue(d.messages.isEmpty());
        assertEquals(2, d.moreLine);
    }

    @Test
    public void newDoesNotDependOnDigestCount() {
        itemShown(chat, item(ALICE, "a", at(1, 0), at(1, 1)));
        // digest count says 1, but a displayed unread message already exists: no hidden "N<=K" rule
        RenderPlan p = r.onDigest(chat, 1, "b", ALICE, "Alice", at(2, 0), false);
        assertEquals(2, p.messages.size());
        assertTrue(p.alert);
        assertEquals(0, p.moreLine);
    }

    @Test
    public void realFillingADisplayedPlaceholderPromiseIsSilent_butExtraMessageAlerts() {
        digestShown(2, "2 new messages", ALICE, at(16, 43), true);
        assertFalse(itemShown(chat, item(ALICE, "x", at(16, 40), at(18, 0))).alert);
        assertFalse(itemShown(chat, item(ALICE, "y", at(16, 41), at(18, 1))).alert);
        assertTrue(itemShown(chat, item(ALICE, "z", at(19, 0), at(19, 1))).alert);
    }

    @Test
    public void placeholderLeavesAfterTwoHoursAndIsCapped() {
        digestShown(1, "hello", ALICE, at(1, 0), false);
        r.onCleared(chat);
        assertEquals(1, r.stateOf(chat).heldPlaceholders());
        now = at(1, 0) + ChatReducer.PLACEHOLDER_TTL_MS + 1;
        r.onItem(chat, item(BOB, "later", now, now));
        assertEquals(0, r.stateOf(chat).heldPlaceholders());
        for (int i = 0; i < 30; i++) {
            r.onDigest(chat, 1, "t" + i, ALICE, "A", now + i * 1000L, false);
            r.onCleared(chat);
        }
        assertTrue(r.stateOf(chat).heldPlaceholders() <= ChatReducer.MAX_PLACEHOLDERS);
    }

    @Test
    public void staleRealCopyRetiresItsReadPlaceholder() {
        digestShown(1, "hello", ALICE, at(16, 43), false);
        r.onCleared(chat);
        r.onItem(chat, item(ALICE, "other", at(17, 0), at(17, 1)));
        r.onCleared(chat);
        assertNull(r.onItem(chat, item(ALICE, "hello", at(16, 40), at(19, 0))));
        assertEquals(0, r.stateOf(chat).heldPlaceholders());
    }

    private Message media(String id, String sender, String text, long serverTime, long arrival) {
        return new Message(id, sender, sender.equals(ME), text, serverTime, arrival, false, Message.Kind.MEDIA);
    }

    @Test
    public void r6_idMatchBeatsEverythingElse() {
        itemShown(chat, new Message("X1", ALICE, false, "one", at(7, 0), at(7, 1)));
        RenderPlan p = r.onItem(chat, new Message("X2", ALICE, false, "one", at(7, 0), at(7, 1)));
        assertEquals(2, p.messages.size());
    }

    @Test
    public void r6_digestTextThenRealCopyMerges_andTakesRealTime() {
        RenderPlan d = digestShown(1, "Sent a message: hi there", ALICE, at(16, 43), false);
        assertEquals(1, d.messages.size());
        // real copy sent 3 s before the digest, delivered 2 min 10 s after it
        RenderPlan p = r.onItem(chat, item(ALICE, "hi there", at(16, 40), at(18, 53)));
        assertNotNull(p);
        assertEquals(1, p.messages.size());
        assertEquals(at(16, 40), p.messages.get(0).serverTime);
        assertFalse(p.alert);
    }

    @Test
    public void r6_digestWithToChatPrefixMatchesPlainText() {
        digestShown(1, "Alice to friends: hello", ALICE, at(1, 0), false);
        r.onItem(chat, item(ALICE, "hello", at(1, 0), at(1, 5)));
        assertEquals(1, r.peek(chat).messages.size());
    }

    // ------------------------------------------------------------ digest N/K

    @Test
    public void digestNGreaterThanK_showsKPlusMoreLine() {
        itemShown(chat, item(ALICE, "m1", at(2, 0), at(2, 1)));
        itemShown(chat, item(ALICE, "m2", at(2, 10), at(2, 11)));
        RenderPlan p = r.onDigest(chat, 5, "5 new messages", ALICE, "Alice and others", at(2, 30), true);
        assertEquals(java.util.Arrays.asList("m1", "m2"), texts(p));
        assertEquals(3, p.moreLine);
        assertEquals("Alice and others", p.moreSenderName);
        assertTrue(p.alert);
    }

    @Test
    public void digestNEqualsK_noMoreLine_andNeverFilledWithOldMessages() {
        itemShown(chat, item(ALICE, "old1", at(1, 0), at(1, 1)));
        itemShown(chat, item(ALICE, "old2", at(1, 10), at(1, 11)));
        r.onCleared(chat);
        RenderPlan p = r.onDigest(chat, 3, "3 new messages", BOB, "Bob and others", at(9, 0), true);
        assertTrue(p.messages.isEmpty());
        assertEquals(3, p.moreLine);
    }

    // ------------------------------------------------------------ clear/stale

    @Test
    public void staleMessagesNeverShownAfterCleared() {
        Message a = item(ALICE, "a", at(3, 0), at(3, 1));
        Message b = item(ALICE, "b", at(3, 10), at(3, 11));
        itemShown(chat, a);
        itemShown(chat, b);
        RenderPlan clear = r.onCleared(chat);
        assertTrue(clear.clear);
        assertNull(r.peek(chat));
        // redelivery of the same, and an older one never seen before
        assertNull(r.onItem(chat, a));
        assertNull(r.onItem(chat, item(ALICE, "older", at(2, 0), at(4, 0))));
        // a newer one shows alone
        RenderPlan p = r.onItem(chat, item(ALICE, "c", at(4, 0), at(4, 1)));
        assertEquals(java.util.Arrays.asList("c"), texts(p));
        assertTrue(p.alert);
    }

    @Test
    public void clearTwiceIsNoChange() {
        itemShown(chat, item(ALICE, "a", at(3, 0), at(3, 1)));
        assertNotNull(r.onCleared(chat));
        assertNull(r.onCleared(chat));
    }

    @Test
    public void lateRealCopyOfAClearedDigestMessageIsNotShownAgain() {
        digestShown(1, "hello", ALICE, at(16, 43), false);
        r.onCleared(chat);
        assertNull(r.onItem(chat, item(ALICE, "hello", at(16, 40), at(19, 0))));
        // and a following real message older than that one is stale too
        assertNull(r.onItem(chat, item(ALICE, "x", at(16, 39), at(19, 1))));
        assertNotNull(r.onItem(chat, item(ALICE, "y", at(17, 0), at(19, 2))));
    }

    @Test
    public void realCopyOfAClearedDigestMessageAlsoClearsOlderUnreadOnes() {
        digestShown(1, "newest", ALICE, at(16, 43), false);
        r.onCleared(chat);
        // an older message is delivered first, then the real copy of the one the user read
        assertNotNull(r.onItem(chat, item(ALICE, "older", at(16, 30), at(16, 50))));
        RenderPlan p = r.onItem(chat, item(ALICE, "newest", at(16, 40), at(16, 51)));
        assertTrue(p.clear);
        assertNull(r.peek(chat));
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    public void unsendRemovesSilently() {
        itemShown(chat, item(ALICE, "keep", at(2, 0), at(2, 1)));
        Message gone = item(ALICE, "oops", at(2, 10), at(2, 11));
        itemShown(chat, gone);
        RenderPlan p = r.onUnsend(chat, gone.itemId);
        assertEquals(java.util.Arrays.asList("keep"), texts(p));
        assertFalse(p.alert);
        // the next real message still alerts
        RenderPlan n = r.onItem(chat, item(ALICE, "next", at(3, 0), at(3, 1)));
        assertTrue(n.alert);
    }

    @Test
    public void unsendOfLastMessageClearsRecord_unknownIdIsNoChange() {
        Message only = item(ALICE, "only", at(2, 0), at(2, 1));
        itemShown(chat, only);
        assertNull(r.onUnsend(chat, "nope"));
        RenderPlan p = r.onUnsend(chat, only.itemId);
        assertTrue(p.clear);
        assertFalse(p.alert);
    }

    @Test
    public void unsendDoesNotLeaveGhostMoreLine() {
        Message a = item(ALICE, "a", at(2, 0), at(2, 1));
        itemShown(chat, a);
        digestShown(1, "a", ALICE, at(2, 2), false);
        RenderPlan p = r.onUnsend(chat, a.itemId);
        assertTrue(p.clear);
    }

    @Test
    public void editReplacesInPlaceWithoutAlertOrSecondCopy() {
        Message a = item(ALICE, "first", at(2, 0), at(2, 1));
        Message b = item(ALICE, "second", at(2, 10), at(2, 11));
        itemShown(chat, a);
        itemShown(chat, b);
        RenderPlan p = r.onEdit(chat, a.itemId, "first (edited)");
        assertEquals(java.util.Arrays.asList("first (edited)", "second"), texts(p));
        assertTrue(p.messages.get(0).edited);
        assertFalse(p.alert);
        assertNull(r.onEdit(chat, a.itemId, "first (edited)"));
        assertNull(r.onEdit(chat, "unknown", "x"));
        // the same id redelivered with the new text does not add a copy
        r.onItem(chat, new Message(a.itemId, ALICE, false, "first again", a.serverTime, at(5, 0)));
        assertEquals(2, r.peek(chat).messages.size());
        assertEquals("first again", r.peek(chat).messages.get(0).text);
    }

    @Test
    public void readElsewhereClearsAndLaterOldItemsAreDropped() {
        itemShown(chat, item(ALICE, "a", at(2, 0), at(2, 1)));
        itemShown(chat, item(ALICE, "b", at(2, 10), at(2, 11)));
        RenderPlan p = r.onReadElsewhere(chat, at(2, 10));
        assertTrue(p.clear);
        assertNull(r.onItem(chat, item(ALICE, "b-again", at(2, 5), at(9, 0))));
        RenderPlan n = r.onItem(chat, item(ALICE, "c", at(5, 0), at(5, 1)));
        assertTrue(n.alert);
        assertEquals(java.util.Arrays.asList("c"), texts(n));
    }

    @Test
    public void readElsewherePartial_keepsNewerSilently() {
        itemShown(chat, item(ALICE, "a", at(2, 0), at(2, 1)));
        itemShown(chat, item(ALICE, "b", at(4, 0), at(4, 1)));
        RenderPlan p = r.onReadElsewhere(chat, at(3, 0));
        assertEquals(java.util.Arrays.asList("b"), texts(p));
        assertFalse(p.alert);
    }

    @Test
    public void readElsewhereCoversPlaceholderDigest() {
        digestShown(3, "3 new messages", ALICE, at(5, 0), true);
        RenderPlan p = r.onReadElsewhere(chat, at(5, 30));
        assertTrue(p.clear);
    }

    // --------------------------------------------------------------- misc

    @Test
    public void ownMessage_noAlert_nullSender_notCountedInMoreLine() {
        RenderPlan p = r.onItem(chat, item(ME, "my reply", at(2, 0), at(2, 1)));
        assertNotNull(p);
        assertFalse(p.alert);
        assertNull(p.messages.get(0).senderId);
        assertTrue(p.messages.get(0).senderIsViewer);
        r.onDisplayed(chat, p);
        RenderPlan q = r.onItem(chat, item(ALICE, "hey", at(3, 0), at(3, 1)));
        assertTrue(q.alert);
        assertNull(q.messages.get(0).senderId);
        assertEquals(ALICE, q.messages.get(1).senderId);
    }

    @Test
    public void alertOnlyForNewerFromOthers_duplicateOrSecondDisplayIsSilent() {
        RenderPlan p = r.onItem(chat, item(ALICE, "hi", at(2, 0), at(2, 1)));
        assertTrue(p.alert);
        r.onDisplayed(chat, p);
        assertNull(r.onItem(chat, noId(ALICE, "hi", at(2, 0), at(2, 3))));
    }

    @Test
    public void twoViewersDoNotCross() {
        ChatKey other = new ChatKey("999", "thread1");
        itemShown(chat, item(ALICE, "for me", at(2, 0), at(2, 1)));
        assertNull(r.peek(other));
        RenderPlan p = r.onItem(other, noId(ALICE, "for 999", at(2, 0), at(2, 1)));
        assertEquals(java.util.Arrays.asList("for 999"), texts(p));
        r.onCleared(other);
        assertEquals(java.util.Arrays.asList("for me"), texts(r.peek(chat)));
        assertEquals(0, r.stateOf(chat).readUpTo());
    }

    @Test
    public void capsAtFiftyMessages_keepingNewest() {
        for (int i = 0; i < 60; i++) itemShown(chat, item(ALICE, "m" + i, T18 + i * 1000L, T18 + i * 1000L));
        RenderPlan p = r.peek(chat);
        assertEquals(ChatReducer.MAX_MESSAGES, p.messages.size());
        assertEquals("m10", p.messages.get(0).text);
        assertEquals("m59", p.messages.get(49).text);
    }

    @Test
    public void planIsAlwaysOrderedByDisplayTime() {
        long[] times = {at(5, 0), at(1, 0), at(9, 0), at(3, 0), at(7, 0)};
        for (long t : times) r.onItem(chat, item(ALICE, "t" + t, t, t + 5));
        r.onDigest(chat, 6, "digest", BOB, "Bob", at(8, 0), false);
        long prev = 0;
        for (Message m : r.peek(chat).messages) {
            assertTrue(m.displayTime() >= prev);
            prev = m.displayTime();
        }
    }

    // ----------------------------------------------------- Instagram posts (phase 4)

    private RenderPlan post(List<Message> items, int digestCount, String digestText, boolean placeholder, long arrival) {
        RenderPlan p = r.onPost(chat, items, digestCount, digestText, ALICE, "Alice and others", arrival, placeholder);
        r.onDisplayed(chat, p);
        return p;
    }

    private static List<Message> list(Message... ms) {
        return new ArrayList<>(java.util.Arrays.asList(ms));
    }

    @Test
    public void post_itemsAndDigest_renderOnce_andRepostIsSilentButStillAPlan() {
        List<Message> items = list(noId(ALICE, "one", at(2, 0), at(2, 5)), noId(ALICE, "two", at(2, 10), at(2, 15)));
        RenderPlan p = post(items, 3, null, true, at(2, 15));
        assertEquals(java.util.Arrays.asList("one", "two"), texts(p));
        assertEquals(1, p.moreLine);
        assertTrue(p.alert);
        // The sibling tag posts the same content: a plan again (it must be rendered), but silent.
        RenderPlan again = post(list(noId(ALICE, "one", at(2, 0), at(2, 20)), noId(ALICE, "two", at(2, 10), at(2, 20))),
                3, null, true, at(2, 20));
        assertNotNull(again);
        assertEquals(java.util.Arrays.asList("one", "two"), texts(again));
        assertEquals(1, again.moreLine);
        assertFalse(again.alert);
    }

    @Test
    public void post_historyAlreadyReadIsNotShownAgain_andAllOldMeansClear() {
        itemShown(chat, item(ALICE, "old", at(1, 0), at(1, 1)));
        r.onCleared(chat);
        RenderPlan p = post(list(noId(ALICE, "old", at(1, 0), at(3, 0)), noId(ALICE, "new", at(2, 30), at(3, 0))),
                0, null, false, at(3, 0));
        assertEquals(java.util.Arrays.asList("new"), texts(p));
        assertTrue(p.alert);
        r.onCleared(chat);
        RenderPlan allOld = post(list(noId(ALICE, "new", at(2, 30), at(4, 0))), 0, null, false, at(4, 0));
        assertTrue(allOld.clear);
    }

    @Test
    public void post_timedItemAndLaterRealtimeItem_areOneMessage() {
        post(list(noId(ALICE, "hello", at(2, 0), at(2, 3))), 0, null, false, at(2, 3));
        RenderPlan p = r.onItem(chat, new Message("id9", ALICE, false, "hello", at(2, 0), at(2, 9)));
        assertTrue(p == null || !p.alert); // the id is bookkeeping; nothing new to announce
        assertEquals(java.util.Arrays.asList("hello"), texts(r.peek(chat)));
        assertEquals("id9", r.stateOf(chat).entries.get(0).msg.itemId);
    }

    @Test
    public void post_replyRepostIsAnOwnMessage_silent_andMergesWithItsEcho() {
        itemShown(chat, item(ALICE, "question", at(2, 0), at(2, 1)));
        RenderPlan reply = post(list(noId(ALICE, "question", at(2, 0), at(2, 40)), noId(ME, "answer", 0, at(2, 40))),
                0, null, false, at(2, 40));
        assertFalse(reply.alert);
        assertNull(reply.messages.get(1).senderId);
        // The server echo of the reply carries an id and a time: still one "answer", still silent.
        RenderPlan echo = r.onItem(chat, new Message("e1", ME, true, "answer", at(2, 39), at(2, 41)));
        assertTrue(echo == null || !echo.alert);
        int answers = 0;
        for (Message m : r.peek(chat).messages) if (m.text.equals("answer")) answers++;
        assertEquals(1, answers);
    }

    @Test
    public void post_oneToOneBareMessageIsMergedWithItsRealCopy_andRepeatedOkStaysTwo() {
        RenderPlan first = post(list(noId(ALICE, "ok", 0, at(2, 0))), 0, null, false, at(2, 0));
        assertTrue(first.alert);
        r.onItem(chat, new Message("a", ALICE, false, "ok", at(1, 59), at(2, 3)));
        post(list(noId(ALICE, "ok", 0, at(2, 20))), 0, null, false, at(2, 20));
        r.onItem(chat, new Message("b", ALICE, false, "ok", at(2, 19), at(2, 23)));
        assertEquals(java.util.Arrays.asList("ok", "ok"), texts(r.peek(chat)));
    }

    @Test
    public void post_digestWithTextOnly_isOneMessage() {
        RenderPlan p = post(list(), 1, "Sent a message: hi there", false, at(2, 0));
        assertEquals(java.util.Arrays.asList("hi there"), texts(p));
        assertEquals(0, p.moreLine);
    }

    @Test
    public void post_isTheAuthority_oldHeldSyncItemsNotInItAreRead_newerLateOnesStay() {
        // A cold start sync fed ten old items while no record was showing.
        for (int i = 0; i < 10; i++) r.onItem(chat, item(ALICE, "old" + i, at(1, i), at(30, i)));
        RenderPlan p = post(list(noId(BOB, "sounds good", at(40, 0), at(40, 1))), 0, null, false, at(40, 1));
        assertEquals(java.util.Arrays.asList("sounds good"), texts(p));
        assertTrue(p.alert);
        // They stay read: a later post that does not contain them does not bring them back.
        RenderPlan q = post(list(noId(BOB, "sounds good", at(40, 0), at(41, 0)), noId(BOB, "next", at(41, 0), at(41, 1))),
                0, null, false, at(41, 1));
        assertEquals(java.util.Arrays.asList("sounds good", "next"), texts(q));
        // A held item newer than the post's newest message is a late delivery and stays unread.
        r.onItem(chat, item(ALICE, "late", at(50, 0), at(50, 5)));
        RenderPlan late = post(list(noId(BOB, "next", at(41, 0), at(51, 0))), 0, null, false, at(51, 0));
        // "sounds good" was shown and never read, so a post holding only "next" keeps it.
        assertEquals(java.util.Arrays.asList("sounds good", "next", "late"), texts(late));
        // And with a record showing, a late item renders.
        RenderPlan live = r.onItem(chat, item(ALICE, "live", at(52, 0), at(52, 1)));
        assertNotNull(live);
        assertEquals("live", live.messages.get(live.messages.size() - 1).text);
    }

    @Test
    public void post_withOnlyItsNewestMessage_keepsShownUnreadMessages_andTheDigestCountsThem() {
        // Four messages were shown and are still unread (no cancel, swipe or read marker).
        for (int i = 0; i < 4; i++) itemShown(chat, item(ALICE, "earlier" + i, at(10, i), at(10, i)));
        // Ten minutes later Instagram posts the chat again, holding only the newest message.
        RenderPlan p = post(list(noId(BOB, "Sent a photo", at(20, 0), at(20, 1))), 0, null, false, at(20, 1));
        assertEquals(java.util.Arrays.asList("earlier0", "earlier1", "earlier2", "earlier3", "Sent a photo"),
                texts(p));
        // Its digest "5 new messages" is then fully covered: no "more" line.
        RenderPlan d = post(list(), 5, "5 new messages", true, at(20, 2));
        assertEquals(0, d.moreLine);
        assertEquals(5, d.messages.size());
    }

    @Test
    public void ownReplyImpliesRead_inPostsAndOnEchoes() {
        RenderPlan p = post(list(noId(ALICE, "pong", at(1, 0), at(5, 0)), noId(ALICE, "hello", at(2, 0), at(5, 0)),
                noId(ALICE, "how are we", at(3, 0), at(5, 0)), noId(ME, "all good yea", at(4, 0), at(5, 0)),
                noId(ALICE, "newer", at(6, 0), at(5, 0))), 0, null, false, at(5, 0));
        assertEquals(java.util.Arrays.asList("all good yea", "newer"), texts(p));
        r.onCleared(chat);
        itemShown(chat, item(ALICE, "q1", at(10, 0), at(10, 1)));
        itemShown(chat, item(ALICE, "q2", at(11, 0), at(11, 1)));
        RenderPlan echo = r.onItem(chat, item(ME, "answer", at(12, 0), at(12, 1)));
        assertNotNull(echo);
        // What was shown stays as context; the reply is added.
        assertEquals(java.util.Arrays.asList("q1", "q2", "answer"), texts(echo));
        assertFalse(echo.alert);
        // A message sent before the reply but delivered late is read as well.
        assertNull(r.onItem(chat, item(ALICE, "q0", at(9, 0), at(13, 0))));
    }

    @Test
    public void replyKeepsDisplayedContext_newMessageAlertsOnce_butNothingComesBackAfterASwipe() {
        // 13:00:18: the record shows the sender's message; the reply repost, then the own echo.
        RenderPlan shown = post(list(noId(ALICE, "ping", at(2, 0), at(2, 1))), 0, null, false, at(2, 1));
        assertTrue(shown.alert);
        RenderPlan repost = post(list(noId(ALICE, "ping", at(2, 0), at(2, 40)), noId(ME, "on my way", 0, at(2, 40))),
                0, null, false, at(2, 40));
        assertEquals(java.util.Arrays.asList("ping", "on my way"), texts(repost));
        assertFalse(repost.alert);
        RenderPlan echo = r.onItem(chat, new Message("e1", ME, true, "on my way", at(2, 38), at(2, 41)));
        RenderPlan current = echo != null ? echo : r.peek(chat);
        assertEquals(java.util.Arrays.asList("ping", "on my way"), texts(current));
        assertFalse(current.alert);
        r.onDisplayed(chat, current);
        // The next message: context, the reply and the new message, in order, one alert.
        RenderPlan next = itemShown(chat, item(ALICE, "next", at(3, 0), at(3, 1)));
        assertEquals(java.util.Arrays.asList("ping", "on my way", "next"), texts(next));
        assertTrue(next.alert);
        assertNull(r.onItem(chat, noId(ALICE, "next", at(3, 0), at(3, 2))));
        // A late delivery older than the reply is read: no alert, not shown.
        assertNull(r.onItem(chat, item(ALICE, "older", at(1, 0), at(3, 5))));
    }

    @Test
    public void replyThenSwipe_newMessageShowsOnlyTheNewOne() {
        post(list(noId(ALICE, "q", at(2, 0), at(2, 1))), 0, null, false, at(2, 1));
        r.onItem(chat, new Message("e1", ME, true, "a", at(2, 30), at(2, 31)));
        r.onDisplayed(chat, r.peek(chat));
        r.onDismissed(chat);
        RenderPlan p = r.onItem(chat, item(ALICE, "fresh", at(5, 0), at(5, 1)));
        assertEquals(java.util.Arrays.asList("fresh"), texts(p));
        assertTrue(p.alert);
    }

    @Test
    public void ownMessagePrefixIsStripped_andBothCopiesAreOne() {
        assertEquals("all good yea", Message.stripOwnPrefix("g8row: all good yea"));
        assertEquals("a: b: c", Message.stripOwnPrefix("x.y_z: a: b: c"));
        assertEquals("two words: x", Message.stripOwnPrefix("two words: x"));
        assertEquals("note:", Message.stripOwnPrefix("note:"));
        assertEquals("plain", Message.stripOwnPrefix("plain"));
        post(list(noId(ALICE, "hi", at(1, 0), at(2, 0))), 0, null, false, at(2, 0));
        post(list(noId(ALICE, "hi", at(1, 0), at(2, 30)), noId(ME, "all good yea", 0, at(2, 30))), 0, null, false, at(2, 30));
        // Instagram's later history copy: real time, prefix stripped by the caller.
        RenderPlan p = post(list(noId(ALICE, "hi", at(1, 0), at(3, 0)), noId(ME, "all good yea", at(2, 25), at(3, 0))),
                0, null, false, at(3, 0));
        int own = 0;
        for (Message m : p.messages) if (m.senderIsViewer) own++;
        assertEquals(1, own);
        // The echo has the same real time and an id: still one.
        r.onItem(chat, new Message("e7", ME, true, "all good yea", at(2, 25), at(3, 5)));
        own = 0;
        for (Message m : r.peek(chat).messages) if (m.senderIsViewer) own++;
        assertEquals(1, own);
    }

    @Test
    public void executor_lastFailureNamesTheCause() {
        ChatExecutor ex = new ChatExecutor(r, () -> now);
        try {
            assertNull(ex.call(rd -> { throw new IllegalStateException("boom"); }, 1000));
            assertTrue(ex.lastFailure().contains("boom"));
        } finally {
            ex.shutdown();
        }
    }

    // ------------------------------------------------------------- executor

    @Test
    public void executor_snapshotReadsOnOwnerThread() {
        ChatExecutor ex = new ChatExecutor(r, () -> now);
        try {
            ex.call(rd -> rd.onItem(chat, item(ALICE, "hi", at(2, 0), at(2, 1))), 1000);
            RenderPlan p = ex.snapshot(chat, 1000);
            assertEquals(java.util.Arrays.asList("hi"), texts(p));
            assertNull(ex.snapshot(new ChatKey(ME, "nothing"), 1000));
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void executor_boundedWaitFailsOpenAndRepeatedFailuresDisable() {
        ChatExecutor ex = new ChatExecutor(r, () -> now);
        try {
            for (int i = 0; i < ChatExecutor.MAX_FAILURES; i++) {
                assertFalse(ex.isDisabled());
                assertNull(ex.call(rd -> { throw new IllegalStateException("boom"); }, 1000));
            }
            assertTrue(ex.isDisabled());
            assertNull(ex.snapshot(chat, 1000));
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void executor_timeoutReturnsNullInsteadOfBlocking() {
        ChatExecutor ex = new ChatExecutor(r, () -> now);
        try {
            long t0 = System.nanoTime();
            assertNull(ex.call(rd -> { Thread.sleep(2000); return "late"; }, 50));
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1000);
        } finally {
            ex.shutdown();
        }
    }
}
