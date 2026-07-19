package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import org.junit.Test;

public class CallAudioModePolicyTest {
    @Test
    public void communicationModeConfirmsTrackedWeChatCandidate() {
        assertTrue(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                true,
                1000L,
                AudioManager.MODE_IN_COMMUNICATION
        ));
        assertTrue(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                true,
                1000L,
                AudioManager.MODE_IN_CALL
        ));
    }

    @Test
    public void normalAndRingtoneModesDoNotConfirmConnection() {
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                true,
                1000L,
                AudioManager.MODE_NORMAL
        ));
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                true,
                1000L,
                AudioManager.MODE_RINGTONE
        ));
    }

    @Test
    public void globalAudioModeRequiresActiveRewriteAndTrackedStatusCandidate() {
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                false,
                false,
                true,
                1000L,
                AudioManager.MODE_IN_COMMUNICATION
        ));
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                true,
                true,
                true,
                1000L,
                AudioManager.MODE_IN_COMMUNICATION
        ));
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                false,
                1000L,
                AudioManager.MODE_IN_COMMUNICATION
        ));
        assertFalse(CallAudioModePolicy.shouldConfirmConnection(
                true,
                false,
                true,
                0L,
                AudioManager.MODE_IN_COMMUNICATION
        ));
    }
}
