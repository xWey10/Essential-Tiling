package moe.xWey10.tiling;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Cell coordinates, not pixels. The base identifies the native layout the draft was made from. */
final class QsLayout {
    static final int MAX_JSON = 96_000;
    record Page(long id, int columns, int rows) { }
    record Item(String id, String title, long page, int x, int y, int w, int h,
                boolean editable, boolean tall, TileForm form) {
        Item(String id,String title,long page,int x,int y,int w,int h,boolean editable,boolean tall) { this(id,title,page,x,y,w,h,editable,tall,TileForm.RECT); }
        Item shaped(TileForm f) { return new Item(id,title,page,x,y,w,h,editable,tall,f); }
        Item at(int x, int y, int w, int h) { return new Item(id, title, page, x, y, w, h, editable, tall, form.baseW()==0&&form.cellular()&&(w!=this.w||h!=this.h)?TileForm.RECT.attributes(form):form.fit(w,h)); }
        boolean occupies(int column,int row) { return form.occupied(column-x,row-y,w,h); }
        int cellCount() { int count=0; for(int row=0;row<h;row++) for(int col=0;col<w;col++) if(form.occupied(col,row,w,h)) count++; return count; }
        boolean boundsOverlap(Item b) { return page==b.page&&x<b.x+b.w&&b.x<x+w&&y<b.y+b.h&&b.y<y+h; }
        boolean overlaps(Item b) {
            if(!boundsOverlap(b)) return false;
            for(int row=Math.max(y,b.y);row<Math.min(y+h,b.y+b.h);row++)
                for(int col=Math.max(x,b.x);col<Math.min(x+w,b.x+b.w);col++) if(occupies(col,row)&&b.occupies(col,row)) return true;
            return false;
        }
        boolean blocks(Item b) { return overlaps(b)&&!TileLayers.stackable(this,b); }
        boolean samePlace(Item b) { return page == b.page && x == b.x && y == b.y && w == b.w && h == b.h && form.equals(b.form); }
        boolean permits(int width, int height) {
            return width >= 1 && height >= 1 && width <= 24 && height <= 24;
        }
    }
    final String base;
    final boolean portrait;
    final List<Page> pages;
    final List<Item> items;
    QsLayout(String base, boolean portrait, List<Page> pages, List<Item> items) {
        this.base = base; this.portrait = portrait; this.pages = List.copyOf(pages); this.items = List.copyOf(items);
    }
    Item item(String id) { for (Item item : items) if (item.id.equals(id)) return item; return null; }
    Page page(long id) { for (Page page : pages) if (page.id == id) return page; return null; }
    QsLayout asSource() { return new QsLayout(fingerprint(), portrait, pages, items); }
    String fingerprint() {
        try {
            ArrayList<Item> ordered = new ArrayList<>();
            for (Item i : items) ordered.add(new Item(i.id, "", i.page, i.x, i.y, i.w, i.h, i.editable, i.tall, i.form));
            ordered.sort(Comparator.comparing(Item::id));
            String canonical = new QsLayout("", portrait, pages, ordered).json();
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    boolean differsFrom(QsLayout source) {
        if (source == null || items.size() != source.items.size()) return true;
        for (Item i : items) { Item original = source.item(i.id); if (original == null || !i.samePlace(original)) return true; }
        return false;
    }
    void validateAgainst(QsLayout source) {
        if (!base.equals(source.base) || portrait != source.portrait || !pages.equals(source.pages)
                || items.size() != source.items.size()) throw stale();
        HashSet<String> ids = new HashSet<>();
        for (Item i : items) {
            Item old = source.item(i.id); Page p = page(i.page);
            if (old == null || !ids.add(i.id) || old.page != i.page || old.editable != i.editable
                    || old.tall != i.tall || p == null || i.x < 0 || i.y < 0 || i.w < 1 || i.h < 1
                    || i.x + i.w > p.columns || i.y + i.h > p.rows || !i.form.valid(i.w,i.h)) throw invalid();
            if (!old.samePlace(i) && (!portrait || !old.editable || !old.permits(i.w, i.h))) throw invalid();
            for (Item b : items) if (i != b && i.blocks(b)) throw invalid();
        }
    }
    /** Keep unaffected tiles still, relocate intersecting tiles to the nearest free cells. */
    QsLayout edit(String id, int x, int y, int w, int h) {
        return edit(id,x,y,w,h,null);
    }
    QsLayout edit(String id,int x,int y,int w,int h,TileForm form) {
        Item old = item(id); if (old == null || !portrait || !old.editable || !old.permits(w, h)) return null;
        Page p = page(old.page); Item changed = old.at(x, y, w, h);
        if(form!=null) changed=changed.shaped(form);
        if(!changed.form.valid(w,h)) return null;
        if (!inside(changed, p)) return null;
        ArrayList<Item> fixed = new ArrayList<>(), displaced = new ArrayList<>();
        fixed.add(changed);
        for (Item i : items) if (!i.id.equals(id)) {
            if (i.blocks(changed)) { if (!i.editable) return null; displaced.add(i); }
            else fixed.add(i);
        }
        displaced.sort(Comparator.comparingInt(Item::cellCount).reversed());
        for (Item i : displaced) {
            Item best = null; int distance = Integer.MAX_VALUE;
            for (int row = 0; row <= p.rows - i.h; row++) for (int col = 0; col <= p.columns - i.w; col++) {
                Item candidate = i.at(col, row, i.w, i.h);
                boolean occupied = false; for (Item b : fixed) if (candidate.blocks(b)) { occupied = true; break; }
                int d = Math.abs(col - i.x) + Math.abs(row - i.y);
                if (!occupied && d < distance) { best = candidate; distance = d; }
            }
            if (best == null) return null;
            fixed.add(best);
        }
        ArrayList<Item> ordered = new ArrayList<>();
        for (Item original : items) for (Item i : fixed) if (original.id.equals(i.id)) { ordered.add(i); break; }
        return new QsLayout(base, portrait, pages, ordered);
    }
    private static boolean inside(Item i, Page p) { return p != null && i.x >= 0 && i.y >= 0 && i.x + i.w <= p.columns && i.y + i.h <= p.rows; }
    static IllegalStateException stale() { return new IllegalStateException("Раскладка шторки изменилась. Отмените черновик и обновите предпросмотр."); }
    private static IllegalStateException invalid() { return new IllegalStateException("Некорректная раскладка плиток."); }
    String json() {
        try {
            JSONObject root = new JSONObject().put("base", base).put("portrait", portrait);
            JSONArray ps = new JSONArray(), list = new JSONArray();
            for (Page p : pages) ps.put(new JSONArray().put(p.id).put(p.columns).put(p.rows));
            for (Item i : items) list.put(new JSONObject().put("id", i.id).put("title", i.title).put("page", i.page)
                    .put("x", i.x).put("y", i.y).put("w", i.w).put("h", i.h).put("edit", i.editable).put("tall", i.tall).put("corner",i.form.corner()).put("cutW",i.form.cutW()).put("cutH",i.form.cutH()).put("cells",i.form.cells()).put("body",i.form.body()).put("nubs",i.form.nubs()).put("rotation",i.form.rotation()).put("seams",i.form.seams()).put("layer",i.form.layer()).put("baseW",i.form.baseW()).put("baseH",i.form.baseH()));
            return root.put("pages", ps).put("items", list).toString();
        } catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    static QsLayout parse(String text) {
        if (text == null || text.length() > MAX_JSON) throw invalid();
        try {
            JSONObject root = new JSONObject(text); JSONArray ps = root.getJSONArray("pages"), list = root.getJSONArray("items");
            if (ps.length() < 1) throw invalid(); // JSON byte budget bounds IPC; no arbitrary page/tile count cap.
            ArrayList<Page> pages = new ArrayList<>(); ArrayList<Item> items = new ArrayList<>();
            for (int n = 0; n < ps.length(); n++) {
                JSONArray p = ps.getJSONArray(n); int cols = p.getInt(1), rows = p.getInt(2);
                if (cols < 1 || cols > 24 || rows < 1 || rows > 24) throw invalid();
                pages.add(new Page(p.getLong(0), cols, rows));
            }
            for (int n = 0; n < list.length(); n++) {
                JSONObject i = list.getJSONObject(n);
                TileForm form=new TileForm(i.optInt("corner"),i.optInt("cutW"),i.optInt("cutH"),i.optString("cells"),i.optString("body"),i.optBoolean("nubs"),(float)i.optDouble("rotation",0),i.optString("seams"),i.optInt("layer"),i.optInt("baseW"),i.optInt("baseH"));
                if(!i.has("seams")) form=TileSeams.legacy(form,i.getInt("w"),i.getInt("h"));
                items.add(new Item(i.getString("id"), i.optString("title"), i.getLong("page"), i.getInt("x"), i.getInt("y"),
                        i.getInt("w"), i.getInt("h"), i.getBoolean("edit"), i.getBoolean("tall"),form));
            }
            return new QsLayout(root.getString("base"), root.getBoolean("portrait"), pages, items);
        } catch (JSONException e) { throw invalid(); }
    }
}
