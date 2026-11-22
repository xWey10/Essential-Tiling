package moe.xWey10.tiling;

import android.content.Context;

/** Survives closing the settings app. Never restores over newer saved settings. */
final class DraftStore {
  record Draft(Config config, String layout) {}

  private static android.content.SharedPreferences prefs(Context c) {
    return c.createDeviceProtectedStorageContext()
        .getSharedPreferences("editor_draft", Context.MODE_PRIVATE);
  }

  static void write(Context c, Config config, String layout) {
    Config saved = SettingsStore.read(c);
    boolean same = config.sameValues(saved);
    if (same && (layout == null || layout.isEmpty())) {
      clear(c);
      return;
    }
    LayoutProfile data =
        new LayoutProfile(
            "Черновик", config, layout == null || layout.isEmpty() ? null : QsLayout.parse(layout));
    prefs(c).edit().putLong("revision", saved.revision).putString("profile", data.json()).apply();
  }

  static Draft read(Context c) {
    var p = prefs(c);
    if (p.getLong("revision", -1) != SettingsStore.read(c).revision) return null;
    try {
      String json = p.getString("profile", "");
      if (json.isEmpty()) return null;
      LayoutProfile data = LayoutProfile.parse(json);
      return new Draft(data.config(), data.layout() == null ? "" : data.layout().json());
    } catch (RuntimeException e) {
      clear(c);
      return null;
    }
  }

  static void clear(Context c) {
    prefs(c).edit().clear().apply();
  }
}
