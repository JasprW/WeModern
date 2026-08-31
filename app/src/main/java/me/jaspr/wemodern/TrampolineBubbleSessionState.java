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
        EMBEDDED_SESSIONS.put(taskId, new Session(conversationId, false));
    }

    static synchronized void onSharedHostLaunchStarted(int taskId) {
        if (taskId < 0) return;
        EMBEDDED_SESSIONS.put(taskId, new Session(null, true));
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

    static synchronized boolean isSharedHostTask(int taskId) {
        Session session = EMBEDDED_SESSIONS.get(taskId);
        return session != null && session.sharedHost;
    }

    static synchronized String conversationIdForTask(int taskId) {
        Session session = EMBEDDED_SESSIONS.get(taskId);
        return session == null ? null : session.conversationId;
    }

    static synchronized boolean shouldCollapseAfterActivityResumed(
            int taskId,
            String componentName
    ) {
        Session session = EMBEDDED_SESSIONS.get(taskId);
        if (session == null || session.sharedHost) return false;

        if (WeChatLauncher.isChattingActivity(componentName)) {
            session.chattingReturnArmed = true;
            session.collapseRequested = false;
            return false;
        }
        if (WeChatLauncher.isLauncherActivity(componentName)) {
            if (!session.chattingReturnArmed || session.collapseRequested) return false;
            session.chattingReturnArmed = false;
            session.collapseRequested = true;
            return true;
        }

        session.chattingReturnArmed = false;
        return false;
    }

    static synchronized void onCollapseRequestFailed(int taskId) {
        Session session = EMBEDDED_SESSIONS.get(taskId);
        if (session == null || session.sharedHost) return;
        session.chattingReturnArmed = true;
        session.collapseRequested = false;
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
        final boolean sharedHost;
        boolean chattingReturnArmed;
        boolean collapseRequested;

        Session(String conversationId, boolean sharedHost) {
            this.conversationId = conversationId;
            this.sharedHost = sharedHost;
        }
    }
}
