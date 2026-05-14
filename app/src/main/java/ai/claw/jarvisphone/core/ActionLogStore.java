package ai.claw.jarvisphone.core;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ActionLogStore {
    private static final String FILE_NAME = "jarvis-actions.log";

    private ActionLogStore() {
    }

    public static synchronized void append(Context context, String event) {
        try {
            File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(timestamp() + " " + clean(event) + "\n");
            }
        } catch (Exception ignored) {
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
