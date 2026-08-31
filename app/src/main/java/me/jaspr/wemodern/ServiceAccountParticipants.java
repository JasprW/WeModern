package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Person;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ServiceAccountParticipants {
    private static final String TAG = "WeModern";
    private static final String PREFERENCES = "service_account_participants";
    private static final String KEY_PARTICIPANTS = "participants";
    private static final String ICON_DIRECTORY = "service_account_participant_icons";
    private static final int MAX_PARTICIPANTS = 25;
    private static final int MAX_SHORTCUT_PARTICIPANTS = 8;
    private static final int PARTICIPANT_ICON_SIZE_PX = 96;

    private ServiceAccountParticipants() {
    }

    static synchronized Icon record(
            Context context,
            String participantKey,
            CharSequence name,
            Icon sourceIcon,
            long seenAt
    ) {
        if (participantKey == null || participantKey.isEmpty()) return sourceIcon;
        List<Entry> entries = load(context);
        String normalizedName = normalizeName(name, participantKey);
        Icon resolvedIcon = resolveIcon(
                context,
                participantKey,
                normalizedName,
                sourceIcon
        );
        int existingIndex = indexOf(entries, participantKey);
        Entry updated = new Entry(
                participantKey,
                normalizedName,
                Math.max(0L, seenAt)
        );
        if (existingIndex >= 0) {
            Entry existing = entries.remove(existingIndex);
            updated = new Entry(
                    participantKey,
                    normalizedName.isEmpty() ? existing.name : normalizedName,
                    Math.max(existing.lastSeenAt, Math.max(0L, seenAt))
            );
        }
        entries.add(updated);
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.lastSeenAt).reversed());
        while (entries.size() > MAX_PARTICIPANTS) {
            Entry removed = entries.remove(entries.size() - 1);
            File removedIcon = iconFile(context, removed.key);
            if (removedIcon.isFile() && !removedIcon.delete()) {
                Log.w(TAG, "failed to delete stale service account avatar: " + removed.key);
            }
        }
        save(context, entries);
        return resolvedIcon;
    }

    static synchronized int migrateLegacyAvatars(Context context) {
        List<Entry> entries = load(context);
        int migratedCount = 0;
        Icon latestIcon = null;
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            boolean hadParticipantIcon = iconFile(context, entry.key).isFile();
            Icon resolved = resolveIcon(context, entry.key, entry.name, null);
            if (!hadParticipantIcon && resolved != null) migratedCount++;
            if (index == 0) latestIcon = resolved;
        }
        if (latestIcon != null) {
            ConversationShortcuts.updateAvatarCache(
                    context,
                    ServiceAccountConversation.CONVERSATION_ID,
                    latestIcon
            );
        }
        if (migratedCount > 0) {
            Log.i(TAG, "migrated legacy service account avatars: " + migratedCount);
        }
        return migratedCount;
    }

    @TargetApi(Build.VERSION_CODES.P)
    static synchronized Person[] getPersons(Context context) {
        List<Entry> entries = load(context);
        int count = Math.min(entries.size(), MAX_SHORTCUT_PARTICIPANTS);
        Person[] persons = new Person[count];
        for (int index = 0; index < count; index++) {
            Entry entry = entries.get(index);
            Person.Builder builder = new Person.Builder()
                    .setName(entry.name)
                    .setKey(entry.key)
                    .setBot(true);
            Icon icon = loadIcon(context, entry.key);
            if (icon != null) builder.setIcon(icon);
            persons[index] = builder.build();
        }
        return persons;
    }

    private static Icon resolveIcon(
            Context context,
            String participantKey,
            CharSequence participantName,
            Icon sourceIcon
    ) {
        if (sourceIcon == null) {
            Icon cached = loadIcon(context, participantKey);
            if (cached != null) return cached;
            String legacyConversationId =
                    ServiceAccountConversation.legacySenderConversationId(participantName);
            if (legacyConversationId == null) return null;
            Bitmap legacy = ConversationShortcuts.loadConversationAvatar(
                    context,
                    legacyConversationId
            );
            if (legacy == null) return null;
            sourceIcon = Icon.createWithBitmap(legacy);
        }
        Bitmap bitmap = ConversationShortcuts.circularAvatarBitmap(context, sourceIcon);
        if (bitmap == null) return sourceIcon;
        if (bitmap.getWidth() != PARTICIPANT_ICON_SIZE_PX
                || bitmap.getHeight() != PARTICIPANT_ICON_SIZE_PX) {
            bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    PARTICIPANT_ICON_SIZE_PX,
                    PARTICIPANT_ICON_SIZE_PX,
                    true
            );
        }
        File directory = iconDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "failed to create service account avatar directory");
            return Icon.createWithBitmap(bitmap);
        }
        try {
            writeBitmap(iconFile(context, participantKey), bitmap);
        } catch (IOException e) {
            Log.w(TAG, "failed to cache service account avatar: " + participantKey, e);
        }
        return Icon.createWithBitmap(bitmap);
    }

    private static Icon loadIcon(Context context, String participantKey) {
        Bitmap bitmap = BitmapFactory.decodeFile(iconFile(context, participantKey).getPath());
        return bitmap == null ? null : Icon.createWithBitmap(bitmap);
    }

    private static int indexOf(List<Entry> entries, String participantKey) {
        for (int index = 0; index < entries.size(); index++) {
            if (participantKey.equals(entries.get(index).key)) return index;
        }
        return -1;
    }

    private static List<Entry> load(Context context) {
        String raw = preferences(context).getString(KEY_PARTICIPANTS, "[]");
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                if (json == null) continue;
                String key = json.optString("key", "");
                if (key.isEmpty()) continue;
                entries.add(new Entry(
                        key,
                        normalizeName(json.optString("name", ""), key),
                        Math.max(0L, json.optLong("lastSeenAt", 0L))
                ));
            }
        } catch (JSONException e) {
            Log.w(TAG, "failed to restore service account participants", e);
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.lastSeenAt).reversed());
        return entries;
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject json = new JSONObject();
            try {
                json.put("key", entry.key);
                json.put("name", entry.name);
                json.put("lastSeenAt", entry.lastSeenAt);
                array.put(json);
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        preferences(context).edit().putString(KEY_PARTICIPANTS, array.toString()).apply();
    }

    private static String normalizeName(CharSequence name, String participantKey) {
        String normalized = name == null ? "" : name.toString().trim();
        return normalized.isEmpty() ? participantKey : normalized;
    }

    private static File iconDirectory(Context context) {
        return new File(context.getFilesDir(), ICON_DIRECTORY);
    }

    private static File iconFile(Context context, String participantKey) {
        return new File(
                iconDirectory(context),
                Integer.toHexString(participantKey.hashCode()) + ".png"
        );
    }

    private static void writeBitmap(File file, Bitmap bitmap) throws IOException {
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("failed to encode service account avatar");
            }
            atomicFile.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            if (output != null) atomicFile.failWrite(output);
            throw e;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static final class Entry {
        final String key;
        final String name;
        final long lastSeenAt;

        Entry(String key, String name, long lastSeenAt) {
            this.key = key;
            this.name = name;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
