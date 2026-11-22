package moe.xWey10.tiling;

import android.graphics.RectF;

/** Retarget from the displayed position, including when a second move interrupts the first. */
final class TileMotion {
  final RectF bounds = new RectF();
  private final RectF start = new RectF(), target = new RectF();
  private long started, duration;

  TileMotion(RectF initial) {
    bounds.set(initial);
    start.set(initial);
    target.set(initial);
  }

  void move(RectF next, long now, long millis) {
    advance(now);
    if (target.equals(next)) {
      if (millis == 0) {
        duration = 0;
        bounds.set(next);
      }
      return;
    }
    start.set(bounds);
    target.set(next);
    started = now;
    duration = millis;
    if (millis == 0) bounds.set(next);
  }

  boolean advance(long now) {
    float t = duration == 0 ? 1 : Math.min(1, Math.max(0, (float) (now - started) / duration));
    float eased = 1 - (1 - t) * (1 - t) * (1 - t);
    bounds.set(
        start.left + (target.left - start.left) * eased,
        start.top + (target.top - start.top) * eased,
        start.right + (target.right - start.right) * eased,
        start.bottom + (target.bottom - start.bottom) * eased);
    return t < 1;
  }
}
