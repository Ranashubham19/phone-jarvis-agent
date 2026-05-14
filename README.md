# Claw Jarvis Phone Agent

Android-first personal AI assistant source project.

This is built for your own phone with explicit permission screens. It is not a hidden controller and it does not bypass Android security. The assistant can listen while visible, run a foreground background service, read notifications after you grant notification access, reply through supported notification reply actions, watch screen activity through Accessibility after you enable it, and route commands to an AI backend.

## What it can do

- Voice chat from the app screen.
- Wake phrase while listening: `hi javris`, `hi jarvis`, or `hey jarvis`.
- Two control levels:
  - Light mode: chat, writing, direct commands, and safe autopilot.
  - Advanced Owner Mode: one-command maximum permission-based automation.
- Write messages, captions, emails, notes, and translations in any language through the AI endpoint.
- Watch notifications and suggest or send safe replies when autopilot is enabled.
- Use Android Accessibility to inspect active screen text and perform taps/typing when you explicitly grant access.
- Keep running through a visible foreground notification.
- Restart its visible service after phone reboot when Android allows it.
- Keep a local action log so you can see what it did.
- Screen calls through Android's call screening role when the user grants that role.

## Modes

Light mode is the default. It follows direct commands and keeps automation conservative.

Advanced Owner Mode turns on every Android-approved automation path the app has after you grant permissions: foreground service, broader notification replies, screen typing, screen taps, app opening, usage context, and call screening hooks.

Voice/text commands:

```text
hi javris turn on your dark mode
hi javris turn off your dark mode
turn on advanced mode
turn off advanced mode
```

The app accepts the phrase `dark mode`, but it does **not** mean hidden control or bypassing Android security. It maps to Advanced Owner Mode.

## What Android will still block

- Hidden 24/7 control without a visible service.
- Bypassing lock screen, passwords, biometrics, banking protections, payment confirmation, or secure screens.
- Reading encrypted app internals unless the app exposes text to notifications or Accessibility.
- Becoming the default phone/SMS handler unless Android and the user grant that role.
- iPhone-style full-device control. iOS does not allow that for normal apps.

## Open in Android Studio

1. Open Android Studio.
2. Choose **Open** and select this `phone-jarvis-agent` folder.
3. Let Gradle sync.
4. Run the `app` configuration on your Android phone.

## Build APK with GitHub Cloud Build

Use this path if you do not want to install Android Studio.

1. Create a new GitHub repository.
2. Upload the contents of this `phone-jarvis-agent` folder to that repository.
3. Open the repository on GitHub.
4. Go to **Actions**.
5. Open **Build Android APK**.
6. Click **Run workflow**.
7. Wait until the build finishes.
8. Open the finished workflow run.
9. Download the artifact named `claw-jarvis-debug-apk`.
10. Extract the zip. Inside it will be `app-debug.apk`.

To install the APK on your phone:

1. Send `app-debug.apk` to your Android phone.
2. Open it on the phone.
3. If Android asks, allow **Install unknown apps** for the file manager/browser you used.
4. Install **Claw Jarvis**.

## Required phone permissions

Inside the app, use the permission buttons:

- **Microphone** for voice commands.
- **Notifications** so the foreground service can stay visible.
- **Notification access** so it can read and reply to incoming notifications.
- **Accessibility** so it can inspect visible app text and type/tap on your behalf.
- **Usage access** so it can know which app is in front.
- **Battery unrestricted** so Android is less likely to stop it.

## AI backend

The app expects an HTTPS endpoint that accepts:

```json
{
  "mode": "chat | write | notification | command",
  "userText": "what the user said",
  "context": {
    "app": "optional package name",
    "screenText": "optional visible screen text",
    "notification": "optional notification text"
  }
}
```

And returns:

```json
{
  "reply": "assistant text",
  "action": "none | reply_notification | type_text | open_app | tap_text",
  "text": "optional action text",
  "target": "optional app, label, or package",
  "confidence": 0.0
}
```

Set the endpoint from the app settings panel. Until you add a backend, the app uses an offline fallback brain for basic chat and writing prompts.

An optional Node server is included in `server/`. It keeps your model API key off the phone:

```bash
cd server
copy .env.example .env
npm start
```

Set these environment variables before running it:

- `AGENT_TOKEN`: token the Android app must send as its bearer token.
- `AI_BASE_URL`: OpenAI-compatible API base URL.
- `AI_API_KEY`: your model provider key.
- `AI_MODEL`: model name from your provider.

For a phone on the same Wi-Fi, set the Android app endpoint to `http://YOUR_COMPUTER_LAN_IP:8787/v1/phone-agent`. For real use outside your home network, host it behind HTTPS.

## Safety model

Autopilot only executes allowed actions. Advanced Owner Mode expands automation to every permission-based action the app supports, but risky actions like payments, deleting data, account changes, OTP sharing, banking, passwords, lock-screen bypass, hidden surveillance, or security settings are blocked by policy and should require you in the loop.
