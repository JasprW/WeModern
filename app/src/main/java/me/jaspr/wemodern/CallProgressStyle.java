package me.jaspr.wemodern;

import android.app.Notification;
import android.graphics.drawable.Icon;

final class CallProgressStyle {
    private CallProgressStyle() {
    }

    static Notification.ProgressStyle create(Icon icon) {
        return new Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgressIndeterminate(true)
                .setProgressTrackerIcon(icon);
    }
}
