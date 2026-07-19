package me.jaspr.wemodern;

import android.app.Notification;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

final class CallNotificationPresentation {
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING =
            "android.requestPromotedOngoing";

    private CallNotificationPresentation() {
    }

    @ChecksSdkIntAtLeast(api = 31)
    static boolean supportsCallStyle() {
        return supportsCallStyle(Build.VERSION.SDK_INT);
    }

    static boolean supportsCallStyle(int sdkInt) {
        return sdkInt >= 31;
    }

    @ChecksSdkIntAtLeast(api = 37)
    static boolean requiresForegroundCallStyle() {
        return requiresForegroundCallStyle(Build.VERSION.SDK_INT);
    }

    static boolean requiresForegroundCallStyle(int sdkInt) {
        return sdkInt >= 37;
    }

    static int foregroundServiceType() {
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
    }

    static Notification buildPromoted(Notification.Builder builder) {
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
        }
        return notification;
    }
}
