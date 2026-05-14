package ai.claw.jarvisphone.core;

import org.json.JSONObject;

public final class AgentResponse {
    public static final String ACTION_NONE = "none";
    public static final String ACTION_REPLY_NOTIFICATION = "reply_notification";
    public static final String ACTION_TYPE_TEXT = "type_text";
    public static final String ACTION_OPEN_APP = "open_app";
    public static final String ACTION_TAP_TEXT = "tap_text";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_HOME = "home";

    public final String reply;
    public final String action;
    public final String text;
    public final String target;
    public final double confidence;

    public AgentResponse(String reply, String action, String text, String target, double confidence) {
        this.reply = safe(reply);
        this.action = safe(action).isEmpty() ? ACTION_NONE : safe(action);
        this.text = safe(text);
        this.target = safe(target);
        this.confidence = confidence;
    }

    public static AgentResponse say(String reply) {
        return new AgentResponse(reply, ACTION_NONE, "", "", 1.0);
    }

    public static AgentResponse action(String reply, String action, String text, String target, double confidence) {
        return new AgentResponse(reply, action, text, target, confidence);
    }

    public static AgentResponse fromJson(String raw) {
        try {
            JSONObject json = new JSONObject(raw);
            return new AgentResponse(
                    json.optString("reply", ""),
                    json.optString("action", ACTION_NONE),
                    json.optString("text", ""),
                    json.optString("target", ""),
                    json.optDouble("confidence", 0.0)
            );
        } catch (Exception ignored) {
            return AgentResponse.say(raw == null ? "" : raw);
        }
    }

    public boolean hasAction() {
        return !ACTION_NONE.equals(action);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
