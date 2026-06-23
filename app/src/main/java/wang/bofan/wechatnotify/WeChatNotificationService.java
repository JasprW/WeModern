package wang.bofan.wechatnotify;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WeChatNotificationService extends NotificationListenerService {
    private static final String TAG = "WeChatNotify";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WECHAT_VOIP_CHANNEL = "voip_notify_channel_new_id";
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";
    private static final int MAX_HISTORY = 8;
    private final Map<String, ArrayDeque<Message>> histories = new HashMap<>();
    private final Map<String, String> originalToConversation = new HashMap<>();
    private final Set<String> selfHiddenOriginals = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensure(this);
        Log.i(TAG, "service created, sdk=" + Build.VERSION.SDK_INT);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "listener connected");
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            if (WECHAT_PACKAGE.equals(sbn.getPackageName())) {
                handleWeChatNotification(sbn, true);
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        handleWeChatNotification(sbn, false);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        handleWeChatNotificationRemoved(sbn, 0);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        handleWeChatNotificationRemoved(sbn, reason);
    }

    private void handleWeChatNotificationRemoved(StatusBarNotification sbn, int reason) {
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        String key = sbn.getKey();
        if (selfHiddenOriginals.remove(key)) {
            originalToConversation.remove(key);
            Log.d(TAG, "wechat hidden by self: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        if (WeChatParser.isVoipNotification(sbn.getNotification())) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(voipReplacementId(sbn));
            Log.i(TAG, "cancel rewritten voip notification: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        String conversationKey = originalToConversation.remove(key);
        if (conversationKey != null) {
            histories.remove(conversationKey);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(stableId(conversationKey));
            Log.i(TAG, "cancel rewritten wechat notification after original removal"
                    + ", key=" + key
                    + ", conversation=" + conversationKey
                    + ", reason=" + reasonName(reason));
            return;
        }
        Log.d(TAG, "wechat removed: key=" + key + ", reason=" + reasonName(reason));
    }

    private void handleWeChatNotification(StatusBarNotification sbn, boolean fromActiveScan) {
        Notification original = sbn.getNotification();
        ParsedVoipNotification voip = WeChatParser.parseVoip(this, sbn);
        if (voip != null) {
            postVoipReplacement(sbn, voip, original);
            hideOriginal(sbn);
            Log.i(TAG, "rewritten wechat voip notification"
                    + ", fromActiveScan=" + fromActiveScan
                    + ", title=" + voip.title
                    + ", text=" + voip.text
                    + ", key=" + sbn.getKey());
            return;
        }

        ParsedNotification parsed = WeChatParser.parse(sbn);
        if (parsed == null) {
            Log.d(TAG, "skip wechat notification: key=" + sbn.getKey()
                    + ", channel=" + channelId(original)
                    + ", flags=" + original.flags);
            return;
        }

        ArrayDeque<Message> history = histories.computeIfAbsent(parsed.conversationKey, key -> new ArrayDeque<>());
        Message message = new Message(parsed.sender, parsed.text, sbn.getPostTime(), resolveSenderIcon(original));
        if (!containsRecentDuplicate(history, message)) {
            history.addLast(message);
            while (history.size() > MAX_HISTORY) history.removeFirst();
        }

        originalToConversation.put(sbn.getKey(), parsed.conversationKey);
        postReplacement(sbn, parsed, history, original);
        hideOriginal(sbn);
        hideDuplicateOriginals(parsed, sbn.getKey());
        Log.i(TAG, "rewritten wechat notification"
                + ", fromActiveScan=" + fromActiveScan
                + ", title=" + parsed.title
                + ", sender=" + parsed.sender
                + ", text=" + parsed.text
                + ", key=" + sbn.getKey());
    }

    private void postReplacement(StatusBarNotification sbn, ParsedNotification parsed,
                                 ArrayDeque<Message> history, Notification original) {
        CharSequence contentText = parsed.groupConversation
                ? parsed.sender + ": " + parsed.text
                : parsed.text;
        Icon senderIcon = resolveSenderIcon(original);

        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_MESSAGES)
                .setSmallIcon(resolveSmallIcon(original))
                .setContentTitle(parsed.title)
                .setContentText(contentText)
                .setStyle(buildMessageStyle(parsed, history, true))
                .setWhen(sbn.getPostTime())
                .setShowWhen(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(0xff33b332)
                .setGroup("wechat.rewritten");
        if (senderIcon != null) {
            builder.setLargeIcon(senderIcon);
        }

        PendingIntent contentIntent = original.contentIntent;
        if (contentIntent != null) builder.setContentIntent(contentIntent);
        PendingIntent deleteIntent = original.deleteIntent;
        if (deleteIntent != null) builder.setDeleteIntent(deleteIntent);

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setAllowSystemGeneratedContextualActions(true);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setShortcutId(parsed.conversationKey);
            builder.setLocusId(new android.content.LocusId(parsed.conversationKey));
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(stableId(parsed.conversationKey), builder.build());
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post with original icons, falling back", e);
            builder.setSmallIcon(R.drawable.ic_wechat_scan_24dp);
            builder.setLargeIcon((Icon) null);
            builder.setStyle(buildMessageStyle(parsed, history, false));
            nm.notify(stableId(parsed.conversationKey), builder.build());
        }
    }

    private Notification.MessagingStyle buildMessageStyle(ParsedNotification parsed,
                                                          ArrayDeque<Message> history,
                                                          boolean includeSenderIcons) {
        Notification.MessagingStyle style = new Notification.MessagingStyle(getString(R.string.app_name))
                .setConversationTitle(parsed.title);
        if (Build.VERSION.SDK_INT >= 28) {
            style.setGroupConversation(parsed.groupConversation);
        }
        for (Message message : history) {
            if (Build.VERSION.SDK_INT >= 28) {
                Person.Builder sender = new Person.Builder().setName(message.sender);
                if (includeSenderIcons && message.senderIcon != null) {
                    sender.setIcon(message.senderIcon);
                }
                style.addMessage(new Notification.MessagingStyle.Message(
                        message.text, message.when, sender.build()));
            } else {
                style.addMessage(message.text, message.when, message.sender);
            }
        }
        return style;
    }

    private void hideDuplicateOriginals(ParsedNotification parsed, String currentKey) {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification activeSbn : active) {
            if (!WECHAT_PACKAGE.equals(activeSbn.getPackageName())) continue;
            if (TextUtils.equals(currentKey, activeSbn.getKey())) continue;
            ParsedNotification activeParsed = WeChatParser.parse(activeSbn);
            if (activeParsed == null) continue;
            if (!TextUtils.equals(parsed.conversationKey, activeParsed.conversationKey)) continue;
            originalToConversation.put(activeSbn.getKey(), activeParsed.conversationKey);
            hideOriginal(activeSbn);
            Log.i(TAG, "hide duplicate original wechat notification"
                    + ", key=" + activeSbn.getKey()
                    + ", conversation=" + activeParsed.conversationKey);
        }
    }

    private void postVoipReplacement(StatusBarNotification sbn, ParsedVoipNotification voip,
                                     Notification original) {
        long startedAt = original.when > 0 ? original.when : sbn.getPostTime();
        Icon callIcon = Icon.createWithResource(this,
                voip.video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24);
        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_CALLS)
                .setSmallIcon(callIcon)
                .setContentTitle(voip.title)
                .setContentText(voip.text)
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
            builder.setStyle(CallProgressStyle.create(callIcon));
        }

        PendingIntent contentIntent = original.contentIntent;
        if (contentIntent != null) builder.setContentIntent(contentIntent);
        PendingIntent deleteIntent = original.deleteIntent;
        if (deleteIntent != null) builder.setDeleteIntent(deleteIntent);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            Notification notification = builder.build();
            if (Build.VERSION.SDK_INT >= 36) {
                notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
                Log.i(TAG, "voip promotedAllowed="
                        + getSystemService(NotificationManager.class).canPostPromotedNotifications()
                        + ", promotable=" + notification.hasPromotableCharacteristics());
            }
            nm.notify(voipReplacementId(sbn), notification);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post voip with material small icon, falling back", e);
            builder.setSmallIcon(R.drawable.ic_material_call_24);
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setStyle(CallProgressStyle.create(
                        Icon.createWithResource(this, R.drawable.ic_material_call_24)));
            }
            Notification notification = builder.build();
            if (Build.VERSION.SDK_INT >= 36) {
                notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
            }
            nm.notify(voipReplacementId(sbn), notification);
        }
    }

    private static Icon resolveSmallIcon(Notification original) {
        Icon icon = original.getSmallIcon();
        if (icon != null) return icon;
        return Icon.createWithResource("wang.bofan.wechatnotify", R.drawable.ic_wechat_scan_24dp);
    }

    private static Icon resolveSenderIcon(Notification original) {
        return original.getLargeIcon();
    }

    private void hideOriginal(StatusBarNotification sbn) {
        try {
            selfHiddenOriginals.add(sbn.getKey());
            cancelNotification(sbn.getKey());
            Log.d(TAG, "cancel original wechat notification: " + sbn.getKey());
        } catch (RuntimeException e) {
            selfHiddenOriginals.remove(sbn.getKey());
            Log.w(TAG, "failed to hide original: " + sbn.getKey(), e);
        }
    }

    private static String reasonName(int reason) {
        if (reason == 0) return "unknown";
        if (reason == NotificationListenerService.REASON_CANCEL) return "cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL) return "app_cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL_ALL) return "app_cancel_all";
        if (reason == 10) return "listener_cancel";
        return String.valueOf(reason);
    }

    private static boolean containsRecentDuplicate(ArrayDeque<Message> history, Message candidate) {
        for (Message message : history) {
            if (TextUtils.equals(message.sender, candidate.sender)
                    && TextUtils.equals(message.text, candidate.text)
                    && Math.abs(message.when - candidate.when) < 5000) {
                return true;
            }
        }
        return false;
    }

    private static int stableId(String key) {
        return 0x57000000 ^ key.hashCode();
    }

    private static int voipReplacementId(StatusBarNotification sbn) {
        return stableId("wechat:voip:" + sbn.getId() + ":" + sbn.getTag());
    }

    private static String channelId(Notification notification) {
        return Build.VERSION.SDK_INT >= 26 ? notification.getChannelId() : "";
    }

    private static final class Message {
        final CharSequence sender;
        final CharSequence text;
        final long when;
        final Icon senderIcon;

        Message(CharSequence sender, CharSequence text, long when, Icon senderIcon) {
            this.sender = sender;
            this.text = text;
            this.when = when;
            this.senderIcon = senderIcon;
        }
    }

    static final class ParsedNotification {
        final String conversationKey;
        final CharSequence title;
        final CharSequence sender;
        final CharSequence text;
        final boolean groupConversation;

        ParsedNotification(String conversationKey, CharSequence title, CharSequence sender,
                           CharSequence text, boolean groupConversation) {
            this.conversationKey = conversationKey;
            this.title = title;
            this.sender = sender;
            this.text = text;
            this.groupConversation = groupConversation;
        }
    }

    static final class ParsedVoipNotification {
        final CharSequence title;
        final CharSequence text;
        final boolean video;

        ParsedVoipNotification(CharSequence title, CharSequence text, boolean video) {
            this.title = title;
            this.text = text;
            this.video = video;
        }
    }

    static final class WeChatParser {
        static ParsedVoipNotification parseVoip(Context context, StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            if (!isVoipNotification(n)) return null;
            Bundle extras = n.extras;
            CharSequence title = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isEmpty(title)) title = context.getString(R.string.wechat_call_title);
            if (isEmpty(text)) text = context.getString(R.string.wechat_call_in_progress);
            return new ParsedVoipNotification(title, text, isVideoCall(title, text));
        }

        private static boolean isVideoCall(CharSequence title, CharSequence text) {
            String value = (clean(title) + " " + clean(text)).toLowerCase();
            return value.contains("视频") || value.contains("視訊") || value.contains("video");
        }

        static boolean isVoipNotification(Notification n) {
            return (n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                    && WECHAT_VOIP_CHANNEL.equals(channelId(n));
        }

        static ParsedNotification parse(StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            if (isVoipNotification(n)) {
                return null;
            }
            Bundle extras = n.extras;
            if (extras == null) return null;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isEmpty(title) || isEmpty(text)) return null;

            String channel = channelId(n);
            if (!isConversationChannel(channel, n)) return null;

            String titleString = clean(title);
            String body = clean(text);
            String ticker = n.tickerText == null ? "" : clean(n.tickerText);

            ParsedText parsedBody = parseBody(titleString, body);
            ParsedText parsedTicker = ticker.isEmpty() ? null : parseBody(titleString, ticker);
            ParsedText chosen = parsedTicker != null && !isEmpty(parsedTicker.text) ? parsedTicker : parsedBody;
            if (chosen == null || isEmpty(chosen.text)) return null;

            boolean group = chosen.sender != null && !TextUtils.equals(chosen.sender, titleString);
            CharSequence sender = group ? chosen.sender : titleString;
            String conversationKey = "wechat:" + titleString;
            return new ParsedNotification(conversationKey, titleString, sender, chosen.text, group);
        }

        private static boolean isConversationChannel(String channel, Notification n) {
            if ("message_channel_new_id".equals(channel)) return true;
            if ("message_dnd_mode_channel_id".equals(channel)) return true;
            if ("message_channel_id".equals(channel)) return true;
            if (n.tickerText != null) return true;
            return channel == null || channel.isEmpty();
        }

        private static ParsedText parseBody(String title, String body) {
            String value = stripCountPrefix(body).trim();
            String sender = null;
            String text = value;

            String titlePrefix = title + ": ";
            if (value.startsWith(titlePrefix)) {
                text = value.substring(titlePrefix.length()).trim();
            } else {
                int colon = value.indexOf(": ");
                if (colon > 0 && colon < 64) {
                    sender = value.substring(0, colon).trim();
                    text = value.substring(colon + 2).trim();
                }
            }
            if (sender == null || sender.isEmpty()) sender = title;
            return new ParsedText(sender, text);
        }

        private static String stripCountPrefix(String value) {
            if (value.startsWith("[") && value.contains("]")) {
                int end = value.indexOf(']');
                if (end > 0 && end < 12) {
                    return value.substring(end + 1);
                }
            }
            return value;
        }

        private static String clean(CharSequence value) {
            return value == null ? "" : value.toString().trim();
        }

        private static boolean isEmpty(CharSequence value) {
            return value == null || value.length() == 0;
        }
    }

    private static final class ParsedText {
        final String sender;
        final String text;

        ParsedText(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }
}
