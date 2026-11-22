package moe.xWey10.tiling;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

@android.annotation.SuppressLint("PrivateApi") // Audited OEM classes; this code executes only inside platform SystemUI.
final class NativeTileActions {
    static Object launcher(NativeGrid grid) throws Exception {
        return Class.forName("com.android.launcher3.Launcher", false, grid.workspace.getClass().getClassLoader())
                .getMethod("getLauncher", Context.class).invoke(null, grid.panel.getContext());
    }
    static Object host(Object launcher) throws Exception { return NativeGrid.field(launcher.getClass(), "mQsHost").get(launcher); }
    static Runnable prepare(NativeGrid grid, Bundle request) throws Exception {
        if (!grid.source.portrait) throw new IllegalStateException("Поверните телефон вертикально.");
        if (!grid.source.base.equals(request.getString("base"))) throw QsLayout.stale();
        Object launcher = launcher(grid), host = host(launcher);
        if (!(Boolean) NativeGrid.call(launcher, "isInteractable")) throw new IllegalStateException("SystemUI ещё не готов.");
        int user = NativeGrid.integer(launcher, "currentUserId");
        if (user < 0 || user != (Integer) NativeGrid.call(host, "getUserId")) throw QsLayout.stale();
        String spec = request.getString("spec", "");
        ClassLoader loader = grid.workspace.getClass().getClassLoader();
        Class<?> infoClass = Class.forName(NativeGrid.INFO, false, loader);
        Object manager = NativeGrid.field(launcher.getClass(), "mInfoManager").get(launcher);
        String action = request.getString("action");
        if ("page".equals(action)) {
            QsLayout.Page last = grid.source.pages.get(grid.source.pages.size() - 1);
            boolean occupied = false; for (QsLayout.Item item : grid.source.items) if (item.page() == last.id()) occupied = true;
            if (!occupied) throw new IllegalStateException("Пустая страница уже есть.");
            long id = (Long) NativeGrid.call(grid.workspace, "getNewScreenId");
            NativeGrid.call(grid.workspace, "insertNewWorkspaceScreen", new Class<?>[]{long.class}, id);
            return () -> { }; // Empty pages become durable when their first tile is saved, as in stock.
        } else if ("remove".equals(action)) {
            return remove(grid,request,launcher,manager,loader,user,spec);
        } else if ("add".equals(action)) {
            if (!NativeTileCatalog.contains(spec, user)) throw new IllegalStateException("Обновите список доступных плиток.");
            if (grid.source.item("t:" + spec) != null) throw new IllegalStateException("Плитка уже добавлена.");
            long page = request.getLong("page"); int x = request.getInt("x"), y = request.getInt("y");
            QsLayout.Page p = grid.source.page(page);
            if (p == null || x < 0 || y < 0 || x >= p.columns() || y >= p.rows()) throw QsLayout.stale();
            for (QsLayout.Item i : grid.source.items) if (i.page() == page && i.occupies(x,y)) throw new IllegalStateException("Ячейка уже занята.");
            Object info = infoClass.getConstructor(String.class).newInstance(spec);
            NativeGrid.field(infoClass, "screenId").setLong(info, page);
            for (String field : new String[]{"cellX", "cellY", "spanX", "spanY"}) NativeGrid.field(infoClass, field).setInt(info, field.equals("cellX") ? x : field.equals("cellY") ? y : 1);
            if (!(Boolean) NativeGrid.call(launcher, "prepareAddTileByReset", new Class<?>[]{infoClass}, info)) throw new IllegalStateException("Плитка больше недоступна.");
            if (!(Boolean) NativeGrid.call(launcher, "addTileByReset", new Class<?>[]{infoClass}, info)) {
                NativeGrid.call(launcher, "deleteTile", new Class<?>[]{String.class, boolean.class}, spec, false);
                throw new IllegalStateException("Не удалось добавить плитку в ячейку.");
            }
            Class<?> itemClass = Class.forName("com.android.launcher3.ItemInfo", false, loader);
            NativeGrid.call(manager, "modifyQSTileItemInfo", new Class<?>[]{itemClass, long.class, int.class, int.class, int.class, int.class, boolean.class}, info, page, x, y, 1, 1, false);
        } else throw new IllegalArgumentException("Unknown tile action");
        Runnable save=durable(grid.panel.getContext(), launcher, manager, loader, user);
        return ()->{ save.run(); NativeForms.clearSpec(grid.panel.getContext(),user,spec); };
    }
    private static Runnable remove(NativeGrid grid, Bundle request, Object launcher, Object manager, ClassLoader loader, int user, String spec) throws Exception {
        QsLayout.Item target=grid.source.item("t:"+spec);
        if(target==null||!target.editable()) throw new IllegalStateException("Эту плитку удалить нельзя.");
        String json=request.getString("layout",""); QsLayout desired=json.isEmpty()?grid.source:QsLayout.parse(json);
        desired.validateAgainst(grid.source);
        if(!NativeForms.supported) for(QsLayout.Item i:desired.items) if(!i.samePlace(grid.source.item(i.id()))&&NativeForms.custom(i)) throw new IllegalStateException("Хуки формы недоступны. Перезапустите SystemUI.");
        Object record=NativeGrid.call(launcher,"getTileRecord",new Class<?>[]{String.class},spec);
        if(record==null) throw QsLayout.stale();
        int minimum=(Integer)NativeGrid.call(launcher,"getMinNumTiles");
        if(((java.util.Collection<?>)NativeGrid.field(launcher.getClass(),"mRecords").get(launcher)).size()<=minimum) throw new IllegalStateException("Минимум плиток Nothing OS: "+minimum+".");
        // Capture and merge the whole final layout before touching the live tree.
        // A resize followed by '-' must be one native transaction, not two refreshes with guessed fingerprints.
        Class<?> info=Class.forName(NativeGrid.INFO,false,loader); ArrayList<Object> copies=new ArrayList<>();
        java.util.HashSet<String> found=new java.util.HashSet<>();
        Object lock=NativeGrid.field(manager.getClass(),"rawTileListKey").get(manager);
        synchronized(lock) {
            for(Object value:(List<?>)NativeGrid.field(manager.getClass(),"rawTileInfoList").get(manager)) {
                Object copy=info.getConstructor(info).newInstance(value); String id="t:"+NativeGrid.call(copy,"getTileSpec");
                QsLayout.Item old=grid.source.item(id), next=desired.item(id);
                if(old!=null) {
                    if(NativeGrid.integer(copy,"cellX")!=old.x()||NativeGrid.integer(copy,"cellY")!=old.y()||NativeGrid.integer(copy,"spanX")!=old.w()||NativeGrid.integer(copy,"spanY")!=old.h()||NativeGrid.field(info,"screenId").getLong(copy)!=old.page()) throw QsLayout.stale();
                    found.add(id);
                }
                if(id.equals("t:"+spec)) continue;
                if(next!=null) {
                    NativeGrid.field(info,"screenId").setLong(copy,next.page());
                    for(String field:new String[]{"cellX","cellY","spanX","spanY"}) NativeGrid.field(info,field).setInt(copy,switch(field){case "cellX"->next.x();case "cellY"->next.y();case "spanX"->next.w();default->next.h();});
                }
                copies.add(copy);
            }
        }
        for(QsLayout.Item i:grid.source.items) if(i.editable()&&!found.contains(i.id())) throw QsLayout.stale();
        // Prepare all reflection/serialization before the irreversible native removal begins.
        Runnable save=durable(grid.panel.getContext(),launcher,loader,user,copies);
        boolean removed=(Boolean)NativeGrid.call(launcher,"deleteTile",new Class<?>[]{String.class,boolean.class},spec,false);
        if(!removed||NativeGrid.call(launcher,"getTileRecord",new Class<?>[]{String.class},spec)!=null) throw new IllegalStateException("SystemUI не удалил плитку. Обновите предпросмотр.");
        NativeGrid.call(manager,"onTileRemoved",new Class<?>[]{String.class},spec);
        ArrayList<QsLayout.Item> remaining=new ArrayList<>(); for(QsLayout.Item i:desired.items) if(!i.id().equals("t:"+spec)) remaining.add(i);
        QsLayout savedForms=new QsLayout("",desired.portrait,desired.pages,remaining);
        return ()->{ save.run(); if(!json.isEmpty()) NativeForms.save(grid.panel.getContext(),user,savedForms); NativeForms.clearSpec(grid.panel.getContext(),user,spec); };
    }
    @android.annotation.SuppressLint("DiscouragedPrivateApi") // Platform SystemUI user-scoped persistence, same native saver as the stock editor.
    private static Runnable durable(Context context, Object launcher, Object manager, ClassLoader loader, int user) throws Exception {
        Class<?> info = Class.forName(NativeGrid.INFO, false, loader); ArrayList<Object> copies = new ArrayList<>();
        Object lock = NativeGrid.field(manager.getClass(), "rawTileListKey").get(manager);
        synchronized (lock) { for (Object value : (List<?>) NativeGrid.field(manager.getClass(), "rawTileInfoList").get(manager)) copies.add(info.getConstructor(info).newInstance(value)); }
        return durable(context,launcher,loader,user,copies);
    }
    @android.annotation.SuppressLint("DiscouragedPrivateApi") // Platform SystemUI persistence, native saver and user-scoped readback.
    private static Runnable durable(Context context,Object launcher,ClassLoader loader,int user,List<Object> copies) throws Exception {
        Class<?> serializer = Class.forName("com.nothing.systemui.qs.QSTileHostEx", false, loader);
        String nt = (String) serializer.getMethod("tileItemInfoToNTString", java.util.Collection.class).invoke(null, copies);
        String normal = (String) serializer.getMethod("tileItemInfoToString", java.util.Collection.class).invoke(null, copies);
        String key = (String) Class.forName("com.android.systemui.qs.pipeline.data.repository.UserTileSpecRepository", false, loader).getField("NT_SETTING").get(null);
        Runnable save = (Runnable) Class.forName("com.android.launcher3.LauncherInfoManager$SaveCurrentQSTilesRunnable", false, loader)
                .getConstructor(Context.class, Class.forName("com.android.systemui.qs.QSHost", false, loader), List.class, boolean.class, Integer.class)
                .newInstance(context, host(launcher), copies, false, user);
        java.lang.reflect.Method read = Settings.Secure.class.getDeclaredMethod("getStringForUser", android.content.ContentResolver.class, String.class, int.class); read.setAccessible(true);
        return () -> {
            save.run();
            try {
                if (!nt.equals(read.invoke(null, context.getContentResolver(), key, user)) || !normal.equals(read.invoke(null, context.getContentResolver(), "sysui_qs_tiles", user))) throw new IllegalStateException("Запись плиток не подтверждена. Обновите предпросмотр.");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Не удалось проверить запись плиток.", e); }
        };
    }
}
