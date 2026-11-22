package moe.xWey10.tiling;

import org.junit.Test;
import static org.junit.Assert.*;

public class TileGeometryTest {
    @Test public void defaultsDoNotChangeNativeGeometry() {
        for (int value : new int[]{0, 64, 78, 156, 234, 600}) {
            assertEquals(value, TileGeometry.size(value, 100, 0, 48));
            assertEquals(10, TileGeometry.centeredOrigin(10, value, value));
        }
        assertFalse(Config.DEFAULT.enabled);
        assertEquals(-1, Config.DEFAULT.radiusDp);
    }

    @Test public void tileStaysInsideNativeCellAndRetainsMinimumTouchSize() {
        for (int nativeSize = 1; nativeSize <= 1000; nativeSize++) {
            for (int scale = 80; scale <= 100; scale++) {
                for (int gap = 0; gap <= 36; gap += 3) {
                    int size = TileGeometry.size(nativeSize, scale, gap, 144);
                    int origin = TileGeometry.centeredOrigin(120, nativeSize, size);
                    assertTrue(size >= Math.min(nativeSize, 144));
                    assertTrue(size <= nativeSize);
                    assertTrue(origin >= 120);
                    assertTrue(origin + size <= 120 + nativeSize);
                    assertTrue(Math.abs((origin - 120) - (120 + nativeSize - origin - size)) <= 1);
                }
            }
        }
    }

    @Test public void untrustedValuesAreClamped() {
        Config c = new Config(true, -1, 1000, 1000, true, -100);
        assertEquals(80, c.scalePercent); assertEquals(12, c.gapDp);
        assertEquals(48, c.radiusDp); assertEquals(0, c.revision);
        assertEquals(-1, new Config(true, 100, 0, -100, false, 0).radiusDp);
    }

    @Test public void batteryIsHiddenInCollapsedPanelAndFadesWithoutOvershoot() {
        assertEquals(0f, TileGeometry.batteryAlpha(0), 0f);
        assertEquals(0f, TileGeometry.batteryAlpha(.75f), 0f);
        assertEquals(.5f, TileGeometry.batteryAlpha(.875f), .0001f);
        assertEquals(1f, TileGeometry.batteryAlpha(1), 0f);
        assertEquals(0f, TileGeometry.batteryAlpha(Float.NaN), 0f);
        assertEquals(1f, TileGeometry.batteryAlpha(2), 0f);
    }
}
