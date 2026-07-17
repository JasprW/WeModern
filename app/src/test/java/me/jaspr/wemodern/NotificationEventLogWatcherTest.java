package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NotificationEventLogWatcherTest {
    @Test
    public void parsesTaskAwareResumedActivityEvents() {
        NotificationCancelLogWatcher.ActivityEvent restarted =
                NotificationCancelLogWatcher.parseActivityEvent(
                        "I wm_restart_activity: [0,42338432,18639,"
                                + "com.tencent.mm/.ui.LauncherUI]"
                );
        assertEquals(NotificationCancelLogWatcher.ActivityEvent.TYPE_RESUMED, restarted.type);
        assertEquals(18639, restarted.taskId);
        assertEquals("com.tencent.mm/.ui.LauncherUI", restarted.componentName);
        assertNull(restarted.action);

        NotificationCancelLogWatcher.ActivityEvent resumed =
                NotificationCancelLogWatcher.parseActivityEvent(
                        "I wm_resume_activity: [0,210598936,18613,"
                                + "com.tencent.mm/.ui.LauncherUI]"
                );
        assertEquals(NotificationCancelLogWatcher.ActivityEvent.TYPE_RESUMED, resumed.type);
        assertEquals(18613, resumed.taskId);
        assertEquals("com.tencent.mm/.ui.LauncherUI", resumed.componentName);
    }

    @Test
    public void parsesCreatedActivityWithTaskAndAction() {
        NotificationCancelLogWatcher.ActivityEvent created =
                NotificationCancelLogWatcher.parseActivityEvent(
                        "I wm_create_activity: [0,259457502,18641,"
                                + "com.tencent.mm/.ui.LauncherUI,NULL,NULL,NULL,67108864]"
                );
        assertEquals(NotificationCancelLogWatcher.ActivityEvent.TYPE_CREATED, created.type);
        assertEquals(18641, created.taskId);
        assertEquals("com.tencent.mm/.ui.LauncherUI", created.componentName);
        assertNull(created.action);

        NotificationCancelLogWatcher.ActivityEvent normalLaunch =
                NotificationCancelLogWatcher.parseActivityEvent(
                        "I wm_create_activity: [0,1,2,com.tencent.mm/.ui.LauncherUI,"
                                + "android.intent.action.MAIN,NULL,NULL,268435456]"
                );
        assertEquals("android.intent.action.MAIN", normalLaunch.action);
    }

    @Test
    public void parsesRemovedBubbleTask() {
        NotificationCancelLogWatcher.ActivityEvent removed =
                NotificationCancelLogWatcher.parseActivityEvent(
                        "I wm_task_removed: [18639,18639,0,removeChild, last child]"
                );
        assertEquals(NotificationCancelLogWatcher.ActivityEvent.TYPE_TASK_REMOVED, removed.type);
        assertEquals(18639, removed.taskId);
        assertNull(removed.componentName);
        assertNull(removed.action);
    }

    @Test
    public void ignoresUnrelatedOrMalformedEvents() {
        assertNull(NotificationCancelLogWatcher.parseActivityEvent(
                "I notification_cancel: [0,0,com.tencent.mm,1,null,0,0,0,8]"));
        assertNull(NotificationCancelLogWatcher.parseActivityEvent(
                "I wm_resume_activity: [0,missing-component]"));
        assertNull(NotificationCancelLogWatcher.parseActivityEvent(null));
    }
}
