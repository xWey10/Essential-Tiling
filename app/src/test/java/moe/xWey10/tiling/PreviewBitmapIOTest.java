package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewBitmapIOTest {
    @Test public void fullResolutionPixelsAndAlphaSurviveWithoutCompression() throws Exception {
        Bitmap original = Bitmap.createBitmap(1440, 2000, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < 1440; x++) original.setPixel(x, 900, (x & 1) == 0 ? Color.WHITE : Color.BLACK);
        original.setPixel(40, 30, Color.argb(100, 40, 80, 120));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); PreviewBitmapIO.write(original, bytes);
        assertEquals(16 + 1440 * 2000 * 4, bytes.size());
        Bitmap decoded = PreviewBitmapIO.read(new ByteArrayInputStream(bytes.toByteArray()), PreviewBitmapIO.FORMAT);
        assertTrue(original.sameAs(decoded)); assertEquals(1440, decoded.getWidth());
        original.recycle(); decoded.recycle();
    }
    @Test public void oldModulesPngStillDecodesAtItsNativeSize() throws Exception {
        Bitmap source = Bitmap.createBitmap(900, 20, Bitmap.Config.ARGB_8888); source.eraseColor(Color.MAGENTA);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); source.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        Bitmap decoded = PreviewBitmapIO.read(new ByteArrayInputStream(bytes.toByteArray()), "");
        assertEquals(900, decoded.getWidth()); assertEquals(Color.MAGENTA, decoded.getPixel(600, 10));
    }
    @Test public void malformedDimensionsAndTruncatedPixelsFailBeforeBeingDisplayed() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4e515331); out.writeInt(4096); out.writeInt(Integer.MAX_VALUE); out.writeInt(4);
        assertThrows(IOException.class, () -> PreviewBitmapIO.read(new ByteArrayInputStream(bytes.toByteArray()), PreviewBitmapIO.FORMAT));
        bytes.reset(); out.writeInt(0x4e515331); out.writeInt(100); out.writeInt(100); out.writeInt(40000);
        assertThrows(IOException.class, () -> PreviewBitmapIO.read(new ByteArrayInputStream(bytes.toByteArray()), PreviewBitmapIO.FORMAT));
    }
}
