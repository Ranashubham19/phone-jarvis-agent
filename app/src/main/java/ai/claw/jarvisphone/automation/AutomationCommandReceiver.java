package ai.claw.jarvisphone.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ai.claw.jarvisphone.core.AgentResponse;
import ai.claw.jarvisphone.core.ActionLogStore;

public final class AutomationCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_EXECUTE = "ai.claw.jarvisphone.EXECUTE_AUTOMATION";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TARGET = "target";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_EXECUTE.equals(intent.getAction())) {
            return;
        }

        String action = intent.getStringExtra(EXTRA_ACTION);
        String text = intent.getStringExtra(EXTRA_TEXT);
        String target = intent.getStringExtra(EXTRA_TARGET);

        boolean handled = false;
        if (AgentResponse.ACTION_TYPE_TEXT.equals(action)) {
            handled = PhoneAccessibilityService.typeText(text);
        } else if (AgentResponse.ACTION_TAP_TEXT.equals(action)) {
            handled = PhoneAccessibilityService.tapText(target);
        } else if (AgentResponse.ACTION_BACK.equals(action)) {
            handled = PhoneAccessibilityService.globalBack();
        } else if (AgentResponse.ACTION_HOME.equals(action)) {
            handled = PhoneAccessibilityService.globalHome();
        }

        ActionLogStore.append(context, "Accessibility command " + action + " handled=" + handled);
    }
}
