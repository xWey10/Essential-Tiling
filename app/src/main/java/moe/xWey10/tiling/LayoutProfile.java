package moe.xWey10.tiling;

import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;

/** Portable settings and optional layout, applied as a validated draft before any system write. */
record LayoutProfile(String name, Config config, QsLayout layout) {
  static final int MAX_BYTES = 320_000;

  LayoutProfile {
    name = name == null ? "" : name.trim();
    if (name.isEmpty() || name.length() > 48)
      throw new IllegalArgumentException("Название: от 1 до 48 символов.");
  }

  String json() {
    try {
      JSONObject root = new JSONObject();
      root.put("format", "nothing-qs-profile");
      root.put("version", 1);
      root.put("name", name);
      root.put(
          "settings",
          new JSONObject()
              .put("enabled", config.enabled)
              .put("scale", config.scalePercent)
              .put("gap", config.gapDp)
              .put("radius", config.radiusDp)
              .put("battery", config.battery));
      if (layout != null) root.put("layout", new JSONObject(layout.json()));
      return root.toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static LayoutProfile parse(String json) {
    try {
      if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
        throw new IllegalArgumentException("Файл слишком большой.");
      checkDepth(json);
      JSONObject root = new JSONObject(json);
      if (!root.getString("format").equals("nothing-qs-profile") || integer(root, "version") != 1)
        throw new IllegalArgumentException("Неизвестный формат профиля.");
      JSONObject s = root.getJSONObject("settings");
      int scale = integer(s, "scale"), gap = integer(s, "gap"), radius = integer(s, "radius");
      if (scale < 80 || scale > 100 || gap < 0 || gap > 12 || radius < -1 || radius > 48)
        throw new IllegalArgumentException("Недопустимые параметры оформления.");
      QsLayout layout =
          root.has("layout") ? QsLayout.parse(root.getJSONObject("layout").toString()) : null;
      if (layout != null) layout.validateAgainst(layout);
      return new LayoutProfile(
          root.getString("name"),
          new Config(s.getBoolean("enabled"), scale, gap, radius, s.getBoolean("battery"), 0),
          layout);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Не удалось прочитать профиль.", e);
    }
  }

  private static int integer(JSONObject object, String name) throws JSONException {
    Object raw = object.get(name);
    if (!(raw instanceof Number number)
        || !Double.isFinite(number.doubleValue())
        || number.doubleValue() != Math.rint(number.doubleValue())
        || number.doubleValue() < Integer.MIN_VALUE
        || number.doubleValue() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Недопустимое число в профиле.");
    return number.intValue();
  }

  private static void checkDepth(String json) {
    int depth = 0;
    boolean string = false, escaped = false;
    for (int n = 0; n < json.length(); n++) {
      char c = json.charAt(n);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (string && c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        string = !string;
        continue;
      }
      if (!string) {
        if ((c == '{' || c == '[') && ++depth > 24)
          throw new IllegalArgumentException("Слишком сложный файл профиля.");
        if (c == '}' || c == ']') depth--;
      }
    }
  }

  QsLayout rebase(QsLayout source) {
    if (layout == null) return null;
    if (source == null) throw new IllegalArgumentException("Сначала загрузите шторку в редакторе.");
    if (!layout.portrait || !source.portrait)
      throw new IllegalArgumentException("Поверните телефон вертикально.");
    if (layout.items.size() != source.items.size() || layout.pages.size() != source.pages.size())
      throw new IllegalArgumentException("Набор плиток или страниц отличается от профиля.");
    ArrayList<QsLayout.Item> items = new ArrayList<>();
    for (QsLayout.Item current : source.items) {
      QsLayout.Item saved = layout.item(current.id());
      if (saved == null) throw new IllegalArgumentException("В шторке другой набор плиток.");
      int index = -1;
      for (int i = 0; i < layout.pages.size(); i++)
        if (layout.pages.get(i).id() == saved.page()) index = i;
      if (index < 0 || source.pages.get(index).id() != current.page())
        throw new IllegalArgumentException("Положение страниц отличается от профиля.");
      QsLayout.Page old = layout.pages.get(index), now = source.pages.get(index);
      if (old.columns() != now.columns() || old.rows() != now.rows())
        throw new IllegalArgumentException("Размер сетки отличается от профиля.");
      items.add(
          new QsLayout.Item(
              current.id(),
              current.title(),
              current.page(),
              saved.x(),
              saved.y(),
              saved.w(),
              saved.h(),
              current.editable(),
              current.tall(),
              saved.form()));
    }
    QsLayout result = new QsLayout(source.base, source.portrait, source.pages, items);
    result.validateAgainst(source);
    return result;
  }
}
