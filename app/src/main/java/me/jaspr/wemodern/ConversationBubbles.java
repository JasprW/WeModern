package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

final class ConversationBubbles {
    private static final String TAG = "WeModern";
    private static final int MIN_BUBBLE_SDK = 29;
    private static final int REQUEST_CODE_NAMESPACE = 0x42000000;

    private ConversationBubbles() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= MIN_BUBBLE_SDK;
    }

    static int requestCodeFor(String conversationId) {
        int hash = conversationId == null ? 0 : conversationId.hashCode();
        return REQUEST_CODE_NAMESPACE ^ hash;
    }

    static int pendingIntentFlags() {
        // Android 12+ requires the Activity PendingIntent attached to BubbleMetadata to be
        // mutable so SystemUI can supply its embedded-task launch options.
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    }

    static void applyTo(
            Context context,
            Notification.Builder builder,
            ConversationBubbleState state,
            Icon icon
    ) {
        if (!isSupported(Build.VERSION.SDK_INT) || state == null || icon == null) return;
        Api29Impl.applyTo(context, builder, state, icon);
    }

    @TargetApi(29)
    private static final class Api29Impl {
        private Api29Impl() {
        }

        static void applyTo(
                Context context,
                Notification.Builder builder,
                ConversationBubbleState state,
                Icon icon
        ) {
            Intent target = new Intent(context, BubbleConversationActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(new Uri.Builder()
                            .scheme("wemodern")
                            .authority("bubble")
                            .appendPath(state.conversationId)
                            .build());
            state.writeTo(target);
            PendingIntent bubbleIntent = createBubbleIntent(context, state, target);
            Notification.BubbleMetadata.Builder metadataBuilder;
            if (Build.VERSION.SDK_INT >= 30) {
                metadataBuilder = Api30Impl.newBuilder(bubbleIntent, icon);
            } else {
                // The no-argument builder is the Android 10 API surface.
                metadataBuilder = new Notification.BubbleMetadata.Builder()
                        .setIntent(bubbleIntent)
                        .setIcon(icon);
            }
            Notification.BubbleMetadata metadata = metadataBuilder
                    .setDesiredHeightResId(R.dimen.conversation_bubble_desired_height)
                    .setAutoExpandBubble(false)
                    .setSuppressNotification(false)
                    .build();
            builder.setBubbleMetadata(metadata);
        }

        private static PendingIntent createBubbleIntent(
                Context context,
                ConversationBubbleState state,
                Intent fallbackTarget
        ) {
            boolean trampolineEnabled = BubbleTrampolineBehavior.isEnabled(context);
            if (trampolineEnabled
                    && MessageTestNotifications.isTestShortcutId(state.conversationId)) {
                Intent weChatTarget = WeChatLauncher.createBubbleRootIntent(context);
                if (weChatTarget != null) {
                    Log.i(TAG, "using mutable WeChat bubble root"
                            + ", conversation=" + state.conversationId
                            + ", component=" + weChatTarget.getComponent());
                    return PendingIntent.getActivity(
                            context,
                            requestCodeFor(state.conversationId),
                            weChatTarget,
                            pendingIntentFlags()
                    );
                }
            }
            if (trampolineEnabled) {
                // WeChat's notification PendingIntent is immutable, but BubbleMetadata requires
                // a mutable Activity PendingIntent. The WeModern activity therefore remains the
                // standards-compliant bridge and sends the untouched WeChat intent on expansion.
                Log.i(TAG, "using Activity trampoline"
                        + ", conversation=" + state.conversationId);
            }
            return PendingIntent.getActivity(
                    context,
                    requestCodeFor(state.conversationId),
                    fallbackTarget,
                    pendingIntentFlags()
            );
        }
    }

    @TargetApi(30)
    private static final class Api30Impl {
        private Api30Impl() {
        }

        static Notification.BubbleMetadata.Builder newBuilder(
                PendingIntent bubbleIntent,
                Icon icon
        ) {
            return new Notification.BubbleMetadata.Builder(bubbleIntent, icon);
        }
    }
}
