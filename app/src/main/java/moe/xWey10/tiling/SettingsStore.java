package moe.xWey10.tiling;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("qs_settings", Context.MODE_PRIVATE);
    }

    static Config read(Context context) {
        SharedPreferences p = prefs(context);
        return new Config(p.getBoolean("enabled", false), p.getInt("scale", 100),
                p.getInt("gap", 0), p.getInt("radius", -1),
                p.getBoolean("battery", false), p.getLong("revision", 0));
    }

    static boolean write(Context context, Config c) {
        return prefs(context).edit().putBoolean("enabled", c.enabled)
                .putInt("scale", c.scalePercent).putInt("gap", c.gapDp)
                .putInt("radius", c.radiusDp).putBoolean("battery", c.battery)
                .putLong("revision", c.revision).commit();
    }
}
