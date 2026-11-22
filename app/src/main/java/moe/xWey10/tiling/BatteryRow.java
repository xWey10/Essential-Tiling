package moe.xWey10.tiling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Locale;

/** Read-only system battery data; receiver follows the native panel's view lifecycle. */
@android.annotation.SuppressLint("AppCompatCustomView") // This View is created inside platform SystemUI with its native theme.
final class BatteryRow extends TextView {
    private boolean registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { update(intent); }
    };

    BatteryRow(Context context) {
        super(context);
        setTextSize(13);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinHeight(dp(36));
        setPadding(dp(16), dp(6), dp(16), dp(6));
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setText("Батарея");
        updateColors();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (registered) return;
        try {
            Intent sticky = getContext().registerReceiver(receiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            registered = true;
            if (sticky != null) update(sticky);
        } catch (RuntimeException e) { setText("Батарея · нет данных"); }
    }

    @Override protected void onDetachedFromWindow() {
        if (registered) {
            try { getContext().unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            registered = false;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        updateColors();
    }

    private void update(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) { setText("Батарея · нет данных"); return; }
        int percent = Config.clamp(Math.round(100f * level / scale), 0, 100);
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        String text = "Батарея · " + percent + "%" + (plugged ? " · питание" : "");
        if (temp != Integer.MIN_VALUE) text += String.format(Locale.getDefault(), " · %.1f °C", temp / 10f);
        setText(text);
    }

    void refreshBattery() {
        Intent intent = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) update(intent);
    }

    private void updateColors() {
        try (TypedArray a = getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary})) {
            ColorStateList colors = a.getColorStateList(0);
            if (colors != null) setTextColor(colors);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
