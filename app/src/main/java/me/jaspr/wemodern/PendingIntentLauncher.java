package me.jaspr.wemodern;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.os.Build;
import android.util.Log;

final class PendingIntentLauncher {
    private static final String TAG = "WeModern";

    private PendingIntentLauncher() {
    }

    static boolean send(PendingIntent target) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        Build.VERSION.SDK_INT >= 36
                                ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                                : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                target.send(options.toBundle());
            } else {
                target.send();
            }
            WeChatForegroundState.onWeChatLaunchSucceeded();
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "WeChat pending intent is no longer valid", e);
            return false;
        }
    }
}
