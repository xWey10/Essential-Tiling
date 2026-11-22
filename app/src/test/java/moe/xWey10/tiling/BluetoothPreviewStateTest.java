package moe.xWey10.tiling;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BluetoothPreviewStateTest {
    @Test public void restoresSelectedDeviceCachedReferencesAndDetachedLabels() throws Exception {
        Context c = RuntimeEnvironment.getApplication(); Tile tile = new Tile();
        Pager pager = new Pager(c); pager.page = 2; tile.mPager = pager;
        TextView original = new TextView(c); original.setSingleLine(true); original.setMaxLines(1);
        original.setLayoutParams(new FrameLayout.LayoutParams(88, 88)); tile.mLabel = original;
        tile.mPagesViewList.put("detached-device", original);
        BluetoothPreviewState snapshot = new BluetoothPreviewState(tile);
        pager.page = 0; tile.mLabel = new TextView(c); original.setSingleLine(false); original.setMaxLines(2);
        original.getLayoutParams().width = 188; snapshot.restore();
        assertSame(original, tile.mLabel); assertEquals(2, pager.page); assertEquals(1, original.getMaxLines());
        assertEquals(88, original.getLayoutParams().width); assertNull(original.getParent());
    }
    static class Tile {
        Object mPager, mLabel, mSecondaryLabel, mIconContainer, mIconView, mClickView;
        Map<String, View> mPagesViewList = new LinkedHashMap<>();
    }
    public static class Pager extends FrameLayout {
        int page; Pager(Context c) { super(c); }
        public int getCurrentItem() { return page; }
        public void setCurrentItem(int page, boolean smooth) { this.page = page; }
    }
}
