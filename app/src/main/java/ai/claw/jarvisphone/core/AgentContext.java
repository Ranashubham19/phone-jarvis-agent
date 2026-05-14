package ai.claw.jarvisphone.core;

import org.json.JSONException;
import org.json.JSONObject;

public final class AgentContext {
    public final String appPackage;
    public final String screenText;
    public final String notificationTitle;
    public final String notificationText;

    public AgentContext(String appPackage, String screenText, String notificationTitle, String notificationText) {
        this.appPackage = clean(appPackage);
        this.screenText = clean(screenText);
        this.notificationTitle = clean(notificationTitle);
        this.notificationText = clean(notificationText);
    }

    public static AgentContext empty() {
        return new AgentContext("", "", "", "");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("app", appPackage);
        json.put("screenText", screenText);
        json.put("notificationTitle", notificationTitle);
        json.put("notificationText", notificationText);
        return json;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
