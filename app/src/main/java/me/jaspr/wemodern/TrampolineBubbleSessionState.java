package me.jaspr.wemodern;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Tracks Android tasks currently owned by trampoline bubbles. */
final class TrampolineBubbleSessionState {
    private static final Map<Integer, Session> EMBEDDED_SESSIONS = new HashMap<>();

    private TrampolineBubbleSessionState() {
    }

    static synchronized void onEmbeddedLaunchStarted(int taskId) {
        onEmbeddedLaunchStarted(taskId, null);
    }

    static synchronized void onEmbeddedLaunchStarted(
            int taskId,
            String conversationId
    ) {
        if (taskId < 0) return;
        EMBEDDED_SESSIONS.put(taskId, new Session(conversationId));
    }

    static synchronized boolean isEmbeddedSessionActive() {
        return !EMBEDDED_SESSIONS.isEmpty();
    }

    static synchronized boolean isEmbeddedTask(int taskId) {
        return EMBEDDED_SESSIONS.containsKey(taskId);
    }

    static synchronized boolean isEmbeddedConversation(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return false;
        for (Session session : EMBEDDED_SESSIONS.values()) {
            if (conversationId.equals(session.conversationId)) return true;
        }
        return false;
    }

    static synchronized boolean onTaskRemoved(int taskId) {
        return EMBEDDED_SESSIONS.remove(taskId) != null;
    }

    static synchronized void onConversationHostDismissed(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return;
        Iterator<Map.Entry<Integer, Session>> iterator =
                EMBEDDED_SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (conversationId.equals(session.conversationId)) {
                iterator.remove();
            }
        }
    }

    static synchronized void onAllHostsCleared() {
        EMBEDDED_SESSIONS.clear();
    }

    static synchronized void resetForTest() {
        EMBEDDED_SESSIONS.clear();
    }

    private static final class Session {
        final String conversationId;

        Session(String conversationId) {
            this.conversationId = conversationId;
        }
    }
}
