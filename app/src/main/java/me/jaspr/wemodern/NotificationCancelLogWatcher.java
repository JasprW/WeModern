package me.jaspr.wemodern;

import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class NotificationCancelLogWatcher {
    interface Callback {
        void onAppCancelled(String pkg, int id, String tag, int userId, int reason);
    }

    interface ActivityCallback {
        void onActivityEvent(ActivityEvent event);
    }

    private static final String TAG = "WeModern.Logs";
    private static final String EVENT_TAG = "notification_cancel";
    private static final String RESTART_ACTIVITY_TAG = "wm_restart_activity";
    private static final String RESUME_ACTIVITY_TAG = "wm_resume_activity";
    private static final String CREATE_ACTIVITY_TAG = "wm_create_activity";
    private static final String TASK_REMOVED_TAG = "wm_task_removed";

    private final Callback callback;
    private final ActivityCallback activityCallback;
    private volatile boolean running;
    private Thread thread;
    private Process process;

    NotificationCancelLogWatcher(Callback callback, ActivityCallback activityCallback) {
        this.callback = callback;
        this.activityCallback = activityCallback;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "notification-cancel-log-watcher");
        thread.start();
        Log.i(TAG, "notification cancel log watcher started");
    }

    synchronized void stop() {
        running = false;
        if (process != null) process.destroy();
        process = null;
        thread = null;
        Log.i(TAG, "notification cancel log watcher stopped");
    }

    private void run() {
        seedActivityState();
        while (running) {
            Process current = null;
            try {
                // Follow only new events. Replaying the whole ring buffer would treat historical
                // WeChat cancels and removed task IDs as if they happened in the current session.
                current = new ProcessBuilder(
                        "logcat",
                        "-b",
                        "events",
                        "-v",
                        "brief",
                        "-T",
                        "1"
                ).redirectErrorStream(true).start();
                process = current;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(current.getInputStream()))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        ActivityEvent activityEvent = parseActivityEvent(line);
                        if (activityEvent != null) activityCallback.onActivityEvent(activityEvent);
                        Event event = parse(line);
                        if (event == null) continue;
                        if (event.reason == NotificationListenerService.REASON_APP_CANCEL
                                || event.reason == NotificationListenerService.REASON_APP_CANCEL_ALL) {
                            if ("com.tencent.mm".equals(event.pkg)) {
                                Log.i(TAG, "wechat notification_cancel event"
                                        + ", userId=" + event.userId
                                        + ", id=" + event.id
                                        + ", tag=" + event.tag
                                        + ", reason=" + event.reason);
                            }
                            callback.onAppCancelled(event.pkg, event.id, event.tag, event.userId, event.reason);
                        }
                    }
                }
            } catch (IOException e) {
                if (running) Log.w(TAG, "Unable to read notification cancel logs", e);
            } finally {
                if (current != null) current.destroy();
                if (process == current) process = null;
            }

            if (running) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void seedActivityState() {
        Process seed = null;
        try {
            seed = new ProcessBuilder(
                    "logcat",
                    "-b",
                    "events",
                    "-d",
                    "-v",
                    "brief",
                    "-t",
                    "200"
            ).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(seed.getInputStream()))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    ActivityEvent event = parseActivityEvent(line);
                    if (event == null || event.type == ActivityEvent.TYPE_CREATED) continue;
                    activityCallback.onActivityEvent(event);
                }
            }
        } catch (IOException e) {
            if (running) Log.w(TAG, "Unable to seed foreground activity state", e);
        } finally {
            if (seed != null) seed.destroy();
        }
    }

    static Event parse(String line) {
        if (line == null || !line.contains(EVENT_TAG)) return null;
        int start = line.indexOf('[');
        int end = line.lastIndexOf(']');
        if (start < 0 || end <= start) return null;

        List<String> fields = splitFields(line.substring(start + 1, end));
        if (fields.size() < 9) return null;

        try {
            String pkg = fields.get(2);
            int id = Integer.parseInt(fields.get(3));
            String tag = normalizeTag(fields.get(4));
            int userId = Integer.parseInt(fields.get(5));
            int reason = Integer.parseInt(fields.get(8));
            if (TextUtils.isEmpty(pkg)) return null;
            return new Event(pkg, id, tag, userId, reason);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Unrecognized notification_cancel line: " + line);
            return null;
        }
    }

    static ActivityEvent parseActivityEvent(String line) {
        if (line == null) return null;
        int start = line.indexOf('[');
        int end = line.lastIndexOf(']');
        if (start < 0 || end <= start) return null;

        List<String> fields = splitFields(line.substring(start + 1, end));
        try {
            if (line.contains(CREATE_ACTIVITY_TAG)) {
                if (fields.size() < 5) return null;
                int taskId = Integer.parseInt(fields.get(2));
                String component = fields.get(3).trim();
                if (component.indexOf('/') <= 0) return null;
                return ActivityEvent.created(
                        taskId,
                        component,
                        normalizeTag(fields.get(4))
                );
            }
            if (line.contains(RESTART_ACTIVITY_TAG) || line.contains(RESUME_ACTIVITY_TAG)) {
                if (fields.size() < 4) return null;
                int taskId = Integer.parseInt(fields.get(2));
                String component = fields.get(3).trim();
                if (component.indexOf('/') <= 0) return null;
                return ActivityEvent.resumed(taskId, component);
            }
            if (line.contains(TASK_REMOVED_TAG)) {
                if (fields.isEmpty()) return null;
                return ActivityEvent.taskRemoved(Integer.parseInt(fields.get(0)));
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Unrecognized activity event line: " + line);
        }
        return null;
    }

    private static List<String> splitFields(String payload) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int nested = 0;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == '[') nested++;
            else if (c == ']') nested = Math.max(0, nested - 1);

            if (c == ',' && nested == 0) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }

    static String normalizeTag(String tag) {
        if (tag == null || tag.length() == 0 || "null".equalsIgnoreCase(tag)) return null;
        return tag;
    }

    static final class Event {
        final String pkg;
        final int id;
        final String tag;
        final int userId;
        final int reason;

        Event(String pkg, int id, String tag, int userId, int reason) {
            this.pkg = pkg;
            this.id = id;
            this.tag = tag;
            this.userId = userId;
            this.reason = reason;
        }
    }

    static final class ActivityEvent {
        static final int TYPE_RESUMED = 1;
        static final int TYPE_TASK_REMOVED = 2;
        static final int TYPE_CREATED = 3;

        final int type;
        final int taskId;
        final String componentName;
        final String action;

        private ActivityEvent(int type, int taskId, String componentName, String action) {
            this.type = type;
            this.taskId = taskId;
            this.componentName = componentName;
            this.action = action;
        }

        static ActivityEvent resumed(int taskId, String componentName) {
            return new ActivityEvent(TYPE_RESUMED, taskId, componentName, null);
        }

        static ActivityEvent created(int taskId, String componentName, String action) {
            return new ActivityEvent(TYPE_CREATED, taskId, componentName, action);
        }

        static ActivityEvent taskRemoved(int taskId) {
            return new ActivityEvent(TYPE_TASK_REMOVED, taskId, null, null);
        }
    }
}
