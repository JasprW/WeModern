package me.jaspr.wemodern;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

final class AppIconBehavior {
    private static final String PREFERENCES = "app_icon_behavior";
    private static final String OPEN_WECHAT = "open_wechat";

    private AppIconBehavior() {
    }

    static boolean hasRequiredPermissions(Context context) {
        return hasNotificationAccess(context) && hasPostNotificationPermission(context);
    }

    static boolean shouldOpenWeChat(Context context) {
        if (!hasRequiredPermissions(context)) {
            setOpenWeChatEnabled(context, false);
            return false;
        }
        return isOpenWeChatEnabled(context);
    }

    static boolean isOpenWeChatEnabled(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(OPEN_WECHAT, false);
    }

    static void setOpenWeChatEnabled(Context context, boolean enabled) {
        if (enabled && !hasRequiredPermissions(context)) return;
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(OPEN_WECHAT, enabled)
                .apply();
    }

    private static boolean hasNotificationAccess(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabled == null) return false;

        ComponentName service = new ComponentName(context, WeChatNotificationService.class);
        for (String component : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(component))) return true;
        }
        return false;
    }

    private static boolean hasPostNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
