package moe.xWey10.tiling;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reflection contract comes from the supplied Nothing APK, including its native serializer. */
final class NativeGrid {
    static final String INFO = "com.android.launcher3.QSTileItemInfo";
    final ViewGroup panel, workspace;
    final QsLayout source;
    final Map<String, View> views = new HashMap<>();
    final List<ViewGroup> containers = new ArrayList<>();
    String focus = "";
    java.util.function.Consumer<View> beforeVariant = tile -> { };

    NativeGrid(ViewGroup panel, ViewGroup workspace) throws ReflectiveOperationException {
        this.panel = panel; this.workspace = workspace;
        Class<?> infoClass = load(INFO);
        ArrayList<QsLayout.Page> pages = new ArrayList<>(); ArrayList<QsLayout.Item> items = new ArrayList<>();
        for (int n = 0; n < workspace.getChildCount(); n++) {
            View cell = workspace.getChildAt(n);
            long screen = (Long) call(workspace, "getScreenIdForPageIndex", new Class<?>[]{int.class}, n);
            int cols = (Integer) call(cell, "getCountX"), rows = (Integer) call(cell, "getCountY");
            ViewGroup container = (ViewGroup) call(cell, "getShortcutsAndWidgets"); containers.add(container);
            pages.add(new QsLayout.Page(screen, cols, rows));
            for (int k = 0; k < container.getChildCount(); k++) {
                View v = container.getChildAt(k); Object tag = v.getTag(), lp = v.getLayoutParams();
                if (v.getVisibility() == View.GONE) continue;
                boolean tile = infoClass.isInstance(tag);
                String key = tile ? "t:" + call(tag, "getTileSpec")
                        : tag == null ? "v:" + screen + ":" + k : "w:" + field(tag.getClass(), "id").getLong(tag);
                boolean editable = tile && hasMethod(v.getClass(), "applySize", int.class, int.class);
                boolean tall = tile && (Boolean) call(tag, "isAllowExtendHeight");
                Object title = tag == null ? null : field(tag.getClass(), "title").get(tag);
                if (tile) {
                    try { Object labelView = call(v, "getLabel");
                        if (labelView instanceof android.widget.TextView) title = ((android.widget.TextView) labelView).getText();
                    } catch (ReflectiveOperationException ignored) { }
                }
                String label = title == null || title.toString().isEmpty() ? (tile ? key.substring(2) : "Виджет") : title.toString();
                QsLayout.Item i = new QsLayout.Item(key, label, screen, integer(lp, "cellX"), integer(lp, "cellY"),
                        integer(lp, "cellHSpan"), integer(lp, "cellVSpan"), editable, tall, tile ? NativeForms.form(key,integer(lp,"cellHSpan"),integer(lp,"cellVSpan")) : TileForm.RECT);
                if (views.put(key, v) != null) throw new IllegalStateException("Повторяющаяся плитка в SystemUI");
                items.add(i);
            }
        }
        source = new QsLayout("", panel.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT,
                pages, items).asSource();
    }

    final class Session {
        final QsLayout layout;
        final Map<View, Object> tags = new HashMap<>();
        final ArrayList<QsLayout.Item> changed = new ArrayList<>();
        Session(String json) {
            layout = json == null || json.isEmpty() ? source : QsLayout.parse(json);
            if (!layout.base.equals(source.base)) throw QsLayout.stale();
            if (layout != source) layout.validateAgainst(source);
        }
        Map<String,QsLayout.Item> previousForms;
        void apply() {
            previousForms=NativeForms.draft.get(); java.util.HashMap<String,QsLayout.Item> values=new java.util.HashMap<>();
            for(QsLayout.Item i:layout.items) values.put(i.id(),i); NativeForms.draft.set(values);
            try {
                for (QsLayout.Item i : layout.items) {
                    QsLayout.Item old = source.item(i.id()); if (i.samePlace(old)) continue;
                    View v = views.get(i.id()); Object original = v.getTag();
                    Object copy = load(INFO).getConstructor(load(INFO)).newInstance(original);
                    tags.put(v, original); changed.add(old);
                    setInfo(copy, i); v.setTag(copy);
                    Object lp = v.getLayoutParams();
                    set(lp, "cellX", i.x()); set(lp, "cellY", i.y()); set(lp, "tmpCellX", i.x()); set(lp, "tmpCellY", i.y());
                    set(lp, "cellHSpan", i.w()); set(lp, "cellVSpan", i.h());
                    field(lp.getClass(), "useTmpCoords").setBoolean(lp, false);
                    if (old.w() != i.w() || old.h() != i.h() || !old.form().equals(i.form())) call(v, "applySize", new Class<?>[]{int.class, int.class}, i.w(), i.h());
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Изменение черновика QS недоступно", e); }
        }
        void restore() {
            if(previousForms==null) NativeForms.draft.remove(); else NativeForms.draft.set(previousForms);
            RuntimeException failure = null;
            for (QsLayout.Item old : changed) {
                View v = views.get(old.id()); v.setTag(tags.get(v)); Object lp = v.getLayoutParams();
                try {
                    set(lp, "cellX", old.x()); set(lp, "cellY", old.y());
                    set(lp, "cellHSpan", old.w()); set(lp, "cellVSpan", old.h());
                    QsLayout.Item i = layout.item(old.id());
                    if (old.w() != i.w() || old.h() != i.h() || !old.form().equals(i.form())) call(v, "applySize", new Class<?>[]{int.class, int.class}, old.w(), old.h());
                } catch (ReflectiveOperationException e) { failure = new IllegalStateException("Восстановление размера QS", e); }
            }
            changed.clear(); tags.clear(); if (failure != null) throw failure;
        }
    }

    /** An atlas contains the actual empty QS surface and actual tile images, never sample tiles. */
    NativePreviewServer.Frame capture(Session session, int page, Bitmap background, float scale, int width, int height) {
        ArrayList<Bitmap> sprites = new ArrayList<>(); ArrayList<View> hidden = new ArrayList<>();
        ArrayList<NativeTileVariants.Image> variants = new ArrayList<>();
        Bitmap base = null, atlas = null;
        try {
            ViewGroup container = containers.get(page); View cell = workspace.getChildAt(page);
            Rect origin = new Rect(0, 0, container.getWidth(), container.getHeight());
            panel.offsetDescendantRectToMyCoords(container, origin);
            Bundle meta = new Bundle(); meta.putInt("baseHeight", height);
            meta.putBoolean("formsSupported",NativeForms.supported);
            meta.putBoolean("cellFormsSupported",NativeForms.supported);
            meta.putString("moduleVersion",BuildConfig.VERSION_NAME);
            meta.putString("formsError",NativeForms.failure);
            meta.putString("source", source.json()); meta.putString("layout", session.layout.json());
            meta.putFloat("gridX", origin.left * scale); meta.putFloat("gridY", origin.top * scale);
            meta.putFloat("cellWidth", (Integer) call(cell, "getCellWidth") * scale);
            meta.putFloat("cellHeight", (Integer) call(cell, "getCellHeight") * scale);
            meta.putBoolean("rtl", (Boolean) call(container, "invertLayoutHorizontally"));
            JSONArray regions = new JSONArray(); int x = 0, y = height, rowHeight = 0;
            long screen = source.pages.get(page).id();
            for (QsLayout.Item i : session.layout.items) if (i.page() == screen) {
                View v = views.get(i.id());
                int sw = Math.max(1, Math.round(v.getWidth() * scale)), sh = Math.max(1, Math.round(v.getHeight() * scale));
                if (sw > width || sh > 4096) throw new IllegalStateException("Некорректные размеры элемента QS");
                if (x + sw > width) { y += rowHeight; rowHeight = 0; x = 0; }
                Rect r = new Rect(0, 0, v.getWidth(), v.getHeight()); panel.offsetDescendantRectToMyCoords(v, r);
                sprites.add(NativeFrameCapture.capture(v, null, scale, sw, sh));
                regions.put(new JSONObject().put("id", i.id()).put("sx", x).put("sy", y).put("sw", sw).put("sh", sh)
                        .put("left", r.left * scale).put("top", r.top * scale)
                        .put("right", r.right * scale).put("bottom", r.bottom * scale));
                x += sw; rowHeight = Math.max(rowHeight, sh);
            }
            for (QsLayout.Item i : session.layout.items) {
                View v = views.get(i.id()); hidden.add(v); v.setVisibility(View.INVISIBLE);
            }
            base = NativeFrameCapture.capture(panel, background, scale, width, height);
            QsLayout.Item focused = session.layout.item(focus);
            JSONArray endpoints = new JSONArray();
            if (focused != null && focused.editable() && focused.page() == screen) {
                try { variants = NativeTileVariants.capture(views.get(focus), focused, scale, beforeVariant); }
                catch (ReflectiveOperationException | RuntimeException e) {
                    android.util.Log.e("EssentialTiling", "Native tile size endpoints", e);
                    meta.putString("variantError", "Предзагрузка размеров недоступна. Переход выполнится после обновления кадра.");
                }
                for (NativeTileVariants.Image variant : variants) {
                    JSONObject endpoint = new JSONObject().put("id", focus).put("w", variant.w()).put("h", variant.h());
                    for (int kind = 0; kind < 2; kind++) {
                        Bitmap image = kind == 0 ? variant.full() : variant.background();
                        if (image.getWidth() > width) throw new IllegalStateException("Размер варианта QS превышает ширину кадра");
                        if (x + image.getWidth() > width) { y += rowHeight; rowHeight = 0; x = 0; }
                        endpoint.put(kind == 0 ? "image" : "background", new JSONArray().put(x).put(y).put(image.getWidth()).put(image.getHeight()));
                        x += image.getWidth(); rowHeight = Math.max(rowHeight, image.getHeight());
                    }
                    endpoints.put(endpoint);
                }
                meta.putString("variants", endpoints.toString());
            }
            int atlasHeight = y + rowHeight;
            if (atlasHeight > 8192) throw new IllegalStateException("Слишком большой кадр QS");
            atlas = Bitmap.createBitmap(width, atlasHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(atlas); Paint p = new Paint(Paint.FILTER_BITMAP_FLAG); canvas.drawBitmap(base, 0, 0, p);
            for (int n = 0; n < sprites.size(); n++) {
                JSONObject r = regions.getJSONObject(n); canvas.drawBitmap(sprites.get(n), r.getInt("sx"), r.getInt("sy"), p);
            }
            for (int n = 0; n < variants.size(); n++) for (int kind = 0; kind < 2; kind++) {
                JSONArray r = endpoints.getJSONObject(n).getJSONArray(kind == 0 ? "image" : "background");
                NativeTileVariants.Image variant = variants.get(n);
                canvas.drawBitmap(kind == 0 ? variant.full() : variant.background(), r.getInt(0), r.getInt(1), p);
            }
            meta.putString("regions", regions.toString());
            NativePreviewServer.Frame frame = new NativePreviewServer.Frame(atlas, page, source.pages.size(), meta);
            atlas = null; return frame;
        } catch (ReflectiveOperationException | org.json.JSONException e) { throw new IllegalStateException("Сетка предпросмотра недоступна", e); }
        finally {
            for (View v : hidden) v.setVisibility(View.VISIBLE);
            if (base != null) base.recycle(); if (atlas != null) atlas.recycle(); for (Bitmap b : sprites) b.recycle();
            for (NativeTileVariants.Image variant : variants) variant.recycle();
        }
    }

    /** Prepare on main; run on a worker. The native save format also preserves custom tile specs. */
    @android.annotation.SuppressLint("DiscouragedPrivateApi") // Only executed in the audited, platform-signed SystemUI process.
    Runnable saveJob(QsLayout draft) throws ReflectiveOperationException {
        draft.validateAgainst(source);
        if(!NativeForms.supported) for(QsLayout.Item i:draft.items) if(NativeForms.custom(i)) throw new IllegalStateException("Хуки нестандартных форм недоступны. Перезапустите SystemUI и проверьте отчёт.");
        if (!source.portrait) throw new IllegalStateException("Для изменения раскладки поверните телефон вертикально.");
        Context context = panel.getContext(); Object launcher = load("com.android.launcher3.Launcher")
                .getMethod("getLauncher", Context.class).invoke(null, context);
        if (!(Boolean) call(launcher, "isInteractable")) throw new IllegalStateException("SystemUI ещё не готов к сохранению раскладки.");
        Object manager = field(launcher.getClass(), "mInfoManager").get(launcher);
        Object host = field(launcher.getClass(), "mQsHost").get(launcher);
        int user = integer(launcher, "currentUserId");
        if (user < 0 || user != (Integer) call(host, "getUserId")) throw QsLayout.stale();
        List<?> raw; Object lock = field(manager.getClass(), "rawTileListKey").get(manager);
        synchronized (lock) { raw = new ArrayList<>((List<?>) field(manager.getClass(), "rawTileInfoList").get(manager)); }
        ArrayList<Object> copies = new ArrayList<>(); HashSet<String> found = new HashSet<>();
        for (Object original : raw) {
            Object copy = load(INFO).getConstructor(load(INFO)).newInstance(original);
            String id = "t:" + call(copy, "getTileSpec"); QsLayout.Item item = draft.item(id), old = source.item(id);
            if (item != null) {
                // Portrait ItemInfoTransformer is identity. Never replace a newly changed native layout.
                if (old == null || integer(copy, "cellX") != old.x() || integer(copy, "cellY") != old.y()
                        || integer(copy, "spanX") != old.w() || integer(copy, "spanY") != old.h()
                        || field(copy.getClass(), "screenId").getLong(copy) != old.page()) throw QsLayout.stale();
                if (!item.samePlace(old)) setInfo(copy, item); found.add(id);
            }
            copies.add(copy);
        }
        for (QsLayout.Item i : draft.items) if (i.editable() && !found.contains(i.id())) throw QsLayout.stale();
        Class<?> serializer = load("com.nothing.systemui.qs.QSTileHostEx");
        String expected = (String) serializer.getMethod("tileItemInfoToNTString", java.util.Collection.class).invoke(null, copies);
        String ordinary = (String) serializer.getMethod("tileItemInfoToString", java.util.Collection.class).invoke(null, copies);
        String key = (String) load("com.android.systemui.qs.pipeline.data.repository.UserTileSpecRepository").getField("NT_SETTING").get(null);
        Runnable save = (Runnable) load("com.android.launcher3.LauncherInfoManager$SaveCurrentQSTilesRunnable")
                .getConstructor(Context.class, load("com.android.systemui.qs.QSHost"), List.class, boolean.class, Integer.class)
                .newInstance(context, host, copies, false, user);
        Method read = Settings.Secure.class.getDeclaredMethod("getStringForUser", android.content.ContentResolver.class, String.class, int.class);
        read.setAccessible(true);
        return () -> {
            save.run();
            try {
                if (!expected.equals(read.invoke(null, context.getContentResolver(), key, user))
                        || !ordinary.equals(read.invoke(null, context.getContentResolver(), "sysui_qs_tiles", user))) {
                    throw new IllegalStateException("SystemUI не подтвердил запись раскладки.");
                }
                NativeForms.save(context,user,draft);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Не удалось проверить сохранение раскладки", e); }
        };
    }

    private void setInfo(Object info, QsLayout.Item i) throws ReflectiveOperationException {
        set(info, "cellX", i.x()); set(info, "cellY", i.y()); set(info, "spanX", i.w()); set(info, "spanY", i.h());
        field(info.getClass(), "screenId").setLong(info, i.page());
    }
    private Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name, false, workspace.getClass().getClassLoader()); }
    static Field field(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static int integer(Object obj, String name) throws ReflectiveOperationException { return field(obj.getClass(), name).getInt(obj); }
    private static void set(Object obj, String name, int value) throws ReflectiveOperationException { field(obj.getClass(), name).setInt(obj, value); }
    private static boolean hasMethod(Class<?> cls, String name, Class<?>... args) { try { method(cls, name, args); return true; } catch (NoSuchMethodException e) { return false; } }
    private static Method method(Class<?> cls, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) try { Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m; }
        catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(name);
    }
    static Object call(Object obj, String name) throws ReflectiveOperationException { return call(obj, name, new Class<?>[0]); }
    static Object call(Object obj, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException { return method(obj.getClass(), name, types).invoke(obj, args); }
}
