package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import com.android.launcher3.QSTileItemInfo;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeTileVariantsTest {
    @Test public void backgroundContainsNoIconAndWideEndpointUsesNativeChildLayout() throws Exception {
        GroupTile tile = tile(); Object tag = tile.getTag(); int left = tile.icon.getLeft();
        QsLayout.Item item = QsLayoutTest.tile("t:wifi", 0, 0, 1, 1);
        ArrayList<NativeTileVariants.Image> images = NativeTileVariants.capture(tile, item, 1, v -> { });
        assertEquals(Color.GREEN, images.get(0).full().getPixel(42, 24));
        assertEquals(Color.DKGRAY, images.get(0).background().getPixel(42, 24));
        assertEquals(Color.GREEN, images.get(1).full().getPixel(14, 24));
        assertEquals(Color.DKGRAY, images.get(1).background().getPixel(14, 24));
        assertEquals(188, images.get(1).full().getWidth());
        assertSame(tag, tile.getTag()); assertEquals(88, tile.getWidth()); assertEquals(left, tile.icon.getLeft());
        assertEquals(View.VISIBLE, tile.icon.getVisibility()); assertEquals(1, tile.span);
        for (NativeTileVariants.Image image : images) image.recycle();
    }
    @Test public void endpointFailureStillReturnsTheNativeGroupAndChildToTheirOriginalState() throws Exception {
        GroupTile tile = tile(); Object tag = tile.getTag(); tile.failWide = true;
        assertThrows(ReflectiveOperationException.class,
                () -> NativeTileVariants.capture(tile, QsLayoutTest.tile("t:wifi", 0, 0, 1, 1), 1, v -> { }));
        assertSame(tag, tile.getTag()); assertEquals(1, tile.span); assertEquals(88, tile.getWidth());
        assertEquals(40, tile.icon.getLeft()); assertEquals(View.VISIBLE, tile.icon.getVisibility());
    }
    private GroupTile tile() {
        Context context = RuntimeEnvironment.getApplication(); GroupTile tile = new GroupTile(context);
        NativeGridTest.Container container = new NativeGridTest.Container(context);
        tile.setTag(new QSTileItemInfo("wifi")); container.addView(tile, new NativeGridTest.Lp()); tile.applySize(1, 1);
        container.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 400, 400); return tile;
    }
    public static class GroupTile extends FrameLayout {
        final View icon; int span = 1; boolean failWide;
        GroupTile(Context c) {
            super(c); GradientDrawable background = new GradientDrawable(); background.setColor(Color.DKGRAY); background.setCornerRadius(12);
            setBackground(background); icon = new View(c); icon.setBackgroundColor(Color.GREEN); addView(icon, new FrameLayout.LayoutParams(8, 8));
        }
        public void applySize(int x, int y) {
            span = x; FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.getLayoutParams(); lp.leftMargin = x == 1 ? 40 : 12; lp.topMargin = 20;
        }
        public void updateResources() { if (failWide && span == 2) throw new IllegalStateException("controlled resize failure"); }
        public void updateLayout() { }
    }
}
