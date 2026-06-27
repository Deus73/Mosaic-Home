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
                Integer current = counts.get(item.getPackageName());
                counts.put(item.getPackageName(), (current == null ? 0 : current) + 1);
            }
        }
        MainActivity.updateBadges(counts);
    }
}
