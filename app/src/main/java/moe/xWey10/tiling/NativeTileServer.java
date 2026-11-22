package moe.xWey10.tiling;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeTileServer {
    interface Ready { NativeGrid grid() throws Exception; }
    private final Context context; private final Ready ready;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "EssentialTiling-tiles"); t.setDaemon(true); return t; });
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean changed) { check(); }
    };
    NativeTileServer(Context context, Ready ready) {
        this.context = context; this.ready = ready;
        context.getContentResolver().registerContentObserver(TileControl.REQUEST, false, observer); check();
    }
    private void check() {
        worker.execute(() -> {
            try {
                Bundle request = context.getContentResolver().call(SettingsProvider.URI, "tiles.claim", null, null);
                if (request == null) return;
                main.post(() -> {
                    try {
                        if(android.os.SystemClock.elapsedRealtime()>=request.getLong("deadline",Long.MAX_VALUE)) throw new IllegalStateException("Запрос истёк. Обновите предпросмотр перед повтором.");
                        NativeGrid grid = ready.grid();
                        if ("catalog".equals(request.getString("action"))) {
                            NativeTileCatalog.query(NativeTileActions.host(NativeTileActions.launcher(grid)), rows -> publish(request.getLong("id"), rows), e -> failure(request.getLong("id"), e));
                        } else {
                            Runnable job = NativeTileActions.prepare(grid, request);
                            worker.execute(() -> {
                                try { job.run(); complete(request.getLong("id"), ""); }
                                catch (Exception | LinkageError e) { failure(request.getLong("id"), e); }
                            });
                        }
                    } catch (Exception | LinkageError e) { failure(request.getLong("id"), e); }
                });
            } catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Tile bridge", e); }
        });
    }
    private void publish(long id, ArrayList<String> rows) {
        worker.execute(() -> {
            try {
                for (int offset = 0; offset < rows.size(); ) {
                    int end = TileControl.pageEnd(rows, offset);
                    Bundle b = new Bundle(); b.putLong("id", id);
                    b.putStringArrayList("rows", new ArrayList<>(rows.subList(offset, end)));
                    context.getContentResolver().call(SettingsProvider.URI, "tiles.publish", null, b);
                    offset = end;
                }
                complete(id, "");
            } catch (RuntimeException e) { failure(id, e); }
        });
    }
    private void failure(long id, Throwable e) {
        android.util.Log.e("EssentialTiling", "Native tile operation", e);
        worker.execute(() -> complete(id, e instanceof IllegalStateException ? e.getMessage() : "Операция с плитками SystemUI недоступна. Обновите предпросмотр."));
    }
    private void complete(long id, String error) {
        Bundle b = new Bundle(); b.putLong("id", id); b.putString("error", error == null ? "Ошибка SystemUI." : error);
        try { context.getContentResolver().call(SettingsProvider.URI, "tiles.complete", null, b); }
        catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Tile acknowledgement", e); }
    }
}
