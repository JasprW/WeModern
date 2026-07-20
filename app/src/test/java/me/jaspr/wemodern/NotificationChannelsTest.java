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
    public void bubbleReadyConversationsUseQuietMessageChannel() {
        assertEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.messageChannelId(false, true));
        assertEquals(
                NotificationChannels.WECHAT_BUBBLED_MESSAGES,
                NotificationChannels.messageChannelId(true, true));
        assertEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.messageChannelId(true, false));
    }

    @Test
    public void alertingQuietAndLegacyConversationChannelsAreRecognizedAsMessages() {
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLED_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_INCOMING_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_ONGOING_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLE_HOST));
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
    public void incomingCallsAreHighPriorityAndOngoingCallsUseDefaultPriority() {
        assertEquals(
                NotificationManager.IMPORTANCE_HIGH,
                NotificationChannels.messageDefaultImportance());
        assertEquals(
                NotificationManager.IMPORTANCE_LOW,
                NotificationChannels.bubbledMessageDefaultImportance());
        assertEquals(
                NotificationManager.IMPORTANCE_HIGH,
                NotificationChannels.incomingCallDefaultImportance());
        assertEquals(
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationChannels.ongoingCallDefaultImportance());
    }

    @Test
    public void bubbleHostUsesASeparateChannelFromConversationNotifications() {
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
    public void stableQuietChannelDoesNotReuseEitherLegacyIdentity() {
        assertEquals(
                "wechat_messages_bubbles_quiet",
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS);
        assertEquals(
                "wechat_messages_bubbles_quiet_v2",
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2);
        assertEquals(
                "wechat_messages_bubbled_quiet",
                NotificationChannels.WECHAT_BUBBLED_MESSAGES);
        assertNotEquals(
                NotificationChannels.LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS,
                NotificationChannels.messageChannelId(true, true));
        assertNotEquals(
                NotificationChannels.LEGACY_WECHAT_BUBBLED_MESSAGES_V2,
                NotificationChannels.messageChannelId(true, true));
    }

    @Test
    public void normalBubblesBecomeQuietOnlyWhenAllConversationsCanBubble() {
        assertTrue(NotificationChannels.isQuietBubblePresentationReady(
                true, false, true, false));
        assertFalse(NotificationChannels.isQuietBubblePresentationReady(
                true, false, false, false));
        assertFalse(NotificationChannels.isQuietBubblePresentationReady(
                false, false, true, false));
    }

    @Test
    public void trampolineBecomesQuietOnlyWhenItsHostCanBubble() {
        assertTrue(NotificationChannels.isQuietBubblePresentationReady(
                true, true, false, true));
        assertFalse(NotificationChannels.isQuietBubblePresentationReady(
                true, true, true, false));
        assertFalse(NotificationChannels.isQuietBubblePresentationReady(
                false, true, true, true));
    }

    @Test
    public void bubbleHostIsReadyForAllConversationsOrAnAllowedHostConversation() {
        assertTrue(NotificationChannels.isBubbleHostBubbleAllowed(true, true, false));
        assertTrue(NotificationChannels.isBubbleHostBubbleAllowed(true, false, true));
        assertFalse(NotificationChannels.isBubbleHostBubbleAllowed(true, false, false));
        assertFalse(NotificationChannels.isBubbleHostBubbleAllowed(false, true, true));
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
