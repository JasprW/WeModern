package me.jaspr.wemodern;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class BubbleTrampolineBehavior {
    private static final String PREFERENCES = "bubble_trampoline_behavior";
    private static final String OPEN_WECHAT_IN_BUBBLE = "open_wechat_in_bubble";
    private static final String LEGACY_TEST_MESSAGE_OPENS_WECHAT = "test_message_opens_wechat";

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
        boolean enabled = preferences.getBoolean(
                OPEN_WECHAT_IN_BUBBLE,
                preferences.getBoolean(LEGACY_TEST_MESSAGE_OPENS_WECHAT, false)
        );
        if (enabled && !ChatBubbleBehavior.isReady(
                ChatBubbleBehavior.isEnabled(context),
                ChatBubbleBehavior.isSystemAllowed(context)
        )) {
            setEnabled(context, false);
            return false;
        }
        return enabled;
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(OPEN_WECHAT_IN_BUBBLE,
                        enabled
                                && isSupported(Build.VERSION.SDK_INT)
                                && ChatBubbleBehavior.isReady(
                                        ChatBubbleBehavior.isEnabled(context),
                                        ChatBubbleBehavior.isSystemAllowed(context)))
                .remove(LEGACY_TEST_MESSAGE_OPENS_WECHAT)
                .apply();
    }

    static boolean shouldOpenWeChatHome(String conversationId, boolean enabled) {
        return enabled && conversationId != null;
    }

    static boolean shouldPreserveMessageReplacement(
            boolean enabled,
            boolean hasConversation
    ) {
        // The trampoline task is owned by a dedicated host notification. Ordinary rewritten
        // messages can therefore follow synchronous removal in every mode.
        return false;
    }
}
