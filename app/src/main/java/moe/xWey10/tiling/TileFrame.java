package moe.xWey10.tiling;

import android.graphics.*;

/** Intrinsic tile dimensions are independent of the grid frame enclosing its rotation. */
final class TileFrame {
  static QsLayout.Item intrinsic(QsLayout.Item item) {
    return new QsLayout.Item(
        item.id(),
        item.title(),
        item.page(),
        0,
        0,
        item.form().columns(item.w()),
        item.form().rows(item.h()),
        item.editable(),
        item.tall(),
        item.form().base(0, 0));
  }

  static RectF rawBounds(
      QsLayout.Item anchor, QsLayout.Item base, RectF frame, float px, float py) {
    RectF old = anchor.form().intrinsic(frame, anchor.w(), anchor.h(), px, py);
    return new RectF(
        old.left + base.x() * px,
        old.top + base.y() * py,
        old.left + (base.x() + base.w()) * px - (anchor.w() * px - frame.width()),
        old.top + (base.y() + base.h()) * py - (anchor.h() * py - frame.height()));
  }

  static RectF bounds(Path path) {
    float[] points = path.approximate(.1f);
    RectF b =
        new RectF(
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY);
    for (int n = 0; n < points.length; n += 3) {
      b.left = Math.min(b.left, points[n + 1]);
      b.right = Math.max(b.right, points[n + 1]);
      b.top = Math.min(b.top, points[n + 2]);
      b.bottom = Math.max(b.bottom, points[n + 2]);
    }
    return b;
  }

  static QsLayout.Item reframe(
      QsLayout.Item anchor,
      QsLayout.Item base,
      QsLayout.Page page,
      RectF frame,
      float px,
      float py,
      float cellX,
      float cellY,
      float radius,
      boolean rtl,
      boolean clamp) {
    return reframe(anchor, base, page, frame, px, py, cellX, cellY, radius, rtl, clamp, false);
  }

  static QsLayout.Item rotate(
      QsLayout.Item anchor,
      float angle,
      QsLayout.Page page,
      RectF frame,
      float px,
      float py,
      float cellX,
      float cellY,
      float radius,
      boolean rtl) {
    QsLayout.Item base = intrinsic(anchor).shaped(anchor.form().base(0, 0).rotated(angle));
    return reframe(anchor, base, page, frame, px, py, cellX, cellY, radius, rtl, false, true);
  }

  private static QsLayout.Item reframe(
      QsLayout.Item anchor,
      QsLayout.Item base,
      QsLayout.Page page,
      RectF frame,
      float px,
      float py,
      float cellX,
      float cellY,
      float radius,
      boolean rtl,
      boolean clamp,
      boolean fixedCenter) {
    RectF raw = rawBounds(anchor, base, frame, px, py);
    TileForm f = base.form().base(0, 0);
    Path shape = f.rawPath(raw, base.w(), base.h(), px, py, radius);
    float cx = raw.centerX(), cy = raw.centerY();
    if (rtl) {
      Matrix mirror = new Matrix();
      mirror.setScale(-1, 1, frame.centerX(), frame.centerY());
      shape.transform(mirror);
      cx = 2 * frame.centerX() - cx;
    }
    Matrix rotation = TileRotation.extra(f, frame, anchor.w(), anchor.h(), px, py, rtl);
    shape.transform(rotation);
    RectF center = TileRotation.point(rotation, cx, cy), b = bounds(shape);
    if (!Float.isFinite(b.left)) return null;
    int w =
        Math.max(
            1,
            (int)
                Math.ceil(
                    (2 * Math.max(center.left - b.left, b.right - center.left)
                                + anchor.w() * px
                                - frame.width())
                            / px
                        - .002f));
    int h =
        Math.max(
            1,
            (int)
                Math.ceil(
                    (2 * Math.max(center.top - b.top, b.bottom - center.top)
                                + anchor.h() * py
                                - frame.height())
                            / py
                        - .002f));
    // Integer grid coordinates can preserve the exact centre only with matching span parity.
    // Reserve an extra empty margin instead of rounding the centre by half a cell.
    if (fixedCenter) {
      if (((w - anchor.w()) & 1) != 0) w++;
      if (((h - anchor.h()) & 1) != 0) h++;
    }
    if (w > page.columns() || h > page.rows()) return null;
    int x =
        Math.round(
            anchor.x()
                + (anchor.w() - w) / 2f
                + (center.left - frame.centerX()) / cellX * (rtl ? -1 : 1));
    int y = Math.round(anchor.y() + (anchor.h() - h) / 2f + (center.top - frame.centerY()) / cellY);
    if (fixedCenter) {
      x = anchor.x() + (anchor.w() - w) / 2;
      y = anchor.y() + (anchor.h() - h) / 2;
    }
    if (clamp) {
      x = Math.max(0, Math.min(page.columns() - w, x));
      y = Math.max(0, Math.min(page.rows() - h, y));
    }
    if (x < 0 || y < 0 || x + w > page.columns() || y + h > page.rows()) return null;
    if (f.rotation() != 0 || w != base.w() || h != base.h()) f = f.base(base.w(), base.h());
    return new QsLayout.Item(
        anchor.id(),
        anchor.title(),
        anchor.page(),
        x,
        y,
        w,
        h,
        anchor.editable(),
        anchor.tall(),
        f);
  }
}
