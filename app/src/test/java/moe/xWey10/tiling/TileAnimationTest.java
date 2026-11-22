package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileAnimationTest {
    @Test public void existingNativeWidgetScaleIsPreservedIndependentlyOfTheResizeAnimation() {
        TileImage nativeFrame = image(120, 60, 16, Color.RED);
        TileImage scaledWidget = new TileImage(nativeFrame.atlas, nativeFrame.source, null, 2, 1, 60, 30);
        TileVisual visual = new TileVisual(new RectF(0, 0, 60, 30), scaledWidget);
        Bitmap result = draw(visual);
        assertEquals(4, redWidth(result, 12)); assertEquals(Color.RED, result.getPixel(9, 12));
        result.recycle();
    }
    @Test public void movementInterpolatesAndRetargetsWithoutJumping() {
        TileMotion motion = new TileMotion(new RectF(0, 0, 60, 60));
        motion.move(new RectF(100, 0, 160, 60), 0, 200);
        assertTrue(motion.advance(60)); float current = motion.bounds.left;
        assertTrue(current > 0 && current < 100);
        motion.move(new RectF(200, 100, 260, 160), 60, 200);
        assertEquals(current, motion.bounds.left, 0);
        assertTrue(motion.advance(160)); assertTrue(motion.bounds.left > current && motion.bounds.left < 200);
        assertFalse(motion.advance(260)); assertEquals(new RectF(200, 100, 260, 160), motion.bounds);
    }
    @Test public void disabledAnimationSnapsBothGeometryAndNativeImage() {
        TileImage small = image(60, 60, 24, Color.GREEN), wide = image(120, 60, 16, Color.RED);
        TileVisual visual = new TileVisual(new RectF(0, 0, 60, 60), small);
        visual.motion.move(new RectF(0, 0, 120, 60), 0, 0); visual.image(wide, 0, false);
        assertFalse(visual.advance(0)); Bitmap result = draw(visual);
        assertEquals(Color.RED, result.getPixel(18, 24)); assertEquals(8, redWidth(result, 24)); result.recycle();
    }
    @Test public void nativeIconKeepsItsWidthDuringResizeAndNewEndpointFadesIn() {
        TileImage small = image(60, 60, 24, Color.GREEN), wide = image(120, 60, 16, Color.RED);
        TileVisual visual = new TileVisual(new RectF(0, 0, 60, 60), small);
        visual.image(wide, 0, true); visual.motion.move(new RectF(0, 0, 120, 60), 0, 200);
        visual.advance(0); Bitmap start = draw(visual); assertEquals(0, redWidth(start, 24)); start.recycle();
        assertTrue(visual.advance(90)); Bitmap middle = draw(visual);
        assertEquals(8, redWidth(middle, 24)); assertTrue(visual.motion.bounds.width() > 60 && visual.motion.bounds.width() < 120);
        middle.recycle(); assertFalse(visual.advance(220)); Bitmap end = draw(visual);
        assertEquals(8, redWidth(end, 24)); assertEquals(Color.RED, end.getPixel(18, 24)); assertEquals(120, visual.motion.bounds.width(), 0);
        end.recycle();
    }
    @Test public void repeatedSizeChangesPreserveTheDisplayedGeometry() {
        TileImage small = image(60, 60, 24, Color.GREEN), wide = image(120, 60, 16, Color.RED);
        TileVisual visual = new TileVisual(new RectF(0, 0, 60, 60), small);
        visual.image(wide, 0, true); visual.motion.move(new RectF(0, 0, 120, 60), 0, 200); visual.advance(70);
        RectF mid = new RectF(visual.motion.bounds);
        visual.image(small, 70, true); visual.motion.move(new RectF(0, 0, 60, 60), 70, 200);
        assertEquals(mid, visual.motion.bounds); visual.advance(300);
        assertEquals(60, visual.motion.bounds.width(), 0); assertEquals(Color.GREEN, draw(visual).getPixel(26, 24));
    }
    @Test public void nineSliceFillsTheExpandedCenterWithoutScalingTheNativeContent() {
        TileImage circle = image(60, 60, 24, Color.GREEN);
        Bitmap result = Bitmap.createBitmap(180, 60, Bitmap.Config.ARGB_8888); Paint p = new Paint();
        circle.background(new Canvas(result), p, new RectF(0, 0, 180, 60));
        assertEquals(Color.DKGRAY, result.getPixel(90, 30)); assertEquals(Color.DKGRAY, result.getPixel(160, 30));
        assertEquals(Color.TRANSPARENT, result.getPixel(0, 0));
        result.recycle();
    }
    static TileImage image(int width, int height, int iconX, int color) {
        Bitmap atlas = Bitmap.createBitmap(width, height * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(atlas); Paint p = new Paint(); p.setColor(Color.DKGRAY);
        c.drawRoundRect(new RectF(0, 0, width, height), 30, 30, p);
        c.drawRoundRect(new RectF(0, height, width, height * 2), 30, 30, p);
        p.setColor(color); c.drawRect(iconX, 20, iconX + 8, 28, p);
        return new TileImage(atlas, new Rect(0, 0, width, height), new Rect(0, height, width, height * 2), width == 60 ? 1 : 2, 1);
    }
    static Bitmap draw(TileVisual visual) {
        Bitmap result = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
        visual.draw(new Canvas(result), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG)); return result;
    }
    static int redWidth(Bitmap b, int row) {
        int count = 0; for (int x = 0; x < b.getWidth(); x++) {
            int c = b.getPixel(x, row); if (Color.red(c) > Color.green(c) + 20 && Color.red(c) > Color.blue(c) + 20) count++;
        } return count;
    }
}
