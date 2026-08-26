package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrampolineBubbleHostTest {
    @Test
    public void sharedHostRequiresSharedModeAndAUsableLauncher() {
        assertTrue(TrampolineBubbleHost.shouldPost(
                true, true, false, true, true, true, true, false));
        assertFalse(TrampolineBubbleHost.shouldPost(
                true, true, true, true, true, true, true, false));
        assertFalse(TrampolineBubbleHost.shouldPost(
                true, true, false, true, true, true, false, false));
        assertFalse(TrampolineBubbleHost.shouldPost(
                true, true, false, true, true, true, true, true));
    }

    @Test
    public void hostSourceExcludesItselfAndGroupSummary() {
        assertTrue(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES, 42, "wechat_alice", false));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES,
                TrampolineBubbleHost.NOTIFICATION_ID,
                TrampolineBubbleHost.SHORTCUT_ID,
                false));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_MESSAGES, 42, "wechat_alice", true));
        assertFalse(TrampolineBubbleHost.isEligibleSource(
                NotificationChannels.WECHAT_INCOMING_CALLS, 42, "wechat_alice", false));
    }

    @Test
    public void fixedHostIdentityDoesNotOverlapConversationBubbleIdentity() {
        assertEquals(TrampolineBubbleHost.requestCode(), TrampolineBubbleHost.requestCode());
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat_alice"),
                TrampolineBubbleHost.requestCode());
        assertTrue(TrampolineBubbleHost.isNewerSource(200L, 100L));
        assertFalse(TrampolineBubbleHost.isNewerSource(100L, 100L));
    }
}
