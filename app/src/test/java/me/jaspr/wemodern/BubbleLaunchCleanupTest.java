package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BubbleLaunchCleanupTest {
    @Test
    public void onlyNormalNotificationsWithBubbleMetadataAreCancelledDirectly() {
        assertTrue(BubbleLaunchCleanup.shouldCancel(123, true));
        assertFalse(BubbleLaunchCleanup.shouldCancel(123, false));
        assertFalse(BubbleLaunchCleanup.shouldCancel(
                TrampolineBubbleHost.NOTIFICATION_ID,
                true));
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
}
