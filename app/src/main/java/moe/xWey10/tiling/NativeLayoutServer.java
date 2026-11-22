package moe.xWey10.tiling;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses the native saver; acknowledges only after both Settings.Secure values are read back. */
final class NativeLayoutServer {
    interface Prepare { Runnable save(String layout) throws Exception; }
    private final Context context;
    private final Prepare prepare;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EssentialTiling-layout"); t.setDaemon(true); return t;
    });
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { check(); }
    };
    NativeLayoutServer(Context context, Prepare prepare) {
        this.context = context; this.prepare = prepare;
        worker.execute(() -> { context.getContentResolver().registerContentObserver(LayoutControl.REQUEST, false, observer); check(); });
    }
    private void check() {
        worker.execute(() -> {
            try {
                Bundle request = context.getContentResolver().call(SettingsProvider.URI, "layout.claim", null, null);
                if (request == null) return;
                main.post(() -> {
                    try {
                        Runnable job = prepare.save(request.getString("layout"));
                        worker.execute(() -> {
                            String error = "";
                            try { job.run(); }
                            catch (RuntimeException | LinkageError e) {
                                android.util.Log.e("EssentialTiling", "Native layout save", e);
                                error = e instanceof IllegalStateException ? e.getMessage() : "Не удалось сохранить раскладку в SystemUI.";
                            }
                            complete(request.getLong("id"), error);
                        });
                    } catch (Exception | LinkageError e) {
                        android.util.Log.e("EssentialTiling", "Prepare native layout", e);
                        String error = e instanceof IllegalStateException ? e.getMessage() : "Сохранение раскладки недоступно в этой версии SystemUI.";
                        worker.execute(() -> complete(request.getLong("id"), error));
                    }
                });
            } catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Layout bridge", e); }
        });
    }
    private void complete(long id, String error) {
        Bundle b = new Bundle(); b.putLong("id", id); b.putString("error", error == null ? "Ошибка сохранения раскладки." : error);
        try { context.getContentResolver().call(SettingsProvider.URI, "layout.complete", null, b); }
        catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Layout acknowledgement", e); }
    }
}
