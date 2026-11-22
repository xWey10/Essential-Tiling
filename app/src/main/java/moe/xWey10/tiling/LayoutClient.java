package moe.xWey10.tiling;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LayoutClient {
  interface Listener {
    void finished(boolean success, String error);
  }

  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "EssentialTiling-layout-client");
            t.setDaemon(true);
            return t;
          });
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile long generation;

  LayoutClient(Context context) {
    this.context = context.getApplicationContext();
  }

  void stop() {
    generation++;
    main.removeCallbacksAndMessages(null);
  }

  void save(String layout, Listener listener) {
    stop();
    long token = generation;
    long started = SystemClock.elapsedRealtime();
    main.postDelayed(
        () ->
            finish(
                token,
                listener,
                false,
                "Сохранение не подтверждено. Обновите предпросмотр перед повторной попыткой."),
        15_000);
    WORKER.execute(
        () -> {
          if (generation != token) return;
          try {
            Bundle b = new Bundle();
            b.putString("layout", layout);
            Bundle request =
                context.getContentResolver().call(SettingsProvider.URI, "layout.request", null, b);
            if (request == null) throw new IllegalStateException("Нет ответа приложения настроек.");
            poll(request.getLong("id"), token, started, listener);
          } catch (RuntimeException e) {
            deliver(token, listener, false, "Не удалось отправить раскладку в SystemUI.");
          }
        });
  }

  private void poll(long id, long token, long started, Listener listener) {
    WORKER.execute(
        () -> {
          if (generation != token) return;
          try {
            Bundle b = new Bundle();
            b.putLong("id", id);
            Bundle result =
                context.getContentResolver().call(SettingsProvider.URI, "layout.status", null, b);
            String state = result == null ? "expired" : result.getString("state", "expired");
            if (state.equals("saved")) {
              deliver(token, listener, true, "");
              return;
            }
            if (state.equals("error") || state.equals("expired")) {
              deliver(
                  token,
                  listener,
                  false,
                  result == null
                      ? "Нет ответа SystemUI."
                      : result.getString("error", "Раскладка не сохранена."));
              return;
            }
            if (SystemClock.elapsedRealtime() - started > 15_000) {
              deliver(
                  token,
                  listener,
                  false,
                  "Сохранение не подтверждено. Обновите предпросмотр перед повторной попыткой.");
              return;
            }
            main.postDelayed(
                () -> {
                  if (generation == token) poll(id, token, started, listener);
                },
                200);
          } catch (RuntimeException e) {
            deliver(token, listener, false, "Не удалось проверить сохранение раскладки.");
          }
        });
  }

  private void finish(long token, Listener listener, boolean success, String error) {
    if (generation != token) return;
    generation++;
    main.removeCallbacksAndMessages(null);
    listener.finished(success, error);
  }

  private void deliver(long token, Listener listener, boolean success, String error) {
    main.post(() -> finish(token, listener, success, error));
  }
}
