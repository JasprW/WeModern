package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.os.Build;
import android.provider.Settings;

final class NotificationChannels {
    static final String WECHAT_MESSAGES = "wechat_messages_alerts";
    static final String WECHAT_BUBBLED_MESSAGES = "wechat_messages_bubbled_quiet";
    static final String LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS =
            "wechat_messages_bubbles_quiet";
    static final String LEGACY_WECHAT_BUBBLED_MESSAGES_V2 =
            "wechat_messages_bubbles_quiet_v2";
    static final String WECHAT_BUBBLE_HOST = "wechat_bubble_host_visual_alerts";
    static final String WECHAT_INCOMING_CALLS = "wechat_calls_incoming";
    static final String WECHAT_ONGOING_CALLS = "wechat_calls_ongoing";

    private NotificationChannels() {
    }

    static void ensure(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("wechat_messages");
        nm.deleteNotificationChannel("status");
        nm.deleteNotificationChannel("status_alerts");
        nm.deleteNotificationChannel("wechat_calls_live");
        nm.deleteNotificationChannel("wechat_calls_live_quiet");
        // v1.7.1 used this ID before bubble delivery was guarded by the effective system
        // permission. Channel behavior is immutable after creation, so never reuse it.
        nm.deleteNotificationChannel(LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS);
        // This temporary migration ID was correctly configured but exposed an implementation
        // version in its identity. Retire it in favor of the stable semantic ID above.
        nm.deleteNotificationChannel(LEGACY_WECHAT_BUBBLED_MESSAGES_V2);
        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel messages = new NotificationChannel(
                WECHAT_MESSAGES,
                context.getString(R.string.channel_wechat_messages),
                messageDefaultImportance());
        messages.setDescription(context.getString(R.string.channel_wechat_messages_description));
        setMessageLockscreenVisibility(messages);
        messages.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        messages.enableVibration(true);
        if (Build.VERSION.SDK_INT == 29) {
            messages.setAllowBubbles(true);
        }
        NotificationChannel bubbledMessages = new NotificationChannel(
                WECHAT_BUBBLED_MESSAGES,
                context.getString(R.string.channel_wechat_bubbled_messages),
                bubbledMessageDefaultImportance());
        bubbledMessages.setDescription(
                context.getString(R.string.channel_wechat_bubbled_messages_description));
        setMessageLockscreenVisibility(bubbledMessages);
        bubbledMessages.setSound(null, null);
        bubbledMessages.enableVibration(false);
        if (Build.VERSION.SDK_INT == 29) {
            bubbledMessages.setAllowBubbles(true);
        }
        NotificationChannel bubbleHost = new NotificationChannel(
                WECHAT_BUBBLE_HOST,
                context.getString(R.string.channel_wechat_bubble_host),
                bubbleHostDefaultImportance());
        bubbleHost.setDescription(
                context.getString(R.string.channel_wechat_bubble_host_description));
        bubbleHost.setSound(null, null);
        bubbleHost.enableVibration(false);
        if (Build.VERSION.SDK_INT == 29) {
            bubbleHost.setAllowBubbles(true);
        }
        AudioAttributes ringtoneAudio = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_RING)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        NotificationChannel incomingCalls = new NotificationChannel(
                WECHAT_INCOMING_CALLS,
                context.getString(R.string.channel_wechat_incoming_calls),
                incomingCallDefaultImportance());
        incomingCalls.setDescription(
                context.getString(R.string.channel_wechat_incoming_calls_description));
        incomingCalls.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                ringtoneAudio);
        incomingCalls.enableVibration(true);
        NotificationChannel ongoingCalls = new NotificationChannel(
                WECHAT_ONGOING_CALLS,
                context.getString(R.string.channel_wechat_ongoing_calls),
                ongoingCallDefaultImportance());
        ongoingCalls.setDescription(
                context.getString(R.string.channel_wechat_ongoing_calls_description));
        ongoingCalls.setSound(null, null);
        ongoingCalls.enableVibration(false);
        nm.createNotificationChannel(messages);
        nm.createNotificationChannel(bubbledMessages);
        nm.createNotificationChannel(bubbleHost);
        nm.createNotificationChannel(incomingCalls);
        nm.createNotificationChannel(ongoingCalls);
    }

    static String messageChannelId(Context context, String conversationId) {
        boolean conversationEnabled =
                ConversationBubblePreferences.isEnabled(context, conversationId);
        return messageChannelId(isQuietBubblePresentationReady(context), conversationEnabled);
    }

    static String messageChannelId(boolean bubbleReady, boolean conversationEnabled) {
        return bubbleReady && conversationEnabled
                ? WECHAT_BUBBLED_MESSAGES
                : WECHAT_MESSAGES;
    }

    static boolean isMessageChannel(String channelId) {
        return WECHAT_MESSAGES.equals(channelId)
                || WECHAT_BUBBLED_MESSAGES.equals(channelId)
                || LEGACY_WECHAT_BUBBLE_MODE_CONVERSATIONS.equals(channelId)
                || LEGACY_WECHAT_BUBBLED_MESSAGES_V2.equals(channelId);
    }

    static boolean isCallChannel(String channelId) {
        return WECHAT_INCOMING_CALLS.equals(channelId)
                || WECHAT_ONGOING_CALLS.equals(channelId);
    }

    static int incomingCallDefaultImportance() {
        return NotificationManager.IMPORTANCE_HIGH;
    }

    static int messageDefaultImportance() {
        return NotificationManager.IMPORTANCE_HIGH;
    }

    static int bubbledMessageDefaultImportance() {
        return NotificationManager.IMPORTANCE_LOW;
    }

    static int ongoingCallDefaultImportance() {
        return NotificationManager.IMPORTANCE_DEFAULT;
    }

    static int messageLockscreenVisibility() {
        return Notification.VISIBILITY_PUBLIC;
    }

    static int bubbleHostDefaultImportance() {
        return NotificationManager.IMPORTANCE_MIN;
    }

    @SuppressWarnings("deprecation")
    @TargetApi(29)
    static boolean isBubbleHostBubbleAllowed(Context context) {
        if (!ChatBubbleBehavior.isSupported(Build.VERSION.SDK_INT)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        boolean systemAllowed = ChatBubbleBehavior.isSystemAllowed(context);
        boolean allConversationsAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && manager.getBubblePreference() == NotificationManager.BUBBLE_PREFERENCE_ALL;
        NotificationChannel hostChannel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hostChannel = manager.getNotificationChannel(
                    WECHAT_BUBBLE_HOST,
                    TrampolineBubbleHost.SHORTCUT_ID
            );
        } else {
            hostChannel = manager.getNotificationChannel(WECHAT_BUBBLE_HOST);
        }
        boolean hostConversationAllowed = hostChannel != null && hostChannel.canBubble();
        return isBubbleHostBubbleAllowed(
                systemAllowed,
                allConversationsAllowed,
                hostConversationAllowed
        );
    }

    static boolean isBubbleHostBubbleAllowed(
            boolean systemAllowed,
            boolean allConversationsAllowed,
            boolean hostConversationAllowed
    ) {
        return systemAllowed && (allConversationsAllowed || hostConversationAllowed);
    }

    @SuppressWarnings("deprecation")
    @TargetApi(29)
    static boolean isQuietBubblePresentationReady(Context context) {
        if (!ChatBubbleBehavior.isSupported(Build.VERSION.SDK_INT)) return false;
        boolean bubbleReady = ChatBubbleBehavior.isReady(
                ChatBubbleBehavior.isEnabled(context),
                ChatBubbleBehavior.isSystemAllowed(context)
        );
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean allConversationsAllowed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            allConversationsAllowed = manager != null
                    && manager.getBubblePreference()
                    == NotificationManager.BUBBLE_PREFERENCE_ALL;
        } else {
            // Android 10 and 11 expose only the app-wide permission here.
            allConversationsAllowed = bubbleReady;
        }
        boolean trampolineEnabled = BubbleTrampolineBehavior.isEnabled(context);
        boolean hostBubbleAllowed = trampolineEnabled && isBubbleHostBubbleAllowed(context);
        return isQuietBubblePresentationReady(
                bubbleReady,
                trampolineEnabled,
                allConversationsAllowed,
                hostBubbleAllowed
        );
    }

    static boolean isQuietBubblePresentationReady(
            boolean bubbleReady,
            boolean trampolineEnabled,
            boolean allConversationsAllowed,
            boolean hostBubbleAllowed
    ) {
        if (!bubbleReady) return false;
        return trampolineEnabled ? hostBubbleAllowed : allConversationsAllowed;
    }

    @SuppressWarnings("deprecation")
    private static void setMessageLockscreenVisibility(NotificationChannel channel) {
        channel.setLockscreenVisibility(messageLockscreenVisibility());
    }

    static boolean isBubbleHostNotificationMinimized(Context context) {
        if (Build.VERSION.SDK_INT < 26) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(WECHAT_BUBBLE_HOST);
        return channel != null && isMinimizedImportance(channel.getImportance());
    }

    static boolean areBubbleHostNotificationsDisabled(Context context) {
        if (Build.VERSION.SDK_INT < 26) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(WECHAT_BUBBLE_HOST);
        return channel != null && isDisabledImportance(channel.getImportance());
    }

    static boolean isMinimizedImportance(int importance) {
        return importance == NotificationManager.IMPORTANCE_MIN;
    }

    static boolean isDisabledImportance(int importance) {
        return importance == NotificationManager.IMPORTANCE_NONE;
    }
}
