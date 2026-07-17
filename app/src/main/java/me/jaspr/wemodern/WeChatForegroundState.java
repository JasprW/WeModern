package me.jaspr.wemodern;

final class WeChatForegroundState {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String LAUNCHER_ACTIVITY =
            "me.jaspr.wemodern/.LauncherActivity";
    private static final String NOTIFICATION_PROXY_ACTIVITY =
            "me.jaspr.wemodern/.WeChatLaunchProxyActivity";

    private static volatile String foregroundComponent;
    private static volatile int foregroundTaskId = -1;
    private static volatile boolean trackingAvailable;

    private WeChatForegroundState() {
    }

    static void onForegroundActivityChanged(int taskId, String componentName) {
        trackingAvailable = true;
        // These activities only forward a launch. MainActivity is intentionally not ignored:
        // opening settings, Home, or any other activity must clear a stale WeChat foreground.
        if (isTransientLaunchActivity(componentName)) return;
        foregroundTaskId = taskId;
        foregroundComponent = componentName;
    }

    static void onWeChatLaunchSucceeded() {
        if (!trackingAvailable) return;
        foregroundTaskId = -1;
        foregroundComponent = WECHAT_PACKAGE + "/.ui.LauncherUI";
    }

    static boolean isWeChatForeground() {
        return trackingAvailable
                && WECHAT_PACKAGE.equals(packageName(foregroundComponent))
                && !TrampolineBubbleSessionState.isEmbeddedTask(foregroundTaskId);
    }

    static void onTaskRemoved(int taskId) {
        if (foregroundTaskId != taskId) return;
        foregroundTaskId = -1;
        foregroundComponent = null;
    }

    static boolean isTransientLaunchActivity(String componentName) {
        return LAUNCHER_ACTIVITY.equals(componentName)
                || NOTIFICATION_PROXY_ACTIVITY.equals(componentName);
    }

    private static String packageName(String componentName) {
        if (componentName == null) return "";
        int separator = componentName.indexOf('/');
        return separator > 0 ? componentName.substring(0, separator) : componentName;
    }

    static void resetForTest() {
        foregroundComponent = null;
        foregroundTaskId = -1;
        trackingAvailable = false;
    }
}
