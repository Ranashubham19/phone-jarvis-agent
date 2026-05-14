package ai.claw.jarvisphone.automation;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import ai.claw.jarvisphone.core.ActionLogStore;

public final class JarvisCallScreeningService extends CallScreeningService {
    private static final String[] SPAM_PREFIXES = {
            "+1400",
            "+1800",
            "+1900"
    };

    @Override
    public void onScreenCall(Call.Details details) {
        Uri handle = details == null ? null : details.getHandle();
        String number = handle == null ? "" : handle.getSchemeSpecificPart();
        boolean spam = looksLikeSpamNumber(number);

        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(spam)
                .setRejectCall(spam)
                .setSkipCallLog(spam)
                .setSkipNotification(spam)
                .build();

        respondToCall(details, response);
        ActionLogStore.append(this, "Screened call " + number + " spam=" + spam);
    }

    private static boolean looksLikeSpamNumber(String number) {
        if (number == null || number.trim().isEmpty()) {
            return false;
        }
        for (String prefix : SPAM_PREFIXES) {
            if (number.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
