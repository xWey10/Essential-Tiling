package com.android.launcher3;
import android.content.Context;
import android.provider.Settings;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.pipeline.data.repository.UserTileSpecRepository;
import com.nothing.systemui.qs.QSTileHostEx;
import java.util.ArrayList;
import java.util.List;
public class LauncherInfoManager {
    public final List<QSTileItemInfo> rawTileInfoList = new ArrayList<>();
    private final Object rawTileListKey = new Object();
    public static boolean discardWrites;
    public final java.util.List<String> removedSpecs=new java.util.ArrayList<>();
    public void onTileRemoved(String spec) { removedSpecs.add(spec); }
    public static List<QSTileItemInfo> received;
    public void modifyQSTileItemInfo(ItemInfo item, long page, int x, int y, int w, int h, boolean save) {
        item.screenId = page; item.cellX = x; item.cellY = y; item.spanX = w; item.spanY = h;
        QSTileItemInfo info = (QSTileItemInfo) item;
        rawTileInfoList.removeIf(old -> old.getTileSpec().equals(info.getTileSpec())); rawTileInfoList.add(info);
    }
    public static class SaveCurrentQSTilesRunnable implements Runnable {
        final Context context; final QSHost host; final List<QSTileItemInfo> infos; final boolean waiting;
        public SaveCurrentQSTilesRunnable(Context c, QSHost host, List<QSTileItemInfo> infos, boolean waiting, Integer user) {
            context = c; this.host = host; this.infos = infos; this.waiting = waiting;
        }
        public void run() {
            if (waiting) return; received = infos;
            if (!discardWrites) {
                Settings.Secure.putString(context.getContentResolver(), UserTileSpecRepository.NT_SETTING, QSTileHostEx.tileItemInfoToNTString(infos));
                Settings.Secure.putString(context.getContentResolver(), "sysui_qs_tiles", QSTileHostEx.tileItemInfoToString(infos));
            }
            host.refreshTiles();
        }
    }
}
