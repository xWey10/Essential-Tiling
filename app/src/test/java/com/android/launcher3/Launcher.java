package com.android.launcher3;
import android.content.Context;
import com.android.systemui.qs.QSHost;
public class Launcher {
    public static Launcher instance;
    public LauncherInfoManager mInfoManager = new LauncherInfoManager();
    public int currentUserId, refreshes;
    public QSHost mQsHost = new QSHost() {
        public int getUserId() { return 0; }
        public android.content.Context getContext() { return com.android.systemui.qs.customize.TileQueryHelper.context(); }
        public com.android.systemui.plugins.qs.QSTile createTile(String spec) { return com.android.systemui.qs.customize.TileQueryHelper.create(spec); }
        public void refreshTiles() { refreshes++; }
    };
    public static Launcher getLauncher(Context c) { return instance; }
    private void resetQSTileInfos(int user,java.util.List<QSTileItemInfo> raw) { }
    public android.content.Context getContext() { return workspace.getContext(); }
    public boolean isInteractable() { return true; }
    public moe.xWey10.tiling.NativeGridTest.Workspace workspace;
    public int prepared, added, removed, minimum;
    public boolean unavailable, rejectEditorDelete, pretendDelete;
    public final java.util.ArrayList<com.android.systemui.qs.QSPanelControllerBase.TileRecord> mRecords=new java.util.ArrayList<>();
    public int getMinNumTiles() { return minimum; }
    private final java.util.Map<String, com.android.systemui.qs.QSPanelControllerBase.TileRecord> records = new java.util.HashMap<>();
    public com.android.systemui.qs.QSPanelControllerBase.TileRecord getTileRecord(String spec) {
        if (records.containsKey(spec)) return records.get(spec);
        for (int n = 0; n < workspace.getChildCount(); n++) {
            android.view.ViewGroup group = ((moe.xWey10.tiling.NativeGridTest.Cell) workspace.getChildAt(n)).getShortcutsAndWidgets();
            for (int k = 0; k < group.getChildCount(); k++) {
                android.view.View v = group.getChildAt(k);
                if (((QSTileItemInfo) v.getTag()).getTileSpec().equals(spec)) {
                    com.android.systemui.qs.QSPanelControllerBase.TileRecord record = new com.android.systemui.qs.QSPanelControllerBase.TileRecord(v); records.put(spec, record); mRecords.add(record); return record;
                }
            }
        }
        return null;
    }
    public boolean prepareAddTileByReset(QSTileItemInfo info) {
        if (unavailable) return false;
        prepared++; AddedTile tile = new AddedTile(workspace.getContext()); tile.setTag(info);
        com.android.systemui.qs.QSPanelControllerBase.TileRecord record=new com.android.systemui.qs.QSPanelControllerBase.TileRecord(tile); records.put(info.getTileSpec(),record); mRecords.add(record); return true;
    }
    public boolean addTileByReset(QSTileItemInfo info) {
        for (int n = 0; n < workspace.getChildCount(); n++) if (workspace.getScreenIdForPageIndex(n) == info.screenId) {
            ((moe.xWey10.tiling.NativeGridTest.Cell) workspace.getChildAt(n)).getShortcutsAndWidgets().addView(records.get(info.getTileSpec()).tileView, new Lp(info));
            mInfoManager.rawTileInfoList.add(info); added++; return true;
        }
        return false;
    }
    public boolean deleteTileByUser(com.android.systemui.qs.QSPanelControllerBase.TileRecord record, boolean save) {
        if(rejectEditorDelete) throw new IllegalStateException("Stock editor is closed");
        if (mInfoManager.rawTileInfoList.size() <= minimum) return false;
        QSTileItemInfo info = (QSTileItemInfo) record.tileView.getTag();
        ((android.view.ViewGroup) record.tileView.getParent()).removeView(record.tileView);
        mInfoManager.rawTileInfoList.removeIf(old -> old.getTileSpec().equals(info.getTileSpec())); records.remove(info.getTileSpec()); removed++; return true;
    }
    public boolean deleteTile(String spec, boolean save) {
        com.android.systemui.qs.QSPanelControllerBase.TileRecord record=getTileRecord(spec); if(record==null) return false;
        if(pretendDelete) return true;
        QSTileItemInfo info=(QSTileItemInfo)record.tileView.getTag();
        if(record.tileView.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)record.tileView.getParent()).removeView(record.tileView);
        mInfoManager.rawTileInfoList.removeIf(old->old.getTileSpec().equals(spec)); records.remove(spec); mRecords.remove(record); removed++; return true;
    }
    public static class AddedTile extends android.widget.FrameLayout {
        AddedTile(Context c) { super(c); }
        public void applySize(int x, int y) { }
    }
    public static class Lp extends android.view.ViewGroup.LayoutParams {
        public int cellX, cellY, cellHSpan, cellVSpan;
        Lp(QSTileItemInfo i) { super(88,88); cellX=i.cellX; cellY=i.cellY; cellHSpan=i.spanX; cellVSpan=i.spanY; }
    }
}
