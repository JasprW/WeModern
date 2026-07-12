package me.jaspr.wemodern;

import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

final class CallProgressStyle {
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";

    private CallProgressStyle() {
    }

    static Notification build(Notification.Builder builder, Icon icon) {
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(create(icon));
        }
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
        }
        return notification;
    }

    @RequiresApi(36)
    private static Notification.ProgressStyle create(Icon icon) {
        return new Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgressIndeterminate(true)
                .setProgressTrackerIcon(icon);
    }
}
