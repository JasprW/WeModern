package me.jaspr.wemodern;

final class CallTestNotifications {
    static final int CURRENT_ID = 101;
    static final int LEGACY_VIDEO_ID = 102;

    private CallTestNotifications() {
    }

    static int idFor(boolean video) {
        return CURRENT_ID;
    }

    static boolean isTestId(int id) {
        return id == CURRENT_ID || id == LEGACY_VIDEO_ID;
    }
}
