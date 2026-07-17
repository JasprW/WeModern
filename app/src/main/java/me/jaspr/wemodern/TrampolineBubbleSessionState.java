package me.jaspr.wemodern;

/** Tracks the Android task currently owned by the trampoline bubble. */
final class TrampolineBubbleSessionState {
    private static final int NO_TASK = -1;

    private static volatile int embeddedTaskId = NO_TASK;

    private TrampolineBubbleSessionState() {
    }

    static void onEmbeddedLaunchStarted(int taskId) {
        embeddedTaskId = taskId;
    }

    static boolean isEmbeddedSessionActive() {
        return embeddedTaskId != NO_TASK;
    }

    static boolean isEmbeddedTask(int taskId) {
        return taskId != NO_TASK && embeddedTaskId == taskId;
    }

    static boolean onTaskRemoved(int taskId) {
        if (!isEmbeddedTask(taskId)) return false;
        embeddedTaskId = NO_TASK;
        return true;
    }

    static void onHostCleared() {
        embeddedTaskId = NO_TASK;
    }

    static void resetForTest() {
        embeddedTaskId = NO_TASK;
    }
}
