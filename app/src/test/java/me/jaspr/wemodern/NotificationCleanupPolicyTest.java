package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.service.notification.NotificationListenerService;

import org.junit.Test;

public class NotificationCleanupPolicyTest {
    @Test
    public void knownSelfNotificationsAreNotTreatedAsOrphanReplacements() {
        assertTrue(WeChatNotificationService.shouldKeepSelfNotification(
                true,
                false,
                false,
                false,
                false));
        assertTrue(WeChatNotificationService.shouldKeepSelfNotification(
                false,
                true,
                false,
                false,
                false));
        assertTrue(WeChatNotificationService.shouldKeepSelfNotification(
                false,
                false,
                true,
                false,
                false));
        assertTrue(WeChatNotificationService.shouldKeepSelfNotification(
                false,
                false,
                false,
                true,
                false));
        assertTrue(WeChatNotificationService.shouldKeepSelfNotification(
                false,
                false,
                false,
                false,
                true));
        assertFalse(WeChatNotificationService.shouldKeepSelfNotification(
                false,
                false,
                false,
                false,
                false));
    }

    @Test
    public void messageSummaryIsRemovedOnlyAfterLastChild() {
        assertTrue(WeChatNotificationService.shouldRemoveMessageGroupSummary(0));
        assertFalse(WeChatNotificationService.shouldRemoveMessageGroupSummary(1));
        assertFalse(WeChatNotificationService.shouldRemoveMessageGroupSummary(2));
    }

    @Test
    public void appDrivenWeChatRemovalTriggersBubbleCleanup() {
        assertTrue(WeChatNotificationService.shouldClearBubblesAfterWeChatRemoval(0));
        assertTrue(WeChatNotificationService.shouldClearBubblesAfterWeChatRemoval(
                NotificationListenerService.REASON_APP_CANCEL));
        assertTrue(WeChatNotificationService.shouldClearBubblesAfterWeChatRemoval(
                NotificationListenerService.REASON_APP_CANCEL_ALL));
        assertFalse(WeChatNotificationService.shouldClearBubblesAfterWeChatRemoval(
                NotificationListenerService.REASON_CANCEL));
        assertFalse(WeChatNotificationService.shouldClearBubblesAfterWeChatRemoval(10));
    }
}
