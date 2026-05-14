package ai.claw.jarvisphone.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AutomationPolicy {
    private static final Set<String> MESSAGE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.facebook.orca",
            "com.signal",
            "com.instagram.android"
    ));

    private static final String[] BLOCKED_TOPICS = {
            "otp", "one time password", "2fa", "verification code", "password", "passcode",
            "bank", "upi", "payment", "pay ", "transfer", "crypto", "wallet", "card",
            "delete", "account deletion", "factory reset", "security settings", "biometric", "login",
            "lock screen", "unlock", "bypass", "root access", "spy", "hidden mode"
    };

    private static final String[] SPAM_MARKERS = {
            "lottery", "claim now", "free money", "loan approved", "urgent kyc",
            "click this link", "investment double", "crypto profit", "winner"
    };

    private AutomationPolicy() {
    }

    public static boolean canAutopilot(String action, String text, String packageName) {
        return canAutopilot(action, text, packageName, false);
    }

    public static boolean canAutopilot(String action, String text, String packageName, boolean advancedOwnerMode) {
        String safeAction = normalize(action);
        if (AgentResponse.ACTION_NONE.equals(safeAction)) {
            return true;
        }
        if (isSensitive(text) || isSensitive(packageName)) {
            return false;
        }
        if (AgentResponse.ACTION_REPLY_NOTIFICATION.equals(safeAction)) {
            return advancedOwnerMode || isKnownMessagingPackage(packageName);
        }
        return AgentResponse.ACTION_TYPE_TEXT.equals(safeAction)
                || AgentResponse.ACTION_TAP_TEXT.equals(safeAction)
                || AgentResponse.ACTION_OPEN_APP.equals(safeAction)
                || AgentResponse.ACTION_BACK.equals(safeAction)
                || AgentResponse.ACTION_HOME.equals(safeAction);
    }

    public static boolean isKnownMessagingPackage(String packageName) {
        String normalized = normalize(packageName);
        for (String allowed : MESSAGE_PACKAGES) {
            if (normalized.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSensitive(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String blocked : BLOCKED_TOPICS) {
            if (normalized.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    public static boolean looksLikeSpam(String title, String text) {
        String normalized = normalize(title + " " + text);
        for (String marker : SPAM_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).trim();
    }
}
