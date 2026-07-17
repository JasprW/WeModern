package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

final class ChatBubbleBehavior {
    private static final String PREFERENCES = "chat_bubble_behavior";
    private static final String ENABLED = "enabled";

    private ChatBubbleBehavior() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.Q;
    }

    static boolean isEnabled(Context context) {
        return isSupported(Build.VERSION.SDK_INT)
                && context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ENABLED, enabled && isSupported(Build.VERSION.SDK_INT))
                .apply();
    }

    @SuppressWarnings("deprecation")
    @TargetApi(29)
    static boolean isSystemAllowed(Context context) {
        if (!isSupported(Build.VERSION.SDK_INT)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int preference = manager.getBubblePreference();
            return manager.areBubblesEnabled()
                    && preference != NotificationManager.BUBBLE_PREFERENCE_NONE;
        }
        return manager.areBubblesAllowed();
    }

    static boolean isReady(boolean enabled, boolean systemAllowed) {
        return enabled && systemAllowed;
    }

    static boolean canEnable(boolean coreReady, boolean systemAllowed) {
        return coreReady && systemAllowed;
    }

    static boolean canUseTrampoline(
            boolean coreReady,
            boolean enabled,
            boolean systemAllowed
    ) {
        return canEnable(coreReady, systemAllowed) && enabled;
    }
}
