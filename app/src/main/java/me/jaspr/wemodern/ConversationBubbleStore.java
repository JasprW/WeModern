package me.jaspr.wemodern;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class ConversationBubbleStore {
    interface Listener {
        void onConversationBubbleChanged(ConversationBubbleState state);
    }

    private static final Map<String, ConversationBubbleState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<Listener>> LISTENERS =
            new ConcurrentHashMap<>();

    private ConversationBubbleStore() {
    }

    static ConversationBubbleState get(String conversationId) {
        return conversationId == null ? null : STATES.get(conversationId);
    }

    static void update(ConversationBubbleState state) {
        if (state == null || state.conversationId == null) return;
        STATES.put(state.conversationId, state);
        notifyListeners(state.conversationId, state);
    }

    static void remove(String conversationId) {
        if (conversationId == null) return;
        STATES.remove(conversationId);
        notifyListeners(conversationId, null);
    }

    static void addListener(String conversationId, Listener listener) {
        if (conversationId == null || listener == null) return;
        LISTENERS.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>())
                .addIfAbsent(listener);
    }

    static void removeListener(String conversationId, Listener listener) {
        if (conversationId == null || listener == null) return;
        List<Listener> listeners = LISTENERS.get(conversationId);
        if (listeners == null) return;
        listeners.remove(listener);
        if (listeners.isEmpty()) LISTENERS.remove(conversationId);
    }

    private static void notifyListeners(String conversationId, ConversationBubbleState state) {
        List<Listener> listeners = LISTENERS.get(conversationId);
        if (listeners == null) return;
        for (Listener listener : listeners) {
            listener.onConversationBubbleChanged(state);
        }
    }

    static void clearAllForTest() {
        STATES.clear();
        LISTENERS.clear();
    }
}
