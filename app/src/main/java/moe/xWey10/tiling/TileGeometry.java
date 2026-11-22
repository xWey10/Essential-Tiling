package moe.xWey10.tiling;

/** Native setup runs first, so every frame starts from fresh native coordinates. */
public final class TileGeometry {
    private TileGeometry() {}

    public static int size(int nativeSize, int percent, int gapPx, int minimumPx) {
        if (nativeSize <= 0) return nativeSize;
        int minimum = Math.min(nativeSize, Math.max(1, minimumPx));
        int desired = Math.round(nativeSize * (Config.clamp(percent, 80, 100) / 100f))
                - Math.max(0, gapPx);
        return Config.clamp(desired, minimum, nativeSize);
    }

    public static int centeredOrigin(int nativeOrigin, int nativeSize, int size) {
        return nativeOrigin + (nativeSize - size) / 2;
    }

    public static float batteryAlpha(float expansion) {
        if (!Float.isFinite(expansion)) return 0f;
        return Math.max(0f, Math.min(1f, (expansion - 0.75f) / 0.25f));
    }
}
