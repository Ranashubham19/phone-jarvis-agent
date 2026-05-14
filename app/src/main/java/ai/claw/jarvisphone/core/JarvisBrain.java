package ai.claw.jarvisphone.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JarvisBrain {
    public interface Callback {
        void onResponse(AgentResponse response);
    }

    private final Context appContext;
    private final AiClient aiClient;
    private final JarvisPrefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public JarvisBrain(Context context) {
        this.appContext = context.getApplicationContext();
        this.aiClient = new AiClient(context);
        this.prefs = new JarvisPrefs(context);
    }

    public void ask(String mode, String userText, AgentContext context, Callback callback) {
        executor.execute(() -> {
            AgentResponse response;
            try {
                AgentResponse localControl = localControl(userText);
                if (localControl != null) {
                    response = localControl;
                } else if (aiClient.isConfigured()) {
                    response = aiClient.request(mode, userText, context);
                } else {
                    response = fallback(mode, userText, context);
                }
            } catch (Exception error) {
                ActionLogStore.append(appContext, "AI endpoint error: " + error.getMessage());
                response = fallback(mode, userText, context);
            }

            AgentResponse finalResponse = response;
            main.post(() -> callback.onResponse(finalResponse));
        });
    }

    private AgentResponse localControl(String userText) {
        String lower = safe(userText).toLowerCase(Locale.US);
        if (lower.contains("turn on your dark mode")
                || lower.contains("turn on dark mode")
                || lower.contains("enable dark mode")
                || lower.contains("turn on advanced mode")
                || lower.contains("enable advanced mode")
                || lower.contains("turn on owner mode")
                || lower.contains("enable owner mode")) {
            prefs.setAdvancedOwnerModeEnabled(true);
            prefs.setAutopilotEnabled(true);
            prefs.setSafeNotificationRepliesEnabled(true);
            return AgentResponse.say("Done. Advanced Owner Mode is on. I will use the permissions you granted to handle safe phone tasks more confidently, like opening apps, typing, tapping visible controls, and managing notification replies. I will still keep sensitive things with you, including OTPs, passwords, banking, payments, account deletion, lock screen access, and security settings.");
        }
        if (lower.contains("turn off your dark mode")
                || lower.contains("turn off dark mode")
                || lower.contains("disable dark mode")
                || lower.contains("turn off advanced mode")
                || lower.contains("disable advanced mode")
                || lower.contains("turn off owner mode")
                || lower.contains("disable owner mode")) {
            prefs.setAdvancedOwnerModeEnabled(false);
            prefs.setAutopilotEnabled(false);
            return AgentResponse.say("Done. Advanced Owner Mode is off. I am back in light mode, so I will wait for clear commands before taking action.");
        }
        return null;
    }

    private AgentResponse fallback(String mode, String userText, AgentContext context) {
        String text = safe(userText);
        String lower = text.toLowerCase(Locale.US);

        if (lower.contains("turn on autopilot") || lower.contains("enable autopilot")) {
            prefs.setAutopilotEnabled(true);
            return AgentResponse.say("Autopilot is on. I will take care of safe actions when I am confident, and I will pause for anything sensitive.");
        }
        if (lower.contains("turn off autopilot") || lower.contains("disable autopilot")) {
            prefs.setAutopilotEnabled(false);
            return AgentResponse.say("Autopilot is off. I will stay hands-off and wait for your direct instruction.");
        }
        if (lower.startsWith("open ")) {
            String target = text.substring(5).trim();
            return AgentResponse.action("Sure, opening " + target + " now.", AgentResponse.ACTION_OPEN_APP, "", target, 0.8);
        }
        if (lower.startsWith("type ")) {
            String message = text.substring(5).trim();
            return AgentResponse.action("Got it. I will type that for you.", AgentResponse.ACTION_TYPE_TEXT, message, "", 0.8);
        }
        if (lower.startsWith("tap ")) {
            String target = text.substring(4).trim();
            return AgentResponse.action("Okay, I will tap " + target + ".", AgentResponse.ACTION_TAP_TEXT, "", target, 0.7);
        }
        if (lower.contains("reply to last") || lower.startsWith("reply ")) {
            String reply = text.replaceFirst("(?i)reply to last", "").replaceFirst("(?i)^reply", "").trim();
            if (reply.isEmpty()) {
                reply = "I will get back to you soon.";
            }
            return AgentResponse.action("I have the reply ready. Sending it now if Android exposes the reply button.", AgentResponse.ACTION_REPLY_NOTIFICATION, reply, "", 0.7);
        }
        if ("notification".equals(mode)) {
            if (AutomationPolicy.looksLikeSpam(context.notificationTitle, context.notificationText)) {
                return AgentResponse.say("That looks like spam, so I am keeping it quiet and logging it.");
            }
            return AgentResponse.say("You have a new notification from " + firstNonEmpty(context.notificationTitle, context.appPackage) + ". " + context.notificationText);
        }
        if ("write".equals(mode) || lower.contains("write") || lower.contains("draft") || lower.contains("translate")) {
            return AgentResponse.say(localWritingDraft(text));
        }
        if (isWakePhrase(lower)) {
            return AgentResponse.say("I am here. Tell me what you need, and I will handle what I can.");
        }

        return AgentResponse.say("I am with you. Ask naturally, like \"write a polite reply\", \"open WhatsApp\", or \"type this message\". For deeper writing and smarter conversation, connect the AI endpoint.");
    }

    private static String localWritingDraft(String prompt) {
        return "I can help with that.\n\n" +
                "Draft direction: keep it clear, natural, and respectful. Your request was: " + prompt + "\n\n" +
                "For polished writing in any language, connect the AI endpoint. Then I can write the full message in the exact tone you want: casual, professional, emotional, short, formal, or friendly.";
    }

    public static boolean isWakePhrase(String text) {
        String lower = safe(text).toLowerCase(Locale.US);
        return lower.startsWith("hi javris")
                || lower.startsWith("hii javris")
                || lower.startsWith("hi jarvis")
                || lower.startsWith("hey jarvis")
                || lower.startsWith("hello jarvis");
    }

    public static String removeWakePhrase(String text) {
        String cleaned = safe(text);
        return cleaned
                .replaceFirst("(?i)^hii? javris[, ]*", "")
                .replaceFirst("(?i)^hi jarvis[, ]*", "")
                .replaceFirst("(?i)^hey jarvis[, ]*", "")
                .replaceFirst("(?i)^hello jarvis[, ]*", "")
                .trim();
    }

    private static String firstNonEmpty(String one, String two) {
        return safe(one).isEmpty() ? safe(two) : safe(one);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
