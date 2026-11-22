package moe.xWey10.tiling;

import android.provider.Settings;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherInfoManager;
import com.android.launcher3.QSTileItemInfo;
import com.android.systemui.qs.pipeline.data.repository.UserTileSpecRepository;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "port")
public class NativeLayoutSaveTest {
    @After public void clean() { Launcher.instance = null; LauncherInfoManager.received = null; LauncherInfoManager.discardWrites = false; }
    private Launcher launcher(NativeGridTest.Fixture f) {
        Launcher l = new Launcher(); Launcher.instance = l; l.mInfoManager.rawTileInfoList.add(f.info);
        QSTileItemInfo other = new QSTileItemInfo("custom(package/.Tile)"); other.screenId = -1; l.mInfoManager.rawTileInfoList.add(other); return l;
    }
    @Test public void usesNativeSaverOnCopiesAndVerifiesBothDurableValues() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); Launcher l = launcher(f);
        NativeGrid grid = new NativeGrid(f.panel, f.workspace); QsLayout draft = grid.source.edit("t:bt", 1, 1, 2, 2);
        Runnable save = grid.saveJob(draft); assertNull(LauncherInfoManager.received); save.run();
        assertEquals(1, l.refreshes); assertEquals(2, LauncherInfoManager.received.size());
        QSTileItemInfo saved = LauncherInfoManager.received.get(0);
        assertEquals(1, saved.cellX); assertEquals(2, saved.spanY); assertNotSame(f.info, saved);
        assertEquals(0, f.info.cellX); assertEquals(1, f.info.spanY);
        assertEquals("custom(package/.Tile)", LauncherInfoManager.received.get(1).getTileSpec());
        assertTrue(Settings.Secure.getString(f.context.getContentResolver(), UserTileSpecRepository.NT_SETTING).contains("bt:8:1:1:2:2"));
    }
    @Test public void customSpansUseTheNativeSerializerAndTheCutIsSavedAlongsideThem() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); launcher(f); NativeForms.user(f.context,0);
        NativeGrid grid=new NativeGrid(f.panel,f.workspace);
        QsLayout draft=grid.source.edit("t:bt",0,0,2,3,new TileForm(2,1,2));
        grid.saveJob(draft).run();
        QSTileItemInfo saved=LauncherInfoManager.received.get(0); assertEquals(2,saved.spanX); assertEquals(3,saved.spanY);
        assertEquals(new TileForm(2,1,2),NativeForms.form("t:bt",2,3));
        NativeForms.user(f.context,1); NativeForms.user(f.context,0); assertEquals(new TileForm(2,1,2),NativeForms.form("t:bt",2,3));
    }
    @Test public void missingShapeHooksRejectCustomWritesBeforeChangingNativeSettings() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture(); launcher(f); NativeGrid grid=new NativeGrid(f.panel,f.workspace);
        NativeForms.supported=false;
        assertThrows(IllegalStateException.class,()->grid.saveJob(grid.source.edit("t:bt",0,0,1,3))); assertNull(LauncherInfoManager.received);
    }
    @Test public void failedWriteNeverProducesSaveAcknowledgement() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); launcher(f);
        Settings.Secure.putString(f.context.getContentResolver(), UserTileSpecRepository.NT_SETTING, "previous");
        LauncherInfoManager.discardWrites = true; NativeGrid grid = new NativeGrid(f.panel, f.workspace);
        Runnable save = grid.saveJob(grid.source.edit("t:bt", 1, 0, 1, 1));
        assertThrows(IllegalStateException.class, save::run); assertEquals(0, f.info.cellX);
    }
    @Test public void nativeRawListChangedSincePreviewIsRejectedBeforeWriting() throws Exception {
        NativeGridTest.Fixture f = new NativeGridTest.Fixture(); launcher(f);
        NativeGrid grid = new NativeGrid(f.panel, f.workspace); QsLayout draft = grid.source.edit("t:bt", 1, 0, 1, 1);
        f.info.cellY = 2;
        assertThrows(IllegalStateException.class, () -> grid.saveJob(draft)); assertNull(LauncherInfoManager.received);
    }
}
