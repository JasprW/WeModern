package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConversationShortcutLimitTest {
    @Test
    public void launcherReservesLastSupportedSlotForSettings() {
        assertEquals(3, ConversationShortcuts.maxLauncherConversationCount(4));
        assertEquals(3, ConversationShortcuts.launcherConversationCount(8, 4, 1));
    }

    @Test
    public void launcherVisibleLimitWinsWhenPublishingLimitIsHigher() {
        assertEquals(3, ConversationShortcuts.maxLauncherConversationCount(8));
        assertEquals(3, ConversationShortcuts.launcherConversationCount(12, 8, 1));
    }

    @Test
    public void pixelPublishingLimitStillKeepsSettingsInFourthVisibleSlot() {
        assertEquals(3, ConversationShortcuts.maxLauncherConversationCount(6));
        assertEquals(3, ConversationShortcuts.launcherConversationCount(8, 6, 1));
    }

    @Test
    public void testNotificationTemporarilyReservesASecondNonContactSlot() {
        assertEquals(2, ConversationShortcuts.launcherConversationCount(8, 4, 2));
    }

    @Test
    public void launcherNeverPublishesNegativeConversationCounts() {
        assertEquals(0, ConversationShortcuts.maxLauncherConversationCount(0));
        assertEquals(0, ConversationShortcuts.launcherConversationCount(3, 1, 2));
    }
}
