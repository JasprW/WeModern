package me.jaspr.wemodern;

import android.content.Context;
import android.os.Build;

final class BubbleTrampolineBehavior {
    private static final String PREFERENCES = "bubble_trampoline_behavior";
    private static final String TEST_MESSAGE_OPENS_WECHAT = "test_message_opens_wechat";

    private BubbleTrampolineBehavior() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.S;
    }

    static boolean isEnabled(Context context) {
        return isSupported(Build.VERSION.SDK_INT)
                && context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(TEST_MESSAGE_OPENS_WECHAT, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(TEST_MESSAGE_OPENS_WECHAT, enabled && isSupported(Build.VERSION.SDK_INT))
                .apply();
    }

    static boolean shouldOpenWeChatHome(String conversationId, boolean enabled) {
        return enabled && MessageTestNotifications.isTestShortcutId(conversationId);
    }
}
