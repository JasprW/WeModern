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
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(WECHAT_PACKAGE);
        if (launchIntent == null) {
            Log.w(TAG, "WeChat launcher activity cannot be resolved");
            return false;
        }
        context.startActivity(launchIntent);
        return true;
    }
}
