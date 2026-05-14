package ai.claw.jarvisphone.automation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import ai.claw.jarvisphone.MainActivity;
import ai.claw.jarvisphone.R;
import ai.claw.jarvisphone.core.ActionLogStore;
import ai.claw.jarvisphone.core.AgentContext;
import ai.claw.jarvisphone.core.AgentResponse;
import ai.claw.jarvisphone.core.CommandRouter;
import ai.claw.jarvisphone.core.JarvisBrain;
import ai.claw.jarvisphone.core.JarvisPrefs;

import java.util.ArrayList;
import java.util.Locale;

public final class JarvisForegroundService extends Service {
    public static final String ACTION_START = "ai.claw.jarvisphone.START";
    public static final String ACTION_STOP = "ai.claw.jarvisphone.STOP";
    public static final String ACTION_ENABLE_LISTENING = "ai.claw.jarvisphone.ENABLE_LISTENING";
    public static final String ACTION_DISABLE_LISTENING = "ai.claw.jarvisphone.DISABLE_LISTENING";

    private static final String CHANNEL_ID = "jarvis_active";
    private static final int NOTIFICATION_ID = 7001;
    private static final long AWAKE_WINDOW_MS = 18000L;
    private static final String SERVICE_UTTERANCE_ID = "jarvis-service-response";

    private Handler mainHandler;
    private JarvisPrefs prefs;
    private JarvisBrain brain;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private boolean listening;
    private boolean destroyed;
    private boolean resumeListeningAfterSpeech;
    private long awakeUntilMs;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = new JarvisPrefs(this);
        brain = new JarvisBrain(this);
        createChannel();
        setupVoice();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.setAlwaysListeningEnabled(false);
            stopAlwaysListening();
            ActionLogStore.append(this, "Foreground service stopped");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_ENABLE_LISTENING.equals(action)) {
            prefs.setAlwaysListeningEnabled(true);
        } else if (ACTION_DISABLE_LISTENING.equals(action)) {
            prefs.setAlwaysListeningEnabled(false);
        }

        startVisibleForeground();
        if (prefs.isAlwaysListeningEnabled()) {
            startAlwaysListening();
        } else {
            stopAlwaysListening();
        }

        ActionLogStore.append(this, "Foreground service active, alwaysListening=" + prefs.isAlwaysListeningEnabled());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        stopAlwaysListening();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setupVoice() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
                textToSpeech.setSpeechRate(0.92f);
                textToSpeech.setPitch(0.98f);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (SERVICE_UTTERANCE_ID.equals(utteranceId) && resumeListeningAfterSpeech) {
                            resumeListeningAfterSpeech = false;
                            restartListening(650);
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        if (SERVICE_UTTERANCE_ID.equals(utteranceId) && resumeListeningAfterSpeech) {
                            resumeListeningAfterSpeech = false;
                            restartListening(900);
                        }
                    }
                });
            }
        });
    }

    private void startAlwaysListening() {
        if (!hasAudioPermission()) {
            prefs.setAlwaysListeningEnabled(false);
            ActionLogStore.append(this, "Always Listening blocked: RECORD_AUDIO missing");
            speak("I need microphone permission before Always Listening can work.");
            startVisibleForeground();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            prefs.setAlwaysListeningEnabled(false);
            ActionLogStore.append(this, "Always Listening blocked: recognizer unavailable");
            speak("Speech recognition is not available on this phone.");
            startVisibleForeground();
            return;
        }
        ensureRecognizer();
        restartListening(300);
        startVisibleForeground();
    }

    private void stopAlwaysListening() {
        listening = false;
        awakeUntilMs = 0L;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {
            }
            speechRecognizer = null;
        }
        if (!destroyed) {
            startVisibleForeground();
        }
    }

    private void ensureRecognizer() {
        if (speechRecognizer != null) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new WakeRecognitionListener());
    }

    private void restartListening(long delayMs) {
        if (destroyed || !prefs.isAlwaysListeningEnabled()) {
            return;
        }
        mainHandler.postDelayed(() -> {
            if (destroyed || !prefs.isAlwaysListeningEnabled() || listening) {
                return;
            }
            ensureRecognizer();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
            try {
                speechRecognizer.startListening(intent);
                listening = true;
                startVisibleForeground();
            } catch (Exception error) {
                listening = false;
                ActionLogStore.append(this, "Listening restart failed: " + error.getMessage());
                restartListening(1800);
            }
        }, delayMs);
    }

    private void handleSpokenText(String rawText) {
        String spoken = rawText == null ? "" : rawText.trim();
        if (spoken.isEmpty()) {
            restartListening(500);
            return;
        }

        boolean hasWakePhrase = JarvisBrain.isWakePhrase(spoken);
        boolean stillAwake = System.currentTimeMillis() < awakeUntilMs;
        if (!hasWakePhrase && !stillAwake) {
            ActionLogStore.append(this, "Ignored background speech without wake phrase");
            restartListening(500);
            return;
        }

        String command = hasWakePhrase ? JarvisBrain.removeWakePhrase(spoken) : spoken;
        awakeUntilMs = System.currentTimeMillis() + AWAKE_WINDOW_MS;
        if (command.isEmpty()) {
            speakAndResume("I am here. What should I do?");
            return;
        }

        ActionLogStore.append(this, "Wake command: " + command);
        AgentContext context = new AgentContext(
                prefs.getCurrentPackage(),
                prefs.getScreenText(),
                "",
                ""
        );
        brain.ask(detectMode(command), command, context, response -> {
            String result = "";
            if (response.hasAction()) {
                result = CommandRouter.execute(this, response, true);
            }

            String spokenReply = response.reply;
            if (result != null && !result.trim().isEmpty()) {
                spokenReply = spokenReply + " " + result;
            }
            speakAndResume(spokenReply);
            startVisibleForeground();
        });
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

    private void speak(String text) {
        if (textToSpeech != null && text != null && !text.trim().isEmpty()) {
            resumeListeningAfterSpeech = false;
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, SERVICE_UTTERANCE_ID);
        }
    }

    private void speakAndResume(String text) {
        if (textToSpeech != null && text != null && !text.trim().isEmpty()) {
            resumeListeningAfterSpeech = true;
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, SERVICE_UTTERANCE_ID);
            if (result == TextToSpeech.ERROR) {
                resumeListeningAfterSpeech = false;
                restartListening(1200);
            }
        } else {
            restartListening(700);
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startVisibleForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                if (prefs != null && prefs.isAlwaysListeningEnabled()) {
                    types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
                startForeground(NOTIFICATION_ID, notification, types);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException error) {
            if (prefs != null) {
                prefs.setAlwaysListeningEnabled(false);
            }
            ActionLogStore.append(this, "Foreground microphone start failed: " + error.getMessage());
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        boolean wakeReady = prefs != null && prefs.isAlwaysListeningEnabled();

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent listenIntent = new Intent(this, JarvisForegroundService.class);
        listenIntent.setAction(wakeReady ? ACTION_DISABLE_LISTENING : ACTION_ENABLE_LISTENING);
        PendingIntent listen = PendingIntent.getService(
                this,
                2,
                listenIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, JarvisForegroundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this,
                3,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentTitle(wakeReady ? "Claw Jarvis is listening" : getString(R.string.foreground_notification_title))
                .setContentText(wakeReady ? "Say \"hi javris\" to wake me." : getString(R.string.foreground_notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(R.drawable.ic_stat_jarvis, "Open", open)
                .addAction(R.drawable.ic_stat_jarvis, wakeReady ? "Stop Wake" : "Wake On", listen)
                .addAction(R.drawable.ic_stat_jarvis, "Stop", stop)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.foreground_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private final class WakeRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
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
        }

        @Override
        public void onError(int error) {
            listening = false;
            if (prefs != null && prefs.isAlwaysListeningEnabled()) {
                restartListening(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 2200 : 900);
            }
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            handleSpokenText(matches == null || matches.isEmpty() ? "" : matches.get(0));
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
