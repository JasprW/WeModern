package me.jaspr.wemodern;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;

final class NotificationChannels {
    static final String WECHAT_MESSAGES = "wechat_messages_alerts";
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
        nm.createNotificationChannel(calls);
        nm.createNotificationChannel(status);
    }
}
