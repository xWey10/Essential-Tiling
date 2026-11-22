package moe.xWey10.tiling;

import android.os.Bundle;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherInfoManager;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35, qualifiers = "port")
public class NativeTileActionsTest {
    @After public void clean() { Launcher.instance = null; LauncherInfoManager.discardWrites = false; LauncherInfoManager.received = null; }
    private Launcher setup(NativeGridTest.Fixture f) throws Exception {
        Launcher l = new Launcher(); Launcher.instance = l; l.workspace = f.workspace; l.mInfoManager.rawTileInfoList.add(f.info);
        NativeTileCatalogTest.seed("wifi", "custom(package/.Tile)");
        NativeTileCatalogTest.queryAndWait(l.mQsHost); l.getTileRecord("bt"); return l;
    }
    private Bundle request(NativeGrid grid, String action, String spec) {
        Bundle b = new Bundle(); b.putString("action", action); b.putString("spec", spec); b.putString("base", grid.source.base);
        b.putLong("page", 8); b.putInt("x", 2); b.putInt("y", 1); return b;
    }
    @Test public void appTileIsCreatedByNativeLauncherAtTheRequestedCellAndSaved() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); Launcher l = setup(f); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        Runnable job = NativeTileActions.prepare(grid, request(grid, "add", "custom(package/.Tile)"));
        assertEquals(1, l.prepared); assertEquals(1, l.added); assertNull(LauncherInfoManager.received);
        QsLayout.Item item = new NativeGrid(f.panel, f.workspace).source.item("t:custom(package/.Tile)");
        assertEquals(2, item.x()); assertEquals(1, item.y()); assertEquals(1, item.w()); assertEquals(1, item.h());
        job.run(); assertEquals(2, LauncherInfoManager.received.size()); assertEquals(1, l.refreshes);
    }
    @Test public void bothHolesOfTheSavedLAcceptRealTileAddAndRemainFreeAfterReload() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); setup(f); NativeForms.supported=true; NativeForms.user(f.context,0);
        f.info.spanX=3; f.info.spanY=2; NativeGridTest.Lp lp=(NativeGridTest.Lp)f.tile.getLayoutParams(); lp.cellHSpan=3; lp.cellVSpan=2;
        NativeForms.save(f.context,0,QsLayoutTest.source(new QsLayout.Item("t:bt","",8,0,0,3,2,true,true,ShapeOccupancyTest.fourCells())));
        NativeGrid grid=new NativeGrid(f.panel,f.workspace); Bundle a=request(grid,"add","wifi"); a.putInt("x",0); a.putInt("y",0); NativeTileActions.prepare(grid,a).run();
        grid=new NativeGrid(f.panel,f.workspace); Bundle b=request(grid,"add","custom(package/.Tile)"); b.putInt("x",1); b.putInt("y",0); NativeTileActions.prepare(grid,b).run();
        NativeForms.user(f.context,1); NativeForms.user(f.context,0); QsLayout reloaded=new NativeGrid(f.panel,f.workspace).source;
        assertEquals(3,reloaded.items.size()); reloaded.validateAgainst(reloaded);
        assertEquals(4,reloaded.item("t:bt").cellCount()); assertEquals(0,reloaded.item("t:wifi").x()); assertEquals(1,reloaded.item("t:custom(package/.Tile)").x());
        assertEquals(3,LauncherInfoManager.received.size()); assertEquals(3,reloaded.item("t:bt").w());
    }
    @Test public void removalUsesNativeClosedPanelPathAndLeavesAHole() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); Launcher l = setup(f); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        NativeTileActions.prepare(grid, request(grid, "remove", "bt")).run();
        assertEquals(1, l.removed); assertEquals(0, f.page.container.getChildCount()); assertTrue(LauncherInfoManager.received.isEmpty());
    }
    @Test public void closedStockEditorDoesNotPreventRemoval() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); Launcher l=setup(f); l.rejectEditorDelete=true;
        NativeGrid grid=new NativeGrid(f.panel,f.workspace); NativeTileActions.prepare(grid,request(grid,"remove","bt")).run();
        assertNull(l.getTileRecord("bt")); assertNull(new NativeGrid(f.panel,f.workspace).source.item("t:bt"));
        assertEquals(java.util.List.of("bt"),l.mInfoManager.removedSpecs);
    }
    @Test public void pendingLayoutAndCornerMetadataAreSavedWithRemovalInOneSnapshot() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); Launcher l=setup(f);
        NativeGrid first=new NativeGrid(f.panel,f.workspace); NativeTileActions.prepare(first,request(first,"add","wifi")).run();
        NativeGrid grid=new NativeGrid(f.panel,f.workspace);
        QsLayout draft=grid.source.edit("t:wifi",1,0,2,3,new TileForm(2,1,2)); assertNotNull(draft);
        Bundle remove=request(grid,"remove","bt"); remove.putString("layout",draft.json()); NativeForms.user(f.context,0);
        NativeTileActions.prepare(grid,remove).run();
        assertEquals(1,LauncherInfoManager.received.size()); com.android.launcher3.QSTileItemInfo saved=LauncherInfoManager.received.get(0);
        assertEquals("wifi",saved.getTileSpec()); assertEquals(1,saved.cellX); assertEquals(0,saved.cellY); assertEquals(2,saved.spanX); assertEquals(3,saved.spanY);
        assertEquals(new TileForm(2,1,2),NativeForms.form("t:wifi",2,3)); assertNull(l.getTileRecord("bt"));
    }
    @Test public void fakeNativeSuccessCannotBeAcknowledgedOrWritten() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); Launcher l=setup(f); l.pretendDelete=true;
        NativeGrid grid=new NativeGrid(f.panel,f.workspace);
        assertThrows(IllegalStateException.class,()->NativeTileActions.prepare(grid,request(grid,"remove","bt")));
        assertNull(LauncherInfoManager.received); assertNotNull(l.getTileRecord("bt"));
    }
    @Test public void stalePendingLayoutIsRejectedBeforeRemovingTheNativeTile() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); Launcher l=setup(f); NativeGrid grid=new NativeGrid(f.panel,f.workspace);
        Bundle remove=request(grid,"remove","bt"); remove.putString("layout",grid.source.edit("t:bt",0,0,1,3).json().replace(grid.source.base,"outdated"));
        assertThrows(IllegalStateException.class,()->NativeTileActions.prepare(grid,remove)); assertEquals(0,l.removed); assertNotNull(l.getTileRecord("bt"));
    }
    @Test public void staleOccupiedDuplicateAndUnavailableRequestsCannotAddTiles() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); Launcher l = setup(f); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        Bundle stale = request(grid, "add", "wifi"); stale.putString("base", "old");
        assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(grid, stale));
        Bundle occupied = request(grid, "add", "wifi"); occupied.putInt("x", 0); occupied.putInt("y", 0);
        assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(grid, occupied));
        assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(grid, request(grid, "add", "unknown")));
        l.unavailable = true; assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(grid, request(grid, "add", "wifi")));
        assertEquals(0, l.added); assertEquals(1, f.page.container.getChildCount());
    }
    @Test public void nativeMinimumIsHonoredAndNoFalseSaveIsReported() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); Launcher l = setup(f); l.minimum = 1; NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(grid, request(grid, "remove", "bt")));
        assertEquals(0, l.removed); assertEquals(1, f.page.container.getChildCount());
    }
    @Test public void anotherPageCanBeCreatedButNotMultipleTrailingEmptyPages() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); setup(f); NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        NativeTileActions.prepare(grid, request(grid, "page", "")).run();
        assertEquals(2, f.workspace.getChildCount()); NativeGrid fresh = new NativeGrid(f.panel, f.workspace);
        assertThrows(IllegalStateException.class, () -> NativeTileActions.prepare(fresh, request(fresh, "page", "")));
    }
}
