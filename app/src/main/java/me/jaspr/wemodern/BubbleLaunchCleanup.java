package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** Closes active WeModern bubbles before handing the user to the full WeChat app. */
final class BubbleLaunchCleanup {
    private static final String TAG = "WeModern";
    private static final String PREFERENCES = "bubble_launch_cleanup";
    private static final String KEY_SUPPRESS_APP_CANCEL_UNTIL = "suppress_app_cancel_until";
    private static final long APP_CANCEL_SUPPRESSION_MS = 5000L;

    private BubbleLaunchCleanup() {
    }

    static void clear(Context context) {
        clearAppCancelSuppression(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        StatusBarNotification[] active;
        try {
            active = manager.getActiveNotifications();
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to inspect active bubbles before opening WeChat", e);
            return;
        }
        if (active == null || active.length == 0) return;

        boolean hasTrampolineHost = false;
        int cleared = 0;
        for (StatusBarNotification sbn : active) {
            Notification notification = sbn.getNotification();
            if (TrampolineBubbleHost.isHostNotificationId(sbn.getId())) {
                hasTrampolineHost = true;
                continue;
            }
            if (!shouldCancel(sbn.getId(), notification.getBubbleMetadata() != null)) continue;

            String conversationId = notification.getShortcutId();
            ConversationBubbleStore.remove(conversationId);
            WeChatNotificationService.forgetPersistedReplacementsForReplacementId(
                    context,
                    sbn.getId()
            );
            manager.cancel(sbn.getTag(), sbn.getId());
            cleared++;
        }
        if (hasTrampolineHost) {
            TrampolineBubbleHost.clear(context);
            cleared++;
        }
        if (cleared > 0) {
            Log.i(TAG, "closed active bubbles before opening WeChat: count=" + cleared);
        }
    }

    static void suppressAppCancelForTrampolineLaunch(Context context) {
        long suppressUntil = System.currentTimeMillis() + APP_CANCEL_SUPPRESSION_MS;
        preferences(context).edit()
                .putLong(KEY_SUPPRESS_APP_CANCEL_UNTIL, suppressUntil)
                .apply();
        Log.d(TAG, "temporarily suppressing bubble cleanup for embedded WeChat launch");
    }

    static void clearAfterWeChatAppCancel(Context context) {
        if (shouldKeepAfterWeChatAppCancel(
                TrampolineBubbleSessionState.isEmbeddedSessionActive(),
                WeChatForegroundState.isWeChatForeground()
        )) {
            Log.d(TAG, "keeping trampoline bubble during embedded WeChat session");
            return;
        }
        long now = System.currentTimeMillis();
        long suppressUntil = preferences(context).getLong(KEY_SUPPRESS_APP_CANCEL_UNTIL, 0L);
        if (shouldSuppressAppCancelCleanup(now, suppressUntil)) {
            Log.d(TAG, "keeping trampoline bubble after embedded WeChat app cancel");
            return;
        }
        clear(context);
    }

    static void clearAppCancelSuppression(Context context) {
        preferences(context).edit().remove(KEY_SUPPRESS_APP_CANCEL_UNTIL).apply();
    }

    static boolean shouldSuppressAppCancelCleanup(long now, long suppressUntil) {
        return suppressUntil > now;
    }

    static boolean shouldKeepAfterWeChatAppCancel(
            boolean embeddedSessionActive,
            boolean fullScreenWeChatForeground
    ) {
        return embeddedSessionActive && !fullScreenWeChatForeground;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static boolean shouldCancel(int notificationId, boolean hasBubbleMetadata) {
        return hasBubbleMetadata && !TrampolineBubbleHost.isHostNotificationId(notificationId);
    }
}
