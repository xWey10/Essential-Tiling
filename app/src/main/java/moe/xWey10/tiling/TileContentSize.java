package moe.xWey10.tiling;

import android.graphics.RectF;

/** The shape's bounding frame must not replace its intrinsic native content size. */
record TileContentSize(int width, int height) {
  int mode() {
    return height == 2 ? 2 : width == 2 ? 1 : 0;
  }

  static TileContentSize of(QsLayout.Item item) {
    RectF body = item.form().content(new RectF(0, 0, item.w(), item.h()), item.w(), item.h());
    return of(body.width(), body.height(), item.tall());
  }

  static RectF body(TileForm form, RectF frame, int w, int h, float pitchX, float pitchY) {
    RectF area = form.content(frame, w, h, pitchX, pitchY);
    if (form.diagonal()) {
      // A capsule can be much longer than the native horizontal layout it contains.
      float gap = Math.max(0, w * pitchX - frame.width());
      float nativeWidth = Math.max(1, 2 * pitchX - gap);
      if (area.width() > nativeWidth) area.inset((area.width() - nativeWidth) / 2, 0);
    }
    return area;
  }

  static TileContentSize of(float width, float height, boolean tall) {
    if (width < 1.5f) return new TileContentSize(1, 1);
    return new TileContentSize(2, tall && height >= 1.5f ? 2 : 1);
  }
}
