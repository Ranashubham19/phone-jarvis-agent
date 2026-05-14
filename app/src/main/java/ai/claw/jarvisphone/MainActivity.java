package ai.claw.jarvisphone;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import ai.claw.jarvisphone.automation.JarvisForegroundService;
import ai.claw.jarvisphone.core.AgentContext;
import ai.claw.jarvisphone.core.AgentResponse;
import ai.claw.jarvisphone.core.CommandRouter;
import ai.claw.jarvisphone.core.JarvisBrain;
import ai.claw.jarvisphone.core.JarvisPrefs;

import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 5001;
    private static final int REQUEST_CALL_SCREENING = 5002;

    private final int colorBg = Color.rgb(7, 16, 22);
    private final int colorPanel = Color.rgb(17, 28, 34);
    private final int colorText = Color.rgb(245, 250, 252);
    private final int colorMuted = Color.rgb(168, 183, 191);
    private final int colorAccent = Color.rgb(53, 209, 166);
    private final int colorLine = Color.rgb(34, 51, 59);

    private JarvisPrefs prefs;
    private JarvisBrain brain;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;

    private LinearLayout messages;
    private ScrollView messageScroll;
    private TextView status;
    private EditText input;
    private EditText endpointInput;
    private EditText tokenInput;
    private Switch autopilotSwitch;
    private Switch safeRepliesSwitch;
    private Switch ownerModeSwitch;

    private boolean listening;
    private boolean syncingModes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new JarvisPrefs(this);
        brain = new JarvisBrain(this);

        buildUi();
        setupSpeech();
        startJarvisService();
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(colorBg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(20));
        rootScroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = label("Claw Jarvis", 30, Typeface.BOLD, colorText);
        root.addView(title);

        TextView subtitle = label("Voice chat, multilingual writing, notification replies, screen control, and safe Android autopilot.", 14, Typeface.NORMAL, colorMuted);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        status = label("Say \"hi javris\" while the mic is listening.", 14, Typeface.BOLD, colorAccent);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status);

        LinearLayout switches = row();
        autopilotSwitch = new Switch(this);
        autopilotSwitch.setText("Autopilot");
        autopilotSwitch.setTextColor(colorText);
        autopilotSwitch.setChecked(prefs.isAutopilotEnabled());
        autopilotSwitch.setOnCheckedChangeListener((CompoundButton button, boolean checked) -> {
            if (syncingModes) {
                return;
            }
            prefs.setAutopilotEnabled(checked);
            addAssistant(checked ? "Autopilot is on for safe actions." : "Autopilot is off.");
        });
        switches.addView(autopilotSwitch, weightParams());

        safeRepliesSwitch = new Switch(this);
        safeRepliesSwitch.setText("Safe replies");
        safeRepliesSwitch.setTextColor(colorText);
        safeRepliesSwitch.setChecked(prefs.isSafeNotificationRepliesEnabled());
        safeRepliesSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (syncingModes) {
                return;
            }
            prefs.setSafeNotificationRepliesEnabled(checked);
        });
        switches.addView(safeRepliesSwitch, weightParams());
        root.addView(switches);

        ownerModeSwitch = new Switch(this);
        ownerModeSwitch.setText("Advanced Owner Mode");
        ownerModeSwitch.setTextColor(colorText);
        ownerModeSwitch.setChecked(prefs.isAdvancedOwnerModeEnabled());
        ownerModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (syncingModes) {
                return;
            }
            prefs.setAdvancedOwnerModeEnabled(checked);
            if (checked) {
                prefs.setAutopilotEnabled(true);
                prefs.setSafeNotificationRepliesEnabled(true);
                refreshModeSwitches();
                addAssistant("Advanced Owner Mode is on. I will use every granted Android permission for safe automation.");
            } else {
                prefs.setAutopilotEnabled(false);
                refreshModeSwitches();
                addAssistant("Advanced Owner Mode is off. Light mode is active.");
            }
        });
        root.addView(ownerModeSwitch);

        LinearLayout permissionGrid = new LinearLayout(this);
        permissionGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout permissionRowOne = row();
        permissionRowOne.addView(button("Permissions", v -> requestCorePermissions()), weightParams());
        permissionRowOne.addView(button("Accessibility", v -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)), weightParams());
        LinearLayout permissionRowTwo = row();
        permissionRowTwo.addView(button("Notifications", v -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)), weightParams());
        permissionRowTwo.addView(button("Usage", v -> openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)), weightParams());
        LinearLayout permissionRowThree = row();
        permissionRowThree.addView(button("Battery", v -> openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)), weightParams());
        permissionRowThree.addView(button("Call Shield", v -> requestCallScreeningRole()), weightParams());
        LinearLayout permissionRowFour = row();
        permissionRowFour.addView(button("Start", v -> startJarvisService()), weightParams());
        permissionRowFour.addView(button("Stop", v -> stopJarvisService()), weightParams());
        permissionGrid.addView(permissionRowOne);
        permissionGrid.addView(permissionRowTwo);
        permissionGrid.addView(permissionRowThree);
        permissionGrid.addView(permissionRowFour);
        root.addView(permissionGrid);

        root.addView(sectionTitle("AI endpoint"));
        endpointInput = inputBox("https://your-server.example.com/v1/phone-agent", false);
        endpointInput.setText(prefs.getAiEndpoint());
        root.addView(endpointInput);

        tokenInput = inputBox("Optional bearer token", true);
        tokenInput.setText(prefs.getAiToken());
        root.addView(tokenInput);

        root.addView(button("Save AI settings", v -> {
            prefs.setAiEndpoint(endpointInput.getText().toString());
            prefs.setAiToken(tokenInput.getText().toString());
            addAssistant("AI settings saved. When the endpoint is set, I will use it for advanced chat and multilingual writing.");
        }));

        root.addView(sectionTitle("Chat"));
        messageScroll = new ScrollView(this);
        messageScroll.setBackgroundColor(colorPanel);
        messageScroll.setPadding(dp(8), dp(8), dp(8), dp(8));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messageScroll.addView(messages);
        root.addView(messageScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(330)
        ));

        LinearLayout inputRow = row();
        input = inputBox("Ask Jarvis to write, reply, open, type, tap...", false);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput();
                return true;
            }
            return false;
        });
        inputRow.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(button("Ask", v -> submitInput()));
        inputRow.addView(button("Mic", v -> startListening()));
        root.addView(inputRow);

        setContentView(rootScroll);

        addAssistant("I am ready. Try: \"hi javris write a professional reply in Hindi\" or \"open WhatsApp\".");
    }

    private void setupSpeech() {
        textToSpeech = new TextToSpeech(this, statusCode -> {
            if (statusCode == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new JarvisRecognitionListener());
        } else {
            status.setText("Speech recognition is not available on this phone.");
        }
    }

    private void submitInput() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        handleUserText(text);
    }

    private void handleUserText(String rawText) {
        String text = JarvisBrain.removeWakePhrase(rawText);
        if (text.isEmpty() && JarvisBrain.isWakePhrase(rawText)) {
            text = "hi javris";
        }

        addUser(rawText);
        status.setText("Thinking...");

        AgentContext context = new AgentContext(
                prefs.getCurrentPackage(),
                prefs.getScreenText(),
                "",
                ""
        );

        String mode = detectMode(text);
        brain.ask(mode, text, context, response -> {
            refreshModeSwitches();
            addAssistant(response.reply);
            speak(response.reply);
            status.setText("Ready.");

            if (response.hasAction()) {
                String result = CommandRouter.execute(this, response, true);
                if (!result.isEmpty()) {
                    addAssistant(result);
                }
            }
        });
    }

    private void refreshModeSwitches() {
        syncingModes = true;
        try {
            if (autopilotSwitch != null && autopilotSwitch.isChecked() != prefs.isAutopilotEnabled()) {
                autopilotSwitch.setChecked(prefs.isAutopilotEnabled());
            }
            if (safeRepliesSwitch != null && safeRepliesSwitch.isChecked() != prefs.isSafeNotificationRepliesEnabled()) {
                safeRepliesSwitch.setChecked(prefs.isSafeNotificationRepliesEnabled());
            }
            if (ownerModeSwitch != null && ownerModeSwitch.isChecked() != prefs.isAdvancedOwnerModeEnabled()) {
                ownerModeSwitch.setChecked(prefs.isAdvancedOwnerModeEnabled());
            }
        } finally {
            syncingModes = false;
        }
    }

    private String detectMode(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        if (lower.contains("write") || lower.contains("draft") || lower.contains("translate")) {
            return "write";
        }
        if (lower.startsWith("open ") || lower.startsWith("type ") || lower.startsWith("tap ") || lower.contains("reply to last")) {
            return "command";
        }
        return "chat";
    }

    private void startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestCorePermissions();
            return;
        }
        if (listening) {
            speechRecognizer.stopListening();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say hi javris...");
        speechRecognizer.startListening(intent);
        listening = true;
        status.setText("Listening...");
    }

    private void requestCorePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        ArrayList<String> permissions = new ArrayList<>();
        addPermissionIfNeeded(permissions, Manifest.permission.RECORD_AUDIO);
        addPermissionIfNeeded(permissions, Manifest.permission.READ_CONTACTS);
        addPermissionIfNeeded(permissions, Manifest.permission.READ_PHONE_STATE);
        addPermissionIfNeeded(permissions, Manifest.permission.READ_SMS);
        addPermissionIfNeeded(permissions, Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermissionIfNeeded(permissions, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            Toast.makeText(this, "Core permissions already granted.", Toast.LENGTH_SHORT).show();
        }
    }

    private void addPermissionIfNeeded(ArrayList<String> permissions, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    Toast.makeText(this, "Call shield is already active.", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_CALL_SCREENING);
                return;
            }
        }
        openSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
    }

    private void openSettings(String action) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }

    private void startJarvisService() {
        Intent intent = new Intent(this, JarvisForegroundService.class);
        intent.setAction(JarvisForegroundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        status.setText("Foreground service active.");
    }

    private void stopJarvisService() {
        Intent intent = new Intent(this, JarvisForegroundService.class);
        intent.setAction(JarvisForegroundService.ACTION_STOP);
        startService(intent);
        status.setText("Foreground service stopped.");
    }

    private void addUser(String text) {
        addMessage("You", text, colorAccent);
    }

    private void addAssistant(String text) {
        addMessage("Jarvis", text, colorText);
    }

    private void addMessage(String sender, String text, int senderColor) {
        TextView view = label(sender + ": " + text, 15, Typeface.NORMAL, colorText);
        view.setTextColor(colorText);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setBackgroundColor(colorPanel);
        messages.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void speak(String text) {
        if (textToSpeech != null && text != null && !text.trim().isEmpty()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response");
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView label(String text, int sp, int style, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(sp);
        label.setTypeface(Typeface.DEFAULT, style);
        label.setTextColor(color);
        label.setLineSpacing(0, 1.08f);
        return label;
    }

    private TextView sectionTitle(String text) {
        TextView title = label(text, 16, Typeface.BOLD, colorText);
        title.setPadding(0, dp(14), 0, dp(6));
        return title;
    }

    private EditText inputBox(String hint, boolean password) {
        EditText box = new EditText(this);
        box.setHint(hint);
        box.setHintTextColor(colorMuted);
        box.setTextColor(colorText);
        box.setSingleLine(true);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundColor(colorPanel);
        box.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return box;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(5, 12, 15));
        button.setBackgroundColor(colorAccent);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), dp(6), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class JarvisRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            status.setText("Listening...");
        }

        @Override
        public void onBeginningOfSpeech() {
            status.setText("I hear you.");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            listening = false;
            status.setText("Processing voice...");
        }

        @Override
        public void onError(int error) {
            listening = false;
            status.setText("Ready.");
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                status.setText("Ready.");
                return;
            }
            handleUserText(matches.get(0));
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String partial = matches.get(0);
                if (JarvisBrain.isWakePhrase(partial)) {
                    status.setText("Wake phrase heard.");
                }
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
