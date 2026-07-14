package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class AppIconLaunchPolicy {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    private AppIconLaunchPolicy() {
    }

    static int taskSeparatingFlags() {
        return Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
    }

    static int adjustWeChatFlags(int originalFlags, boolean launchedFromBubble) {
        if (!launchedFromBubble) return originalFlags;
        return originalFlags & ~taskSeparatingFlags();
    }

    static boolean canLaunchWeChatActivityIntent(String creatorPackage, boolean isActivity) {
        return isActivity && WECHAT_PACKAGE.equals(creatorPackage);
    }

    static int adjustSettingsFlags(int originalFlags, boolean launchedFromBubble) {
        if (launchedFromBubble) return originalFlags;
        return originalFlags | Intent.FLAG_ACTIVITY_NEW_TASK;
    }

    static boolean shouldForwardFromSettings(
            boolean launchedFromBubble,
            boolean openWeChatEnabled
    ) {
        return launchedFromBubble && openWeChatEnabled;
    }

    static boolean isLaunchedFromBubble(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && context instanceof Activity
                && ((Activity) context).isLaunchedFromBubble();
    }
}
