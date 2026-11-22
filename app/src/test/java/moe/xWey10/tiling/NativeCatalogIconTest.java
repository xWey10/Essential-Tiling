package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.android.systemui.qs.customize.TileQueryHelper;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeCatalogIconTest {
    public static final class Icon {
        final Drawable drawable;
        Icon(Drawable drawable) { this.drawable = drawable; }
        public Drawable getDrawable(Context context) { return drawable; }
    }
    @Test public void nativeGlyphKeepsItsAspectAndAlphaAndDoesNotChangeTheSourceDrawable() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Bitmap source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888); source.eraseColor(Color.RED);
        Drawable drawable = new BitmapDrawable(c.getResources(), source); drawable.setBounds(2, 3, 22, 13);
        TileQueryHelper.State state = new TileQueryHelper.State(); state.icon = new Icon(drawable);
        String png = NativeCatalogIcon.encode(state, c); assertFalse(png.isEmpty()); assertTrue(png.length() <= 5500);
        TileClient.Entry entry = TileClient.Entry.decode(new JSONObject().put("spec", "custom(app/.Tile)")
                .put("label", "Application tile").put("icon", png).toString());
        assertNotNull(entry.icon()); assertEquals(96, entry.icon().getWidth());
        assertEquals(0, Color.alpha(entry.icon().getPixel(48, 8)));
        assertEquals(Color.WHITE, entry.icon().getPixel(48, 48));
        assertEquals(new Rect(2, 3, 22, 13), drawable.getBounds());
        assertEquals(Color.RED, source.getPixel(10, 5));
    }
    @Test public void stateSupplierProvidesTheNativeIconAndFailureDoesNotHideTheTile() throws Exception {
        Context c = RuntimeEnvironment.getApplication(); Bitmap source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888); source.eraseColor(Color.BLUE);
        TileQueryHelper.State state = new TileQueryHelper.State(); state.iconSupplier = () -> new Icon(new BitmapDrawable(c.getResources(), source));
        assertFalse(NativeCatalogIcon.encode(state, c).isEmpty());
        state.iconSupplier = () -> { throw new IllegalStateException("Unavailable resource"); };
        assertEquals("", NativeCatalogIcon.encode(state, c));
        TileClient.Entry entry = TileClient.Entry.decode("{\"spec\":\"wifi\",\"label\":\"Wi-Fi\",\"icon\":\"bad png\"}");
        assertEquals("wifi", entry.spec()); assertNull(entry.icon());
    }
    @Test public void noisyAppGlyphIsBoundedForPagedBinderTransfer() throws Exception {
        Context c = RuntimeEnvironment.getApplication(); Bitmap source = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        java.util.Random random = new java.util.Random(10);
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) source.setPixel(x, y, Color.argb(random.nextInt(256), 120, 10, 30));
        TileQueryHelper.State state = new TileQueryHelper.State(); state.icon = new Icon(new BitmapDrawable(c.getResources(), source));
        String encoded = NativeCatalogIcon.encode(state, c); assertFalse(encoded.isEmpty()); assertTrue(encoded.length() <= 5500);
        JSONObject row = new JSONObject().put("spec", "custom(app/.Noisy)").put("label", "Tile").put("icon", encoded);
        assertTrue(row.toString().length() < 8192);
        TileClient.Entry entry = TileClient.Entry.decode(row.toString()); assertNotNull(entry.icon()); assertTrue(entry.icon().getWidth() <= 96);
    }
}
