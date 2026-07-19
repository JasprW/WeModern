package me.jaspr.wemodern;

import android.app.Notification;
import android.os.Bundle;

import java.util.Locale;

/** Classifies the two-notification WeChat incoming-call sequence without using an Activity as state. */
final class WeChatCallClassifier {
    private static final String EXTRA_CONTAINS_CUSTOM_VIEW = "android.contains.customView";
    static final long CONNECTED_UPDATE_CONFIRMATION_GAP_MS = 1_000L;

    enum Signal {
        NONE,
        INCOMING,
        WAITING_STATUS,
        CONNECTED_STATUS,
    }

    private WeChatCallClassifier() {
    }

    static Signal classify(Notification notification) {
        if (notification == null) return Signal.NONE;
        Bundle extras = notification.extras;
        CharSequence title = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        boolean containsCustomView = notification.contentView != null
                || notification.bigContentView != null
                || (extras != null && extras.getBoolean(EXTRA_CONTAINS_CUSTOM_VIEW));
        return classify(
                notification.flags,
                notification.getChannelId(),
                notification.category,
                notification.fullScreenIntent != null,
                containsCustomView,
                title,
                text,
                notification.tickerText
        );
    }

    static Signal classify(
            int flags,
            String channel,
            String category,
            boolean hasFullScreenIntent,
            boolean containsCustomView,
            CharSequence title,
            CharSequence text,
            CharSequence ticker
    ) {
        if ((flags & Notification.FLAG_ONGOING_EVENT) == 0) return Signal.NONE;
        String value = combined(title, text, ticker);
        if (Notification.CATEGORY_CALL.equals(category)
                && hasFullScreenIntent
                && containsCustomView
                && isInvitationText(value)) {
            return Signal.INCOMING;
        }
        if (!isStatusText(value)) return Signal.NONE;
        if (channel != null && !channel.isEmpty() && !"reminder_channel_id".equals(channel)) {
            return Signal.NONE;
        }
        return hasConnectedFlags(flags) ? Signal.CONNECTED_STATUS : Signal.WAITING_STATUS;
    }

    static boolean hasConnectedFlags(int flags) {
        int required = Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_NO_CLEAR
                | Notification.FLAG_FOREGROUND_SERVICE;
        return (flags & required) == required;
    }

    static boolean isLaterConnectedUpdate(long firstObservedAt, long currentObservedAt) {
        return firstObservedAt > 0L
                && currentObservedAt - firstObservedAt >= CONNECTED_UPDATE_CONFIRMATION_GAP_MS;
    }

    static String callerName(CharSequence title, CharSequence text) {
        String cleanTitle = clean(title);
        if (!isGenericAppTitle(cleanTitle)) return cleanTitle;

        String body = clean(text);
        int marker = firstMarker(body, "invites you", "邀请", "邀請");
        if (marker > 0) {
            String caller = trimCaller(body.substring(0, marker));
            if (!caller.isEmpty()) return caller;
        }
        return cleanTitle;
    }

    static boolean isVideo(CharSequence title, CharSequence text) {
        String value = combined(title, text, null);
        return value.contains("video") || value.contains("视频") || value.contains("視訊");
    }

    private static boolean isInvitationText(String value) {
        boolean call = containsCallKind(value);
        boolean invitation = value.contains("invite")
                || value.contains("incoming")
                || value.contains("邀请")
                || value.contains("邀請")
                || value.contains("来电")
                || value.contains("來電");
        return call && invitation;
    }

    private static boolean isStatusText(String value) {
        if (!containsCallKind(value)) return false;
        return value.contains("in use")
                || value.contains("in progress")
                || value.contains("ongoing")
                || value.contains("active")
                || value.contains("通话中")
                || value.contains("通話中")
                || value.contains("进行中")
                || value.contains("進行中");
    }

    private static boolean containsCallKind(String value) {
        return value.contains("call")
                || value.contains("通话")
                || value.contains("通話")
                || value.contains("语音")
                || value.contains("語音")
                || value.contains("视频")
                || value.contains("視訊");
    }

    private static boolean isGenericAppTitle(String value) {
        return value.isEmpty()
                || "wechat".equalsIgnoreCase(value)
                || "微信".equals(value);
    }

    private static String combined(
            CharSequence title,
            CharSequence text,
            CharSequence ticker
    ) {
        return (clean(title) + " " + clean(text) + " " + clean(ticker))
                .toLowerCase(Locale.ROOT);
    }

    private static String clean(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int firstMarker(String value, String... markers) {
        int result = -1;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private static String trimCaller(String value) {
        return value.replaceAll("[\\s:：,，-]+$", "").trim();
    }
}
