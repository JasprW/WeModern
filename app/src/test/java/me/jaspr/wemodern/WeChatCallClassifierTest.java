package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import org.junit.Test;

public class WeChatCallClassifierTest {
    private static final int ONGOING = Notification.FLAG_ONGOING_EVENT;

    @Test
    public void legacyVoipChannelRemainsSupported() {
        assertTrue(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "voip_notify_channel_new_id", "妈妈", ""));
    }

    @Test
    public void reminderChannelVideoCallIsRecognized() {
        assertTrue(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "reminder_channel_id", "妈妈", "视频通话中"));
    }

    @Test
    public void reminderChannelVoiceCallIsRecognized() {
        assertTrue(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "reminder_channel_id", "妈妈", "语音通话中"));
        assertTrue(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "reminder_channel_id", "Mom", "Voice call in progress"));
        assertTrue(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "reminder_channel_id", "MOM", "VOICE CALL IN PROGRESS"));
    }

    @Test
    public void unrelatedOngoingReminderIsNotRecognizedAsCall() {
        assertFalse(WeChatNotificationService.WeChatParser.isVoipNotification(
                ONGOING, "reminder_channel_id", "微信", "正在运行"));
    }

    @Test
    public void nonOngoingCallTextIsNotRecognizedAsActiveCall() {
        assertFalse(WeChatNotificationService.WeChatParser.isVoipNotification(
                0, "reminder_channel_id", "妈妈", "视频通话中"));
    }

    @Test
    public void detectsVideoCallAcrossSupportedLanguages() {
        assertTrue(WeChatNotificationService.WeChatParser.isVideoCall("妈妈", "视频通话中"));
        assertTrue(WeChatNotificationService.WeChatParser.isVideoCall("媽媽", "視訊通話中"));
        assertTrue(WeChatNotificationService.WeChatParser.isVideoCall("Mom", "Video call in progress"));
        assertTrue(WeChatNotificationService.WeChatParser.isVideoCall("MOM", "VIDEO CALL IN PROGRESS"));
        assertFalse(WeChatNotificationService.WeChatParser.isVideoCall("妈妈", "语音通话中"));
    }

    @Test
    public void foregroundAndNoClearOriginalsRequireSnoozing() {
        assertTrue(WeChatNotificationService.shouldSnoozeOriginal(
                Notification.FLAG_FOREGROUND_SERVICE));
        assertTrue(WeChatNotificationService.shouldSnoozeOriginal(
                Notification.FLAG_NO_CLEAR));
        assertFalse(WeChatNotificationService.shouldSnoozeOriginal(
                Notification.FLAG_AUTO_CANCEL));
    }
}
