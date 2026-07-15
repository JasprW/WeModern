package me.jaspr.wemodern;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class WeChatLauncher {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    private WeChatLauncher() {
    }

    static boolean open(Context context) {
        return open(context, AppIconLaunchPolicy.isLaunchedFromBubble(context));
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

    private static boolean open(
            Context context,
            boolean keepInCurrentTask
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
        try {
            Log.i(TAG, "opening WeChat keepInCurrentTask=" + keepInCurrentTask
                    + " flags=0x" + Integer.toHexString(launchIntent.getFlags())
                    + " action=" + launchIntent.getAction()
                    + " categories=" + launchIntent.getCategories());
            context.startActivity(launchIntent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to open WeChat", e);
            return false;
        }
    }
}
