package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BubbleLaunchCleanupTest {
    @Test
    public void onlyNotificationsWithBubbleMetadataAreCancelledDirectly() {
        assertTrue(BubbleLaunchCleanup.shouldCancel(true));
        assertFalse(BubbleLaunchCleanup.shouldCancel(false));
    }

    @Test
    public void embeddedLaunchSuppressesAppCancelCleanupOnlyUntilDeadline() {
        assertTrue(BubbleLaunchCleanup.shouldSuppressAppCancelCleanup(1000L, 1001L));
        assertFalse(BubbleLaunchCleanup.shouldSuppressAppCancelCleanup(1000L, 1000L));
        assertFalse(BubbleLaunchCleanup.shouldSuppressAppCancelCleanup(1000L, 999L));
        assertFalse(BubbleLaunchCleanup.shouldSuppressAppCancelCleanup(1000L, 0L));
    }

    @Test
    public void embeddedAppCancelIsKeptUnlessFullScreenWeChatIsForeground() {
        assertTrue(BubbleLaunchCleanup.shouldKeepAfterWeChatAppCancel(true, false));
        assertFalse(BubbleLaunchCleanup.shouldKeepAfterWeChatAppCancel(true, true));
        assertFalse(BubbleLaunchCleanup.shouldKeepAfterWeChatAppCancel(false, false));
    }

    @Test
    public void embeddedLaunchClearsOnlyItsExactConversationHistory() {
        assertTrue(BubbleLaunchCleanup.shouldClearPreservedConversationHistory(
                1000L, 1001L, "wechat_alice", "wechat_alice", false));
        assertFalse(BubbleLaunchCleanup.shouldClearPreservedConversationHistory(
                1000L, 1001L, "wechat_alice", "wechat_bob", false));
        assertFalse(BubbleLaunchCleanup.shouldClearPreservedConversationHistory(
                1000L, 1001L, null, "wechat_alice", false));
    }

    @Test
    public void normalSyncRemovalClearsHistoryEvenWhenBubbleHostSurvives() {
        assertTrue(BubbleLaunchCleanup.shouldClearPreservedConversationHistory(
                1000L, 1000L, "wechat_alice", "wechat_bob", false));
        assertTrue(BubbleLaunchCleanup.shouldClearPreservedConversationHistory(
                1000L, 2000L, "wechat_alice", "wechat_bob", true));
    }
}
