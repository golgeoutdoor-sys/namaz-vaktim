package com.namazvaktim.app;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        // Son kaydedilen vakitleri yeniden planla; uygulamanın tekrar açılmasını bekleme.
        try {
            android.content.SharedPreferences p = c.getSharedPreferences("namaz", Context.MODE_PRIVATE);
            String raw = p.getString("timings_json", "");
            if (!raw.isEmpty()) {
                org.json.JSONObject timings = new org.json.JSONObject(raw);
                NotificationHelper.schedulePrayerNotifications(c, timings);
                NotificationHelper.scheduleDailyAyet(c);
            }
        } catch (Exception ignored) {}
    }
}
