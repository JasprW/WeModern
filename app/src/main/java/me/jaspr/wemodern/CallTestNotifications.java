package me.jaspr.wemodern;

final class CallTestNotifications {
    static final int CURRENT_ID = 101;
    static final int LEGACY_VIDEO_ID = 102;
    static final String ACTION_START = "me.jaspr.wemodern.calltest.START";
    static final String ACTION_ANSWER = "me.jaspr.wemodern.calltest.ANSWER";
    static final String ACTION_DECLINE = "me.jaspr.wemodern.calltest.DECLINE";
    static final String ACTION_HANG_UP = "me.jaspr.wemodern.calltest.HANG_UP";

    enum Command {
        SHOW_INCOMING,
        SHOW_ONGOING,
        STOP,
        IGNORE
    }

    private CallTestNotifications() {
    }

    static int idFor(boolean video) {
        return CURRENT_ID;
    }

    static boolean isTestId(int id) {
        return id == CURRENT_ID || id == LEGACY_VIDEO_ID;
    }

    static int mockCallerNameResource() {
        return R.string.test_message_sender;
    }

    static int mockCallerAvatarResource() {
        return R.drawable.ic_test_message_avatar_48;
    }

    static Command commandFor(String action) {
        if (ACTION_START.equals(action)) return Command.SHOW_INCOMING;
        if (ACTION_ANSWER.equals(action)) return Command.SHOW_ONGOING;
        if (ACTION_DECLINE.equals(action) || ACTION_HANG_UP.equals(action)) {
            return Command.STOP;
        }
        return Command.IGNORE;
    }

    static boolean isCurrentSession(
            long activeSessionId,
            boolean activeVideo,
            long actionSessionId,
            boolean actionVideo
    ) {
        return activeSessionId == actionSessionId && activeVideo == actionVideo;
    }

    static int requestCodeFor(String action, boolean video, long sessionId) {
        int result = 31 * action.hashCode() + (video ? 1 : 0);
        return 31 * result + Long.hashCode(sessionId);
    }
}
