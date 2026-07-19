package me.jaspr.wemodern;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Prevents duplicate listener instances from processing the same notification post twice. */
final class NotificationPostDeduplicator {
    private static final int MAX_TRACKED_KEYS = 128;

    private final LinkedHashMap<String, PostIdentity> latestPosts = new LinkedHashMap<>();

    synchronized boolean shouldProcess(String key, long postTime, long notificationWhen) {
        if (key == null) return true;
        PostIdentity candidate = new PostIdentity(postTime, notificationWhen);
        if (candidate.equals(latestPosts.get(key))) return false;

        latestPosts.put(key, candidate);
        if (latestPosts.size() > MAX_TRACKED_KEYS) {
            String oldestKey = latestPosts.entrySet().iterator().next().getKey();
            latestPosts.remove(oldestKey);
        }
        return true;
    }

    synchronized void forget(String key) {
        if (key != null) latestPosts.remove(key);
    }

    private static final class PostIdentity {
        final long postTime;
        final long notificationWhen;

        PostIdentity(long postTime, long notificationWhen) {
            this.postTime = postTime;
            this.notificationWhen = notificationWhen;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PostIdentity)) return false;
            PostIdentity that = (PostIdentity) other;
            return postTime == that.postTime && notificationWhen == that.notificationWhen;
        }

        @Override
        public int hashCode() {
            return Objects.hash(postTime, notificationWhen);
        }
    }
}
