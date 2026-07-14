package me.jaspr.wemodern;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ConversationShortcutIconTest {
    @Test
    public void adaptiveShortcutAvatarUsesTheUnmaskedSafeZone() {
        assertArrayEquals(
                new int[] {28, 28, 164, 164},
                ConversationShortcuts.adaptiveIconBounds(192)
        );
    }
}
