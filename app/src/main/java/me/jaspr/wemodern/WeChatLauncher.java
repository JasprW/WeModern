package me.jaspr.wemodern;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
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

    static boolean openConversationInCurrentTask(
            Activity activity,
            PendingIntent contentIntent
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || contentIntent == null) {
            return false;
        }
        if (!AppIconLaunchPolicy.canLaunchWeChatActivityIntent(
                contentIntent.getCreatorPackage(),
                contentIntent.isActivity()
        )) {
            Log.w(TAG, "conversation PendingIntent is not a WeChat activity"
                    + ", creator=" + contentIntent.getCreatorPackage());
            return false;
        }
        try {
            Log.i(TAG, "opening WeChat conversation in current bubble"
                    + ", creator=" + contentIntent.getCreatorPackage()
                    + ", immutable=" + contentIntent.isImmutable()
                    + ", flagsMask=0x" + Integer.toHexString(
                    AppIconLaunchPolicy.taskSeparatingFlags()));
            // As with the launcher trampoline, WeChat must become the bubble task root before its
            // LauncherUI instance check runs. startIntentSender also lets us clear task-separating
            // flags while preserving WeChat's private conversation extras.
            activity.finish();
            activity.startIntentSender(
                    contentIntent.getIntentSender(),
                    null,
                    AppIconLaunchPolicy.taskSeparatingFlags(),
                    0,
                    0
            );
            return true;
        } catch (IntentSender.SendIntentException | RuntimeException e) {
            Log.w(TAG, "failed to open WeChat conversation in current bubble", e);
            return false;
        }
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
            boolean keepInCurrentTask,
            boolean replaceBubbleRoot
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
