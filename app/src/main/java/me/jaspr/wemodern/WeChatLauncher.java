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
        return open(context, AppIconLaunchPolicy.isLaunchedFromBubble(context), false);
    }

    static boolean openInCurrentTask(Activity activity) {
        return open(activity, true, true);
    }

    private static boolean open(
            Context context,
            boolean keepInCurrentTask,
            boolean replaceBubbleRoot
    ) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(WECHAT_PACKAGE);
        if (launchIntent == null) {
            Log.w(TAG, "WeChat launcher activity cannot be resolved");
            return false;
        }
        launchIntent.setFlags(AppIconLaunchPolicy.adjustWeChatFlags(
                launchIntent.getFlags(),
                keepInCurrentTask
        ));
        if (keepInCurrentTask) {
            // WeChat's launcher treats a second MAIN/LAUNCHER intent as a duplicate launch and
            // immediately finishes when it is hosted in the bubble task. Its own in-app route to
            // LauncherUI uses an explicit, component-only CLEAR_TOP intent instead.
            launchIntent.setAction(null);
            launchIntent.removeCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        try {
            Log.i(TAG, "opening WeChat keepInCurrentTask=" + keepInCurrentTask
                    + " replaceBubbleRoot=" + replaceBubbleRoot
                    + " flags=0x" + Integer.toHexString(launchIntent.getFlags())
                    + " action=" + launchIntent.getAction()
                    + " categories=" + launchIntent.getCategories());
            if (replaceBubbleRoot) {
                // LauncherUI rejects a second instance when another package remains the root of
                // its task. Mark this trampoline as finishing before the launch so WeChat becomes
                // the root of the existing bubble task during its instance check.
                ((Activity) context).finish();
            }
            context.startActivity(launchIntent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to open WeChat", e);
            return false;
        }
    }
}
