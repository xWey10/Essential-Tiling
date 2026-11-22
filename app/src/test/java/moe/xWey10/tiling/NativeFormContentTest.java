package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeFormContentTest {
    @Test public void existingContentStaysOutsideTheCutWithoutScalingOrPermanentPaddingChanges() throws Exception {
        for(int corner=1;corner<=4;corner++) {
            QsLayout.Item item=QsLayoutTest.tile("t:demo",0,0,2,2).shaped(new TileForm(corner,1,1));
            FormBodyTestView tile=new FormBodyTestView(RuntimeEnvironment.getApplication(),item); tile.setGravity(Gravity.CENTER); tile.setPadding(8,6,8,6); tile.setBackgroundColor(Color.RED);
            View icon=new View(tile.getContext()); icon.setBackgroundColor(Color.GREEN); tile.addView(icon,new LinearLayout.LayoutParams(20,20));
            tile.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));
            tile.layout(0,0,200,200);
            assertEquals(200,tile.getMeasuredWidth()); assertEquals(200,tile.getMeasuredHeight());
            assertEquals(20,icon.getWidth()); assertEquals(20,icon.getHeight()); assertEquals(6,tile.getPaddingTop()); assertEquals(6,tile.getPaddingBottom());
            assertEquals(item.form().top()?150:50,(icon.getTop()+icon.getBottom())/2,1);
            Bitmap bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap);
            canvas.clipPath(item.form().path(new RectF(0,0,200,200),2,2,12)); tile.draw(canvas);
            assertEquals(0,bitmap.getPixel(item.form().left()?50:150,item.form().top()?50:150));
            assertEquals(Color.GREEN,bitmap.getPixel(100,item.form().top()?150:50)); bitmap.recycle();
        }
    }
    @Test public void formIsDurableAndSpecificToUserAndSavedSpan() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(c,0);
        QsLayout layout=QsLayoutTest.source(QsLayoutTest.tile("t:wifi",0,0,2,3).shaped(new TileForm(2,1,2)));
        NativeForms.save(c,0,layout); assertEquals(new TileForm(2,1,2),NativeForms.form("t:wifi",2,3));
        assertEquals(TileForm.RECT,NativeForms.form("t:wifi",2,2));
        NativeForms.user(c,1); assertEquals(TileForm.RECT,NativeForms.form("t:wifi",2,3));
        NativeForms.user(c,0); assertEquals(new TileForm(2,1,2),NativeForms.form("t:wifi",2,3));
        NativeForms.save(c,0,QsLayoutTest.source(QsLayoutTest.tile("t:wifi",0,0,2,3)));
        assertEquals(TileForm.RECT,NativeForms.form("t:wifi",2,3));
    }    @Test public void removedTileDoesNotRegainAnOldNotchAfterBeingAddedAgain() {
        android.content.Context c=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(c,0);
        QsLayout l=QsLayoutTest.source(QsLayoutTest.tile("t:wifi",0,0,2,2).shaped(new TileForm(2,1,1)));
        NativeForms.save(c,0,l); NativeForms.clearSpec(c,0,"wifi");
        NativeForms.user(c,1); NativeForms.user(c,0); assertEquals(TileForm.RECT,NativeForms.form("t:wifi",2,2));
    }

}
