package me.jaspr.wemodern;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/** Notification click trampoline that closes WeModern bubbles before opening WeChat. */
public final class WeChatLaunchProxyActivity extends Activity {
    private static final String TAG = "WeModern";
    private static final String ACTION_OPEN_WECHAT =
            "me.jaspr.wemodern.action.OPEN_WECHAT_NOTIFICATION";
    private static final String EXTRA_TARGET =
            "me.jaspr.wemodern.extra.WECHAT_CONTENT_INTENT";
    private static final int REQUEST_CODE_NAMESPACE = 0x45000000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PendingIntent target = targetFrom(getIntent());
        if (target == null) {
            Log.w(TAG, "notification launch proxy missing WeChat target");
            finish();
            return;
        }

        BubbleLaunchCleanup.clear(this);
        if (!PendingIntentLauncher.send(target)) {
            WeChatLauncher.open(this);
        }
        finish();
    }

    static PendingIntent wrap(Context context, String launchKey, PendingIntent target) {
        if (target == null) return null;
        Intent proxy = new Intent(context, WeChatLaunchProxyActivity.class)
                .setAction(ACTION_OPEN_WECHAT)
                .putExtra(EXTRA_TARGET, target);
        return PendingIntent.getActivity(
                context,
                requestCodeFor(launchKey),
                proxy,
                pendingIntentFlags()
        );
    }

    static int requestCodeFor(String launchKey) {
        int hash = launchKey == null ? 0 : launchKey.hashCode();
        return REQUEST_CODE_NAMESPACE ^ hash;
    }

    static int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    @SuppressWarnings("deprecation")
    private static PendingIntent targetFrom(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_TARGET, PendingIntent.class);
        }
        return intent.getParcelableExtra(EXTRA_TARGET);
    }
}
