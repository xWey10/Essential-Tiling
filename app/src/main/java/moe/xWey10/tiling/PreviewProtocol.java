package moe.xWey10.tiling;

import android.net.Uri;
import android.os.Bundle;

final class PreviewProtocol {
    static final Uri REQUEST = Uri.parse(SettingsProvider.BASE_URI + "/preview/request");
    static final Uri RESULT = Uri.parse(SettingsProvider.BASE_URI + "/preview/result");
    static final Uri SOURCE = Uri.parse(SettingsProvider.BASE_URI + "/preview/source");
    static Uri image(long id) { return Uri.parse(SettingsProvider.BASE_URI + "/preview/image/" + id); }
    static Bundle request(Config c, int page) {
        return request(c, page, "");
    }
    static Bundle request(Config c, int page, String layout) {
        return request(c, page, layout, "");
    }
    static Bundle request(Config c, int page, String layout, String focus) {
        Bundle b = new Bundle();
        b.putBoolean("enabled", c.enabled); b.putInt("scale", c.scalePercent);
        b.putInt("gap", c.gapDp); b.putInt("radius", c.radiusDp);
        b.putBoolean("battery", c.battery); b.putInt("page", Math.max(0, page));
        if (layout != null && !layout.isEmpty()) {
            if (layout.length() > QsLayout.MAX_JSON) throw new IllegalArgumentException("Layout too large");
            b.putString("layout", layout);
        }
        if (focus != null && !focus.isEmpty()) {
            if (focus.length() > 1024) throw new IllegalArgumentException("Tile ID too large");
            b.putString("focus", focus);
        }
        return b;
    }
    static Config config(Bundle b) {
        return new Config(b.getBoolean("enabled"), b.getInt("scale", 100), b.getInt("gap"),
                b.getInt("radius", -1), b.getBoolean("battery"), 0);
    }
}
