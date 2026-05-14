package ai.claw.jarvisphone.core;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AiClient {
    private final JarvisPrefs prefs;

    public AiClient(Context context) {
        this.prefs = new JarvisPrefs(context);
    }

    public boolean isConfigured() {
        return !prefs.getAiEndpoint().isEmpty();
    }

    public AgentResponse request(String mode, String userText, AgentContext context) throws Exception {
        String endpoint = prefs.getAiEndpoint();
        if (endpoint.isEmpty()) {
            throw new IllegalStateException("AI endpoint is not configured");
        }

        JSONObject body = new JSONObject();
        body.put("mode", mode);
        body.put("userText", userText);
        body.put("context", context.toJson());

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        String token = prefs.getAiToken();
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body.toString());
        }

        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readFully(input);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("AI endpoint returned " + status + ": " + response);
        }

        return AgentResponse.fromJson(response);
    }

    private static String readFully(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
