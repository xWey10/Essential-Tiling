package moe.xWey10.tiling;

import com.android.systemui.qs.customize.TileQueryHelper;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class NativeTileCatalogTest {
    static void seed(String... specs) {
        NativeTileCatalog.type = TileQueryHelper.class;
        NativeTileCatalog.dependencies = new Object[]{TileQueryHelper.context(), new com.android.systemui.settings.UserTracker() { }, (Executor) Runnable::run, (Executor) Runnable::run};
        TileQueryHelper.source.clear(); TileQueryHelper.created.clear(); TileQueryHelper.stallPackages = false;
        for (String spec : specs) TileQueryHelper.source.add(new TileQueryHelper.TileInfo(spec, "Label " + spec, spec.startsWith("custom(") ? "Application" : ""));
    }
    @After public void clear() { NativeTileCatalog.dependencies = null; NativeTileCatalog.type = null; TileQueryHelper.source.clear(); TileQueryHelper.stallPackages = false; }
    @Test public void stockHelperReturnsEverySystemAndAppTileWithoutReplacingItsListeners() throws Exception {
        ArrayList<String> specs = new ArrayList<>(); specs.add("wifi"); specs.add("bt");
        for (int n = 0; n < 350; n++) specs.add("custom(app" + n + "/.TileService)"); seed(specs.toArray(new String[0]));
        TileQueryHelper original = new TileQueryHelper(RuntimeEnvironment.getApplication(), null, Runnable::run, Runnable::run);
        int[] originalCalls = {0}; original.addListener(rows -> originalCalls[0]++);
        java.util.List<ArrayList<String>> results = new ArrayList<>();
        results.add(queryAndWait(new com.android.launcher3.Launcher().mQsHost));
        assertEquals(352, results.get(0).size()); assertEquals(0, originalCalls[0]); assertEquals(1, original.listeners.size());
        assertTrue(NativeTileCatalog.contains("custom(app349/.TileService)", 0));
        assertFalse(NativeTileCatalog.contains("custom(app349/.TileService)", 1));
        assertTrue(results.get(0).get(351).contains("Application"));
        assertFalse(TileQueryHelper.created.isEmpty());
        for (TileQueryHelper.FakeTile tile : TileQueryHelper.created) { assertTrue(tile.destroyed); assertFalse(tile.listening); assertFalse(tile.isTileReady()); }
    }
    public static final class BootstrapHost implements com.android.systemui.qs.QSHost {
        public final Interactor interactor = new Interactor();
        public int getUserId() { return 0; }
        public void refreshTiles() { }
        public android.content.Context getContext() { return TileQueryHelper.context(); }
        public com.android.systemui.plugins.qs.QSTile createTile(String spec) { return TileQueryHelper.create(spec); }
    }
    public static final class Interactor { public final com.android.systemui.settings.UserTracker userTracker = new com.android.systemui.settings.UserTracker() { }; }
    @Test public void hostSuppliesRealDependenciesWithoutOpeningTheStockEditorFirst() throws Exception {
        seed("wifi", "custom(app/.Tile)"); NativeTileCatalog.type = null; NativeTileCatalog.dependencies = null;
        assertEquals(2, queryAndWait(new BootstrapHost()).size());
    }
    @Test public void packageTimeoutIsBoundedAndLateRepliesCannotReplaceTheError() throws Exception {
        seed("wifi", "custom(app/.Tile)"); TileQueryHelper.stallPackages = true;
        java.util.List<Exception> errors = new ArrayList<>();
        NativeTileCatalog.query(new com.android.launcher3.Launcher().mQsHost, rows -> fail("Must report the timeout"), errors::add);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(8100));
        assertEquals(1, errors.size()); assertTrue(errors.get(0).getMessage().contains("Повторить"));
        for (TileQueryHelper.FakeTile tile : TileQueryHelper.created) { assertTrue(tile.destroyed); assertFalse(tile.listening); }
    }
    static ArrayList<String> queryAndWait(Object host) throws Exception {
        java.util.concurrent.atomic.AtomicReference<ArrayList<String>> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> error = new java.util.concurrent.atomic.AtomicReference<>();
        NativeTileCatalog.query(host, result::set, error::set);
        for (int n=0; n<250 && result.get()==null && error.get()==null; n++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10));
            Thread.sleep(4);
        }
        if (error.get()!=null) throw error.get(); assertNotNull("Catalogue must finish despite unready stock tiles",result.get()); return result.get();
    }
    @Test public void missingHelperReportsUnavailableInsteadOfReturningAFakeCatalogue() {
        NativeTileCatalog.dependencies = null; NativeTileCatalog.type = null;
        assertThrows(ReflectiveOperationException.class, () -> NativeTileCatalog.query(new com.android.launcher3.Launcher().mQsHost, rows -> fail(), e -> fail()));
    }
}
