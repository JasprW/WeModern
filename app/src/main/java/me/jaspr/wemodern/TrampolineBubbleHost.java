package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Owns the single, stable bubble used by the experimental WeChat trampoline mode. */
final class TrampolineBubbleHost {
    private static final String TAG = "WeModern";

    private static final int LEGACY_NOTIFICATION_ID = 0x57424853;
    static final int NOTIFICATION_ID = 0x57424854;
    static final int SECONDARY_NOTIFICATION_ID = 0x57424855;
    static final String SHORTCUT_ID = "wemodern_wechat_bubble_host";

    private static final int REQUEST_CODE = 0x44000000;
    private static final String HOST_STATE_PREFERENCES = "trampoline_bubble_host_state";
    private static final String KEY_NOTIFICATION_ID = "notification_id";
    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_CONVERSATION_BRIDGE = "conversation_bridge";
    private static volatile boolean hostNotificationPosted;

    private TrampolineBubbleHost() {
    }

    static boolean isHostNotificationId(int notificationId) {
        return notificationId == NOTIFICATION_ID
                || notificationId == SECONDARY_NOTIFICATION_ID
                || notificationId == LEGACY_NOTIFICATION_ID;
    }

    static int requestCode() {
        return REQUEST_CODE;
    }

    static boolean isHostNotificationPosted() {
        return hostNotificationPosted;
    }

    static boolean shouldAutoExpand() {
        return false;
    }

    static boolean shouldSuppressNotification() {
        return false;
    }

    static boolean shouldOnlyAlertOnce() {
        return false;
    }

    static boolean shouldPost(
            boolean chatBubblesReady,
            boolean trampolineEnabled,
            boolean conversationEnabled,
            boolean hasSource,
            boolean hasIcon,
            boolean hasWeChatLauncher,
            boolean weChatForeground
    ) {
        return chatBubblesReady
                && trampolineEnabled
                && conversationEnabled
                && hasSource
                && hasIcon
                && hasWeChatLauncher
                && !weChatForeground;
    }

    static boolean isEligibleSource(
            String channelId,
            int notificationId,
            String shortcutId,
            boolean groupSummary
    ) {
        return NotificationChannels.isMessageChannel(channelId)
                && !isHostNotificationId(notificationId)
                && !groupSummary
                && shortcutId != null
                && !shortcutId.isEmpty();
    }

    static boolean isNewerSource(long candidatePostTime, long currentPostTime) {
        return candidatePostTime > currentPostTime;
    }

    static boolean shouldUseConversationBridge(
            boolean experimentalEnabled,
            boolean hasUsableBridgeIntent
    ) {
        return experimentalEnabled && hasUsableBridgeIntent;
    }

    static boolean shouldRotateHost(
            boolean hasActiveHost,
            String currentConversationId,
            String nextConversationId,
            boolean currentConversationBridge,
            boolean nextConversationBridge
    ) {
        if (!hasActiveHost) return false;
        if (currentConversationBridge != nextConversationBridge) return true;
        return nextConversationBridge
                && !Objects.equals(currentConversationId, nextConversationId);
    }

    static int alternateNotificationId(int currentNotificationId) {
        return currentNotificationId == NOTIFICATION_ID
                ? SECONDARY_NOTIFICATION_ID
                : NOTIFICATION_ID;
    }

    @TargetApi(31)
    static boolean update(
            Context context,
            Notification source,
            String sourceConversationId,
            CharSequence sourceTitle,
            Icon sourceIcon,
            PendingIntent sourceConversationIntent
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        if (BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(context)) {
            return false;
        }
        boolean chatBubblesReady = ChatBubbleBehavior.isReady(
                ChatBubbleBehavior.isEnabled(context),
                ChatBubbleBehavior.isSystemAllowed(context)
        );
        boolean trampolineEnabled = BubbleTrampolineBehavior.isEnabled(context);
        boolean conversationEnabled =
                ConversationBubblePreferences.isEnabled(context, sourceConversationId);
        Intent weChatTarget = WeChatLauncher.createBubbleRootIntent(context);
        boolean weChatForeground = WeChatForegroundState.isWeChatForeground();
        if (!shouldPost(
                chatBubblesReady,
                trampolineEnabled,
                conversationEnabled,
                source != null,
                sourceIcon != null,
                weChatTarget != null,
                weChatForeground
        )) {
            if (weChatForeground) {
                Log.d(TAG, "skip trampoline bubble update while WeChat is foreground");
            }
            return false;
        }

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (notificationManager == null || shortcutManager == null) return false;
        notificationManager.cancel(LEGACY_NOTIFICATION_ID);

        Icon bubbleIcon = ConversationShortcuts.adaptiveBubbleIcon(context, sourceIcon);
        String label = normalizedLabel(context, sourceTitle);
        ShortcutInfo shortcut = buildShortcut(context, label, bubbleIcon);
        ShortcutPublication publication = publishShortcut(context, shortcutManager, shortcut);
        if (!publication.ready) return false;

        boolean posted = false;
        try {
            boolean conversationBridgeEnabled =
                    BubbleTrampolineBehavior.isConversationBridgeEnabled(context);
            PendingIntent bridgeIntent = conversationBridgeEnabled
                    ? TrampolineBridgeActivity.createBubbleIntent(
                            context,
                            sourceConversationIntent,
                            sourceConversationId
                    )
                    : null;
            boolean useConversationBridge = shouldUseConversationBridge(
                    conversationBridgeEnabled,
                    bridgeIntent != null
            );
            PendingIntent bubbleIntent = useConversationBridge
                    ? bridgeIntent
                    : PendingIntent.getActivity(
                            context,
                            requestCode(),
                            weChatTarget,
                            ConversationBubbles.pendingIntentFlags()
                    );
            HostIdentity hostIdentity = resolveHostIdentity(
                    context,
                    notificationManager,
                    sourceConversationId,
                    useConversationBridge
            );
            if (hostIdentity.rotating) {
                TrampolineBubbleSessionState.onHostReplaced();
            }
            Notification.BubbleMetadata metadata = new Notification.BubbleMetadata.Builder(
                    bubbleIntent,
                    bubbleIcon
            )
                    .setDesiredHeightResId(R.dimen.conversation_bubble_desired_height)
                    .setAutoExpandBubble(shouldAutoExpand())
                    .setSuppressNotification(shouldSuppressNotification())
                    .build();

            Notification.Builder builder = Notification.Builder.recoverBuilder(context, source)
                    .setChannelId(NotificationChannels.WECHAT_BUBBLE_HOST)
                    .setGroup(null)
                    .setSortKey(null)
                    .setGroupSummary(false)
                    .setAutoCancel(false)
                    .setDeleteIntent(null)
                    .setOnlyAlertOnce(shouldOnlyAlertOnce())
                    .setDefaults(0)
                    .setSound(null)
                    .setVibrate(null)
                    .setContentIntent(bubbleIntent)
                    .setShortcutId(SHORTCUT_ID)
                    .setLocusId(new android.content.LocusId(SHORTCUT_ID))
                    .setBubbleMetadata(metadata);

            notificationManager.notify(hostIdentity.nextNotificationId, builder.build());
            if (hostIdentity.rotating) {
                notificationManager.cancel(hostIdentity.previousNotificationId);
            }
            persistHostIdentity(
                    context,
                    hostIdentity.nextNotificationId,
                    sourceConversationId,
                    useConversationBridge
            );
            posted = true;
            hostNotificationPosted = true;
            Log.i(TAG, "updated trampoline bubble host"
                    + ", sourceConversation=" + sourceConversationId
                    + ", shortcut=" + SHORTCUT_ID
                    + ", notificationId=" + hostIdentity.nextNotificationId
                    + ", rotated=" + hostIdentity.rotating
                    + ", launchMode=" + (useConversationBridge
                    ? "conversation-bridge"
                    : "wechat-home"));
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to update trampoline bubble host", e);
        } finally {
            if (publication.temporarilyDynamic) {
                retainShortcutAsCached(context, shortcutManager);
            }
        }
        return posted;
    }

    @TargetApi(29)
    static void syncFromActive(Context context, StatusBarNotification[] active) {
        if (active == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(context)) {
            clear(context);
            return;
        }
        StatusBarNotification newest = null;
        long newestPostTime = Long.MIN_VALUE;
        for (StatusBarNotification sbn : active) {
            Notification notification = sbn.getNotification();
            boolean groupSummary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (!isEligibleSource(
                    notification.getChannelId(),
                    sbn.getId(),
                    notification.getShortcutId(),
                    groupSummary
            ) || !ConversationBubblePreferences.isEnabled(
                    context,
                    notification.getShortcutId()
            )) {
                continue;
            }
            if (newest == null || isNewerSource(sbn.getPostTime(), newestPostTime)) {
                newest = sbn;
                newestPostTime = sbn.getPostTime();
            }
        }
        if (newest == null) {
            clear(context);
            return;
        }

        Notification source = newest.getNotification();
        Icon icon = source.getLargeIcon();
        Notification.BubbleMetadata currentBubble = source.getBubbleMetadata();
        if (icon == null && currentBubble != null) icon = currentBubble.getIcon();
        if (icon == null) icon = Icon.createWithResource(context, R.mipmap.ic_launcher);
        CharSequence title = source.extras == null
                ? null
                : source.extras.getCharSequence(Notification.EXTRA_TITLE);
        ConversationBubbleState state = ConversationBubbleStore.get(source.getShortcutId());
        PendingIntent conversationIntent = state == null ? null : state.contentIntent;
        update(
                context,
                source,
                source.getShortcutId(),
                title,
                icon,
                conversationIntent
        );
    }

    static void clear(Context context) {
        hostNotificationPosted = false;
        TrampolineBubbleSessionState.onHostCleared();
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            notificationManager.cancel(SECONDARY_NOTIFICATION_ID);
            notificationManager.cancel(LEGACY_NOTIFICATION_ID);
        }
        context.getSharedPreferences(HOST_STATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        if (Build.VERSION.SDK_INT < 30) return;

        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;
        try {
            shortcutManager.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
            shortcutManager.removeLongLivedShortcuts(Collections.singletonList(SHORTCUT_ID));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to remove trampoline bubble shortcut", e);
        }
        ConversationShortcuts.refreshIcons(context);
    }

    @TargetApi(29)
    private static ShortcutInfo buildShortcut(Context context, String label, Icon icon) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setActivity(new ComponentName(context, LauncherActivity.class))
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .setLocusId(new android.content.LocusId(SHORTCUT_ID))
                .setPerson(new Person.Builder()
                        .setName(label)
                        .setKey(SHORTCUT_ID)
                        .setIcon(icon)
                        .build());
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setLongLived(true)
                    .setCategories(Collections.singleton(
                            ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION));
        }
        return builder.build();
    }

    @TargetApi(30)
    private static ShortcutPublication publishShortcut(
            Context context,
            ShortcutManager manager,
            ShortcutInfo shortcut
    ) {
        try {
            int flags = ShortcutManager.FLAG_MATCH_DYNAMIC
                    | ShortcutManager.FLAG_MATCH_PINNED
                    | ShortcutManager.FLAG_MATCH_CACHED;
            List<ShortcutInfo> shortcuts = manager.getShortcuts(flags);
            for (ShortcutInfo existing : shortcuts) {
                if (!SHORTCUT_ID.equals(existing.getId())) continue;
                if (!manager.updateShortcuts(Collections.singletonList(shortcut))) {
                    Log.w(TAG, "launcher rejected trampoline shortcut update");
                }
                return new ShortcutPublication(true, false);
            }

            ConversationShortcuts.reserveTransientShortcutSlot(context);
            manager.pushDynamicShortcut(shortcut);
            return new ShortcutPublication(true, true);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to publish trampoline bubble shortcut", e);
            return new ShortcutPublication(false, false);
        }
    }

    @TargetApi(30)
    private static void retainShortcutAsCached(Context context, ShortcutManager manager) {
        try {
            manager.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to cache trampoline bubble shortcut", e);
        }
        ConversationShortcuts.refreshIcons(context);
    }

    private static String normalizedLabel(Context context, CharSequence sourceTitle) {
        String label = sourceTitle == null ? "" : sourceTitle.toString().trim();
        return label.isEmpty() ? context.getString(R.string.app_name) : label;
    }

    private static HostIdentity resolveHostIdentity(
            Context context,
            NotificationManager manager,
            String nextConversationId,
            boolean nextConversationBridge
    ) {
        SharedPreferences preferences = context.getSharedPreferences(
                HOST_STATE_PREFERENCES,
                Context.MODE_PRIVATE
        );
        int storedNotificationId = preferences.getInt(
                KEY_NOTIFICATION_ID,
                NOTIFICATION_ID
        );
        int activeNotificationId = activeHostNotificationId(manager, storedNotificationId);
        if (activeNotificationId == 0) {
            return new HostIdentity(0, NOTIFICATION_ID, false);
        }

        boolean storedIdentityIsActive = storedNotificationId == activeNotificationId;
        String currentConversationId = storedIdentityIsActive
                ? preferences.getString(KEY_CONVERSATION_ID, null)
                : null;
        boolean currentConversationBridge = storedIdentityIsActive
                && preferences.getBoolean(KEY_CONVERSATION_BRIDGE, false);
        boolean rotating = !storedIdentityIsActive || shouldRotateHost(
                true,
                currentConversationId,
                nextConversationId,
                currentConversationBridge,
                nextConversationBridge
        );
        int nextNotificationId = rotating
                ? alternateNotificationId(activeNotificationId)
                : activeNotificationId;
        return new HostIdentity(
                activeNotificationId,
                nextNotificationId,
                rotating
        );
    }

    private static int activeHostNotificationId(
            NotificationManager manager,
            int preferredNotificationId
    ) {
        try {
            int fallback = 0;
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                int notificationId = notification.getId();
                if (notificationId != NOTIFICATION_ID
                        && notificationId != SECONDARY_NOTIFICATION_ID) {
                    continue;
                }
                if (notificationId == preferredNotificationId) return notificationId;
                fallback = notificationId;
            }
            return fallback;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to inspect active trampoline host identity", e);
            return 0;
        }
    }

    private static void persistHostIdentity(
            Context context,
            int notificationId,
            String conversationId,
            boolean conversationBridge
    ) {
        context.getSharedPreferences(HOST_STATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .putString(KEY_CONVERSATION_ID, conversationId)
                .putBoolean(KEY_CONVERSATION_BRIDGE, conversationBridge)
                .apply();
    }

    private static final class HostIdentity {
        final int previousNotificationId;
        final int nextNotificationId;
        final boolean rotating;

        HostIdentity(
                int previousNotificationId,
                int nextNotificationId,
                boolean rotating
        ) {
            this.previousNotificationId = previousNotificationId;
            this.nextNotificationId = nextNotificationId;
            this.rotating = rotating;
        }
    }

    private static final class ShortcutPublication {
        final boolean ready;
        final boolean temporarilyDynamic;

        ShortcutPublication(boolean ready, boolean temporarilyDynamic) {
            this.ready = ready;
            this.temporarilyDynamic = temporarilyDynamic;
        }
    }
}
