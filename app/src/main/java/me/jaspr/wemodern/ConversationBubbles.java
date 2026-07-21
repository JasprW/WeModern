package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;

final class ConversationBubbles {
    private static final String TAG = "WeModern";
    private static final int MIN_BUBBLE_SDK = 29;
    private static final int REQUEST_CODE_NAMESPACE = 0x42000000;
    private static final int DISMISS_REQUEST_CODE_NAMESPACE = 0x43000000;

    private ConversationBubbles() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= MIN_BUBBLE_SDK;
    }

    static int requestCodeFor(String conversationId) {
        int hash = conversationId == null ? 0 : conversationId.hashCode();
        return REQUEST_CODE_NAMESPACE ^ hash;
    }

    static int pendingIntentFlags() {
        // Android 12+ requires the Activity PendingIntent attached to BubbleMetadata to be
        // mutable so SystemUI can supply its embedded-task launch options.
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    }

    static int dismissRequestCodeFor(String conversationId) {
        int hash = conversationId == null ? 0 : conversationId.hashCode();
        return DISMISS_REQUEST_CODE_NAMESPACE ^ hash;
    }

    static int dismissPendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    static boolean shouldAutoExpand() {
        return false;
    }

    static boolean shouldSuppressNotification() {
        return false;
    }

    static void applyTo(
            Context context,
            Notification.Builder builder,
            ConversationBubbleState state,
            Icon icon,
            int notificationId
    ) {
        boolean enabled = ChatBubbleBehavior.isEnabled(context)
                && ConversationBubblePreferences.isEnabled(
                        context,
                        state == null ? null : state.conversationId
                );
        boolean trampolineEnabled = BubbleTrampolineBehavior.isEnabled(context);
        if (shouldApplyTrampoline(
                Build.VERSION.SDK_INT,
                enabled,
                trampolineEnabled,
                state != null,
                icon != null,
                state != null && state.contentIntent != null
        )) {
            PendingIntent bridgeIntent = TrampolineBridgeActivity.createBubbleIntent(
                    context,
                    state.contentIntent,
                    state.conversationId
            );
            if (bridgeIntent != null) {
                Icon bridgeIcon = ConversationShortcuts.adaptiveBubbleIcon(context, icon);
                Api29Impl.applyTo(
                        context,
                        builder,
                        state,
                        bridgeIcon == null ? icon : bridgeIcon,
                        notificationId,
                        bridgeIntent
                );
            }
            if (bridgeIntent == null
                    && MessageTestNotifications.isTestShortcutId(state.conversationId)) {
                Api29Impl.applyTo(context, builder, state, icon, notificationId);
            }
            return;
        }
        if (!shouldApply(
                Build.VERSION.SDK_INT,
                enabled,
                trampolineEnabled,
                state != null,
                icon != null
        )) return;
        Api29Impl.applyTo(context, builder, state, icon, notificationId);
    }

    static boolean shouldApply(
            int sdkInt,
            boolean enabled,
            boolean trampolineEnabled,
            boolean hasState,
            boolean hasIcon
    ) {
        return isSupported(sdkInt)
                && enabled
                && !trampolineEnabled
                && hasState
                && hasIcon;
    }

    static boolean shouldApplyTrampoline(
            int sdkInt,
            boolean enabled,
            boolean trampolineEnabled,
            boolean hasState,
            boolean hasIcon,
            boolean hasConversationIntent
    ) {
        return BubbleTrampolineBehavior.isSupported(sdkInt)
                && enabled
                && trampolineEnabled
                && hasState
                && hasIcon
                && hasConversationIntent;
    }

    @TargetApi(29)
    static void syncActiveNotifications(Context context) {
        if (!isSupported(Build.VERSION.SDK_INT)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        boolean enabled = ChatBubbleBehavior.isEnabled(context);
        boolean bubbleReady = ChatBubbleBehavior.isReady(
                enabled,
                ChatBubbleBehavior.isSystemAllowed(context)
        );
        boolean trampolineEnabled =
                bubbleReady && BubbleTrampolineBehavior.isEnabled(context);
        StatusBarNotification[] active = manager.getActiveNotifications();
        if (active == null) return;

        for (StatusBarNotification sbn : active) {
            Notification notification = sbn.getNotification();
            if (!NotificationChannels.isMessageChannel(notification.getChannelId())) continue;
            boolean groupSummary =
                    (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (groupSummary) continue;

            String conversationId = notification.getShortcutId();
            ConversationBubbleState state = ConversationBubbleStore.get(conversationId);
            Icon icon = notification.getLargeIcon();
            Notification.BubbleMetadata currentBubble = notification.getBubbleMetadata();
            if (icon == null && currentBubble != null) icon = currentBubble.getIcon();
            if (icon == null) {
                icon = Icon.createWithResource(context, R.mipmap.ic_launcher);
            }
            boolean hasBubble = currentBubble != null;
            boolean conversationPreferenceEnabled =
                    ConversationBubblePreferences.isEnabled(context, conversationId);
            boolean conversationBubbleEnabled = enabled && conversationPreferenceEnabled;
            boolean conversationBubbleReady = bubbleReady && conversationPreferenceEnabled;
            String desiredChannelId = NotificationChannels.messageChannelId(
                    context,
                    conversationId
            );
            boolean channelChanged = !desiredChannelId.equals(notification.getChannelId());

            if (trampolineEnabled) {
                boolean shouldHaveBubble = conversationBubbleReady
                        && state != null
                        && state.contentIntent != null;
                if (!shouldUpdateActiveNotification(
                        shouldHaveBubble,
                        conversationId,
                        state != null,
                        icon != null,
                        hasBubble
                ) && !channelChanged) continue;
                updateBubbleMetadata(
                        context,
                        manager,
                        sbn,
                        state,
                        icon,
                        conversationId,
                        shouldHaveBubble,
                        desiredChannelId
                );
                continue;
            }

            if (!shouldUpdateActiveNotification(
                    conversationBubbleEnabled,
                    conversationId,
                    state != null,
                    icon != null,
                    hasBubble
            ) && !channelChanged) continue;
            updateBubbleMetadata(
                    context,
                    manager,
                    sbn,
                    state,
                    icon,
                    conversationId,
                    conversationBubbleEnabled,
                    desiredChannelId
            );
        }
    }

    @TargetApi(29)
    private static void updateBubbleMetadata(
            Context context,
            NotificationManager manager,
            StatusBarNotification sbn,
            ConversationBubbleState state,
            Icon icon,
            String conversationId,
            boolean enabled,
            String channelId
    ) {
        try {
            Notification.Builder builder = Notification.Builder.recoverBuilder(
                    context,
                    sbn.getNotification()
            )
                    .setChannelId(channelId)
                    .setVisibility(NotificationChannels.messageLockscreenVisibility())
                    .setOnlyAlertOnce(true);
            if (enabled) {
                builder.setBubbleMetadata(null);
                applyTo(context, builder, state, icon, sbn.getId());
            } else {
                builder.setBubbleMetadata(null);
            }
            manager.notify(sbn.getTag(), sbn.getId(), builder.build());
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to update active bubble metadata"
                    + ", id=" + sbn.getId()
                    + ", conversation=" + conversationId
                    + ", enabled=" + enabled, e);
        }
    }

    static boolean shouldUpdateActiveNotification(
            boolean enabled,
            String conversationId,
            boolean hasState,
            boolean hasIcon,
            boolean hasBubble
    ) {
        if (conversationId == null || conversationId.isEmpty()) return false;
        // Rebuild existing metadata as well: its PendingIntent captures whether the
        // WeChat trampoline was enabled when the notification was posted.
        return enabled ? hasState && hasIcon : hasBubble;
    }

    static boolean isActiveTrampolineHost(
            int notificationId,
            int expectedNotificationId,
            String shortcutId,
            String expectedShortcutId,
            boolean hasBubbleMetadata,
            boolean systemMarkedAsBubble
    ) {
        return notificationId == expectedNotificationId
                && (expectedShortcutId == null
                ? shortcutId == null
                : expectedShortcutId.equals(shortcutId))
                && hasBubbleMetadata
                && systemMarkedAsBubble;
    }

    @TargetApi(29)
    private static final class Api29Impl {
        private Api29Impl() {
        }

        static void applyTo(
                Context context,
                Notification.Builder builder,
                ConversationBubbleState state,
                Icon icon,
                int notificationId
        ) {
            Intent target = new Intent(context, BubbleConversationActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(new Uri.Builder()
                            .scheme("wemodern")
                            .authority("bubble")
                            .appendPath(state.conversationId)
                            .build());
            state.writeTo(target);
            PendingIntent bubbleIntent = PendingIntent.getActivity(
                    context,
                    requestCodeFor(state.conversationId),
                    target,
                    pendingIntentFlags()
            );
            applyTo(context, builder, state, icon, notificationId, bubbleIntent);
        }

        static void applyTo(
                Context context,
                Notification.Builder builder,
                ConversationBubbleState state,
                Icon icon,
                int notificationId,
                PendingIntent bubbleIntent
        ) {
            PendingIntent deleteIntent = createDeleteIntent(context, state, notificationId);
            Notification.BubbleMetadata.Builder metadataBuilder;
            if (Build.VERSION.SDK_INT >= 30) {
                metadataBuilder = Api30Impl.newBuilder(bubbleIntent, icon);
            } else {
                // The no-argument builder is the Android 10 API surface.
                metadataBuilder = new Notification.BubbleMetadata.Builder()
                        .setIntent(bubbleIntent)
                        .setIcon(icon);
            }
            Notification.BubbleMetadata metadata = metadataBuilder
                    .setDeleteIntent(deleteIntent)
                    .setDesiredHeightResId(R.dimen.conversation_bubble_desired_height)
                    .setAutoExpandBubble(shouldAutoExpand())
                    .setSuppressNotification(shouldSuppressNotification())
                    .build();
            builder.setBubbleMetadata(metadata);
        }

        private static PendingIntent createDeleteIntent(
                Context context,
                ConversationBubbleState state,
                int notificationId
        ) {
            Intent intent = new Intent(context, BubbleDismissReceiver.class)
                    .setAction(BubbleDismissReceiver.ACTION_DISMISS)
                    .setData(new Uri.Builder()
                            .scheme("wemodern")
                            .authority("bubble-dismiss")
                            .appendPath(state.conversationId)
                            .build())
                    .putExtra(BubbleDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    .putExtra(BubbleDismissReceiver.EXTRA_CONVERSATION_ID, state.conversationId);
            return PendingIntent.getBroadcast(
                    context,
                    dismissRequestCodeFor(state.conversationId),
                    intent,
                    dismissPendingIntentFlags()
            );
        }

    }

    @TargetApi(30)
    private static final class Api30Impl {
        private Api30Impl() {
        }

        static Notification.BubbleMetadata.Builder newBuilder(
                PendingIntent bubbleIntent,
                Icon icon
        ) {
            return new Notification.BubbleMetadata.Builder(bubbleIntent, icon);
        }
    }
}
