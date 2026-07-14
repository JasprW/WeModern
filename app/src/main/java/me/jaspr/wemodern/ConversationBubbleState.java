package me.jaspr.wemodern;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ConversationBubbleState {
    static final String EXTRA_CONVERSATION_ID =
            "me.jaspr.wemodern.extra.BUBBLE_CONVERSATION_ID";
    static final String EXTRA_TITLE = "me.jaspr.wemodern.extra.BUBBLE_TITLE";
    static final String EXTRA_SENDERS = "me.jaspr.wemodern.extra.BUBBLE_SENDERS";
    static final String EXTRA_TEXTS = "me.jaspr.wemodern.extra.BUBBLE_TEXTS";
    static final String EXTRA_TIMESTAMPS = "me.jaspr.wemodern.extra.BUBBLE_TIMESTAMPS";
    static final String EXTRA_CONTENT_INTENT = "me.jaspr.wemodern.extra.BUBBLE_CONTENT_INTENT";

    private static final int MAX_MESSAGES = 8;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_SENDER_LENGTH = 256;
    private static final int MAX_TEXT_LENGTH = 2_048;
    private static final long DUPLICATE_WINDOW_MS = 5_000L;

    final String conversationId;
    final String title;
    final List<Message> messages;
    final PendingIntent contentIntent;

    private ConversationBubbleState(
            String conversationId,
            String title,
            List<Message> messages,
            PendingIntent contentIntent
    ) {
        this.conversationId = conversationId;
        this.title = title;
        this.messages = Collections.unmodifiableList(messages);
        this.contentIntent = contentIntent;
    }

    static ConversationBubbleState create(
            String conversationId,
            CharSequence title,
            PendingIntent contentIntent
    ) {
        return new ConversationBubbleState(
                conversationId,
                clean(title, MAX_TITLE_LENGTH),
                Collections.emptyList(),
                contentIntent
        );
    }

    ConversationBubbleState append(
            CharSequence sender,
            CharSequence text,
            long timestamp,
            PendingIntent latestContentIntent
    ) {
        String cleanSender = clean(sender, MAX_SENDER_LENGTH);
        String cleanText = clean(text, MAX_TEXT_LENGTH);
        List<Message> updated = new ArrayList<>(messages);
        if (!updated.isEmpty()) {
            Message latest = updated.get(updated.size() - 1);
            if (latest.sender.equals(cleanSender)
                    && latest.text.equals(cleanText)
                    && Math.abs(latest.timestamp - timestamp) < DUPLICATE_WINDOW_MS) {
                return new ConversationBubbleState(
                        conversationId,
                        title,
                        updated,
                        latestContentIntent != null ? latestContentIntent : contentIntent
                );
            }
        }
        updated.add(new Message(cleanSender, cleanText, timestamp));
        while (updated.size() > MAX_MESSAGES) updated.remove(0);
        return new ConversationBubbleState(
                conversationId,
                title,
                updated,
                latestContentIntent != null ? latestContentIntent : contentIntent
        );
    }

    ConversationBubbleState withMetadata(CharSequence latestTitle, PendingIntent latestContentIntent) {
        String cleanTitle = clean(latestTitle, MAX_TITLE_LENGTH);
        return new ConversationBubbleState(
                conversationId,
                cleanTitle.isEmpty() ? title : cleanTitle,
                new ArrayList<>(messages),
                latestContentIntent != null ? latestContentIntent : contentIntent
        );
    }

    void writeTo(Intent intent) {
        ArrayList<String> senders = new ArrayList<>(messages.size());
        ArrayList<String> texts = new ArrayList<>(messages.size());
        long[] timestamps = new long[messages.size()];
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            senders.add(message.sender);
            texts.add(message.text);
            timestamps[index] = message.timestamp;
        }
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putStringArrayListExtra(EXTRA_SENDERS, senders);
        intent.putStringArrayListExtra(EXTRA_TEXTS, texts);
        intent.putExtra(EXTRA_TIMESTAMPS, timestamps);
        if (contentIntent != null) intent.putExtra(EXTRA_CONTENT_INTENT, contentIntent);
    }

    static ConversationBubbleState from(Intent intent) {
        if (intent == null) return null;
        String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId == null || conversationId.isEmpty()) return null;
        String title = intent.getStringExtra(EXTRA_TITLE);
        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= 33) {
            contentIntent = intent.getParcelableExtra(EXTRA_CONTENT_INTENT, PendingIntent.class);
        } else {
            //noinspection deprecation
            contentIntent = intent.getParcelableExtra(EXTRA_CONTENT_INTENT);
        }
        ConversationBubbleState state = create(conversationId, title, contentIntent);
        ArrayList<String> senders = intent.getStringArrayListExtra(EXTRA_SENDERS);
        ArrayList<String> texts = intent.getStringArrayListExtra(EXTRA_TEXTS);
        long[] timestamps = intent.getLongArrayExtra(EXTRA_TIMESTAMPS);
        if (senders == null || texts == null || timestamps == null) return state;
        int count = Math.min(Math.min(senders.size(), texts.size()), timestamps.length);
        for (int index = 0; index < count; index++) {
            state = state.append(senders.get(index), texts.get(index), timestamps[index], null);
        }
        return state;
    }

    private static String clean(CharSequence value, int maxLength) {
        String cleaned = value == null ? "" : value.toString().trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    static final class Message {
        final String sender;
        final String text;
        final long timestamp;

        Message(String sender, String text, long timestamp) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
        }
    }
}
