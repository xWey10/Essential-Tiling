package moe.xWey10.tiling;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

final class ProfileStore {
  private static android.content.SharedPreferences prefs(Context c) {
    return c.createDeviceProtectedStorageContext()
        .getSharedPreferences("profiles", Context.MODE_PRIVATE);
  }

  static List<LayoutProfile> read(Context c) {
    ArrayList<LayoutProfile> result = new ArrayList<>();
    try {
      JSONArray all = new JSONArray(prefs(c).getString("items", "[]"));
      for (int n = 0; n < all.length(); n++) {
        try {
          result.add(LayoutProfile.parse(all.getString(n)));
        } catch (RuntimeException ignored) {
        }
      }
    } catch (org.json.JSONException ignored) {
    }
    return result;
  }

  static boolean save(Context c, LayoutProfile profile) {
    List<LayoutProfile> all = read(c);
    all.removeIf(p -> p.name().equals(profile.name()));
    if (all.size() >= 20) throw new IllegalArgumentException("Можно сохранить до 20 профилей.");
    all.add(profile);
    return write(c, all);
  }

  static boolean delete(Context c, String name) {
    List<LayoutProfile> all = read(c);
    all.removeIf(p -> p.name().equals(name));
    return write(c, all);
  }

  private static boolean write(Context c, List<LayoutProfile> all) {
    JSONArray array = new JSONArray();
    for (LayoutProfile p : all) array.put(p.json());
    return prefs(c).edit().putString("items", array.toString()).commit();
  }
}
