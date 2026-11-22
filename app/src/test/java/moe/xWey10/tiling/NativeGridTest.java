package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.launcher3.QSTileItemInfo;
import java.util.List;
import org.json.JSONArray;
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
public class NativeGridTest {
    @Test public void warmsRealSizeEndpointsAndTheirBackgroundsWithoutChangingLiveTile() throws Exception {
        Fixture f = new Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace); grid.focus = "t:bt";
        NativePreviewServer.Frame frame = f.render(grid, grid.new Session(""), () -> { });
        JSONArray variants = new JSONArray(frame.metadata.getString("variants"));
        assertEquals(3, variants.length());
        assertEquals(88, variants.getJSONObject(0).getJSONArray("image").getInt(2));
        assertEquals(188, variants.getJSONObject(1).getJSONArray("image").getInt(2));
        assertEquals(88, variants.getJSONObject(1).getJSONArray("image").getInt(3));
        assertEquals(188, variants.getJSONObject(2).getJSONArray("image").getInt(3));
        assertEquals(188, variants.getJSONObject(1).getJSONArray("background").getInt(2));
        assertSame(f.info, f.tile.getTag()); assertEquals(1, f.tile.nativeSpanX); assertEquals(1, f.tile.nativeSpanY);
        assertEquals(1, ((Lp) f.tile.getLayoutParams()).cellHSpan); assertEquals(88, f.tile.getWidth());
        assertEquals(View.VISIBLE, f.tile.getVisibility()); frame.bitmap.recycle();
    }
    @Test public void failedEndpointRenderingRestoresOriginalTagSizeAndVisibility() throws Exception {
        Fixture f = new Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace); grid.focus = "t:bt";
        f.tile.failWide = true;
        NativePreviewServer.Frame frame = f.render(grid, grid.new Session(""), () -> { });
        assertTrue(frame.metadata.containsKey("variantError"));
        assertSame(f.info, f.tile.getTag()); assertEquals(1, f.tile.nativeSpanX); assertEquals(88, f.tile.getWidth());
        assertEquals(View.VISIBLE, f.tile.getVisibility()); assertEquals(1, ((Lp) f.tile.getLayoutParams()).cellHSpan);
        frame.bitmap.recycle();
    }
    @Test public void rendersNativeResizedSpriteAndEmptyGridThenRestoresRealTagAndText() throws Exception {
        Fixture f = new Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        QsLayout edited = grid.source.edit("t:bt", 1, 1, 2, 2); assertNotNull(edited);
        NativeGrid.Session session = grid.new Session(edited.json());
        NativePreviewServer.Frame frame = f.render(grid, session, () -> { });
        QSTileItemInfo original = f.info;
        assertSame(original, f.tile.getTag()); assertEquals(0, original.cellX); assertEquals(1, original.spanX);
        assertEquals(1, f.tile.nativeSpanX); assertEquals(1, f.tile.nativeSpanY);
        assertEquals(0, ((Lp) f.tile.getLayoutParams()).cellX);
        assertEquals(1, f.tile.getMaxLines());
        assertEquals(Color.BLACK, frame.bitmap.getPixel(160, 160)); // blank base below the tile
        JSONArray regions = new JSONArray(frame.metadata.getString("regions"));
        assertEquals(1, regions.length());
        assertEquals(188, regions.getJSONObject(0).getInt("sw"));
        assertEquals(108, regions.getJSONObject(0).getInt("left"));
        PreviewView view = new PreviewView(f.context); view.setFrame(frame.bitmap, frame.metadata);
        Bitmap composed = draw(view, 400);
        assertEquals(Color.RED, composed.getPixel(160, 160));
        assertEquals(Color.BLACK, composed.getPixel(30, 30));
        assertEquals(frame.metadata.getInt("baseHeight"), composed.getHeight());
        frame.bitmap.recycle(); composed.recycle();
    }
    @Test public void nativeSizeAndTagAreRestoredEvenWhenRenderingThrows() throws Exception {
        Fixture f = new Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        NativeGrid.Session session = grid.new Session(grid.source.edit("t:bt", 0, 0, 2, 1).json());
        assertThrows(IllegalStateException.class, () -> f.render(grid, session, () -> { throw new IllegalStateException("controlled"); }));
        assertSame(f.info, f.tile.getTag()); assertEquals(1, f.info.spanX); assertEquals(1, f.tile.nativeSpanX);
        assertEquals(1, ((Lp) f.tile.getLayoutParams()).cellHSpan); assertEquals(1, f.tile.getMaxLines());
    }
    @Test public void rejectsStaleNativeGridWithoutApplyingAnything() throws Exception {
        Fixture f = new Fixture(); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        String draft = grid.source.edit("t:bt", 1, 0, 1, 1).json();
        ((Lp) f.tile.getLayoutParams()).cellY = 1;
        NativeGrid newer = new NativeGrid(f.panel, f.workspace);
        assertThrows(IllegalStateException.class, () -> newer.new Session(draft));
        assertSame(f.info, f.tile.getTag()); assertEquals(1, f.tile.nativeSpanX);
    }
    static Bitmap draw(PreviewView v, int width) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, width, v.getMeasuredHeight());
        Bitmap image = Bitmap.createBitmap(width, v.getHeight(), Bitmap.Config.ARGB_8888); v.draw(new Canvas(image)); return image;
    }
    static final class Fixture {
        final Context context = RuntimeEnvironment.getApplication();
        final FrameLayout panel = new FrameLayout(context);
        final Workspace workspace = new Workspace(context);
        final Cell page = new Cell(context);
        final Tile tile = new Tile(context);
        final QSTileItemInfo info = new QSTileItemInfo("bt");
        final Bitmap background = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888);
        Fixture() {
            NativeForms.supported=true;
            background.eraseColor(Color.BLACK); tile.setTag(info); tile.setBackgroundColor(Color.RED);
            tile.setSingleLine(true); tile.setMaxLines(1);
            page.container.addView(tile, new Lp()); workspace.addView(page, new LinearLayout.LayoutParams(400, 400));
            panel.addView(workspace, new FrameLayout.LayoutParams(400, 400));
            panel.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
            panel.layout(0, 0, 400, 500);
        }
        NativePreviewServer.Frame render(NativeGrid grid, NativeGrid.Session session, Runnable after) {
            return NativePreviewRenderer.render(panel, workspace, List.of(tile), null, List.of(), moe.xWey10.tiling.Config.DEFAULT,
                    0, background, () -> { session.apply(); after.run(); }, session::restore, grid, session);
        }
    }
    public static class Workspace extends LinearLayout {
        Workspace(Context c) { super(c); setOrientation(HORIZONTAL); }
        public long getScreenIdForPageIndex(int page) { return 8 + page; }
        private long getNewScreenId() { return 8 + getChildCount(); }
        public void insertNewWorkspaceScreen(long id) { addView(new Cell(getContext()), new LinearLayout.LayoutParams(400, 400)); }
        public int getScrollForPage(int page) { return 0; }
    }
    public static class Cell extends FrameLayout {
        final Container container;
        Cell(Context c) { super(c); container = new Container(c); addView(container, new FrameLayout.LayoutParams(400, 400)); }
        public int getCountX() { return 4; }
        public int getCountY() { return 4; }
        public int getCellWidth() { return 100; }
        int getCellHeight() { return 100; }
        public Container getShortcutsAndWidgets() { return container; }
    }
    public static class Container extends ViewGroup {
        Container(Context c) { super(c); }
        public boolean invertLayoutHorizontally() { return false; }
        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
            for (int n = 0; n < getChildCount(); n++) {
                measureChild(getChildAt(n));
            }
        }
        public void measureChild(View v) {
            Lp lp = (Lp) v.getLayoutParams(); lp.width = lp.cellHSpan * 100 - 12; lp.height = lp.cellVSpan * 100 - 12;
            v.measure(MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int n = 0; n < getChildCount(); n++) {
                View v = getChildAt(n); Lp lp = (Lp) v.getLayoutParams(); int x = lp.cellX * 100 + 8, y = lp.cellY * 100 + 8;
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            }
        }
    }
    public static class Lp extends ViewGroup.LayoutParams {
        public int cellX, cellY, tmpCellX, tmpCellY, cellHSpan = 1, cellVSpan = 1;
        public boolean useTmpCoords;
        Lp() { super(88, 88); }
    }
    public static class Tile extends TextView {
        int nativeSpanX = 1, nativeSpanY = 1;
        boolean failWide;
        Tile(Context c) { super(c); }
        public void applySize(int x, int y) {
            nativeSpanX = x; nativeSpanY = y; setSingleLine(false); setMaxLines(y == 2 ? 2 : 1);
        }
        public TextView getLabel() { return this; }
        public void updateResources() { if (failWide && nativeSpanX == 2) throw new IllegalStateException("controlled endpoint failure"); }
        public void updateLayout() { }
    }
}
