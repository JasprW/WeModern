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

    private static final String TAG = "WeModern.Logs";
    private static final String EVENT_TAG = "notification_cancel";

    private final Callback callback;
    private volatile boolean running;
    private Thread thread;
    private Process process;

    NotificationCancelLogWatcher(Callback callback) {
        this.callback = callback;
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
        while (running) {
            Process current = null;
            try {
                current = new ProcessBuilder("logcat", "-b", "events", "-v", "brief").redirectErrorStream(true).start();
                process = current;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(current.getInputStream()))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
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
}
