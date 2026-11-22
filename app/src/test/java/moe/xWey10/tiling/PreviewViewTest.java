package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewViewTest {
    @Test public void noSourceMeansNoInventedTilesEvenWhenDraftChanges() {
        PreviewView preview = new PreviewView(RuntimeEnvironment.getApplication());
        preview.setConfig(new moe.xWey10.tiling.Config(true, 80, 12, 48, true, 0));
        Bitmap output = draw(preview, 240);
        assertEquals(Color.TRANSPARENT, output.getPixel(60, 20));
        assertTrue(preview.getContentDescription().toString().contains("SystemUI"));
    }
    @Test public void displaysSourceFrameWithItsActualAspectRatio() {
        PreviewView preview = new PreviewView(RuntimeEnvironment.getApplication());
        Bitmap source = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.MAGENTA);
        preview.setFrame(source);
        Bitmap output = draw(preview, 240);
        assertEquals(400, output.getHeight());
        assertEquals(Color.MAGENTA, output.getPixel(100, 180));
        preview.setFrame(null);
        Bitmap cleared = draw(preview, 240);
        assertEquals(Color.TRANSPARENT, cleared.getPixel(30, 20));
    }
    private static Bitmap draw(PreviewView preview, int width) {
        preview.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        preview.layout(0, 0, width, preview.getMeasuredHeight());
        Bitmap image = Bitmap.createBitmap(width, preview.getHeight(), Bitmap.Config.ARGB_8888);
        preview.draw(new Canvas(image)); return image;
    }
}
