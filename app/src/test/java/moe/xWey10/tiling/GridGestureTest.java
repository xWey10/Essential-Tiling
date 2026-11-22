package moe.xWey10.tiling;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "port")
public class GridGestureTest {
    @Test public void selectedTileShowsAnActionableMinusAndEmptyCellsOpenThePicker() throws Exception {
        try (Fixture f = new Fixture()) {
            String[] removed = {null}; long[] page = {-1}; int[] cell = {-1, -1};
            f.view.setListener(new PreviewView.Listener() {
                @Override public void edited(QsLayout layout) { fail("Membership must use the native action path"); }
                @Override public void selected(QsLayout.Item item) { }
                @Override public void message(String text) { }
                @Override public void remove(String id) { removed[0] = id; }
                @Override public void empty(long p, int x, int y) { page[0] = p; cell[0] = x; cell[1] = y; }
            });
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            f.touch(MotionEvent.ACTION_DOWN, 10, 10); f.touch(MotionEvent.ACTION_UP, 10, 10);
            assertEquals("t:bt", removed[0]);
            f.touch(MotionEvent.ACTION_DOWN, 250, 150); f.touch(MotionEvent.ACTION_UP, 250, 150);
            assertEquals(8, page[0]); assertArrayEquals(new int[]{2, 1}, cell);
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void resizeTracksEveryPixelBeforeReleaseAndThenSnapsToTheNativeSize() throws Exception {
        try (Fixture f = new Fixture()) {
            NativeGrid grid = new NativeGrid(f.nativeTree.panel, f.nativeTree.workspace); grid.focus = "t:bt";
            NativePreviewServer.Frame warm = f.nativeTree.render(grid, grid.new Session(""), () -> { });
            f.view.setFrame(warm.bitmap, warm.metadata);
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            f.touch(MotionEvent.ACTION_DOWN, 94, 50); f.touch(MotionEvent.ACTION_MOVE, 114, 50);
            assertEquals(new android.graphics.RectF(8, 8, 116, 96), visualBounds(f.view));
            assertEquals(115, redEdge(f.view, 50, false), 3);
            f.touch(MotionEvent.ACTION_MOVE, 139, 50);
            assertEquals(new android.graphics.RectF(8, 8, 141, 96), visualBounds(f.view));
            assertEquals(140, redEdge(f.view, 50, false), 3); assertEquals(0, f.changes);
            f.touch(MotionEvent.ACTION_MOVE, 159, 50);
            assertEquals(160, redEdge(f.view, 50, false), 3);
            f.view.setFrame(warm.bitmap, warm.metadata);
            assertEquals(160, redEdge(f.view, 50, false), 3);
            f.touch(MotionEvent.ACTION_UP, 159, 50);
            assertEquals(2, f.edited.item("t:bt").w());
            assertEquals(160, redEdge(f.view, 50, false), 3);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
            assertEquals(195, redEdge(f.view, 50, false), 3); // Selection handles overlap the edge.
            assertEquals(new android.graphics.RectF(8, 8, 196, 96), visualBounds(f.view));
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void pullingLeftKeepsRightEdgeFixedAndCancelRestoresTheOriginalSize() throws Exception {
        try (Fixture f = new Fixture()) {
            NativeGrid grid = new NativeGrid(f.nativeTree.panel, f.nativeTree.workspace); grid.focus = "t:bt";
            String draft = grid.source.edit("t:bt", 1, 1, 1, 1).json();
            NativePreviewServer.Frame warm = f.nativeTree.render(grid, grid.new Session(draft), () -> { });
            f.view.setFrame(warm.bitmap, warm.metadata);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); redEdge(f.view, 150, true);
            f.touch(MotionEvent.ACTION_DOWN, 150, 150); f.touch(MotionEvent.ACTION_UP, 150, 150);
            f.touch(MotionEvent.ACTION_DOWN, 110, 150); f.touch(MotionEvent.ACTION_MOVE, 90, 150);
            assertEquals(new android.graphics.RectF(88, 108, 196, 196), visualBounds(f.view));
            assertEquals(88, redEdge(f.view, 150, true), 3); assertEquals(195, redEdge(f.view, 150, false), 3);
            f.touch(MotionEvent.ACTION_CANCEL, 90, 150);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
            assertEquals(108, redEdge(f.view, 150, true), 3);
            assertEquals(new android.graphics.RectF(108, 108, 196, 196), visualBounds(f.view)); assertEquals(0, f.changes);
        }
    }
    private android.graphics.RectF visualBounds(PreviewView view) {
        java.util.Map<String, TileVisual> visuals = org.robolectric.util.ReflectionHelpers.getField(view, "visuals");
        return new android.graphics.RectF(visuals.get("t:bt").motion.bounds);
    }
    @Test public void tileHoldStartsWithinThreeHundredMilliseconds() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(301));
            assertNotNull(f.selected);
            f.touch(MotionEvent.ACTION_MOVE, 150, 50); f.touch(MotionEvent.ACTION_UP, 150, 50);
            assertEquals(1, f.changes);
        }
    }
    private int redEdge(PreviewView view, int y, boolean first) {
        android.graphics.Bitmap frame = NativeGridTest.draw(view, 400); int edge = -1;
        for (int x = 0; x < 400; x++) {
            int c = frame.getPixel(x, y);
            if (android.graphics.Color.red(c) > android.graphics.Color.green(c) + 60
                    && android.graphics.Color.red(c) > android.graphics.Color.blue(c) + 60) {
                edge = x; if (first) break;
            }
        }
        frame.recycle(); assertTrue(edge >= 0); return edge;
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void previewFollowsTheFingerSmoothlyThenSettlesIntoTheSnappedCell() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
            float before = redCenter(f.view);
            f.touch(MotionEvent.ACTION_MOVE, 230, 50);
            assertEquals(before, redCenter(f.view), 2);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));
            float halfway = redCenter(f.view); assertTrue(halfway > before && halfway < 230);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            float floating = redCenter(f.view); assertEquals(232, floating, 3);
            f.touch(MotionEvent.ACTION_UP, 230, 50);
            assertEquals(floating, redCenter(f.view), 2);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
            assertEquals(252, redCenter(f.view), 2); assertEquals(2, f.edited.item("t:bt").x());
        }
    }
    private float redCenter(PreviewView view) {
        android.graphics.Bitmap frame = NativeGridTest.draw(view, 400);
        float sum = 0; int count = 0;
        for (int x = 0; x < 400; x++) {
            int color = frame.getPixel(x, 50);
            if (android.graphics.Color.red(color) > android.graphics.Color.green(color) + 60
                    && android.graphics.Color.red(color) > android.graphics.Color.blue(color) + 60) { sum += x; count++; }
        }
        frame.recycle(); assertTrue(count > 0); return sum / count;
    }
    @Test public void tapSelectsButOnlyLongPressMovesAndCancelDoesNotSave() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            assertNotNull(f.selected); assertEquals(0, f.changes);
            f.touch(MotionEvent.ACTION_DOWN, 50, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
            f.touch(MotionEvent.ACTION_MOVE, 250, 150); f.touch(MotionEvent.ACTION_CANCEL, 250, 150);
            assertEquals(0, f.changes); assertEquals(0, f.view.getLayoutDraft().item("t:bt").x());
            f.touch(MotionEvent.ACTION_DOWN, 50, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
            f.touch(MotionEvent.ACTION_MOVE, 250, 150); f.touch(MotionEvent.ACTION_UP, 250, 150);
            assertEquals(1, f.changes); assertEquals(2, f.edited.item("t:bt").x()); assertEquals(1, f.edited.item("t:bt").y());
            assertSame(f.nativeTree.info, f.nativeTree.tile.getTag()); assertEquals(0, f.nativeTree.info.cellX);
        }
    }
    @Test public void bottomRightEdgeSnapsToTwoByTwoAndPresetsCanShrink() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            f.touch(MotionEvent.ACTION_DOWN, 98, 98); f.touch(MotionEvent.ACTION_MOVE, 198, 198); f.touch(MotionEvent.ACTION_UP, 198, 198);
            assertEquals(1, f.changes); assertEquals(2, f.edited.item("t:bt").w()); assertEquals(2, f.edited.item("t:bt").h());
            f.view.resizeSelected(2, 1); assertEquals(1, f.edited.item("t:bt").h());
            f.view.resizeSelected(1, 1); assertEquals(1, f.edited.item("t:bt").w());
        }
    }
    @Test public void resizingFromLeftKeepsRightEdgeAnchoredAndBusyPreventsEdits() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            f.view.moveSelected(1, 0); assertEquals(1, f.edited.item("t:bt").x());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); NativeGridTest.draw(f.view, f.view.getWidth()).recycle();
            f.touch(MotionEvent.ACTION_DOWN, 102, 50); f.touch(MotionEvent.ACTION_MOVE, 2, 50); f.touch(MotionEvent.ACTION_UP, 2, 50);
            assertEquals(2, f.edited.item("t:bt").w()); assertEquals(0, f.edited.item("t:bt").x());
            int count = f.changes; f.view.setBusy(true); f.view.resizeSelected(1, 1); assertEquals(count, f.changes);
        }
    }
    @Test public void normalScrollingMotionDoesNotBecomeADrag() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_MOVE, 50, 180);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            f.touch(MotionEvent.ACTION_UP, 50, 180); assertEquals(0, f.changes); assertNull(f.selected);
        }
    }
    @Test public void verticalEdgeCanExpandToOneByTwoWithoutChangingItsWidth() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50); f.touch(MotionEvent.ACTION_UP, 50, 50);
            f.touch(MotionEvent.ACTION_DOWN, 50, 98); f.touch(MotionEvent.ACTION_MOVE, 50, 170); f.touch(MotionEvent.ACTION_UP, 50, 170);
            assertEquals(1, f.edited.item("t:bt").w()); assertEquals(2, f.edited.item("t:bt").h());
        }
    }
    @Test public void arrivingNativeEndpointsDoNotInterruptAnActiveDrag() throws Exception {
        try (Fixture f = new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN, 50, 50);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
            f.touch(MotionEvent.ACTION_MOVE, 160, 60);
            NativeGrid grid = new NativeGrid(f.nativeTree.panel, f.nativeTree.workspace); grid.focus = "t:bt";
            NativePreviewServer.Frame warm = f.nativeTree.render(grid, grid.new Session(""), () -> { });
            f.view.setFrame(warm.bitmap, warm.metadata);
            f.touch(MotionEvent.ACTION_MOVE, 250, 150); f.touch(MotionEvent.ACTION_UP, 250, 150);
            assertEquals(1, f.changes); assertEquals(2, f.edited.item("t:bt").x()); assertEquals(1, f.edited.item("t:bt").y());
            assertSame(f.nativeTree.info, f.nativeTree.tile.getTag());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void bottomBorderFollowsTheFingerToOneByThreeAndKeepsItsWidthAfterRelease() throws Exception {
        try(Fixture f=new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN,50,50); f.touch(MotionEvent.ACTION_UP,50,50);
            f.touch(MotionEvent.ACTION_DOWN,50,94); f.touch(MotionEvent.ACTION_MOVE,50,294);
            assertEquals(new android.graphics.RectF(8,8,96,296),visualBounds(f.view));
            f.touch(MotionEvent.ACTION_UP,50,294);
            assertEquals(1,f.edited.item("t:bt").w()); assertEquals(3,f.edited.item("t:bt").h());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); NativeGridTest.draw(f.view,400).recycle();
            assertEquals(new android.graphics.RectF(8,8,96,296),visualBounds(f.view));
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void inwardCornerDragCreatesLAndItsInnerBoundaryCanRestoreTheRectangle() throws Exception {
        try(Fixture f=new Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace); grid.focus="t:bt";
            NativePreviewServer.Frame big=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",0,0,2,2).json()),()->{});
            f.view.setFrame(big.bitmap,big.metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250)); NativeGridTest.draw(f.view,400).recycle();
            f.touch(MotionEvent.ACTION_DOWN,50,50); f.touch(MotionEvent.ACTION_UP,50,50);
            f.touch(MotionEvent.ACTION_DOWN,194,194); f.touch(MotionEvent.ACTION_MOVE,130,130);
            assertEquals(new android.graphics.RectF(8,8,196,196),visualBounds(f.view));
            f.touch(MotionEvent.ACTION_UP,130,130); assertEquals(new TileForm(3,1,1),f.edited.item("t:bt").form());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            f.touch(MotionEvent.ACTION_DOWN,102,102); f.touch(MotionEvent.ACTION_MOVE,194,194); f.touch(MotionEvent.ACTION_UP,194,194);
            assertEquals(TileForm.RECT,f.edited.item("t:bt").form()); assertEquals(2,f.edited.item("t:bt").w());
            big.bitmap.recycle();
        }
    }
    @Test public void cancelledNotchDragKeepsTheOriginalRectangle() throws Exception {
        try(Fixture f=new Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);
            NativePreviewServer.Frame big=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",0,0,2,2).json()),()->{});
            f.view.setFrame(big.bitmap,big.metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250)); NativeGridTest.draw(f.view,400).recycle();
            f.touch(MotionEvent.ACTION_DOWN,50,50); f.touch(MotionEvent.ACTION_UP,50,50); int before=f.changes;
            f.touch(MotionEvent.ACTION_DOWN,194,194); f.touch(MotionEvent.ACTION_MOVE,125,125); f.touch(MotionEvent.ACTION_CANCEL,125,125);
            assertEquals(before,f.changes); assertEquals(TileForm.RECT,f.view.getLayoutDraft().item("t:bt").form()); big.bitmap.recycle();
        }
    }
    @Test public void oldModuleWithoutShapeCapabilityDoesNotAcceptCustomGestures() throws Exception {
        try(Fixture f=new Fixture()) {
            android.os.Bundle metadata=new android.os.Bundle(f.frame.metadata); metadata.remove("formsSupported"); f.view.setFrame(f.frame.bitmap,metadata);
            NativeGridTest.draw(f.view,400).recycle(); f.touch(MotionEvent.ACTION_DOWN,50,50); f.touch(MotionEvent.ACTION_UP,50,50);
            f.view.resizeSelected(1,3); f.view.formSelected(true); assertEquals(0,f.changes); assertEquals(1,f.view.getLayoutDraft().item("t:bt").h());
        }
    }
    @Test public void aLateWarmFrameDoesNotOverwriteTheLocalLayoutOrMoveItsSprite() throws Exception {
        try(Fixture f=new Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN,50,50);f.touch(MotionEvent.ACTION_UP,50,50);f.view.moveSelected(1,1);f.view.resizeSelected(2,1);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));NativeGridTest.draw(f.view,400).recycle();android.graphics.RectF before=visualBounds(f.view);String draft=f.view.getLayoutDraft().json();
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);grid.focus="t:bt";NativePreviewServer.Frame warm=f.nativeTree.render(grid,grid.new Session(""),()->{});
            f.view.setFrame(warm.bitmap,warm.metadata);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));NativeGridTest.draw(f.view,400).recycle();
            assertEquals(draft,f.view.getLayoutDraft().json());assertEquals(before,visualBounds(f.view));assertEquals(1,f.view.getLayoutDraft().item("t:bt").x());assertEquals(2,f.view.getLayoutDraft().item("t:bt").w());
        }
    }
    @Test public void aRegularNativeFrameKeepsAlreadyLoadedStockVariantsForTheCurrentPage() throws Exception {
        try(Fixture f=new Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);grid.focus="t:bt";var warm=f.nativeTree.render(grid,grid.new Session(""),()->{});f.view.setFrame(warm.bitmap,warm.metadata);assertTrue(f.view.hasVariants("t:bt"));
            grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);var plain=f.nativeTree.render(grid,grid.new Session(""),()->{});f.view.setFrame(plain.bitmap,plain.metadata);assertTrue(f.view.hasVariants("t:bt"));
        }
    }
    static final class Fixture implements AutoCloseable, PreviewView.Listener {
        final ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        final NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture();
        final PreviewView view = new PreviewView(controller.get());
        final NativePreviewServer.Frame frame;
        QsLayout.Item selected; QsLayout edited; int changes;
        Fixture() throws Exception {
            NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
            frame = nativeTree.render(grid, grid.new Session(""), () -> { });
            controller.get().setContentView(view); view.setListener(this); view.setFrame(frame.bitmap, frame.metadata);
            NativeGridTest.draw(view, 400).recycle();
        }
        void touch(int action, float x, float y) {
            float scale = (float) view.getWidth() / frame.bitmap.getWidth();
            MotionEvent event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, x * scale, y * scale, 0);
            view.onTouchEvent(event); event.recycle();
        }
        @Override public void edited(QsLayout model) { changes++; edited = model; }
        @Override public void selected(QsLayout.Item item) { selected = item; }
        @Override public void message(String text) { }
        @Override public void close() { controller.close(); frame.bitmap.recycle(); }
    }
}
