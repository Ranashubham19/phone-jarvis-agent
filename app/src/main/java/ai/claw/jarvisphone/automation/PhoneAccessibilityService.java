package ai.claw.jarvisphone.automation;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import ai.claw.jarvisphone.core.ActionLogStore;
import ai.claw.jarvisphone.core.JarvisPrefs;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Queue;

public final class PhoneAccessibilityService extends AccessibilityService {
    private static WeakReference<PhoneAccessibilityService> activeService = new WeakReference<>(null);
    private JarvisPrefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new JarvisPrefs(this);
    }

    @Override
    protected void onServiceConnected() {
        activeService = new WeakReference<>(this);
        ActionLogStore.append(this, "Accessibility service connected");
    }

    @Override
    public void onDestroy() {
        activeService.clear();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName != null) {
            prefs.setCurrentPackage(packageName.toString());
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            StringBuilder builder = new StringBuilder();
            collectVisibleText(root, builder, 3000);
            prefs.setScreenText(builder.toString());
            root.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        ActionLogStore.append(this, "Accessibility service interrupted");
    }

    public static boolean typeText(String text) {
        PhoneAccessibilityService service = activeService.get();
        if (service == null || text == null || text.trim().isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = service.findEditableNode(service.getRootInActiveWindow());
        }
        if (focused == null) {
            return false;
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        focused.recycle();
        return ok;
    }

    public static boolean tapText(String target) {
        PhoneAccessibilityService service = activeService.get();
        if (service == null || target == null || target.trim().isEmpty()) {
            return false;
        }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo node = findNodeContainingText(root, target.trim());
        root.recycle();
        if (node == null) {
            return false;
        }
        AccessibilityNodeInfo clickable = nearestClickable(node);
        boolean ok = clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (clickable != null) {
            clickable.recycle();
        }
        node.recycle();
        return ok;
    }

    public static boolean globalBack() {
        PhoneAccessibilityService service = activeService.get();
        return service != null && service.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean globalHome() {
        PhoneAccessibilityService service = activeService.get();
        return service != null && service.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isEditable() && node.isEnabled()) {
                return AccessibilityNodeInfo.obtain(node);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            if (node != root) {
                node.recycle();
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findNodeContainingText(AccessibilityNodeInfo root, String target) {
        String lowerTarget = target.toLowerCase();
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            CharSequence text = firstText(node);
            if (text != null && text.toString().toLowerCase().contains(lowerTarget)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            node.recycle();
        }
        return null;
    }

    private static AccessibilityNodeInfo nearestClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        while (current != null) {
            if (current.isClickable() && current.isEnabled()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null;
    }

    private static void collectVisibleText(AccessibilityNodeInfo node, StringBuilder builder, int maxChars) {
        if (node == null || builder.length() >= maxChars) {
            return;
        }
        if (!node.isPassword()) {
            CharSequence text = firstText(node);
            if (text != null) {
                String clean = text.toString().trim();
                if (!clean.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(" | ");
                    }
                    builder.append(clean);
                }
            }
        }

        for (int i = 0; i < node.getChildCount() && builder.length() < maxChars; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectVisibleText(child, builder, maxChars);
                child.recycle();
            }
        }
    }

    private static CharSequence firstText(AccessibilityNodeInfo node) {
        if (node.getText() != null) {
            return node.getText();
        }
        if (node.getContentDescription() != null) {
            return node.getContentDescription();
        }
        return null;
    }
}
