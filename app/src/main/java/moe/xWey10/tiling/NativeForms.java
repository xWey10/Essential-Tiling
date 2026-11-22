package moe.xWey10.tiling;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** Shape metadata belongs to the module; stock spans/positions remain in Nothing's own settings. */
final class NativeForms {
    static volatile boolean supported;
    static volatile String failure="";
    private record Stored(int w, int h, TileForm form) { }
    private static volatile Map<String,Stored> forms = Map.of();
    private static int user = -1;
    @android.annotation.SuppressLint("StaticFieldLeak") // SystemUI Application for the process lifetime.
    private static Context context;
    static final ThreadLocal<Map<String, QsLayout.Item>> draft = new ThreadLocal<>();
    static final java.util.Set<View> views = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static void init(Context c) { context=c.getApplicationContext(); }
    static synchronized void user(Context c, int id) {
        init(c); if(user==id) return; user=id;
        forms=parse(context.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).getString("u"+id,"{}"));
    }
    static TileForm form(String id, int w, int h) {
        if(!supported) return TileForm.RECT;
        Map<String,QsLayout.Item> current=draft.get();
        if(current!=null && current.containsKey(id)) { QsLayout.Item i=current.get(id); return i.form().cellular()&&(i.w()!=w||i.h()!=h)?TileForm.RECT:i.form().fit(w,h); }
        Stored saved=forms.get(id); return saved==null || saved.w!=w || saved.h!=h ? TileForm.RECT : saved.form;
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Membership saver worker, before ACK.
    static synchronized void clearSpec(Context c,int id,String spec) {
        Map<String,Stored> values=new HashMap<>(parse(c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).getString("u"+id,"{}")));
        if(values.remove("t:"+spec)==null) return;
        if(!c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).edit().putString("u"+id,json(values)).commit()) throw new IllegalStateException("Не удалось обновить формы плиток.");
        if(user==id) forms=Map.copyOf(values);
    }
    private record Cached(Object tag,Object forms,Object draft,QsLayout.Item item) { }
    private static final java.util.WeakHashMap<View,Cached> cache=new java.util.WeakHashMap<>();
    static void forget(View view) { cache.remove(view); }
    static boolean hasCuts() {
        Map<String,QsLayout.Item> current=draft.get();
        if(current==null) return !forms.isEmpty();
        for(QsLayout.Item i:current.values()) if(i.form().cut()) return true; return false;
    }
    static QsLayout.Item item(View view) {
        try {
            Object tag=view.getTag(); if(tag==null) return null;
            Cached old=cache.get(view); if(old!=null&&old.tag==tag&&old.forms==forms&&old.draft==draft.get()) return old.item;
            String id="t:"+NativeGrid.call(tag,"getTileSpec");
            int w=NativeGrid.integer(tag,"spanX"),h=NativeGrid.integer(tag,"spanY");
            boolean tall=(Boolean)NativeGrid.call(tag,"isAllowExtendHeight");
            QsLayout.Item result=new QsLayout.Item(id,"",0,0,0,w,h,true,tall,form(id,w,h));
            cache.put(view,new Cached(tag,forms,draft.get(),result)); return result;
        } catch(Exception e) { return null; }
    }
    static boolean custom(QsLayout.Item i) { return i!=null && (i.form().baseW()>0 || i.form().geometry() || !(i.w()==1&&i.h()==1 || i.w()==2&&(i.h()==1 || i.h()==2&&i.tall()))); }
    @android.annotation.SuppressLint("ApplySharedPref") // Native layout saver worker must persist before ACK.
    static void save(Context c,int id,QsLayout layout) {
        Map<String,Stored> copy;
        synchronized(NativeForms.class) {
            copy=new HashMap<>(parse(c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).getString("u"+id,"{}")));
            for(QsLayout.Item i:layout.items) if(i.editable()) {
                if(i.form().stored()) copy.put(i.id(),new Stored(i.w(),i.h(),i.form())); else copy.remove(i.id());
            }
            if(!c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).edit().putString("u"+id,json(copy)).commit()) throw new IllegalStateException("Форма плиток не сохранена. Обновите предпросмотр.");
            if(user==id) forms=Map.copyOf(copy);
        }
        new Handler(Looper.getMainLooper()).post(()-> {
            for(View view:new java.util.ArrayList<>(views)) {
                QsLayout.Item i=item(view); if(i==null) continue;
                try { NativeGrid.call(view,"applySize",new Class<?>[]{int.class,int.class},i.w(),i.h()); }
                catch(Exception e) { android.util.Log.w("EssentialTiling","Refresh tile form",e); }
                NativeOccupancyHooks.refresh(view);
                view.requestLayout(); view.invalidate();
            }
        });
    }
    private static Map<String,Stored> parse(String text) {
        HashMap<String,Stored> result=new HashMap<>();
        try {
            JSONObject root=new JSONObject(text); java.util.Iterator<String> keys=root.keys();
            while(keys.hasNext()) { String id=keys.next(); org.json.JSONArray a=root.getJSONArray(id);
                int w=a.getInt(0),h=a.getInt(1); TileForm f=new TileForm(a.getInt(2),a.getInt(3),a.getInt(4),a.optString(5,""),a.optString(6,""),a.optBoolean(7,false),(float)a.optDouble(8,0),a.optString(9,""),a.optInt(10,0),a.optInt(11,0),a.optInt(12,0));
                if(a.length()<10) f=TileSeams.legacy(f,w,h);
                if(w>0&&w<=24&&h>0&&h<=24&&f.valid(w,h)) result.put(id,new Stored(w,h,f));
            }
        } catch(Exception e) { android.util.Log.w("EssentialTiling","Read tile forms",e); }
        return Map.copyOf(result);
    }
    private static String json(Map<String,Stored> map) {
        JSONObject root=new JSONObject();
        try { for(var e:map.entrySet()) { Stored s=e.getValue(); root.put(e.getKey(),new org.json.JSONArray().put(s.w).put(s.h).put(s.form.corner()).put(s.form.cutW()).put(s.form.cutH()).put(s.form.cells()).put(s.form.body()).put(s.form.nubs()).put(s.form.rotation()).put(s.form.seams()).put(s.form.layer()).put(s.form.baseW()).put(s.form.baseH())); } }
        catch(org.json.JSONException e) { throw new IllegalStateException(e); } return root.toString();
    }
}
