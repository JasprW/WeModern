package me.jaspr.wemodern;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ConversationShortcutIconTest {
    @Test
    public void adaptiveShortcutAvatarKeepsFullImageInsideBubbleSafeZone() {
        assertArrayEquals(
                new int[] {32, 32, 160, 160},
                ConversationShortcuts.adaptiveIconBounds(192)
        );
    }

    @Test
    public void transparentPaddingIsExcludedFromFitXYSourceBounds() {
        int[] pixels = new int[16];
        pixels[5] = 0xff112233;
        pixels[6] = 0xff112233;
        pixels[9] = 0xff112233;
        pixels[10] = 0xff112233;

        assertArrayEquals(
                new int[] {1, 1, 3, 3},
                ConversationShortcuts.visiblePixelBounds(pixels, 4, 4)
        );
    }

    @Test
    public void fullyTransparentAvatarKeepsItsOriginalBounds() {
        assertArrayEquals(
                new int[] {0, 0, 4, 4},
                ConversationShortcuts.visiblePixelBounds(new int[16], 4, 4)
        );
    }
}
