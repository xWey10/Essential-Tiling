package moe.xWey10.tiling;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Success means a different SystemUI session registered, not just that an IPC call returned. */
final class RestartClient {
  interface Listener {
    void result(boolean success, String text);
  }

  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "EssentialTiling-restart-client");
            t.setDaemon(true);
            return t;
          });
  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile long generation;

  RestartClient(Context context, Listener listener) {
    this.context = context.getApplicationContext();
    this.listener = listener;
  }

  void start() {
    stop();
    long token = generation;
    long deadline = SystemClock.elapsedRealtime() + 15_000;
    main.postDelayed(
        () ->
            finish(
                token,
                false,
                "Нет подтверждения нового запуска SystemUI. Проверьте модуль в LSPosed."),
        15_000);
    WORKER.execute(
        () -> {
          if (token != generation) return;
          try {
            Bundle accepted =
                context
                    .getContentResolver()
                    .call(SettingsProvider.URI, "restart.request", null, null);
            if (accepted == null || "unavailable".equals(accepted.getString("state"))) {
              complete(
                  token,
                  false,
                  "Перезагрузите телефон один раз, чтобы загрузить обновлённый модуль LSPosed.");
              return;
            }
            String id = accepted.getString("id"), session = accepted.getString("session");
            if (id == null || session == null || session.isEmpty())
              throw new IllegalStateException("Invalid restart response");
            main.post(() -> poll(token, id, session, deadline));
          } catch (RuntimeException e) {
            complete(
                token,
                false,
                "Нет связи с модулем. Проверьте, что Essential Tiling включён в LSPosed для SystemUI.");
          }
        });
  }

  void stop() {
    generation++;
    main.removeCallbacksAndMessages(null);
  }

  private void poll(long token, String id, String session, long deadline) {
    if (token != generation) return;
    WORKER.execute(
        () -> {
          if (token != generation) return;
          try {
            Bundle query = new Bundle();
            query.putString("id", id);
            Bundle status =
                context
                    .getContentResolver()
                    .call(SettingsProvider.URI, "restart.status", null, query);
            if (status != null
                && status.getInt("protocol") == RestartControl.PROTOCOL
                && !session.equals(status.getString("session", session))) {
              complete(token, true, "SystemUI перезапущен. Настройки загружаются.");
              return;
            }
            if (status != null && "expired".equals(status.getString("state"))) {
              complete(
                  token, false, "Модуль не подтвердил перезапуск. Проверьте LSPosed и повторите.");
              return;
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
              complete(
                  token,
                  false,
                  "Нет подтверждения нового запуска SystemUI. Проверьте модуль в LSPosed.");
              return;
            }
            main.postDelayed(() -> poll(token, id, session, deadline), 300);
          } catch (RuntimeException e) {
            complete(token, false, "Связь с модулем потеряна. Проверьте отчёт SystemUI.");
          }
        });
  }

  private void finish(long token, boolean success, String text) {
    if (token != generation) return;
    generation++;
    main.removeCallbacksAndMessages(null);
    listener.result(success, text);
  }

  private void complete(long token, boolean success, String text) {
    main.post(() -> finish(token, success, text));
  }
}
