package moe.xWey10.tiling;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PreviewClient {
  interface Listener {
    void loading();

    void frame(Bitmap bitmap, Bundle metadata);

    void error(String message);
  }

  private record Cached(Bitmap bitmap, Bundle metadata, long time) {}

  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "EssentialTiling-preview-client");
            t.setDaemon(true);
            return t;
          });
  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final LinkedHashMap<Integer, Cached> frames = new LinkedHashMap<>(4, .75f, true);
  private final HashSet<Integer> prefetched = new HashSet<>();
  private final HashSet<String> warmed = new HashSet<>();
  private Config config = Config.DEFAULT;
  private int page, pages = 1, queuedPage;
  private String layout = "", focus = "", source = "";
  private boolean warm, preload, refreshStates;
  private boolean mutation;
  private volatile boolean active;
  private volatile long generation;
  private long requestId, readingId, readingGeneration;
  private boolean reading, readAgain;
  private Runnable timeout;
  private final Runnable request = this::request;
  private final ContentObserver results =
      new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
          readResult();
        }
      };
  private final ContentObserver sources =
      new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
          if (layout.isEmpty()) {
            invalidate();
            schedule(true, true, false, page, 250);
          }
        }
      };

  PreviewClient(Context context, Listener listener) {
    this.context = context.getApplicationContext();
    this.listener = listener;
  }

  void setConfig(Config c) {
    boolean changed = !c.sameValues(config);
    config = c;
    if (changed) {
      invalidate();
      schedule(false, true, false, page, 180);
    }
  }

  void setLayout(String json) {
    // Local edits, including returning to the source layout, use the existing native atlas.
    layout = json == null ? "" : json;
  }

  void setFocus(String id) {
    String next = id == null ? "" : id;
    if (focus.equals(next)) return;
    focus = next;
    if (!next.isEmpty() && !warmed.contains(next)) schedule(true, false, false, page, 0);
  }

  void setFocus(String id, boolean variantsReady) {
    String next = id == null ? "" : id;
    if (variantsReady) {
      focus = next;
      warmed.add(next);
      return;
    }
    if (next.equals(focus) && (requestId != 0 || main.hasCallbacks(request))) return;
    warmed.remove(next);
    focus = "";
    setFocus(next);
  }

  void setPage(int next) {
    page = Math.max(0, next);
    focus = "";
    Cached cached = frames.get(page);
    if (cached != null) listener.frame(cached.bitmap, new Bundle(cached.metadata));
    if (cached != null && SystemClock.uptimeMillis() - cached.time < 5_000) {
      cancelRequest();
      prefetch();
    } else schedule(cached != null, false, false, page, 0);
  }

  void resolvedPage(int page) {
    this.page = Math.max(0, page);
  }

  void refresh() {
    invalidate();
    schedule(false, true, false, page, 0);
  }

  void beginMutation() {
    mutation = true;
    cancelRequest();
    invalidate();
    focus = "";
  }

  void endMutation(String draft) {
    layout = draft == null ? "" : draft;
    mutation = false;
    invalidate();
    focus = "";
    schedule(false, true, false, page, 0);
  }

  void start() {
    if (active) return;
    active = true;
    context.getContentResolver().registerContentObserver(PreviewProtocol.RESULT, false, results);
    context.getContentResolver().registerContentObserver(PreviewProtocol.SOURCE, false, sources);
    Cached cached = frames.get(page);
    if (cached == null) schedule(false, true, false, page, 0);
    else {
      listener.frame(cached.bitmap, new Bundle(cached.metadata));
      if (layout.isEmpty() && SystemClock.uptimeMillis() - cached.time >= 5_000)
        schedule(true, true, false, page, 0);
      else if (!focus.isEmpty() && !warmed.contains(focus)) schedule(true, false, false, page, 0);
      else prefetch();
    }
  }

  void stop() {
    active = false;
    cancelRequest();
    main.removeCallbacksAndMessages(null);
    context.getContentResolver().unregisterContentObserver(results);
    context.getContentResolver().unregisterContentObserver(sources);
  }

  private void invalidate() {
    frames.clear();
    prefetched.clear();
    warmed.clear();
    source = "";
  }

  private void cancelRequest() {
    generation++;
    requestId = 0;
    reading = false;
    readAgain = false;
    main.removeCallbacks(request);
    clearTimeout();
  }

  private void clearTimeout() {
    if (timeout != null) main.removeCallbacks(timeout);
    timeout = null;
  }

  private void failRequest(String message, boolean background, boolean quiet) {
    cancelRequest();
    failed(message, background, quiet);
  }

  private void schedule(
      boolean warming, boolean refresh, boolean background, int target, long delay) {
    if (!active || mutation) return;
    cancelRequest();
    warm = warming;
    preload = background;
    refreshStates = refresh;
    queuedPage = target;
    if (!warming && !background) listener.loading();
    main.postDelayed(request, delay);
  }

  private void request() {
    long token = generation;
    boolean background = preload, quiet = warm;
    Bundle draft = PreviewProtocol.request(config, queuedPage, layout, background ? "" : focus);
    draft.putBoolean("refreshStates", refreshStates);
    // The UI deadline covers the queue and provider call, not just the result transfer.
    timeout =
        () -> {
          if (current(token))
            failRequest(
                "Нет ответа SystemUI. Нажмите «Обновить» или перезапустите SystemUI.",
                background,
                quiet);
        };
    main.postDelayed(timeout, 8_000);
    WORKER.execute(
        () -> {
          if (!current(token)) return;
          try {
            Bundle accepted =
                context
                    .getContentResolver()
                    .call(SettingsProvider.URI, "preview.request", null, draft);
            if (accepted == null) throw new IllegalStateException("No provider");
            long id = accepted.getLong("id");
            if (id <= 0) throw new IllegalStateException("Invalid preview ID");
            main.post(
                () -> {
                  if (!current(token)) return;
                  requestId = id;
                  readResult();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (current(token))
                    failRequest("Предпросмотр SystemUI недоступен.", background, quiet);
                });
          }
        });
  }

  private boolean current(long token) {
    return active && !mutation && token == generation;
  }

  private void readResult() {
    if (!active || requestId == 0) return;
    long id = requestId, token = generation;
    boolean background = preload, quiet = warm;
    if (reading && readingId == id && readingGeneration == token) {
      readAgain = true;
      return;
    }
    reading = true;
    readAgain = false;
    readingId = id;
    readingGeneration = token;
    WORKER.execute(
        () -> {
          if (!current(token)) return;
          try {
            Bundle result =
                context
                    .getContentResolver()
                    .call(SettingsProvider.URI, "preview.result", null, null);
            if (result == null || result.getLong("id") != id) {
              main.post(
                  () -> {
                    if (current(token) && requestId == id) finishRead(id, token, true);
                  });
              return;
            }
            String error = result.getString("error", "");
            Bitmap bitmap = null;
            if (error.isEmpty()) {
              try (InputStream stream =
                  context.getContentResolver().openInputStream(PreviewProtocol.image(id))) {
                if (stream == null) throw new IllegalStateException("Missing preview");
                bitmap = PreviewBitmapIO.read(stream, result.getString("imageFormat", ""));
              }
            }
            Bitmap frame = bitmap;
            main.post(
                () -> {
                  if (!current(token) || requestId != id) {
                    if (frame != null) frame.recycle();
                    return;
                  }
                  finishRead(id, token, false);
                  clearTimeout();
                  requestId = 0;
                  if (!error.isEmpty()) {
                    failRequest(error, background, quiet);
                    return;
                  }
                  String nextSource = result.getString("source", "");
                  if (!source.equals(nextSource)) {
                    frames.clear();
                    prefetched.clear();
                    warmed.clear();
                    source = nextSource;
                  }
                  if (!result.containsKey("variantError"))
                    try {
                      org.json.JSONArray variants =
                          new org.json.JSONArray(result.getString("variants", "[]"));
                      for (int n = 0; n < variants.length(); n++)
                        warmed.add(variants.getJSONObject(n).getString("id"));
                    } catch (org.json.JSONException e) {
                      android.util.Log.w("EssentialTiling", "Invalid warm variants", e);
                    }
                  pages = result.getInt("pages", 1);
                  int actual = result.getInt("page");
                  frames.put(
                      actual, new Cached(frame, new Bundle(result), SystemClock.uptimeMillis()));
                  trim();
                  if (!background) {
                    if (result.containsKey("variantError")) focus = "";
                    listener.frame(frame, result);
                  }
                  prefetch();
                });
          } catch (Exception e) {
            android.util.Log.w("EssentialTiling", "Preview frame transfer", e);
            main.post(
                () -> {
                  if (current(token) && requestId == id) {
                    failRequest(
                        "Не удалось получить кадр SystemUI. Нажмите «Обновить».",
                        background,
                        quiet);
                  }
                });
          }
        });
  }

  private void finishRead(long id, long token, boolean retry) {
    if (readingId != id || readingGeneration != token) return;
    reading = false;
    boolean again = readAgain;
    readAgain = false;
    if (retry && again) readResult();
  }

  private void trim() {
    long bytes = 0;
    for (Cached f : frames.values()) bytes += f.bitmap.getAllocationByteCount();
    java.util.Iterator<java.util.Map.Entry<Integer, Cached>> iterator =
        frames.entrySet().iterator();
    while ((bytes > 64L * 1024 * 1024 || frames.size() > 3) && iterator.hasNext()) {
      java.util.Map.Entry<Integer, Cached> entry = iterator.next();
      if (entry.getKey() == page) continue;
      bytes -= entry.getValue().bitmap.getAllocationByteCount();
      iterator.remove();
    }
    // A displayed View may still use an evicted atlas. Let GC release it when that View moves on.
  }

  private void prefetch() {
    if (!active || requestId != 0 || !focus.isEmpty()) return;
    for (int target : new int[] {page + 1, page - 1})
      if (target >= 0 && target < pages && !frames.containsKey(target) && prefetched.add(target)) {
        schedule(true, false, true, target, 120);
        return;
      }
  }

  private void failed(String message, boolean background, boolean quiet) {
    if (background) {
      prefetch();
      return;
    }
    if (quiet && frames.containsKey(page)) {
      focus = "";
      return;
    }
    if (quiet) focus = "";
    else listener.error(message);
  }
}
