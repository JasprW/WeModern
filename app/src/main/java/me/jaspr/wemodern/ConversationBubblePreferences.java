package me.jaspr.wemodern;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ConversationBubblePreferences {
    enum Override {
        DEFAULT,
        ENABLED,
        DISABLED
    }

    enum SortOrder {
        RECENT,
        COUNT
    }

    static final class Entry {
        private final String id;
        private final String title;
        private final boolean groupConversation;
        private final long lastSeenAt;
        private final long notificationCount;
        private final long avatarRevision;
        private final Override override;

        Entry(
                String id,
                String title,
                boolean groupConversation,
                long lastSeenAt,
                long notificationCount,
                long avatarRevision,
                Override override
        ) {
            this.id = id;
            this.title = title;
            this.groupConversation = groupConversation;
            this.lastSeenAt = lastSeenAt;
            this.notificationCount = notificationCount;
            this.avatarRevision = Math.max(0L, avatarRevision);
            this.override = override == null ? Override.DEFAULT : override;
        }

        String getId() {
            return id;
        }

        String getTitle() {
            return title;
        }

        boolean isGroupConversation() {
            return groupConversation;
        }

        long getLastSeenAt() {
            return lastSeenAt;
        }

        long getNotificationCount() {
            return notificationCount;
        }

        long getAvatarRevision() {
            return avatarRevision;
        }

        Override getOverride() {
            return override;
        }

        Entry withNotification(
                String newTitle,
                boolean group,
                long seenAt,
                long newAvatarRevision
        ) {
            long normalizedSeenAt = Math.max(0L, seenAt);
            boolean newNotification = normalizedSeenAt > lastSeenAt;
            return new Entry(
                    id,
                    normalizeTitle(newTitle, id),
                    group,
                    Math.max(lastSeenAt, normalizedSeenAt),
                    notificationCount + (newNotification ? 1L : 0L),
                    Math.max(avatarRevision, Math.max(0L, newAvatarRevision)),
                    override
            );
        }

        Entry withOverride(Override newOverride) {
            return new Entry(
                    id,
                    title,
                    groupConversation,
                    lastSeenAt,
                    notificationCount,
                    avatarRevision,
                    newOverride
            );
        }
    }

    private static final String PREFERENCES = "conversation_bubble_preferences";
    private static final String KEY_DEFAULT_PRIVATE = "default_private";
    private static final String KEY_DEFAULT_GROUP = "default_group";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_CONVERSATIONS = "conversations";

    private ConversationBubblePreferences() {
    }

    static boolean isDefaultPrivateEnabled(Context context) {
        return preferences(context).getBoolean(KEY_DEFAULT_PRIVATE, true);
    }

    static void setDefaultPrivateEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_DEFAULT_PRIVATE, enabled).apply();
    }

    static boolean isDefaultGroupEnabled(Context context) {
        return preferences(context).getBoolean(KEY_DEFAULT_GROUP, true);
    }

    static void setDefaultGroupEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_DEFAULT_GROUP, enabled).apply();
    }

    static SortOrder getSortOrder(Context context) {
        return parseSortOrder(preferences(context).getString(
                KEY_SORT_ORDER,
                SortOrder.RECENT.name()
        ));
    }

    static void setSortOrder(Context context, SortOrder sortOrder) {
        SortOrder resolved = sortOrder == null ? SortOrder.RECENT : sortOrder;
        preferences(context).edit().putString(KEY_SORT_ORDER, resolved.name()).apply();
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

    static synchronized void record(
            Context context,
            String conversationId,
            CharSequence title,
            boolean groupConversation,
            long seenAt,
            long avatarRevision
    ) {
        if (conversationId == null || conversationId.isEmpty()) return;
        List<Entry> entries = load(context);
        int index = indexOf(entries, conversationId);
        String normalizedTitle = normalizeTitle(
                title == null ? null : title.toString(),
                conversationId
        );
        if (index < 0) {
            entries.add(new Entry(
                    conversationId,
                    normalizedTitle,
                    groupConversation,
                    Math.max(0L, seenAt),
                    1L,
                    Math.max(0L, avatarRevision),
                    Override.DEFAULT
            ));
        } else {
            entries.set(index, entries.get(index).withNotification(
                    normalizedTitle,
                    groupConversation,
                    seenAt,
                    avatarRevision
            ));
        }
        save(context, entries);
    }

    static synchronized void setOverride(
            Context context,
            String conversationId,
            Override override
    ) {
        if (conversationId == null || conversationId.isEmpty()) return;
        List<Entry> entries = load(context);
        int index = indexOf(entries, conversationId);
        if (index < 0) return;
        entries.set(index, entries.get(index).withOverride(override));
        save(context, entries);
    }

    static synchronized List<Entry> getConversations(Context context) {
        return Collections.unmodifiableList(sort(load(context), getSortOrder(context)));
    }

    static synchronized boolean isEnabled(Context context, String conversationId) {
        boolean defaultPrivate = isDefaultPrivateEnabled(context);
        boolean defaultGroup = isDefaultGroupEnabled(context);
        if (conversationId == null || conversationId.isEmpty()) return defaultPrivate;
        List<Entry> entries = load(context);
        int index = indexOf(entries, conversationId);
        if (index < 0) return defaultPrivate;
        Entry entry = entries.get(index);
        return resolve(
                defaultPrivate,
                defaultGroup,
                entry.groupConversation,
                entry.override
        );
    }

    static boolean resolve(
            boolean defaultPrivate,
            boolean defaultGroup,
            boolean groupConversation,
            Override override
    ) {
        if (override == Override.ENABLED) return true;
        if (override == Override.DISABLED) return false;
        return groupConversation ? defaultGroup : defaultPrivate;
    }

    static List<Entry> sort(List<Entry> source, SortOrder order) {
        List<Entry> entries = new ArrayList<>(source);
        Comparator<Entry> comparator;
        if (order == SortOrder.COUNT) {
            comparator = Comparator.comparingLong(Entry::getNotificationCount)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(Entry::getLastSeenAt).reversed());
        } else {
            comparator = Comparator.comparingLong(Entry::getLastSeenAt).reversed();
        }
        comparator = comparator.thenComparing(
                Entry::getTitle,
                String.CASE_INSENSITIVE_ORDER
        );
        entries.sort(comparator);
        return entries;
    }

    private static int indexOf(List<Entry> entries, String conversationId) {
        for (int index = 0; index < entries.size(); index++) {
            if (conversationId.equals(entries.get(index).id)) return index;
        }
        return -1;
    }

    private static List<Entry> load(Context context) {
        String value = preferences(context).getString(KEY_CONVERSATIONS, "[]");
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(value == null ? "[]" : value);
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                if (json == null) continue;
                String id = json.optString("id", "");
                if (id.isEmpty()) continue;
                entries.add(new Entry(
                        id,
                        normalizeTitle(json.optString("title", ""), id),
                        json.optBoolean("group", false),
                        Math.max(0L, json.optLong("lastSeenAt", 0L)),
                        Math.max(0L, json.optLong("notificationCount", 0L)),
                        Math.max(0L, json.optLong("avatarRevision", 0L)),
                        parseOverride(json.optString("override", Override.DEFAULT.name()))
                ));
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        return entries;
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", entry.id);
                json.put("title", entry.title);
                json.put("group", entry.groupConversation);
                json.put("lastSeenAt", entry.lastSeenAt);
                json.put("notificationCount", entry.notificationCount);
                json.put("avatarRevision", entry.avatarRevision);
                json.put("override", entry.override.name());
                array.put(json);
            } catch (JSONException ignored) {
            }
        }
        preferences(context).edit().putString(KEY_CONVERSATIONS, array.toString()).apply();
    }

    private static Override parseOverride(String value) {
        try {
            return Override.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Override.DEFAULT;
        }
    }

    private static SortOrder parseSortOrder(String value) {
        try {
            return SortOrder.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return SortOrder.RECENT;
        }
    }

    private static String normalizeTitle(String title, String conversationId) {
        String normalized = title == null ? "" : title.trim();
        if (!normalized.isEmpty()) return normalized;
        if (conversationId != null && conversationId.startsWith("wechat:")) {
            normalized = conversationId.substring("wechat:".length()).trim();
        }
        return normalized.isEmpty() ? conversationId : normalized;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
