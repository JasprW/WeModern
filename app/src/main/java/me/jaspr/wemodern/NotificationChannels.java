package me.jaspr.wemodern;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;

final class NotificationChannels {
    static final String WECHAT_MESSAGES = "wechat_messages_alerts";
    static final String WECHAT_BUBBLE_MESSAGES = "wechat_messages_bubbles_quiet";
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
        messages.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        messages.enableVibration(true);
        if (Build.VERSION.SDK_INT == 29) {
            messages.setAllowBubbles(true);
        }
        NotificationChannel bubbleMessages = new NotificationChannel(
                WECHAT_BUBBLE_MESSAGES,
                context.getString(R.string.channel_wechat_bubble_messages),
                NotificationManager.IMPORTANCE_LOW);
        bubbleMessages.setDescription(
                context.getString(R.string.channel_wechat_bubble_messages_description));
        bubbleMessages.setSound(null, null);
        bubbleMessages.enableVibration(false);
        if (Build.VERSION.SDK_INT == 29) {
            bubbleMessages.setAllowBubbles(true);
        }
        NotificationChannel bubbleHost = new NotificationChannel(
                WECHAT_BUBBLE_HOST,
                context.getString(R.string.channel_wechat_bubble_host),
                NotificationManager.IMPORTANCE_HIGH);
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
        nm.createNotificationChannel(bubbleMessages);
        nm.createNotificationChannel(bubbleHost);
        nm.createNotificationChannel(calls);
        nm.createNotificationChannel(status);
    }

    static String messageChannelId(Context context) {
        boolean bubbleReady = ChatBubbleBehavior.isReady(
                ChatBubbleBehavior.isEnabled(context),
                ChatBubbleBehavior.isSystemAllowed(context)
        );
        return messageChannelId(bubbleReady);
    }

    static String messageChannelId(boolean bubbleReady) {
        return bubbleReady ? WECHAT_BUBBLE_MESSAGES : WECHAT_MESSAGES;
    }

    static boolean isMessageChannel(String channelId) {
        return WECHAT_MESSAGES.equals(channelId) || WECHAT_BUBBLE_MESSAGES.equals(channelId);
    }

    static boolean areBubbleMessageNotificationsDisabled(Context context) {
        if (Build.VERSION.SDK_INT < 26) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(WECHAT_BUBBLE_MESSAGES);
        return channel != null && isDisabledImportance(channel.getImportance());
    }

    static boolean isDisabledImportance(int importance) {
        return importance == NotificationManager.IMPORTANCE_NONE;
    }
}
