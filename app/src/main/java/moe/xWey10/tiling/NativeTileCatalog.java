package moe.xWey10.tiling;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Stock catalogue with bounded stock-state warmup, followed by the OEM package query. */
@android.annotation.SuppressLint({"PrivateApi", "DiscouragedApi"})
final class NativeTileCatalog {
    static volatile Class<?> type;
    static volatile Object[] dependencies;
    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "EssentialTiling-catalogue"); t.setDaemon(true); return t; });
    private static final java.util.Set<String> available = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile int availableUser = -1;
    static boolean contains(String spec, int user) { return availableUser == user && available.contains(spec); }

    static void query(Object host, Consumer<ArrayList<String>> done, Consumer<Exception> error) throws Exception {
        Handler main = new Handler(Looper.getMainLooper());
        ClassLoader loader = host.getClass().getClassLoader();
        Class<?> cls = type != null ? type : Class.forName("com.android.systemui.qs.customize.TileQueryHelper", false, loader);
        Object[] seed = dependencies;
        Context context = seed != null ? (Context) seed[0] : (Context) NativeGrid.call(host, "getContext");
        Object tracker;
        try {
            Object interactor = NativeGrid.field(host.getClass(), "interactor").get(host);
            tracker = NativeGrid.field(interactor.getClass(), "userTracker").get(interactor);
        } catch (ReflectiveOperationException e) {
            if (seed == null) throw e;
            tracker = seed[1];
        }
        Class<?> trackerClass = Class.forName("com.android.systemui.settings.UserTracker", false, loader);
        Object helper = cls.getConstructor(Context.class, trackerClass, Executor.class, Executor.class)
                .newInstance(context, tracker, (Executor) command -> main.post(command), BACKGROUND);
        int user = (Integer) NativeGrid.call(host, "getUserId");
        Class<?> listener = Class.forName(cls.getName() + "$TileStateListener", false, loader);
        Class<?> hostClass = Class.forName("com.android.systemui.qs.QSHost", false, loader);
        Class<?> stateClass = Class.forName("com.android.systemui.plugins.qs.QSTile$State", false, loader);
        ArrayList<Object> owned = new ArrayList<>();
        Object owner = new Object(); AtomicBoolean finished = new AtomicBoolean();
        Runnable cleanup = () -> {
            for (Object tile : owned) {
                try { NativeGrid.call(tile, "setListening", new Class<?>[]{Object.class, boolean.class}, owner, false); }
                catch (Exception e) { android.util.Log.w("EssentialTiling", "Catalogue listener cleanup", e); }
                try { NativeGrid.call(tile, "destroy"); }
                catch (Exception e) { android.util.Log.w("EssentialTiling", "Catalogue tile cleanup", e); }
            }
            owned.clear();
        };
        Consumer<Exception> fail = e -> { if (finished.compareAndSet(false, true)) { main.post(cleanup); error.accept(e); } };
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, values) -> {
            if (method.getName().equals("onTilesChanged")) {
                List<?> snapshots = new ArrayList<>((List<?>) values[0]);
                BACKGROUND.execute(() -> {
                    if (finished.get()) return;
                    try {
                        ArrayList<String> rows = new ArrayList<>(); java.util.HashSet<String> specs = new java.util.HashSet<>();
                        for (Object info : snapshots) {
                            String spec = (String) NativeGrid.field(info.getClass(), "spec").get(info);
                            Object state = NativeGrid.field(info.getClass(), "state").get(info);
                            Object label = NativeGrid.field(state.getClass(), "label").get(state);
                            Object app = NativeGrid.field(state.getClass(), "secondaryLabel").get(state);
                            if (spec == null || !specs.add(spec)) continue;
                            rows.add(new JSONObject().put("spec", spec).put("label", label == null ? spec : label.toString())
                                    .put("app", app == null ? "" : app.toString()).put("icon", NativeCatalogIcon.encode(state, context)).toString());
                        }
                        if (user != (Integer) NativeGrid.call(host, "getUserId")) throw QsLayout.stale();
                        if (finished.compareAndSet(false, true)) { available.clear(); available.addAll(specs); availableUser = user; done.accept(rows); }
                    } catch (Exception e) { fail.accept(e); }
                });
                return null;
            }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == values[0];
            if (method.getName().equals("toString")) return "EssentialTiling catalogue listener";
            return null;
        });
        NativeGrid.call(helper, "addListener", new Class<?>[]{listener}, callback);
        int stockId = context.getResources().getIdentifier("nt_quick_settings_tiles_stock", "string", "com.android.systemui");
        if (stockId == 0) throw new IllegalStateException("Не найден штатный список плиток Nothing OS.");
        try {
            for (String spec : context.getString(stockId).split(",")) {
                spec = spec.trim(); if (spec.isEmpty() || spec.startsWith("custom(")) continue;
                Object tile = null;
                try {
                    tile = NativeGrid.call(host, "createTile", new Class<?>[]{String.class}, spec);
                    if (tile == null) continue;
                    if (!(Boolean) NativeGrid.call(tile, "isAvailable")) { NativeGrid.call(tile, "destroy"); continue; }
                    owned.add(tile);
                    NativeGrid.call(tile, "setListening", new Class<?>[]{Object.class, boolean.class}, owner, true);
                    NativeGrid.call(tile, "refreshState");
                } catch (Exception e) {
                    if (tile != null && !owned.contains(tile)) try { NativeGrid.call(tile, "destroy"); } catch (Exception ignored) { }
                    android.util.Log.w("EssentialTiling", "Catalogue stock tile " + spec, e);
                }
            }
            // TileQueryHelper's collector waits for every isTileReady(), without a timeout.
            // Snapshot each available stock controller after a short warmup instead of waiting for that barrier.
            main.postDelayed(() -> {
                if (finished.get()) { cleanup.run(); return; }
                try {
                    for (Object tile : owned) {
                        Object state = NativeGrid.call(NativeGrid.call(tile, "getState"), "copy");
                        Object label = NativeGrid.call(tile, "getTileLabel");
                        NativeGrid.field(state.getClass(), "label").set(state, label);
                        String spec = (String) NativeGrid.call(tile, "getTileSpec");
                        NativeGrid.call(helper, "addTile", new Class<?>[]{String.class, CharSequence.class, stateClass, boolean.class}, spec, null, state, true);
                    }
                    cleanup.run();
                    NativeGrid.call(helper, "addPackageTiles", new Class<?>[]{hostClass}, host);
                } catch (Exception e) { cleanup.run(); fail.accept(e); }
            }, 200);
            main.postDelayed(() -> fail.accept(new IllegalStateException("Каталог SystemUI не ответил. Нажмите «Повторить».")), 8_000);
        } catch (Exception e) { cleanup.run(); throw e; }
    }
}
