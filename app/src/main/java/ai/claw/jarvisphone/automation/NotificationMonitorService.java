package ai.claw.jarvisphone.automation;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import ai.claw.jarvisphone.core.ActionLogStore;
import ai.claw.jarvisphone.core.AgentContext;
import ai.claw.jarvisphone.core.AgentResponse;
import ai.claw.jarvisphone.core.AutomationPolicy;
import ai.claw.jarvisphone.core.CommandRouter;
import ai.claw.jarvisphone.core.JarvisBrain;
import ai.claw.jarvisphone.core.JarvisPrefs;

import java.lang.ref.WeakReference;

public final class NotificationMonitorService extends NotificationListenerService {
    private static WeakReference<NotificationMonitorService> activeService = new WeakReference<>(null);
    private static ReplyCandidate latestReplyCandidate;

    private JarvisBrain brain;
    private JarvisPrefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = new WeakReference<>(this);
        brain = new JarvisBrain(this);
        prefs = new JarvisPrefs(this);
        ActionLogStore.append(this, "Notification monitor created");
    }

    @Override
    public void onDestroy() {
        activeService.clear();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }

        Notification notification = sbn.getNotification();
        String packageName = sbn.getPackageName();
        String title = charSequence(notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = charSequence(notification.extras.getCharSequence(Notification.EXTRA_TEXT));

        ReplyCandidate candidate = findReplyCandidate(sbn);
        if (candidate != null) {
            latestReplyCandidate = candidate;
        }

        if (AutomationPolicy.looksLikeSpam(title, text)) {
            cancelNotification(sbn.getKey());
            ActionLogStore.append(this, "Cancelled spam-like notification from " + packageName + ": " + title + " " + text);
            return;
        }

        ActionLogStore.append(this, "Notification from " + packageName + ": " + title + " " + text);

        if (!prefs.isAutopilotEnabled() || !prefs.isSafeNotificationRepliesEnabled() || candidate == null) {
            return;
        }

        AgentContext context = new AgentContext(packageName, prefs.getScreenText(), title, text);
        String prompt = "Incoming notification. Decide if a short safe reply is useful. Title: " + title + ". Text: " + text;
        brain.ask("notification", prompt, context, response -> {
            if (!AgentResponse.ACTION_REPLY_NOTIFICATION.equals(response.action)) {
                ActionLogStore.append(this, "Notification suggestion: " + response.reply);
                return;
            }
            String result = CommandRouter.execute(this, response, true);
            ActionLogStore.append(this, result);
        });
    }

    public static boolean replyToLatestNotification(Context context, String message) {
        ReplyCandidate candidate = latestReplyCandidate;
        if (candidate == null || message == null || message.trim().isEmpty()) {
            return false;
        }
        return candidate.reply(context, message.trim());
    }

    private static ReplyCandidate findReplyCandidate(StatusBarNotification sbn) {
        Notification.Action[] actions = sbn.getNotification().actions;
        if (actions == null) {
            return null;
        }
        for (Notification.Action action : actions) {
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs != null && remoteInputs.length > 0) {
                return new ReplyCandidate(sbn.getPackageName(), action, remoteInputs);
            }
        }
        return null;
    }

    private static String charSequence(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static final class ReplyCandidate {
        private final String packageName;
        private final Notification.Action action;
        private final RemoteInput[] remoteInputs;

        ReplyCandidate(String packageName, Notification.Action action, RemoteInput[] remoteInputs) {
            this.packageName = packageName;
            this.action = action;
            this.remoteInputs = remoteInputs;
        }

        boolean reply(Context context, String message) {
            JarvisPrefs prefs = new JarvisPrefs(context);
            if (!AutomationPolicy.canAutopilot(
                    AgentResponse.ACTION_REPLY_NOTIFICATION,
                    message,
                    packageName,
                    prefs.isAdvancedOwnerModeEnabled()
            )) {
                ActionLogStore.append(context, "Blocked notification reply for " + packageName);
                return false;
            }

            Bundle results = new Bundle();
            for (RemoteInput remoteInput : remoteInputs) {
                results.putCharSequence(remoteInput.getResultKey(), message);
            }
            Intent intent = new Intent();
            RemoteInput.addResultsToIntent(remoteInputs, intent, results);
            try {
                action.actionIntent.send(context, 0, intent);
                ActionLogStore.append(context, "Sent notification reply to " + packageName + ": " + message);
                return true;
            } catch (PendingIntent.CanceledException error) {
                ActionLogStore.append(context, "Reply failed: " + error.getMessage());
                return false;
            }
        }
    }
}
