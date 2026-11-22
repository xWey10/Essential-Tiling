package moe.xWey10.tiling;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Process;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lives only in SystemUI. The sole supported action is ending its own process. */
final class RestartServer {
    private final Context context;
    private final String session = UUID.randomUUID().toString();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EssentialTiling-restart-server"); t.setDaemon(true); return t;
    });
    private final ContentObserver observer = new ContentObserver(null) {
        @Override public void onChange(boolean selfChange) { check(); }
    };

    RestartServer(Context context) {
        this.context = context;
        worker.execute(() -> {
            try {
                context.getContentResolver().registerContentObserver(RestartControl.REQUEST, false, observer);
                Bundle hello = new Bundle(); hello.putString("session", session);
                hello.putInt("protocol", RestartControl.PROTOCOL);
                context.getContentResolver().call(SettingsProvider.URI, "restart.register", null, hello);
            } catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Restart registration", e); }
        });
    }

    private void check() {
        if (queued.get()) return;
        worker.execute(() -> {
            if (queued.get()) return;
            try {
                Bundle request = new Bundle(); request.putString("session", session);
                Bundle claimed = context.getContentResolver().call(SettingsProvider.URI, "restart.claim", null, request);
                if (claimed == null || !session.equals(claimed.getString("session"))
                        || !"accepted".equals(claimed.getString("state")) || !queued.compareAndSet(false, true)) return;
                // Return the acknowledgement first; ending our own process needs no UI-thread work.
                worker.schedule(() -> Process.killProcess(Process.myPid()), 250, TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) { android.util.Log.e("EssentialTiling", "Restart request", e); }
        });
    }
}
