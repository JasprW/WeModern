package me.jaspr.wemodern;

import android.content.Context;
import android.content.SharedPreferences;

/** Runtime controls for raw WeChat notification capture and notification rewriting. */
final class NotificationDebugPreferences {
    /**
     * Debug builds start with capture and logging on and rewriting off, so notification
     * shapes can be recorded before any rewrite touches them.
     */
    static final boolean DEFAULT_CAPTURE_LOGGING_ENABLED = BuildConfig.DEBUG;

    /**
     * Release builds start the other way around: rewriting on, because that is what the
     * app is installed for, and capture off, so a released build stores no notification
     * content unless the user opts in.
     */
    static final boolean DEFAULT_REWRITE_ENABLED = !BuildConfig.DEBUG;

    private static final String PREFERENCES = "notification_debug_preferences";
    private static final String KEY_CAPTURE_LOGGING = "capture_logging";
    private static final String KEY_REWRITE = "rewrite";

    private NotificationDebugPreferences() {
    }

    static boolean isCaptureLoggingEnabled(Context context) {
        return preferences(context).getBoolean(
                KEY_CAPTURE_LOGGING,
                DEFAULT_CAPTURE_LOGGING_ENABLED
        );
    }

    static void setCaptureLoggingEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_CAPTURE_LOGGING, enabled).apply();
    }

    static boolean isRewriteEnabled(Context context) {
        return preferences(context).getBoolean(KEY_REWRITE, DEFAULT_REWRITE_ENABLED);
    }

    static void setRewriteEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_REWRITE, enabled).apply();
    }

    static void registerListener(
            Context context,
            SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    static void unregisterListener(
            Context context,
            SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
