package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class AppIconLaunchPolicy {
    private AppIconLaunchPolicy() {
    }

    static int adjustWeChatFlags(int originalFlags, boolean launchedFromBubble) {
        if (!launchedFromBubble) return originalFlags;
        return originalFlags & ~Intent.FLAG_ACTIVITY_NEW_TASK;
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
