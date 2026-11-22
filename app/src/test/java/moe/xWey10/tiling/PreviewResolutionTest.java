package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
public class PreviewResolutionTest {
    @Test public void fractionalPreviewScaleKeepsGuidesOnePhysicalPixelWide() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        NativePreviewServer.Frame frame = f.render(grid, grid.new Session(""), () -> { });
        PreviewView preview = new PreviewView(RuntimeEnvironment.getApplication()); preview.setFrame(frame.bitmap, frame.metadata);
        Bitmap output = NativeGridTest.draw(preview, 266);
        assertEquals(Color.BLACK, output.getPixel(65, 150));
        assertNotEquals(Color.BLACK, output.getPixel(66, 150));
        assertEquals(Color.BLACK, output.getPixel(67, 150));
        assertNotEquals(Color.BLACK, output.getPixel(265, 150));
        output.recycle(); frame.bitmap.recycle();
    }
    @Test public void nativeFineDetailsSurviveCaptureAndDisplayOnHighResolutionScreens() {
        Context context = RuntimeEnvironment.getApplication();
        for (int width : new int[]{1080, 1440}) {
            View panel = new View(context) {
                final Paint paint = new Paint();
                @Override protected void onDraw(Canvas canvas) {
                    for (int x = 0; x < getWidth(); x++) {
                        paint.setColor(x % 2 == 0 ? Color.BLACK : Color.WHITE);
                        canvas.drawRect(x, 0, x + 1, getHeight(), paint);
                    }
                }
            };
            measure(panel, width, 80);
            float scale = NativePreviewRenderer.scale(panel);
            Bitmap source = NativeFrameCapture.capture(panel, null, scale,
                    Math.round(width * scale), Math.round(80 * scale));
            assertEquals(width, source.getWidth());
            PreviewView preview = new PreviewView(context); preview.setFrame(source); measure(preview, width, 80);
            Bitmap output = Bitmap.createBitmap(width, 80, Bitmap.Config.ARGB_8888); preview.draw(new Canvas(output));
            for (int x = 0; x < width; x++) assertEquals("native column " + x,
                    x % 2 == 0 ? Color.BLACK : Color.WHITE, output.getPixel(x, 40));
            output.recycle(); source.recycle();
        }
    }
    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
}
