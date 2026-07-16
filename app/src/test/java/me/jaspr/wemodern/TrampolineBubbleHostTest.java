package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrampolineBubbleHostTest {
    @Test
    public void hostUsesFixedIdentityOutsideExistingNotificationNamespaces() {
        assertTrue(TrampolineBubbleHost.isHostNotificationId(
                TrampolineBubbleHost.NOTIFICATION_ID));
        assertFalse(TrampolineBubbleHost.isHostNotificationId(
                MessageTestNotifications.CURRENT_ID));
        assertNotEquals(
                MessageTestNotifications.SHORTCUT_ID,
                TrampolineBubbleHost.SHORTCUT_ID);
    }

    @Test
    public void hostRequiresReadyTrampolineAndUsableSource() {
        assertTrue(TrampolineBubbleHost.shouldPost(true, true, true, true, true));
        assertFalse(TrampolineBubbleHost.shouldPost(false, true, true, true, true));
        assertFalse(TrampolineBubbleHost.shouldPost(true, false, true, true, true));
        assertFalse(TrampolineBubbleHost.shouldPost(true, true, false, true, true));
        assertFalse(TrampolineBubbleHost.shouldPost(true, true, true, false, true));
        assertFalse(TrampolineBubbleHost.shouldPost(true, true, true, true, false));
    }

    @Test
    public void hostSourceExcludesItselfAndGroupSummary() {
        assertTrue(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES,
                42,
                "wechat_alice",
                false));
        assertTrue(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_BUBBLE_MESSAGES,
                42,
                "wechat_alice",
                false));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES,
                TrampolineBubbleHost.NOTIFICATION_ID,
                TrampolineBubbleHost.SHORTCUT_ID,
                false));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES,
                42,
                "wechat_alice",
                true));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_CALLS,
                42,
                "wechat_alice",
                false));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES,
                42,
                null,
                false));
    }

    @Test
    public void newestEligibleNotificationWinsHostSourceSelection() {
        assertTrue(TrampolineBubbleHost.isNewerSource(200L, 100L));
        assertFalse(TrampolineBubbleHost.isNewerSource(100L, 200L));
        assertFalse(TrampolineBubbleHost.isNewerSource(100L, 100L));
    }

    @Test
    public void fixedHostRequestCodeIsStable() {
        assertEquals(
                TrampolineBubbleHost.requestCode(),
                TrampolineBubbleHost.requestCode());
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat_alice"),
                TrampolineBubbleHost.requestCode());
    }

    @Test
    public void hostStaysCollapsedAndExposesMessageUpdates() {
        assertFalse(TrampolineBubbleHost.shouldAutoExpand());
        assertFalse(TrampolineBubbleHost.shouldSuppressNotification());
        assertFalse(TrampolineBubbleHost.shouldOnlyAlertOnce());
    }
}
