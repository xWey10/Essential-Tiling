package moe.xWey10.tiling;

import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;

/** One-shot operations; catalogue pages keep Binder messages bounded without truncating the list. */
final class TileControl {
    static final Uri REQUEST = Uri.parse(SettingsProvider.BASE_URI + "/tiles/request");
    private Bundle pending, result;
    private long sequence, started;
    private final ArrayList<String> catalog = new ArrayList<>();
    static int pageEnd(java.util.List<String> rows, int offset) {
        int end = offset, characters = 0;
        while (end < rows.size() && end - offset < 64 && characters + rows.get(end).length() <= 96_000) characters += rows.get(end++).length();
        if (end == offset && offset < rows.size()) throw new IllegalArgumentException("Catalogue entry too large");
        return end;
    }
    synchronized Bundle request(Bundle args, long now) {
        expire(now);
        if (pending != null && result == null) throw new IllegalStateException("Операция с плитками уже выполняется.");
        String action = args.getString("action", "");
        if (!action.equals("catalog") && !action.equals("add") && !action.equals("remove") && !action.equals("page")) throw new IllegalArgumentException("Unknown tile action");
        String spec = args.getString("spec", "");
        if (spec.length() > 1024 || args.getString("base", "").length() > 128) throw new IllegalArgumentException("Invalid request");
        String layout=args.getString("layout","");
        if(!layout.isEmpty()) { if(!action.equals("remove")) throw new IllegalArgumentException("Draft is only accepted for removal"); QsLayout.parse(layout); }
        pending = new Bundle(args); result = null; catalog.clear(); started = now;
        pending.putLong("deadline",Math.min(now+30_000,args.getLong("deadline",now+30_000)));
        sequence = Math.max(System.currentTimeMillis(), sequence + 1); pending.putLong("id", sequence);
        pending.putBoolean("claimed", false); return status(sequence, now);
    }
    synchronized Bundle claim(long now) {
        expire(now); if (pending == null || result != null || pending.getBoolean("claimed")) return null;
        pending.putBoolean("claimed", true); return new Bundle(pending);
    }
    synchronized void page(Bundle b) {
        if (!accepts(b)) return;
        ArrayList<String> rows = b.getStringArrayList("rows");
        if (rows == null || rows.size() > 64) throw new IllegalArgumentException("Invalid catalogue page");
        for (String row : rows) if (row == null || row.length() > 8192) throw new IllegalArgumentException("Invalid tile");
        catalog.addAll(rows);
    }
    synchronized void complete(Bundle b) {
        if (!accepts(b)) return; result = new Bundle(b); result.putInt("count", catalog.size());
    }
    synchronized void cancel(long id) {
        if (pending != null && id == sequence && result == null && "catalog".equals(pending.getString("action"))) {
            result = new Bundle(); result.putString("error", "Отменено");
        }
    }
    private boolean accepts(Bundle b) { return b != null && pending != null && pending.getBoolean("claimed") && result == null && b.getLong("id") == sequence; }
    synchronized Bundle status(long id, long now) {
        expire(now); Bundle b = result == null ? new Bundle() : new Bundle(result);
        b.putLong("id", sequence); b.putString("state", id != sequence ? "expired" : result == null ? "pending" : result.getString("error", "").isEmpty() ? "done" : "error");
        return b;
    }
    synchronized Bundle read(long id, int offset, long now) {
        Bundle state = status(id, now);
        if (!"done".equals(state.getString("state")) || offset < 0 || offset > catalog.size()) throw new IllegalStateException("Catalogue not ready");
        state.putStringArrayList("rows", new ArrayList<>(catalog.subList(offset, pageEnd(catalog, offset)))); return state;
    }
    synchronized boolean changing(long now) { expire(now); return pending != null && result == null && !"catalog".equals(pending.getString("action")); }
    private void expire(long now) {
        if (pending != null && result == null && now - started > 30_000) {
            result = new Bundle(); result.putString("error", "Нет подтверждения SystemUI. Обновите шторку и повторите.");
        }
    }
}
