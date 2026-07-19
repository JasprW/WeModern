package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import org.junit.Test;

public class WeChatCallClassifierTest {
    private static final int ONGOING = Notification.FLAG_ONGOING_EVENT;

    @Test
    public void capturedIncomingCardIsRecognizedWithoutDependingOnDynamicChannel() {
        assertEquals(WeChatCallClassifier.Signal.INCOMING, WeChatCallClassifier.classify(
                0x96,
                "0a4e61c50271c31f#voip_ringtone_channel_1784393751603",
                Notification.CATEGORY_CALL,
                true,
                true,
                "WeChat",
                "米老鼠invites you to a voice call",
                null));
    }

    @Test
    public void capturedVideoCallUsesTheSameIncomingAndStatusSequence() {
        String invitation = "JASPRinvites you to video call";
        String status = "Video call in progress";

        assertEquals(WeChatCallClassifier.Signal.INCOMING, WeChatCallClassifier.classify(
                0x96,
                "0a4e61c50271c31f#voip_ringtone_channel_1784393751603",
                Notification.CATEGORY_CALL,
                true,
                true,
                "WeChat",
                invitation,
                null));
        assertEquals(WeChatCallClassifier.Signal.WAITING_STATUS,
                WeChatCallClassifier.classify(
                        ONGOING,
                        "reminder_channel_id",
                        null,
                        false,
                        false,
                        "JASPR",
                        status,
                        status));
        assertEquals(WeChatCallClassifier.Signal.CONNECTED_STATUS,
                WeChatCallClassifier.classify(
                        ONGOING | Notification.FLAG_NO_CLEAR
                                | Notification.FLAG_FOREGROUND_SERVICE,
                        "reminder_channel_id",
                        null,
                        false,
                        false,
                        "JASPR",
                        status,
                        status));
        assertEquals("JASPR", WeChatCallClassifier.callerName("WeChat", invitation));
        assertTrue(WeChatCallClassifier.isVideo("WeChat", invitation));
        assertTrue(WeChatCallClassifier.isVideo("JASPR", status));
    }

    @Test
    public void inUseStatusDoesNotConnectUntilForegroundAndNoClearFlagsAppear() {
        assertEquals(WeChatCallClassifier.Signal.WAITING_STATUS, WeChatCallClassifier.classify(
                ONGOING,
                "reminder_channel_id",
                null,
                false,
                false,
                "米老鼠",
                "Voice call in use",
                "Voice call in use"));
        assertEquals(WeChatCallClassifier.Signal.CONNECTED_STATUS, WeChatCallClassifier.classify(
                ONGOING | Notification.FLAG_NO_CLEAR | Notification.FLAG_FOREGROUND_SERVICE,
                "reminder_channel_id",
                null,
                false,
                false,
                "米老鼠",
                "Voice call in use",
                "Voice call in use"));
    }

    @Test
    public void connectedFlagsRequireALaterUpdateBeyondTheForegroundTransitionBurst() {
        long firstForegroundServicePost = 1_784_427_321_939L;

        assertFalse(WeChatCallClassifier.isLaterConnectedUpdate(
                firstForegroundServicePost,
                1_784_427_322_119L));
        assertTrue(WeChatCallClassifier.isLaterConnectedUpdate(
                firstForegroundServicePost,
                1_784_427_330_773L));
    }

    @Test
    public void unrelatedOngoingNotificationIsNotCallStatus() {
        assertEquals(WeChatCallClassifier.Signal.NONE, WeChatCallClassifier.classify(
                ONGOING,
                "reminder_channel_id",
                null,
                false,
                false,
                "WeChat",
                "Sync in progress",
                null));
    }

    @Test
    public void callerNameComesFromTitleOrInvitationPrefix() {
        assertEquals("米老鼠", WeChatCallClassifier.callerName(
                "WeChat", "米老鼠invites you to a voice call"));
        assertEquals("妈妈", WeChatCallClassifier.callerName(
                "微信", "妈妈邀请你进行语音通话"));
        assertEquals("Alice", WeChatCallClassifier.callerName(
                "Alice", "Voice call in use"));
    }

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

    @Test
    public void callStatusUsesRollingSnoozeOnlyAfterReplacementIsConnected() {
        int protectedFlags = Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_NO_CLEAR
                | Notification.FLAG_FOREGROUND_SERVICE;

        assertFalse(WeChatNotificationService.shouldUseRollingCallStatusSnooze(
                false,
                protectedFlags
        ));
        assertTrue(WeChatNotificationService.shouldUseRollingCallStatusSnooze(
                true,
                protectedFlags
        ));
        assertFalse(WeChatNotificationService.shouldUseRollingCallStatusSnooze(
                true,
                Notification.FLAG_ONGOING_EVENT
        ));
    }

    @Test
    public void voiceAndVideoTestsReplaceTheSameNotification() {
        assertEquals(CallTestNotifications.idFor(false), CallTestNotifications.idFor(true));
        assertTrue(CallTestNotifications.isTestId(CallTestNotifications.idFor(false)));
        assertTrue(CallTestNotifications.isTestId(CallTestNotifications.LEGACY_VIDEO_ID));
    }
}
