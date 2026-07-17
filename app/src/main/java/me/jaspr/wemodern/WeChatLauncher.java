package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class WeChatLauncher {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    private WeChatLauncher() {
    }

    static boolean open(Context context) {
        return open(
                context,
                AppIconLaunchPolicy.isLaunchedFromBubble(context),
                true,
                true
        );
    }

    static Intent createBubbleRootIntent(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(WECHAT_PACKAGE);
        if (launchIntent == null) return null;
        launchIntent.setFlags(AppIconLaunchPolicy.adjustWeChatFlags(
                launchIntent.getFlags(),
                true
        ));
        launchIntent.setAction(null);
        launchIntent.removeCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return launchIntent;
    }

    static boolean openFromBubbleFallback(Activity activity) {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(activity.getTaskId());
        BubbleLaunchCleanup.suppressAppCancelForTrampolineLaunch(activity);
        // This path is used only if SystemUI restores the conversation shortcut intent instead
        // of the BubbleMetadata PendingIntent. Let WeChat replace the fallback Activity as root.
        activity.finish();
        boolean opened = open(activity, true, false, false);
        if (!opened) {
            TrampolineBubbleSessionState.onHostCleared();
            BubbleLaunchCleanup.clearAppCancelSuppression(activity);
        }
        return opened;
    }

    static boolean isBubbleRootActivity(String componentName, String action) {
        if (action != null || componentName == null) return false;
        return componentName.equals(WECHAT_PACKAGE + "/.ui.LauncherUI")
                || componentName.equals(WECHAT_PACKAGE + "/com.tencent.mm.ui.LauncherUI");
    }

    private static boolean open(
            Context context,
            boolean keepInCurrentTask,
            boolean clearBubbles,
            boolean markWeChatForeground
    ) {
        Intent launchIntent = keepInCurrentTask
                ? createBubbleRootIntent(context)
                : context.getPackageManager().getLaunchIntentForPackage(WECHAT_PACKAGE);
        if (launchIntent == null) {
            Log.w(TAG, "WeChat launcher activity cannot be resolved");
            return false;
        }
        if (!keepInCurrentTask) {
            launchIntent.setFlags(AppIconLaunchPolicy.adjustWeChatFlags(
                    launchIntent.getFlags(),
                    false
            ));
        }
        if (clearBubbles) BubbleLaunchCleanup.clear(context);
        try {
            Log.i(TAG, "opening WeChat keepInCurrentTask=" + keepInCurrentTask
                    + " flags=0x" + Integer.toHexString(launchIntent.getFlags())
                    + " action=" + launchIntent.getAction()
                    + " categories=" + launchIntent.getCategories());
            context.startActivity(launchIntent);
            if (markWeChatForeground) WeChatForegroundState.onWeChatLaunchSucceeded();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to open WeChat", e);
            return false;
        }
    }
}
