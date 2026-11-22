package moe.xWey10.tiling;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs in SystemUI: UI rendering on main, IPC and PNG compression off main. */
final class NativePreviewServer {
    interface Renderer { Frame render(Config config, int page, String layout, String focus) throws Exception; }
    static final class Frame {
        final Bitmap bitmap;
        final int page, pages;
        final Bundle metadata;
        Frame(Bitmap bitmap, int page, int pages) { this(bitmap, page, pages, Bundle.EMPTY); }
        Frame(Bitmap bitmap, int page, int pages, Bundle metadata) {
            this.bitmap = bitmap; this.page = page; this.pages = pages; this.metadata = metadata;
        }
    }
    private final Context context;
    private final Renderer renderer;
    private final Runnable prepare, finish;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EssentialTiling-preview-server"); t.setDaemon(true); return t;
    });
    private volatile long lastRequest;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { check(); }
    };
    NativePreviewServer(Context context, Renderer renderer, Runnable prepare, Runnable finish) {
        this.context = context; this.renderer = renderer; this.prepare = prepare; this.finish = finish;
        worker.execute(() -> {
            context.getContentResolver().registerContentObserver(PreviewProtocol.REQUEST, false, observer);
            check();
        });
    }
    void sourceChanged() {
        worker.execute(() -> {
            try { context.getContentResolver().call(SettingsProvider.URI, "preview.source", null, Bundle.EMPTY); }
            catch (RuntimeException ignored) { }
        });
    }
    private void check() {
        worker.execute(() -> {
            try {
                Bundle request = context.getContentResolver().call(SettingsProvider.URI, "preview.pending", null, null);
                if (request == null) return;
                long id = request.getLong("id");
                if (id == lastRequest) return;
                lastRequest = id;
                main.post(() -> {
                    if (id != lastRequest) return;
                    boolean refresh = request.getBoolean("refreshStates", true);
                    if (refresh) {
                        try { prepare.run(); }
                        catch (RuntimeException e) { finish.run(); }
                    }
                    // Native tile controllers deliver refreshed state on their own handlers.
                    main.postDelayed(() -> { if (id == lastRequest) render(id, request); }, refresh ? 150 : 0);
                });
            } catch (RuntimeException ignored) { }
        });
    }
    private void render(long id, Bundle request) {
        Bundle result = new Bundle(); result.putLong("id", id);
        Frame frame;
        try {
            frame = renderer.render(PreviewProtocol.config(request), request.getInt("page"), request.getString("layout", ""), request.getString("focus", ""));
            result.putAll(frame.metadata);
            result.putInt("page", frame.page); result.putInt("pages", frame.pages);
            result.putLong("time", System.currentTimeMillis());
            result.putString("imageFormat", PreviewBitmapIO.FORMAT);
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            android.util.Log.e("EssentialTiling", "Native QS preview", e);
            result.putString("error", e instanceof IllegalStateException ? e.getMessage()
                    : "Не удалось отрисовать шторку SystemUI: " + e.getClass().getSimpleName());
            worker.execute(() -> complete(result));
            return;
        } finally { finish.run(); }
        worker.execute(() -> {
            try (OutputStream output = context.getContentResolver().openOutputStream(PreviewProtocol.image(id), "w")) {
                if (output == null) throw new IllegalStateException("Missing preview output");
                PreviewBitmapIO.write(frame.bitmap, output);
            } catch (Exception e) { result.putString("error", "Кадр устарел. Нажмите «Обновить»."); }
            finally { frame.bitmap.recycle(); }
            complete(result);
        });
    }
    private void complete(Bundle result) {
        try { context.getContentResolver().call(SettingsProvider.URI, "preview.complete", null, result); }
        catch (RuntimeException ignored) { }
    }
}
