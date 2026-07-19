package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallTestNotificationsTest {
    @Test
    public void callTestsReuseMessageMockContactIdentity() {
        assertEquals(
                R.string.test_message_sender,
                CallTestNotifications.mockCallerNameResource()
        );
        assertEquals(
                R.drawable.ic_test_message_avatar_48,
                CallTestNotifications.mockCallerAvatarResource()
        );
    }

    @Test
    public void testCallActionsFollowTwoStageLifecycle() {
        assertEquals(
                CallTestNotifications.Command.SHOW_INCOMING,
                CallTestNotifications.commandFor(CallTestNotifications.ACTION_START)
        );
        assertEquals(
                CallTestNotifications.Command.SHOW_ONGOING,
                CallTestNotifications.commandFor(CallTestNotifications.ACTION_ANSWER)
        );
        assertEquals(
                CallTestNotifications.Command.STOP,
                CallTestNotifications.commandFor(CallTestNotifications.ACTION_DECLINE)
        );
        assertEquals(
                CallTestNotifications.Command.STOP,
                CallTestNotifications.commandFor(CallTestNotifications.ACTION_HANG_UP)
        );
        assertEquals(
                CallTestNotifications.Command.IGNORE,
                CallTestNotifications.commandFor("unknown")
        );
    }

    @Test
    public void voiceAndVideoActionPendingIntentsHaveSeparateIdentities() {
        assertNotEquals(
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_ANSWER, false, 1L),
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_ANSWER, true, 1L)
        );
        assertNotEquals(
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_ANSWER, false, 1L),
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_DECLINE, false, 1L)
        );
        assertNotEquals(
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_HANG_UP, false, 1L),
                CallTestNotifications.requestCodeFor("content", false, 1L)
        );
        assertNotEquals(
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_HANG_UP, false, 1L),
                CallTestNotifications.requestCodeFor(
                        CallTestNotifications.ACTION_HANG_UP, false, 2L)
        );
    }

    @Test
    public void staleNotificationActionDoesNotMatchNewTestSession() {
        assertTrue(CallTestNotifications.isCurrentSession(2L, false, 2L, false));
        assertFalse(CallTestNotifications.isCurrentSession(2L, false, 1L, false));
        assertFalse(CallTestNotifications.isCurrentSession(2L, false, 2L, true));
    }
}
