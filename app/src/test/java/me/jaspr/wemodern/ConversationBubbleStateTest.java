package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.After;
import org.junit.Test;

public class ConversationBubbleStateTest {
    @After
    public void cleanStore() {
        ConversationBubbleStore.clearAllForTest();
    }

    @Test
    public void historyIsBoundedToEightMessages() {
        ConversationBubbleState state = ConversationBubbleState.create("alice", "Alice", null);

        for (int index = 0; index < 10; index++) {
            state = state.append("Alice", "message-" + index, index, null);
        }

        assertEquals(8, state.messages.size());
        assertEquals("message-2", state.messages.get(0).text);
        assertEquals("message-9", state.messages.get(7).text);
    }

    @Test
    public void immediateDuplicateIsSuppressed() {
        ConversationBubbleState state = ConversationBubbleState.create("alice", "Alice", null)
                .append("Alice", "On my way", 1_000L, null)
                .append("Alice", "On my way", 3_000L, null);

        assertEquals(1, state.messages.size());
    }

    @Test
    public void sameTextLaterIsKept() {
        ConversationBubbleState state = ConversationBubbleState.create("alice", "Alice", null)
                .append("Alice", "OK", 1_000L, null)
                .append("Alice", "OK", 7_000L, null);

        assertEquals(2, state.messages.size());
    }

    @Test
    public void storeKeepsConversationsIndependent() {
        ConversationBubbleState alice = ConversationBubbleState.create("alice", "Alice", null)
                .append("Alice", "A", 1L, null);
        ConversationBubbleState bob = ConversationBubbleState.create("bob", "Bob", null)
                .append("Bob", "B", 2L, null);

        ConversationBubbleStore.update(alice);
        ConversationBubbleStore.update(bob);

        assertNotSame(ConversationBubbleStore.get("alice"), ConversationBubbleStore.get("bob"));
        assertEquals("A", ConversationBubbleStore.get("alice").messages.get(0).text);
        assertEquals("B", ConversationBubbleStore.get("bob").messages.get(0).text);
    }

    @Test
    public void intentSnapshotBoundsUntrustedNotificationText() {
        String oversized = "x".repeat(3_000);

        ConversationBubbleState state = ConversationBubbleState.create("alice", oversized, null)
                .append(oversized, oversized, 1L, null);

        assertEquals(256, state.title.length());
        assertEquals(256, state.messages.get(0).sender.length());
        assertEquals(2_048, state.messages.get(0).text.length());
    }
}
