package me.jaspr.wemodern;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.util.Log;

import java.util.Collections;

final class BubbleTrampolineBehavior {
    private static final String TAG = "WeModern";
    private static final String PREFERENCES = "bubble_trampoline_behavior";
    private static final String OPEN_WECHAT_IN_BUBBLE = "open_wechat_in_bubble";
    private static final String LEGACY_OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL =
            "open_conversation_with_bridge_experimental";
    private static final String LEGACY_MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL =
            "multiple_conversation_bubbles_experimental";
    private static final String LEGACY_TEST_MESSAGE_OPENS_WECHAT = "test_message_opens_wechat";
    private static final String LEGACY_HOST_STATE_PREFERENCES = "trampoline_bubble_host_state";
    private static final String LEGACY_HOST_SHORTCUT_ID = "wemodern_wechat_bubble_host";
    private static final int LEGACY_HOST_NOTIFICATION_ID = 0x57424853;
    private static final int LEGACY_HOST_PRIMARY_NOTIFICATION_ID = 0x57424854;
    private static final int LEGACY_HOST_SECONDARY_NOTIFICATION_ID = 0x57424855;

    private BubbleTrampolineBehavior() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.S;
    }

    static boolean isEnabled(Context context) {
        if (!isSupported(Build.VERSION.SDK_INT)) return false;
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
        return preferences.getBoolean(
                OPEN_WECHAT_IN_BUBBLE,
                preferences.getBoolean(LEGACY_TEST_MESSAGE_OPENS_WECHAT, false)
        );
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(
                        OPEN_WECHAT_IN_BUBBLE,
                        shouldStoreEnabledPreference(enabled, isSupported(Build.VERSION.SDK_INT))
                )
                .remove(LEGACY_TEST_MESSAGE_OPENS_WECHAT)
                .remove(LEGACY_OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL)
                .remove(LEGACY_MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL)
                .apply();
    }

    static void removeLegacySingleHost(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(LEGACY_OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL)
                .remove(LEGACY_MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL)
                .apply();
        context.deleteSharedPreferences(LEGACY_HOST_STATE_PREFERENCES);
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(LEGACY_HOST_NOTIFICATION_ID);
            notificationManager.cancel(LEGACY_HOST_PRIMARY_NOTIFICATION_ID);
            notificationManager.cancel(LEGACY_HOST_SECONDARY_NOTIFICATION_ID);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;
        try {
            shortcutManager.removeDynamicShortcuts(
                    Collections.singletonList(LEGACY_HOST_SHORTCUT_ID));
            shortcutManager.removeLongLivedShortcuts(
                    Collections.singletonList(LEGACY_HOST_SHORTCUT_ID));
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to remove legacy trampoline host shortcut", e);
        }
    }

    static boolean shouldStoreEnabledPreference(boolean enabled, boolean supported) {
        return enabled && supported;
    }

    static boolean shouldPreserveMessageReplacement(
            boolean enabled,
            boolean hasActiveBubbleHost
    ) {
        // In trampoline mode the rewritten notification is the bubble host. Keep that host when
        // WeChat marks one or more source messages read during an embedded launch.
        // The bubble dismissal callback remains responsible for explicit removal.
        return enabled && hasActiveBubbleHost;
    }
}
