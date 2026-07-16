package me.jaspr.wemodern;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConversationBubblesTest {
    @Test
    public void bubblesRequireAndroid10() {
        assertFalse(ConversationBubbles.isSupported(28));
        assertTrue(ConversationBubbles.isSupported(29));
        assertTrue(ConversationBubbles.isSupported(37));
    }

    @Test
    public void requestCodeIsStableForConversation() {
        assertEquals(
                ConversationBubbles.requestCodeFor("wechat:alice"),
                ConversationBubbles.requestCodeFor("wechat:alice")
        );
    }

    @Test
    public void requestCodeSeparatesConversations() {
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat:alice"),
                ConversationBubbles.requestCodeFor("wechat:bob")
        );
    }

    @Test
    public void bubblePendingIntentIsMutableForSystemUiLaunchOptions() {
        assertEquals(FLAG_UPDATE_CURRENT | FLAG_MUTABLE, ConversationBubbles.pendingIntentFlags());
    }

    @Test
    public void bubbleDismissPendingIntentIsImmutableAndSeparate() {
        assertEquals(
                FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE,
                ConversationBubbles.dismissPendingIntentFlags()
        );
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat:alice"),
                ConversationBubbles.dismissRequestCodeFor("wechat:alice")
        );
        assertNotEquals(
                ConversationBubbles.dismissRequestCodeFor("wechat:alice"),
                ConversationBubbles.dismissRequestCodeFor("wechat:bob")
        );
    }

    @Test
    public void bubbleMetadataRequiresTheLocalFeatureSwitch() {
        assertFalse(ConversationBubbles.shouldApply(37, false, false, true, true));
        assertTrue(ConversationBubbles.shouldApply(37, true, false, true, true));
        assertFalse(ConversationBubbles.shouldApply(37, true, false, false, true));
        assertFalse(ConversationBubbles.shouldApply(37, true, false, true, false));
    }

    @Test
    public void trampolineUsesOnlyTheDedicatedHostBubble() {
        assertFalse(ConversationBubbles.shouldApply(37, true, true, true, true));
    }

    @Test
    public void bubbleNotificationsStayCollapsedAndExposeTheirNotification() {
        assertFalse(ConversationBubbles.shouldAutoExpand());
        assertFalse(ConversationBubbles.shouldSuppressNotification());
    }

    @Test
    public void disablingUpdatesOnlyNotificationsThatStillHaveBubbleMetadata() {
        assertTrue(ConversationBubbles.shouldUpdateActiveNotification(
                false, "wechat:alice", false, false, true));
        assertFalse(ConversationBubbles.shouldUpdateActiveNotification(
                false, "wechat:alice", true, true, false));
        assertFalse(ConversationBubbles.shouldUpdateActiveNotification(
                false, null, true, true, true));
    }

    @Test
    public void enablingUpdatesOnlyRestorableConversationNotifications() {
        assertTrue(ConversationBubbles.shouldUpdateActiveNotification(
                true, "wechat:alice", true, true, false));
        assertTrue(ConversationBubbles.shouldUpdateActiveNotification(
                true, "wechat:alice", true, true, true));
        assertFalse(ConversationBubbles.shouldUpdateActiveNotification(
                true, "wechat:alice", false, true, false));
        assertFalse(ConversationBubbles.shouldUpdateActiveNotification(
                true, "wechat:alice", true, false, false));
    }
}
