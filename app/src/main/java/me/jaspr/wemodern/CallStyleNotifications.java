package me.jaspr.wemodern;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.graphics.drawable.Icon;

import androidx.annotation.RequiresApi;

final class CallStyleNotifications {
    private static final int WECHAT_GREEN = 0xff33b332;

    private CallStyleNotifications() {
    }

    static Notification buildIncoming(
            Context context,
            CharSequence caller,
            CharSequence text,
            boolean video,
            Icon avatar,
            PendingIntent contentIntent,
            PendingIntent declineIntent,
            PendingIntent answerIntent
    ) {
        Icon callIcon = callIcon(context, video);
        Notification.Builder builder = baseBuilder(
                context,
                NotificationChannels.WECHAT_INCOMING_CALLS,
                caller,
                text,
                callIcon,
                avatar
        )
                .setShowWhen(false)
                .setOngoing(true)
                .setContentIntent(contentIntent);

        if (CallNotificationPresentation.supportsCallStyle()) {
            Person callerPerson = callerPerson(caller, avatar);
            builder.setStyle(Notification.CallStyle.forIncomingCall(
                    callerPerson,
                    declineIntent,
                    answerIntent
            ).setIsVideo(video));
            builder.addPerson(callerPerson);
        } else {
            builder.addAction(new Notification.Action.Builder(
                    callIcon,
                    context.getString(R.string.call_action_decline),
                    declineIntent
            ).build());
            builder.addAction(new Notification.Action.Builder(
                    callIcon,
                    context.getString(R.string.call_action_answer),
                    answerIntent
            ).build());
        }
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;
        return notification;
    }

    static Notification buildOngoing(
            Context context,
            CharSequence caller,
            CharSequence text,
            boolean video,
            Icon avatar,
            long connectedAt,
            PendingIntent contentIntent,
            PendingIntent hangUpIntent
    ) {
        Icon callIcon = callIcon(context, video);
        Notification.Builder builder = baseBuilder(
                context,
                NotificationChannels.WECHAT_ONGOING_CALLS,
                caller,
                text,
                callIcon,
                avatar
        )
                .setWhen(connectedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setOngoing(true)
                .setContentIntent(contentIntent);

        if (CallNotificationPresentation.supportsCallStyle()) {
            Person callerPerson = callerPerson(caller, avatar);
            builder.setStyle(Notification.CallStyle.forOngoingCall(
                    callerPerson,
                    hangUpIntent
            ).setIsVideo(video));
            builder.addPerson(callerPerson);
        } else {
            builder.addAction(new Notification.Action.Builder(
                    callIcon,
                    context.getString(R.string.call_action_hang_up),
                    hangUpIntent
            ).build());
        }
        return CallNotificationPresentation.buildPromoted(builder);
    }

    private static Notification.Builder baseBuilder(
            Context context,
            String channelId,
            CharSequence caller,
            CharSequence text,
            Icon callIcon,
            Icon avatar
    ) {
        Notification.Builder builder = new Notification.Builder(
                context,
                channelId
        )
                .setSmallIcon(callIcon)
                .setContentTitle(caller)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(WECHAT_GREEN);
        if (avatar != null) builder.setLargeIcon(avatar);
        return builder;
    }

    @RequiresApi(31)
    private static Person callerPerson(CharSequence caller, Icon avatar) {
        Person.Builder builder = new Person.Builder()
                .setName(caller)
                .setImportant(true);
        if (avatar != null) builder.setIcon(avatar);
        return builder.build();
    }

    private static Icon callIcon(Context context, boolean video) {
        return Icon.createWithResource(
                context,
                video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24
        );
    }
}
