package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class TrampolineBubbleSessionStateTest {
    @After
    public void resetState() {
        TrampolineBubbleSessionState.resetForTest();
    }

    @Test
    public void sessionTracksTheExactEmbeddedTask() {
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());

        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(41));

        assertFalse(TrampolineBubbleSessionState.onTaskRemoved(41));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());

        assertTrue(TrampolineBubbleSessionState.onTaskRemoved(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void clearingHostEndsTheEmbeddedSession() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        TrampolineBubbleSessionState.onHostCleared();
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }
}
