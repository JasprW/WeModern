package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;

final class NotificationChannels {
    static final String WECHAT_MESSAGES = "wechat_messages_alerts";
    // Keep the persisted channel ID stable, but name the symbol after its real role:
    // ordinary per-conversation notifications posted while bubble mode is ready.
    static final String WECHAT_BUBBLE_MODE_CONVERSATIONS = "wechat_messages_bubbles_quiet";
    static final String WECHAT_BUBBLE_HOST = "wechat_bubble_host_visual_alerts";
    static final String WECHAT_CALLS = "wechat_calls_live_quiet";
    static final String STATUS = "status_alerts";

    private NotificationChannels() {
    }

    static void ensure(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("wechat_messages");
        nm.deleteNotificationChannel("status");
        nm.deleteNotificationChannel("wechat_calls_live");
        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel messages = new NotificationChannel(
                WECHAT_MESSAGES,
                context.getString(R.string.channel_wechat_messages),
                NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription(context.getString(R.string.channel_wechat_messages_description));
        setMessageLockscreenVisibility(messages);
        messages.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        messages.enableVibration(true);
        if (Build.VERSION.SDK_INT == 29) {
            messages.setAllowBubbles(true);
        }
        NotificationChannel bubbleModeConversations = new NotificationChannel(
                WECHAT_BUBBLE_MODE_CONVERSATIONS,
                context.getString(R.string.channel_wechat_bubble_mode_conversations),
                NotificationManager.IMPORTANCE_LOW);
        bubbleModeConversations.setDescription(
                context.getString(
                        R.string.channel_wechat_bubble_mode_conversations_description));
        setMessageLockscreenVisibility(bubbleModeConversations);
        bubbleModeConversations.setSound(null, null);
        bubbleModeConversations.enableVibration(false);
        if (Build.VERSION.SDK_INT == 29) {
            bubbleModeConversations.setAllowBubbles(true);
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
        NotificationChannel status = new NotificationChannel(
                STATUS,
                context.getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_HIGH);
        status.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        status.enableVibration(true);
        NotificationChannel calls = new NotificationChannel(
                WECHAT_CALLS,
                context.getString(R.string.channel_wechat_calls),
                NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription(context.getString(R.string.channel_wechat_calls_description));
        calls.setSound(null, null);
        calls.enableVibration(false);
        nm.createNotificationChannel(messages);
        nm.createNotificationChannel(bubbleModeConversations);
        nm.createNotificationChannel(bubbleHost);
        nm.createNotificationChannel(calls);
        nm.createNotificationChannel(status);
    }

    static String messageChannelId(Context context, String conversationId) {
        boolean bubbleReady = ChatBubbleBehavior.isReady(
                ChatBubbleBehavior.isEnabled(context),
                ChatBubbleBehavior.isSystemAllowed(context)
        );
        boolean conversationEnabled =
                ConversationBubblePreferences.isEnabled(context, conversationId);
        return messageChannelId(bubbleReady, conversationEnabled);
    }

    static String messageChannelId(boolean bubbleReady, boolean conversationEnabled) {
        return bubbleReady && conversationEnabled
                ? WECHAT_BUBBLE_MODE_CONVERSATIONS
                : WECHAT_MESSAGES;
    }

    static boolean isMessageChannel(String channelId) {
        return WECHAT_MESSAGES.equals(channelId)
                || WECHAT_BUBBLE_MODE_CONVERSATIONS.equals(channelId);
    }

    static int messageLockscreenVisibility() {
        return Notification.VISIBILITY_PUBLIC;
    }

    static int bubbleHostDefaultImportance() {
        return NotificationManager.IMPORTANCE_MIN;
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
