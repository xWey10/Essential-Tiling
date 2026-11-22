package moe.xWey10.tiling;

import java.util.ArrayDeque;

/** Bounded local geometry history. A native membership/source change starts a new history. */
final class LayoutHistory {
  private static final int LIMIT = 40;
  private final ArrayDeque<QsLayout> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private QsLayout current, compound;

  void accept(QsLayout next) {
    if (next == null) {
      clear();
      return;
    }
    if (current == null || !compatible(current, next)) {
      compound = null;
      current = next;
      undo.clear();
      redo.clear();
      return;
    }
    if (!next.differsFrom(current)) {
      current = next;
      return;
    }
    if (compound != null) {
      current = next;
      redo.clear();
      return;
    }
    undo.addLast(current);
    while (undo.size() > LIMIT) undo.removeFirst();
    redo.clear();
    current = next;
  }

  void reset(QsLayout source) {
    clear();
    current = source;
  }

  void begin() {
    compound = current;
  }

  void end() {
    QsLayout initial = compound;
    compound = null;
    if (initial != null
        && current != null
        && compatible(initial, current)
        && current.differsFrom(initial)) {
      undo.addLast(initial);
      while (undo.size() > LIMIT) undo.removeFirst();
      redo.clear();
    }
  }

  void clear() {
    compound = null;
    current = null;
    undo.clear();
    redo.clear();
  }

  boolean canUndo() {
    return !undo.isEmpty();
  }

  boolean canRedo() {
    return !redo.isEmpty();
  }

  QsLayout undo() {
    if (!canUndo()) return null;
    redo.addLast(current);
    return current = undo.removeLast();
  }

  QsLayout redo() {
    if (!canRedo()) return null;
    undo.addLast(current);
    return current = redo.removeLast();
  }

  String snapshot() {
    if (current == null) return "";
    try {
      org.json.JSONObject root = new org.json.JSONObject().put("current", current.json());
      org.json.JSONArray back = new org.json.JSONArray(), forward = new org.json.JSONArray();
      root.put("undo", back);
      root.put("redo", forward);
      int bytes = root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      if (bytes > 48_000) return "";
      for (var pair : java.util.List.of(new Object[] {undo, back}, new Object[] {redo, forward})) {
        @SuppressWarnings("unchecked")
        var stack = (ArrayDeque<QsLayout>) pair[0];
        var array = (org.json.JSONArray) pair[1];
        var it = stack.descendingIterator();
        while (it.hasNext()) {
          String value = it.next().json();
          int extra = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length * 2 + 8;
          if (bytes + extra > 48_000) break;
          array.put(value);
          bytes += extra;
        }
      }
      return root.toString();
    } catch (org.json.JSONException e) {
      return "";
    }
  }

  void restore(String json) {
    clear();
    if (json == null || json.isEmpty() || json.length() > 48_000) return;
    try {
      org.json.JSONObject root = new org.json.JSONObject(json);
      current = QsLayout.parse(root.getString("current"));
      for (var pair :
          java.util.List.of(
              new Object[] {undo, root.getJSONArray("undo")},
              new Object[] {redo, root.getJSONArray("redo")})) {
        @SuppressWarnings("unchecked")
        var stack = (ArrayDeque<QsLayout>) pair[0];
        var array = (org.json.JSONArray) pair[1];
        if (array.length() > LIMIT) throw new IllegalArgumentException();
        for (int n = array.length() - 1; n >= 0; n--) {
          QsLayout value = QsLayout.parse(array.getString(n));
          if (!compatible(current, value)) throw new IllegalArgumentException();
          value.validateAgainst(current);
          stack.addLast(value);
        }
      }
    } catch (org.json.JSONException | RuntimeException e) {
      clear();
    }
  }

  private static boolean compatible(QsLayout a, QsLayout b) {
    if (!a.base.equals(b.base)
        || a.portrait != b.portrait
        || !a.pages.equals(b.pages)
        || a.items.size() != b.items.size()) return false;
    for (QsLayout.Item item : a.items) if (b.item(item.id()) == null) return false;
    return true;
  }
}
