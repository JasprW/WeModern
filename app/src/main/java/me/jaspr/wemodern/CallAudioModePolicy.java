package me.jaspr.wemodern;

import android.media.AudioManager;

/** Keeps the device-global audio mode signal scoped to an identified WeChat call. */
final class CallAudioModePolicy {
    private CallAudioModePolicy() {
    }

    static boolean shouldConfirmConnection(
            boolean rewriteEnabled,
            boolean alreadyConnected,
            boolean hasTrackedStatus,
            long connectedCandidateAt,
            int audioMode
    ) {
        return rewriteEnabled
                && !alreadyConnected
                && hasTrackedStatus
                && connectedCandidateAt > 0L
                && isConnectedMode(audioMode);
    }

    static boolean isConnectedMode(int audioMode) {
        return audioMode == AudioManager.MODE_IN_COMMUNICATION
                || audioMode == AudioManager.MODE_IN_CALL;
    }

    static String modeName(int audioMode) {
        if (audioMode == AudioManager.MODE_NORMAL) return "normal";
        if (audioMode == AudioManager.MODE_RINGTONE) return "ringtone";
        if (audioMode == AudioManager.MODE_IN_CALL) return "in_call";
        if (audioMode == AudioManager.MODE_IN_COMMUNICATION) return "in_communication";
        return String.valueOf(audioMode);
    }
}
