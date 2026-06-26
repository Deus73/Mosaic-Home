package com.codex.mosaichome;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

public class BadgeNotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publishBadges();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publishBadges();
    }

    @Override
    public void onListenerConnected() {
        publishBadges();
    }

    private void publishBadges() {
        StatusBarNotification[] active = getActiveNotifications();
        Map<String, Integer> counts = new HashMap<>();
        if (active != null) {
            for (StatusBarNotification item : active) {
                counts.put(item.getPackageName(), counts.getOrDefault(item.getPackageName(), 0) + 1);
            }
        }
        MainActivity.updateBadges(counts);
    }
}
