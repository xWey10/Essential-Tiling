package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class TilePickerViewTest {
    static List<TileClient.Entry> entries() {
        ArrayList<TileClient.Entry> entries = new ArrayList<>(); entries.add(new TileClient.Entry("bt", "Bluetooth", ""));
        for (int n = 0; n < 400; n++) entries.add(new TileClient.Entry("custom(app" + n + "/.Tile)", "Tile " + String.format(java.util.Locale.ROOT, "%03d", n), "Application " + n));
        return entries;
    }
    static void measure(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }
    static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) for (int n = 0; n < group.getChildCount(); n++) { T found = find(group.getChildAt(n), type); if (found != null) return found; }
        return null;
    }
    @Test public void catalogueScrollDoesNotMoveTheEditorAndKeyboardStillLeavesRoomForResults() throws Exception {
        NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture(); NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
        NativePreviewServer.Frame frame = nativeTree.render(grid, grid.new Session(""), () -> { });
        PreviewView preview = new PreviewView(nativeTree.context); preview.setFrame(frame.bitmap, frame.metadata);
        TilePickerView picker = new TilePickerView(nativeTree.context, preview, new LinearLayout(nativeTree.context), () -> 0, () -> { }, (entry, cell) -> { });
        picker.entries(entries());picker.applications(true); measure(picker, 1080, 2400);
        GridView catalogue = find(picker, GridView.class); int[] top = new int[2]; preview.getLocationInWindow(top);
        assertEquals(400, catalogue.getAdapter().getCount()); assertTrue(catalogue.getHeight() > 300);
        catalogue.setSelection(320); measure(picker, 1080, 2400);
        assertTrue(catalogue.getFirstVisiblePosition() >= 300);
        int[] after = new int[2]; preview.getLocationInWindow(after); assertArrayEquals(top, after);
        measure(picker, 1080, 1200); assertTrue(catalogue.getHeight() > 100); assertTrue(preview.getHeight() < 600);
        frame.bitmap.recycle();
    }
    @Test public void searchIncludesApplicationsAndAddUsesTheTappedCellUntilAFreshFrameArrives() throws Exception {
        NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture(); NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
        NativePreviewServer.Frame frame = nativeTree.render(grid, grid.new Session(""), () -> { });
        PreviewView preview = new PreviewView(nativeTree.context); preview.setFrame(frame.bitmap, frame.metadata);
        TilePickerView.Cell[] chosen = {null};
        TilePickerView picker = new TilePickerView(nativeTree.context, preview, new LinearLayout(nativeTree.context), () -> 0, () -> { }, (entry, cell) -> chosen[0] = cell);
        picker.entries(entries());picker.applications(true); picker.target(8, 2, 1); measure(picker, 1080, 2400);
        GridView catalogue = find(picker, GridView.class); EditText search = find(picker, EditText.class);
        search.setText("Application 399"); measure(picker, 1080, 2400); assertEquals(1, catalogue.getAdapter().getCount());
        catalogue.performItemClick(catalogue.getChildAt(0), 0, 0); assertEquals(new TilePickerView.Cell(8, 2, 1), chosen[0]);
        picker.waitingForFrame(); assertFalse(catalogue.isEnabled()); picker.busy(false); assertFalse(catalogue.isEnabled());
        picker.frameReady(); assertTrue(catalogue.isEnabled());
        search.setText(""); assertEquals(400, catalogue.getAdapter().getCount()); // Already placed Bluetooth stays excluded.
        frame.bitmap.recycle();
    }
    @Test public void catalogueErrorSurvivesPreviewFramesAndSearchAndCanBeRetried() {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        TilePickerView picker = new TilePickerView(c, new PreviewView(c), new LinearLayout(c), () -> 0, () -> { }, (entry, cell) -> { });
        picker.catalogError("Каталог не ответил"); picker.frameReady(); picker.busy(false);
        find(picker, EditText.class).setText("wifi");
        assertEquals("Каталог не ответил", org.robolectric.util.ReflectionHelpers.<android.widget.TextView>getField(picker, "status").getText().toString());
        android.widget.Button retry = org.robolectric.util.ReflectionHelpers.getField(picker, "retry"); assertEquals(View.VISIBLE, retry.getVisibility());
        int[] calls = {0}; picker.retry(() -> { calls[0]++; picker.loading(); }); retry.performClick();
        assertEquals(1, calls[0]); assertEquals(View.GONE, retry.getVisibility());
    }
    @Test public void openingAndClosingReusesTheSamePreviewAndPreservesItsDraft() throws Exception {
        NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture(); NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
        NativePreviewServer.Frame frame = nativeTree.render(grid, grid.new Session(grid.source.edit("t:bt", 1, 0, 2, 1).json()), () -> { });
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app = controller.get(); PreviewView preview = ReflectionHelpers.getField(app, "preview");
            preview.setFrame(frame.bitmap, frame.metadata); Object parent = preview.getParent(); String draft = preview.getLayoutDraft().json();
            ReflectionHelpers.callInstanceMethod(app, "showTilePicker", ReflectionHelpers.ClassParameter.from(long.class, 8L),
                    ReflectionHelpers.ClassParameter.from(int.class, 3), ReflectionHelpers.ClassParameter.from(int.class, 2));
            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertTrue(dialog.isShowing());
            assertFalse("Catalogue loading must not block page navigation", org.robolectric.util.ReflectionHelpers.<Boolean>getField(app, "committing"));
            assertSame(preview, find(dialog.getWindow().getDecorView(), PreviewView.class)); assertNotSame(parent, preview.getParent());
            dialog.dismiss(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); assertSame(parent, preview.getParent()); assertEquals(draft, preview.getLayoutDraft().json());
            assertNull(ReflectionHelpers.getField(app, "tilePicker"));
        }
        frame.bitmap.recycle();
    }
}
