package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileFormTest {
    @Test public void everyCutCornerIsTransparentAndTheTwoArmsStayConnected() {
        for(int corner=1;corner<=4;corner++) {
            TileForm f=new TileForm(corner,1,1); Bitmap bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.RED);
            new Canvas(bitmap).drawPath(f.path(new RectF(0,0,200,200),2,2,12),paint);
            assertEquals(0,bitmap.getPixel(f.left()?50:150,f.top()?50:150));
            assertEquals(Color.RED,bitmap.getPixel(f.left()?150:50,50));
            assertEquals(Color.RED,bitmap.getPixel(50,f.top()?150:50));
            bitmap.recycle();
        }
    }
    @Test public void shapeAndDimensionsRoundTripAndUseTheBoundingFootprintForCollisions() {
        QsLayout source=QsLayoutTest.source(QsLayoutTest.tile("t:wifi",0,0,1,1));
        QsLayout l=source.edit("t:wifi",0,0,2,3,new TileForm(2,1,2)); assertNotNull(l); l.validateAgainst(source);
        QsLayout decoded=QsLayout.parse(l.json()); assertEquals(new TileForm(2,1,2),decoded.item("t:wifi").form());
        assertFalse(decoded.differsFrom(l)); assertNotEquals(l.asSource().base,source.base);
        assertFalse(decoded.item("t:wifi").overlaps(QsLayoutTest.tile("other",1,0,1,1)));
        QsLayout vertical=source.edit("t:wifi",0,0,1,3); assertNotNull(vertical); vertical.validateAgainst(source);
        assertNull(source.edit("t:wifi",0,0,1,5));
        assertNull(source.edit("t:wifi",0,0,2,2,new TileForm(2,2,1)));
        assertEquals(TileForm.RECT,decoded.item("t:wifi").at(0,0,1,3).form());
    }
    @Test public void oneByThreeUsesCustomNativePresentationAndStockSizesStayStock() {
        assertTrue(NativeForms.custom(QsLayoutTest.tile("t:wifi",0,0,1,3)));
        assertFalse(NativeForms.custom(QsLayoutTest.tile("t:wifi",0,0,2,2)));
        assertTrue(NativeForms.custom(QsLayoutTest.tile("t:wifi",0,0,2,2).shaped(new TileForm(3,1,1))));
    }
}
