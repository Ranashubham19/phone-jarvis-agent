package ai.claw.jarvisphone.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import ai.claw.jarvisphone.automation.AutomationCommandReceiver;
import ai.claw.jarvisphone.automation.NotificationMonitorService;

import java.util.List;
import java.util.Locale;

public final class CommandRouter {
    private CommandRouter() {
    }

    public static String execute(Context context, AgentResponse response, boolean requireAutopilotPolicy) {
        if (response == null || !response.hasAction()) {
            return "";
        }

        JarvisPrefs prefs = new JarvisPrefs(context);
        String packageName = prefs.getCurrentPackage();
        if (requireAutopilotPolicy && !AutomationPolicy.canAutopilot(
                response.action,
                response.text + " " + response.target,
                packageName,
                prefs.isAdvancedOwnerModeEnabled()
        )) {
            ActionLogStore.append(context, "Blocked risky action: " + response.action + " " + response.target);
            return "I blocked that action because it looks sensitive. Please do it yourself or give a more specific safe command.";
        }

        switch (response.action) {
            case AgentResponse.ACTION_REPLY_NOTIFICATION:
                boolean sent = NotificationMonitorService.replyToLatestNotification(context, response.text);
                return sent ? "Reply sent." : "I could not find a notification reply button to use.";
            case AgentResponse.ACTION_TYPE_TEXT:
            case AgentResponse.ACTION_TAP_TEXT:
            case AgentResponse.ACTION_BACK:
            case AgentResponse.ACTION_HOME:
                sendAccessibilityCommand(context, response.action, response.text, response.target);
                return "Command sent to screen control.";
            case AgentResponse.ACTION_OPEN_APP:
                return openApp(context, response.target);
            default:
                return "";
        }
    }

    private static void sendAccessibilityCommand(Context context, String action, String text, String target) {
        Intent intent = new Intent(context, AutomationCommandReceiver.class);
        intent.setAction(AutomationCommandReceiver.ACTION_EXECUTE);
        intent.putExtra(AutomationCommandReceiver.EXTRA_ACTION, action);
        intent.putExtra(AutomationCommandReceiver.EXTRA_TEXT, text);
        intent.putExtra(AutomationCommandReceiver.EXTRA_TARGET, target);
        context.sendBroadcast(intent);
    }

    private static String openApp(Context context, String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isEmpty()) {
            return "Tell me which app to open.";
        }

        PackageManager packageManager = context.getPackageManager();
        Intent launch = packageManager.getLaunchIntentForPackage(normalizedTarget);
        if (launch == null) {
            launch = findLaunchIntentByLabel(context, normalizedTarget);
        }
        if (launch == null) {
            Intent settings = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settings.setData(Uri.parse("package:" + normalizedTarget));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(settings);
                return "I opened app details because I could not launch it directly.";
            } catch (Exception ignored) {
                return "I could not find that app.";
            }
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        ActionLogStore.append(context, "Opened app: " + normalizedTarget);
        return "Opened " + normalizedTarget + ".";
    }

    private static Intent findLaunchIntentByLabel(Context context, String label) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(launcher, 0);
        String wanted = label.toLowerCase(Locale.US);
        for (ResolveInfo info : apps) {
            CharSequence appLabel = info.loadLabel(packageManager);
            if (appLabel != null && appLabel.toString().toLowerCase(Locale.US).contains(wanted)) {
                return packageManager.getLaunchIntentForPackage(info.activityInfo.packageName);
            }
        }
        return null;
    }
}
