package moe.xWey10.tiling;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

final class TileClient {
    record Entry(String spec, String label, String app, android.graphics.Bitmap icon) {
        Entry(String spec, String label, String app) { this(spec, label, app, null); }
        @Override public String toString() { return label + (app.isEmpty() ? "" : " · " + app); }
        static Entry decode(String row) throws Exception {
            JSONObject item = new JSONObject(row);
            android.graphics.Bitmap bitmap = null;
            String encoded = item.optString("icon");
            if (!encoded.isEmpty() && encoded.length() <= 5500) {
                try {
                    byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (options.outWidth > 0 && options.outWidth <= 96 && options.outHeight > 0 && options.outHeight <= 96) {
                        options.inJustDecodeBounds = false; options.inScaled = false;
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            return new Entry(item.getString("spec"), item.getString("label"), item.optString("app"), bitmap);
        }
    }
    interface Listener { void result(List<Entry> entries, String error); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "EssentialTiling-tiles-client"); t.setDaemon(true); return t; });
    private volatile long generation;
    private Ticket active;
    private static final long TIMEOUT=15_000;
    private static final class Ticket {
        final long token,deadline; final String action; final Listener listener;
        volatile long id; volatile boolean cancelled;
        Runnable timeout;
        Ticket(long token,long deadline,String action,Listener listener) { this.token=token;this.deadline=deadline;this.action=action;this.listener=listener; }
    }
    TileClient(Context context) { this.context = context.getApplicationContext(); }
    void stop() {
        generation++; if(active!=null) active.cancelled=true; active=null; main.removeCallbacksAndMessages(null);
    }
    void cancelCatalog() {
        Ticket ticket=active; if(ticket==null||!ticket.action.equals("catalog")) return;
        ticket.cancelled=true; generation++; active=null; main.removeCallbacks(ticket.timeout);
        // Use this catalogue's own ID. A queued cancellation must never address a newer mutation.
        WORKER.execute(()-> {
            if(ticket.id==0) return;
            Bundle b=new Bundle(); b.putLong("id",ticket.id);
            try { context.getContentResolver().call(SettingsProvider.URI,"tiles.cancel",null,b); } catch(RuntimeException ignored) { }
        });
    }
    void request(Bundle args,Listener listener) {
        if(active!=null) {
            if(active.action.equals("catalog")) cancelCatalog();
            else { main.post(()->listener.result(null,"Дождитесь завершения операции с плитками.")); return; }
        }
        Ticket ticket=new Ticket(++generation,SystemClock.elapsedRealtime()+TIMEOUT,args.getString("action",""),listener); active=ticket;
        Bundle request=new Bundle(args); request.putLong("deadline",ticket.deadline);
        ticket.timeout=()->finish(ticket,null,ticket.action.equals("catalog")?"Каталог не ответил. Нажмите «Повторить».":"Нет подтверждения SystemUI. Обновите предпросмотр перед повтором.");
        // This deadline runs on the app UI looper, independently of any blocked Binder/worker call.
        main.postDelayed(ticket.timeout,TIMEOUT);
        WORKER.execute(()-> {
            if(!current(ticket)||SystemClock.elapsedRealtime()>=ticket.deadline) return;
            try {
                Bundle accepted=context.getContentResolver().call(SettingsProvider.URI,"tiles.request",null,request);
                if(accepted==null) throw new IllegalStateException("Модуль SystemUI недоступен. Перезапустите SystemUI.");
                ticket.id=accepted.getLong("id"); poll(ticket);
            } catch(Exception e) { deliver(ticket,null,e instanceof IllegalStateException && e.getMessage()!=null ? e.getMessage() : "Не удалось связаться с SystemUI. Перезапустите SystemUI."); }
        });
    }
    private boolean current(Ticket ticket) { return !ticket.cancelled && generation==ticket.token; }
    private void poll(Ticket ticket) {
        WORKER.execute(()-> {
            if(!current(ticket)) return;
            try {
                Bundle b=new Bundle(); b.putLong("id",ticket.id);
                Bundle result=context.getContentResolver().call(SettingsProvider.URI,"tiles.status",null,b);
                if(result==null) throw new IllegalStateException("No status");
                String state=result.getString("state","expired");
                if(state.equals("done")) {
                    ArrayList<Entry> entries=new ArrayList<>(); int count=result.getInt("count");
                    for(int offset=0;offset<count;) {
                        if(!current(ticket)) return;
                        b.putInt("offset",offset); Bundle page=context.getContentResolver().call(SettingsProvider.URI,"tiles.read",null,b);
                        ArrayList<String> rows=page==null?null:page.getStringArrayList("rows");
                        if(rows==null||rows.isEmpty()) throw new IllegalStateException("Catalogue expired");
                        for(String row:rows) entries.add(Entry.decode(row)); offset+=rows.size();
                    }
                    deliver(ticket,entries,""); return;
                }
                if(state.equals("error")||state.equals("expired")) { deliver(ticket,null,result.getString("error","Запрос устарел. Повторите.")); return; }
                main.postDelayed(()->{ if(current(ticket)) poll(ticket); },150);
            } catch(Exception e) { deliver(ticket,null,"Нет подтверждения SystemUI. Обновите предпросмотр перед повтором."); }
        });
    }
    private void deliver(Ticket ticket,List<Entry> entries,String error) { main.post(()->finish(ticket,entries,error)); }
    private void finish(Ticket ticket,List<Entry> entries,String error) {
        if(!current(ticket)) return;
        ticket.cancelled=true; generation++; if(active==ticket) active=null; main.removeCallbacks(ticket.timeout);
        ticket.listener.result(entries,error);
    }
}
