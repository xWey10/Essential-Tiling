package com.android.systemui.qs.customize;

import android.content.Context;
import com.android.systemui.qs.QSHost;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.plugins.qs.QSTile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Test double only; production uses firmware resources, factory and native package discovery. */
public class TileQueryHelper {
    public static final List<TileInfo> source = new ArrayList<>();
    public static final List<FakeTile> created = new ArrayList<>();
    public static boolean stallPackages;
    public final List<TileStateListener> listeners = new ArrayList<>();
    private final List<TileInfo> collected = new ArrayList<>();
    private final Executor main, bg;
    public TileQueryHelper(Context c, UserTracker user, Executor main, Executor bg) { this.main=main; this.bg=bg; }
    public interface TileStateListener { void onTilesChanged(List<TileInfo> tiles); }
    public void addListener(TileStateListener listener) { listeners.add(listener); }
    public void queryTiles(QSHost host) { throw new AssertionError("Unbounded readiness barrier must not be used"); }
    private void addTile(String spec, CharSequence app, QSTile.State state, boolean system) { collected.add(new TileInfo(spec, state)); }
    private void addPackageTiles(QSHost host) {
        if (stallPackages) return;
        bg.execute(() -> { for (TileInfo info : source) if (info.spec.startsWith("custom(")) collected.add(info);
            main.execute(() -> { for (TileStateListener listener : listeners) listener.onTilesChanged(new ArrayList<>(collected)); }); });
    }
    public static Context context() {
        Context app = org.robolectric.RuntimeEnvironment.getApplication();
        android.content.res.Resources resources = new android.content.res.Resources(app.getAssets(), app.getResources().getDisplayMetrics(), app.getResources().getConfiguration()) {
            @Override public int getIdentifier(String name, String type, String pkg) { return name.equals("nt_quick_settings_tiles_stock") ? 0x123456 : super.getIdentifier(name,type,pkg); }
            @Override public String getString(int id) { if(id==0x123456) return source.stream().filter(i->!i.spec.startsWith("custom(")).map(i->i.spec).collect(java.util.stream.Collectors.joining(",")); return super.getString(id); }
        };
        return new android.content.ContextWrapper(app) { @Override public android.content.res.Resources getResources() { return resources; } };
    }
    public static class State extends QSTile.State { }
    public static class TileInfo {
        public String spec; public QSTile.State state;
        public TileInfo(String spec, String label, String app) { this.spec=spec; state=new State(); state.label=label; state.secondaryLabel=app; }
        TileInfo(String spec, QSTile.State state) { this.spec=spec; this.state=state; }
    }
    public static class FakeTile implements QSTile {
        public final TileInfo info; public boolean destroyed, listening;
        FakeTile(TileInfo info) { this.info=info; }
        public boolean isAvailable() { return true; }
        public boolean isTileReady() { return false; } // Reproduces the OEM collector's forever-loading case.
        public void setListening(Object owner, boolean enabled) { listening=enabled; }
        public void refreshState() { }
        public void destroy() { destroyed=true; }
        public QSTile.State getState() { return info.state; }
        public CharSequence getTileLabel() { return info.state.label; }
        public String getTileSpec() { return info.spec; }
    }
    public static QSTile create(String spec) {
        for(TileInfo info:source) if(info.spec.equals(spec)) { FakeTile tile=new FakeTile(info); created.add(tile); return tile; }
        return null;
    }
}
