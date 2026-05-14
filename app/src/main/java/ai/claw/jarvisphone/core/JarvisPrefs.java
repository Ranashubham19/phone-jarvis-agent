package ai.claw.jarvisphone.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class JarvisPrefs {
    private static final String PREFS = "claw_jarvis_prefs";
    private static final String KEY_AI_ENDPOINT = "ai_endpoint";
    private static final String KEY_AI_TOKEN = "ai_token";
    private static final String KEY_AUTOPILOT = "autopilot";
    private static final String KEY_ADVANCED_OWNER_MODE = "advanced_owner_mode";
    private static final String KEY_ALWAYS_LISTENING = "always_listening";
    private static final String KEY_SAFE_NOTIFICATION_REPLIES = "safe_notification_replies";
    private static final String KEY_CURRENT_PACKAGE = "current_package";
    private static final String KEY_SCREEN_TEXT = "screen_text";

    private final SharedPreferences prefs;

    public JarvisPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getAiEndpoint() {
        return prefs.getString(KEY_AI_ENDPOINT, "");
    }

    public void setAiEndpoint(String value) {
        prefs.edit().putString(KEY_AI_ENDPOINT, safe(value)).apply();
    }

    public String getAiToken() {
        return prefs.getString(KEY_AI_TOKEN, "");
    }

    public void setAiToken(String value) {
        prefs.edit().putString(KEY_AI_TOKEN, safe(value)).apply();
    }

    public boolean isAutopilotEnabled() {
        return prefs.getBoolean(KEY_AUTOPILOT, false);
    }

    public void setAutopilotEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOPILOT, enabled).apply();
    }

    public boolean isAdvancedOwnerModeEnabled() {
        return prefs.getBoolean(KEY_ADVANCED_OWNER_MODE, false);
    }

    public void setAdvancedOwnerModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ADVANCED_OWNER_MODE, enabled).apply();
    }

    public boolean isAlwaysListeningEnabled() {
        return prefs.getBoolean(KEY_ALWAYS_LISTENING, false);
    }

    public void setAlwaysListeningEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, enabled).apply();
    }

    public boolean isSafeNotificationRepliesEnabled() {
        return prefs.getBoolean(KEY_SAFE_NOTIFICATION_REPLIES, true);
    }

    public void setSafeNotificationRepliesEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAFE_NOTIFICATION_REPLIES, enabled).apply();
    }

    public String getCurrentPackage() {
        return prefs.getString(KEY_CURRENT_PACKAGE, "");
    }

    public void setCurrentPackage(String packageName) {
        prefs.edit().putString(KEY_CURRENT_PACKAGE, safe(packageName)).apply();
    }

    public String getScreenText() {
        return prefs.getString(KEY_SCREEN_TEXT, "");
    }

    public void setScreenText(String screenText) {
        prefs.edit().putString(KEY_SCREEN_TEXT, safe(screenText)).apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
