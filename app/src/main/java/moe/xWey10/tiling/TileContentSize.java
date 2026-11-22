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

  static TileContentSize of(float width, float height, boolean tall) {
    if (width < 1.5f) return new TileContentSize(1, 1);
    return new TileContentSize(2, tall && height >= 1.5f ? 2 : 1);
  }
}
