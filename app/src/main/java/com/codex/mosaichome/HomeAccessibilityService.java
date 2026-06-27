package com.codex.mosaichome;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class HomeAccessibilityService extends AccessibilityService {
    private static HomeAccessibilityService instance;

    static boolean runAction(String action) {
        HomeAccessibilityService service = instance;
        if (service == null) return false;
        if ("notifications".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        if ("quick_settings".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        if ("recents".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_RECENTS);
        if ("power".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        return false;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
