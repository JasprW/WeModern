package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;

import org.junit.Test;

public class NotificationChannelsTest {
    @Test
    public void conversationNotificationsShowFullContentOnLockscreenByDefault() {
        assertEquals(
                Notification.VISIBILITY_PUBLIC,
                NotificationChannels.messageLockscreenVisibility());
    }

    @Test
    public void everyConversationNotificationUsesTheAlertingMessageChannel() {
        assertEquals(
                "wechat_messages_alerts",
                NotificationChannels.WECHAT_MESSAGES);
    }

    @Test
    public void currentAndLegacyConversationChannelsAreRecognizedAsMessages() {
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_INCOMING_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_ONGOING_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLE_HOST));
        assertFalse(NotificationChannels.isMessageChannel(null));
    }

    @Test
    public void callsUseSeparateStableChannelsForIncomingAndOngoingStates() {
        assertEquals(
                "wechat_calls_incoming",
                NotificationChannels.WECHAT_INCOMING_CALLS);
        assertEquals(
                "wechat_calls_ongoing",
                NotificationChannels.WECHAT_ONGOING_CALLS);
        assertNotEquals(
                NotificationChannels.WECHAT_INCOMING_CALLS,
                NotificationChannels.WECHAT_ONGOING_CALLS);
        assertTrue(NotificationChannels.isCallChannel(
                NotificationChannels.WECHAT_INCOMING_CALLS));
        assertTrue(NotificationChannels.isCallChannel(
                NotificationChannels.WECHAT_ONGOING_CALLS));
        assertFalse(NotificationChannels.isCallChannel(
                NotificationChannels.WECHAT_MESSAGES));
        assertFalse(NotificationChannels.isCallChannel(null));
    }

    @Test
    public void messagesAndIncomingCallsAreHighPriorityAndOngoingCallsUseDefaultPriority() {
        assertEquals(
                NotificationManager.IMPORTANCE_HIGH,
                NotificationChannels.messageDefaultImportance());
        assertEquals(
                NotificationManager.IMPORTANCE_HIGH,
                NotificationChannels.incomingCallDefaultImportance());
        assertEquals(
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationChannels.ongoingCallDefaultImportance());
    }

    @Test
    public void allQuietMessageChannelIdentitiesAreMigrationOnly() {
        assertEquals(
                "wechat_messages_bubbles_quiet",
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS);
        assertEquals(
                "wechat_messages_bubbles_quiet_v2",
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2);
        assertEquals(
                "wechat_messages_bubbled_quiet",
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES);
        assertNotEquals(
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS,
                NotificationChannels.WECHAT_MESSAGES);
        assertNotEquals(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2,
                NotificationChannels.WECHAT_MESSAGES);
        assertNotEquals(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES,
                NotificationChannels.WECHAT_MESSAGES);
    }
}
