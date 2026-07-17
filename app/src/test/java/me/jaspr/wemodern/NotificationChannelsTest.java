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
    public void onlyBubbleEligibleConversationsUseQuietChannel() {
        assertEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.messageChannelId(false, true));
        assertEquals(
                NotificationChannels.WECHAT_BUBBLE_MODE_CONVERSATIONS,
                NotificationChannels.messageChannelId(true, true));
        assertEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.messageChannelId(true, false));
        assertNotEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.WECHAT_BUBBLE_MODE_CONVERSATIONS);
    }

    @Test
    public void bothAlertingAndBubbleModeChannelsAreMessageChannels() {
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLE_MODE_CONVERSATIONS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLE_HOST));
        assertFalse(NotificationChannels.isMessageChannel(null));
    }

    @Test
    public void bubbleHostUsesASeparateChannelFromConversationNotifications() {
        assertNotEquals(
                NotificationChannels.WECHAT_BUBBLE_MODE_CONVERSATIONS,
                NotificationChannels.WECHAT_BUBBLE_HOST);
        assertNotEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.WECHAT_BUBBLE_HOST);
    }

    @Test
    public void bubbleHostIsMinimizedByDefault() {
        assertEquals(
                NotificationManager.IMPORTANCE_MIN,
                NotificationChannels.bubbleHostDefaultImportance());
    }

    @Test
    public void renamedConversationChannelKeepsPersistedId() {
        assertEquals(
                "wechat_messages_bubbles_quiet",
                NotificationChannels.WECHAT_BUBBLE_MODE_CONVERSATIONS);
    }

    @Test
    public void minimumImportanceCompletesBubbleHostOptimization() {
        assertTrue(NotificationChannels.isMinimizedImportance(
                NotificationManager.IMPORTANCE_MIN));
        assertFalse(NotificationChannels.isMinimizedImportance(
                NotificationManager.IMPORTANCE_NONE));
        assertFalse(NotificationChannels.isMinimizedImportance(
                NotificationManager.IMPORTANCE_LOW));
        assertFalse(NotificationChannels.isMinimizedImportance(
                NotificationManager.IMPORTANCE_HIGH));
    }

    @Test
    public void disabledImportanceDoesNotCountAsMinimizedBubbleHost() {
        assertTrue(NotificationChannels.isDisabledImportance(
                NotificationManager.IMPORTANCE_NONE));
        assertFalse(NotificationChannels.isDisabledImportance(
                NotificationManager.IMPORTANCE_MIN));
        assertFalse(NotificationChannels.isDisabledImportance(
                NotificationManager.IMPORTANCE_LOW));
        assertFalse(NotificationChannels.isDisabledImportance(
                NotificationManager.IMPORTANCE_HIGH));
    }
}
