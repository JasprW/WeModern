package wang.bofan.wechatnotify;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "WeChatNotify";
    private static final int NOTIFICATION_TEST_MESSAGE = 100;
    private static final int NOTIFICATION_TEST_VOICE_CALL = 101;
    private static final int NOTIFICATION_TEST_VIDEO_CALL = 102;
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";

    private TextView statusText;
    private Button listenerButton;
    private Button notificationButton;
    private int colorPrimary;
    private int colorOnPrimary;
    private int colorPrimaryContainer;
    private int colorSurface;
    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorOnSurface;
    private int colorOnSurfaceVariant;
    private int colorOutline;

    private enum ButtonStyle {
        PRIMARY,
        TONAL,
        OUTLINE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSystemPalette();
        NotificationChannels.ensure(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_TEST_MESSAGE);
        nm.cancel(NOTIFICATION_TEST_VOICE_CALL);
        nm.cancel(NOTIFICATION_TEST_VIDEO_CALL);

        getWindow().setStatusBarColor(colorSurface);
        if (Build.VERSION.SDK_INT >= 26) getWindow().setNavigationBarColor(colorSurface);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colorSurface);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), statusBarHeight() + dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(colorOnSurface);
        title.setIncludeFontPadding(false);
        root.addView(title, fullWidth(0, 0, 0, 8));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.subtitle);
        subtitle.setTextSize(16);
        subtitle.setTextColor(colorOnSurfaceVariant);
        root.addView(subtitle, fullWidth(0, 0, 0, 22));

        LinearLayout statusCard = surfacePanel(colorPrimaryContainer, 28, 20);
        TextView statusLabel = new TextView(this);
        statusLabel.setText(R.string.section_status);
        statusLabel.setTextSize(14);
        statusLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusLabel.setTextColor(colorOnSurfaceVariant);
        statusCard.addView(statusLabel, fullWidth(0, 0, 0, 10));

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTextColor(colorOnSurface);
        statusText.setLineSpacing(dp(3), 1.0f);
        statusText.setText(buildStatusText());
        statusCard.addView(statusText, fullWidth());
        root.addView(statusCard, fullWidth(0, 0, 0, 18));

        LinearLayout setupPanel = surfacePanel(colorSurfaceContainer, 24, 16);
        setupPanel.addView(sectionLabel(getString(R.string.section_permissions)), fullWidth(0, 0, 0, 10));

        listenerButton = actionButton(getString(R.string.action_open_listener), ButtonStyle.PRIMARY);
        listenerButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        setupPanel.addView(listenerButton, fullWidth(0, 0, 0, 10));

        notificationButton = actionButton(getString(R.string.action_request_notifications), ButtonStyle.PRIMARY);
        notificationButton.setOnClickListener(v -> requestPostNotifications());
        setupPanel.addView(notificationButton, fullWidth(0, 0, 0, 10));

        Button appSettingsButton = actionButton(getString(R.string.action_open_app_settings), ButtonStyle.OUTLINE);
        appSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        setupPanel.addView(appSettingsButton, fullWidth());
        root.addView(setupPanel, fullWidth(0, 0, 0, 18));

        LinearLayout testPanel = surfacePanel(colorSurfaceContainer, 24, 16);
        testPanel.addView(sectionLabel(getString(R.string.section_tests)), fullWidth(0, 0, 0, 10));

        Button testButton = actionButton(getString(R.string.action_test_notification), ButtonStyle.OUTLINE);
        testButton.setOnClickListener(v -> postTestNotification());
        testPanel.addView(testButton, fullWidth(0, 0, 0, 10));

        Button voiceCallTestButton = actionButton(getString(R.string.action_test_voice_call), ButtonStyle.TONAL);
        voiceCallTestButton.setOnClickListener(v -> postCallTestNotification(false));
        testPanel.addView(voiceCallTestButton, fullWidth(0, 0, 0, 10));

        Button videoCallTestButton = actionButton(getString(R.string.action_test_video_call), ButtonStyle.TONAL);
        videoCallTestButton.setOnClickListener(v -> postCallTestNotification(true));
        testPanel.addView(videoCallTestButton, fullWidth());
        root.addView(testPanel, fullWidth());

        scroll.addView(root);
        setContentView(scroll);
        refreshPermissionActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusText != null) statusText.setText(buildStatusText());
        refreshPermissionActions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (statusText != null) statusText.setText(buildStatusText());
            refreshPermissionActions();
        }
    }

    private String buildStatusText() {
        String enabled = getString(R.string.status_enabled);
        String disabled = getString(R.string.status_disabled);
        return getString(R.string.status_listener, isListenerEnabled() ? enabled : disabled)
                + "\n" + getString(R.string.status_notifications, hasPostNotificationPermission() ? enabled : disabled)
                + "\n" + getString(R.string.status_live_update,
                canPostPromotedNotifications()
                        ? getString(R.string.status_system_allowed)
                        : getString(R.string.status_system_not_allowed));
    }

    private boolean isListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, WeChatNotificationService.class);
        for (String item : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(item);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private boolean hasPostNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canPostPromotedNotifications() {
        if (Build.VERSION.SDK_INT < 36) return false;
        return getSystemService(NotificationManager.class).canPostPromotedNotifications();
    }

    private void requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPostNotificationPermission()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_notifications_enabled)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void postTestNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this, NotificationChannels.STATUS)
                .setSmallIcon(R.drawable.ic_wechat_scan_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.test_notification_text))
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFICATION_TEST_MESSAGE, notification);
    }

    private void postCallTestNotification(boolean video) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        long startedAt = System.currentTimeMillis() - 65_000L;
        int iconRes = video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24;
        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_CALLS)
                .setSmallIcon(iconRes)
                .setContentTitle(getString(video ? R.string.test_video_call_title : R.string.test_voice_call_title))
                .setContentText(getString(video ? R.string.test_video_call_text : R.string.test_voice_call_text))
                .setWhen(startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(0xff33b332);
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(CallProgressStyle.create(Icon.createWithResource(this, iconRes)));
        }
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
            Log.i(TAG, "test call promotedAllowed=" + canPostPromotedNotifications()
                    + ", promotable=" + notification.hasPromotableCharacteristics()
                    + ", video=" + video);
        }
        nm.notify(video ? NOTIFICATION_TEST_VIDEO_CALL : NOTIFICATION_TEST_VOICE_CALL, notification);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(colorOnSurfaceVariant);
        return label;
    }

    private LinearLayout surfacePanel(int color, int radiusDp, int paddingDp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        panel.setBackground(rounded(color, radiusDp, 0));
        return panel;
    }

    private Button actionButton(String text, ButtonStyle style) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(56));
        button.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            button.setElevation(0);
            button.setTranslationZ(0);
            button.setStateListAnimator(null);
        }
        if (style == ButtonStyle.PRIMARY) {
            button.setTextColor(colorOnPrimary);
            button.setBackground(rounded(colorPrimary, 18, 0));
        } else if (style == ButtonStyle.TONAL) {
            button.setTextColor(colorPrimary);
            button.setBackground(rounded(colorPrimaryContainer, 18, 0));
        } else {
            button.setTextColor(colorPrimary);
            button.setBackground(rounded(colorSurfaceContainerHigh, 18, colorOutline));
        }
        return button;
    }

    private void refreshPermissionActions() {
        updatePermissionButton(listenerButton, isListenerEnabled(),
                getString(R.string.action_open_listener),
                getString(R.string.action_listener_enabled));
        updatePermissionButton(notificationButton, hasPostNotificationPermission(),
                getString(R.string.action_request_notifications),
                getString(R.string.action_notifications_enabled));
    }

    private void updatePermissionButton(Button button, boolean granted, String pendingText, String grantedText) {
        if (button == null) return;
        button.setText(granted ? grantedText : pendingText);
        button.setEnabled(!granted);
        button.setAlpha(granted ? 0.58f : 1.0f);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return fullWidth(0, 0, 0, 0);
    }

    private LinearLayout.LayoutParams fullWidth(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadSystemPalette() {
        colorPrimary = systemColor("system_accent1_600", 0xff006d3b);
        colorOnPrimary = systemColor("system_accent1_0", 0xffffffff);
        colorPrimaryContainer = systemColor("system_accent1_100", 0xffc4eed0);
        colorSurface = systemColor("system_neutral1_10", 0xfffbfdf7);
        colorSurfaceContainer = systemColor("system_neutral1_50", 0xffeff3ea);
        colorSurfaceContainerHigh = systemColor("system_neutral1_100", 0xffe4e9df);
        colorOnSurface = systemColor("system_neutral1_900", 0xff191d18);
        colorOnSurfaceVariant = systemColor("system_neutral2_700", 0xff43483f);
        colorOutline = systemColor("system_neutral2_500", 0xff73796e);
    }

    private int systemColor(String name, int fallback) {
        if (Build.VERSION.SDK_INT < 31) return fallback;
        Resources resources = getResources();
        int id = resources.getIdentifier(name, "color", "android");
        return id == 0 ? fallback : getColor(id);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : getResources().getDimensionPixelSize(id);
    }
}
