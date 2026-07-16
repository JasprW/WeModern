package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;

import org.junit.Test;

public class NotificationChannelsTest {
    @Test
    public void bubbleReadyMessagesUseDedicatedQuietChannel() {
        assertEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.messageChannelId(false));
        assertEquals(
                NotificationChannels.WECHAT_BUBBLE_MESSAGES,
                NotificationChannels.messageChannelId(true));
        assertNotEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.WECHAT_BUBBLE_MESSAGES);
    }

    @Test
    public void bothAlertingAndBubbleChannelsAreMessageChannels() {
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_MESSAGES));
        assertTrue(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLE_MESSAGES));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_CALLS));
        assertFalse(NotificationChannels.isMessageChannel(
                NotificationChannels.WECHAT_BUBBLE_HOST));
        assertFalse(NotificationChannels.isMessageChannel(null));
    }

    @Test
    public void bubbleHostUsesASeparateVisualAlertChannel() {
        assertNotEquals(
                NotificationChannels.WECHAT_BUBBLE_MESSAGES,
                NotificationChannels.WECHAT_BUBBLE_HOST);
        assertNotEquals(
                NotificationChannels.WECHAT_MESSAGES,
                NotificationChannels.WECHAT_BUBBLE_HOST);
    }

    @Test
    public void disabledImportanceCompletesBubbleMessageOptimization() {
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
