package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ConversationBubblePreferencesTest {
    @Test
    public void defaultsAllowPrivateAndGroupConversationsIndependently() {
        assertTrue(ConversationBubblePreferences.resolve(
                true, false, false, ConversationBubblePreferences.Override.DEFAULT));
        assertFalse(ConversationBubblePreferences.resolve(
                true, false, true, ConversationBubblePreferences.Override.DEFAULT));
        assertFalse(ConversationBubblePreferences.resolve(
                false, true, false, ConversationBubblePreferences.Override.DEFAULT));
        assertTrue(ConversationBubblePreferences.resolve(
                false, true, true, ConversationBubblePreferences.Override.DEFAULT));
    }

    @Test
    public void conversationOverrideWinsOverChatTypeDefault() {
        assertTrue(ConversationBubblePreferences.resolve(
                false, false, true, ConversationBubblePreferences.Override.ENABLED));
        assertFalse(ConversationBubblePreferences.resolve(
                true, true, false, ConversationBubblePreferences.Override.DISABLED));
    }

    @Test
    public void recentSortUsesLatestNotificationFirst() {
        List<ConversationBubblePreferences.Entry> sorted =
                ConversationBubblePreferences.sort(
                        List.of(
                                entry("older", 100L, 20L),
                                entry("newer", 300L, 1L),
                                entry("middle", 200L, 10L)
                        ),
                        ConversationBubblePreferences.SortOrder.RECENT
                );

        assertEquals("newer", sorted.get(0).getId());
        assertEquals("middle", sorted.get(1).getId());
        assertEquals("older", sorted.get(2).getId());
    }

    @Test
    public void countSortUsesTotalThenRecency() {
        List<ConversationBubblePreferences.Entry> sorted =
                ConversationBubblePreferences.sort(
                        List.of(
                                entry("lower", 500L, 2L),
                                entry("older-tie", 100L, 7L),
                                entry("newer-tie", 200L, 7L)
                        ),
                        ConversationBubblePreferences.SortOrder.COUNT
                );

        assertEquals("newer-tie", sorted.get(0).getId());
        assertEquals("older-tie", sorted.get(1).getId());
        assertEquals("lower", sorted.get(2).getId());
    }

    @Test
    public void avatarRevisionUpdatesEvenForRepeatedNotificationTimestamp() {
        ConversationBubblePreferences.Entry original = new ConversationBubblePreferences.Entry(
                "conversation",
                "Conversation",
                false,
                100L,
                3L,
                10L,
                ConversationBubblePreferences.Override.DEFAULT
        );

        ConversationBubblePreferences.Entry updated = original.withNotification(
                "Conversation",
                false,
                100L,
                20L
        );

        assertEquals(3L, updated.getNotificationCount());
        assertEquals(20L, updated.getAvatarRevision());
    }

    @Test
    public void olderAvatarRevisionDoesNotReplaceNewerCachedAvatar() {
        ConversationBubblePreferences.Entry original = new ConversationBubblePreferences.Entry(
                "conversation",
                "Conversation",
                false,
                100L,
                3L,
                20L,
                ConversationBubblePreferences.Override.DEFAULT
        );

        ConversationBubblePreferences.Entry updated = original.withNotification(
                "Conversation",
                false,
                200L,
                15L
        );

        assertEquals(4L, updated.getNotificationCount());
        assertEquals(20L, updated.getAvatarRevision());
    }

    private static ConversationBubblePreferences.Entry entry(
            String id,
            long lastSeenAt,
            long count
    ) {
        return new ConversationBubblePreferences.Entry(
                id,
                id,
                false,
                lastSeenAt,
                count,
                0L,
                ConversationBubblePreferences.Override.DEFAULT
        );
    }
}
