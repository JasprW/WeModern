package me.jaspr.wemodern;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class BubbleTrampolineBehavior {
    private static final String PREFERENCES = "bubble_trampoline_behavior";
    private static final String OPEN_WECHAT_IN_BUBBLE = "open_wechat_in_bubble";
    private static final String OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL =
            "open_conversation_with_bridge_experimental";
    private static final String MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL =
            "multiple_conversation_bubbles_experimental";
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
                .apply();
    }

    static boolean isConversationBridgeEnabled(Context context) {
        if (!isSupported(Build.VERSION.SDK_INT)) return false;
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL, false);
    }

    static void setConversationBridgeEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(
                        OPEN_CONVERSATION_WITH_BRIDGE_EXPERIMENTAL,
                        shouldStoreConversationBridgePreference(
                                enabled,
                                isSupported(Build.VERSION.SDK_INT)
                        )
                )
                .apply();
    }

    static boolean isMultipleConversationBubblesEnabled(Context context) {
        if (!isSupported(Build.VERSION.SDK_INT)) return false;
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL, false);
    }

    static void setMultipleConversationBubblesEnabled(Context context, boolean enabled) {
        boolean storedEnabled = shouldStoreMultipleConversationBubblesPreference(
                enabled,
                isSupported(Build.VERSION.SDK_INT)
        );
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(
                        MULTIPLE_CONVERSATION_BUBBLES_EXPERIMENTAL,
                        storedEnabled
                )
                .apply();
        if (!storedEnabled) {
            TrampolineBubbleSessionState.onIndependentHostsCleared();
        }
    }

    static boolean shouldUseMultipleConversationBubbles(Context context) {
        return shouldUseMultipleConversationBubbles(
                isEnabled(context),
                isConversationBridgeEnabled(context),
                isMultipleConversationBubblesEnabled(context)
        );
    }

    static boolean shouldStoreEnabledPreference(boolean enabled, boolean supported) {
        return enabled && supported;
    }

    static boolean shouldStoreConversationBridgePreference(
            boolean enabled,
            boolean supported
    ) {
        return enabled && supported;
    }

    static boolean shouldStoreMultipleConversationBubblesPreference(
            boolean enabled,
            boolean supported
    ) {
        return enabled && supported;
    }

    static boolean shouldUseMultipleConversationBubbles(
            boolean trampolineEnabled,
            boolean conversationBridgeEnabled,
            boolean multipleConversationBubblesEnabled
    ) {
        return trampolineEnabled
                && conversationBridgeEnabled
                && multipleConversationBubblesEnabled;
    }

    static boolean shouldOpenWeChatHome(String conversationId, boolean enabled) {
        return enabled && conversationId != null;
    }

    static boolean shouldPreserveMessageReplacement(
            boolean enabled,
            boolean hasActiveBubbleHost
    ) {
        // In multi-conversation mode the rewritten notification is the bubble host. Keep that
        // host when WeChat marks one or more source messages read during an embedded launch.
        // The bubble dismissal callback remains responsible for explicit removal.
        return enabled && hasActiveBubbleHost;
    }
}
