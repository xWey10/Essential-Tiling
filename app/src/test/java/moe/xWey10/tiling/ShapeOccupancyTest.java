package moe.xWey10.tiling;
import android.view.View;
import com.android.launcher3.CellLayout;
import com.android.launcher3.QSTileItemInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ShapeOccupancyTest {
    static TileForm fourCells() { return new TileForm(0,0,0,"001111","0,1,3,1"); }
    @org.junit.After public void cleanup() { de.robv.android.xposed.XposedBridge.hooks.clear(); NativeForms.draft.remove(); }
    @Test public void aThreeByTwoLUsesFourCellsAndBothEmptyCellsAcceptOtherTiles() {
        QsLayout.Item l=new QsLayout.Item("t:l","",8,0,0,3,2,true,true,fourCells());
        QsLayout source=QsLayoutTest.source(l,QsLayoutTest.tile("t:a",3,0,1,1),QsLayoutTest.tile("t:b",3,1,1,1));
        assertEquals(4,l.cellCount()); QsLayout first=source.edit("t:a",0,0,1,1); assertNotNull(first);
        QsLayout both=first.edit("t:b",1,0,1,1); assertNotNull(both); both.validateAgainst(source);
        assertEquals(0,both.item("t:l").x()); assertEquals(0,both.item("t:a").x()); assertEquals(1,both.item("t:b").x());
        assertFalse(l.overlaps(both.item("t:a"))); assertFalse(l.overlaps(both.item("t:b")));
        assertTrue(l.overlaps(QsLayoutTest.tile("filled",2,0,1,1)));
    }
    @Test public void nativeMarkingLookupAndRemovingTheLKeepTheTwoHoleTiles() throws Exception {
        NativeFormHooksTest.install(); CellLayout cell=new CellLayout(RuntimeEnvironment.getApplication());
        View l=tile(cell,"l",0,0,3,2); NativeForms.draft.set(java.util.Map.of("t:l",new QsLayout.Item("t:l","",0,0,0,3,2,true,true,fourCells())));
        cell.markCellsAsOccupiedForView(l); int count=0; for(boolean[] col:cell.mOccupied.cells) for(boolean value:col) if(value) count++; assertEquals(4,count);
        assertTrue(cell.mOccupied.isRegionVacant(0,0,2,1)); assertNull(cell.getShortcutsAndWidgets().getChildAt(0,0));
        View a=tile(cell,"a",0,0,1,1),b=tile(cell,"b",1,0,1,1); cell.markCellsAsOccupiedForView(a); cell.markCellsAsOccupiedForView(b);
        assertSame(a,cell.getShortcutsAndWidgets().getChildAt(0,0)); assertSame(b,cell.getShortcutsAndWidgets().getChildAt(1,0)); assertSame(l,cell.getShortcutsAndWidgets().getChildAt(2,0));
        cell.getShortcutsAndWidgets().removeView(b); cell.markCellsAsUnoccupiedForView(b);
        cell.commitTempPlacement(); assertFalse(cell.mOccupied.cells[1][0]); assertTrue(cell.mOccupied.cells[0][0]);
        cell.getShortcutsAndWidgets().addView(b); cell.markCellsAsOccupiedForView(b);
        cell.markCellsAsUnoccupiedForView(l); assertTrue(cell.mOccupied.cells[0][0]); assertTrue(cell.mOccupied.cells[1][0]); assertFalse(cell.mOccupied.cells[2][0]);
    }
    @Test public void rawInfoMarkingAfterReloadUsesTheSavedMask() throws Exception {
        NativeFormHooksTest.install(); android.content.Context c=RuntimeEnvironment.getApplication(); NativeForms.user(c,0);
        NativeForms.save(c,0,QsLayoutTest.source(new QsLayout.Item("t:l","",8,0,0,3,2,true,true,fourCells()))); NativeForms.user(c,1); NativeForms.user(c,0);
        com.android.launcher3.util.GridOccupancy grid=new com.android.launcher3.util.GridOccupancy(4,4); QSTileItemInfo info=new QSTileItemInfo("l"); info.spanX=3; info.spanY=2;
        grid.markCells(info,true); assertTrue(grid.isRegionVacant(0,0,2,1)); assertTrue(grid.cells[2][0]); assertTrue(grid.cells[0][1]);
    }
    @Test public void tappingTheHoleOfAnLInTheEditorOpensThePickerAtThatCell() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);
            NativePreviewServer.Frame frame=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",0,0,3,2,fourCells()).json()),()->{});
            f.view.setFrame(frame.bitmap,frame.metadata); NativeGridTest.draw(f.view,400).recycle();
            int[] chosen={-1,-1}; f.view.setListener(new PreviewView.Listener() {
                public void edited(QsLayout model) { } public void selected(QsLayout.Item item) { assertNull(item); } public void message(String text) { }
                public void empty(long page,int x,int y) { chosen[0]=x; chosen[1]=y; }
            });
            f.touch(android.view.MotionEvent.ACTION_DOWN,150,50); f.touch(android.view.MotionEvent.ACTION_UP,150,50);
            assertArrayEquals(new int[]{1,0},chosen); frame.bitmap.recycle();
        }
    }
    static View tile(CellLayout cell,String spec,int x,int y,int w,int h) {
        View view=new android.view.View(cell.getContext()); QSTileItemInfo info=new QSTileItemInfo(spec); info.cellX=x; info.cellY=y; info.spanX=w; info.spanY=h; view.setTag(info);
        NativeGridTest.Lp lp=new NativeGridTest.Lp(); lp.cellX=x; lp.cellY=y; lp.cellHSpan=w; lp.cellVSpan=h; cell.getShortcutsAndWidgets().addView(view,lp); return view;
    }
}
