package moe.xWey10.tiling;

/** Immutable values; the same bounds apply to the app, IPC and hooks. */
public final class Config {
  public static final Config DEFAULT = new Config(false, 100, 0, -1, false, 0);
  public final boolean enabled;
  public final int scalePercent;
  public final int gapDp;
  public final int radiusDp;
  public final boolean battery;
  public final long revision;

  public Config(
      boolean enabled, int scalePercent, int gapDp, int radiusDp, boolean battery, long revision) {
    this.enabled = enabled;
    this.scalePercent = clamp(scalePercent, 80, 100);
    this.gapDp = clamp(gapDp, 0, 12);
    this.radiusDp = radiusDp < 0 ? -1 : clamp(radiusDp, 0, 48);
    this.battery = battery;
    this.revision = Math.max(0, revision);
  }

  boolean sameValues(Config other) {
    return other != null
        && enabled == other.enabled
        && scalePercent == other.scalePercent
        && gapDp == other.gapDp
        && radiusDp == other.radiusDp
        && battery == other.battery;
  }

  static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
