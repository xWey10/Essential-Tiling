package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Full content is always painted at its native scale. Only an empty background is nine-sliced. */
final class TileImage {
    TileForm form=TileForm.RECT;
    final Bitmap atlas;
    final Rect source, background;
    final int w, h;
    final float radius, width, height;
    private final Rect piece = new Rect();
    private final RectF destination = new RectF();
    TileImage(Bitmap atlas, Rect source, Rect background, int w, int h) {
        this(atlas, source, background, w, h, source.width(), source.height());
    }
    TileImage(Bitmap atlas, Rect source, Rect background, int w, int h, float width, float height) {
        this.atlas = atlas; this.source = source; this.background = background; this.w = w; this.h = h;
        this.width = width; this.height = height;
        radius = corner(atlas, background == null ? source : background) * Math.min(width / source.width(), height / source.height());
    }
    private Integer surface;
    int surface() {
        if(surface!=null) return surface;
        // A selected tile's empty native layer can still be in flight. Read its real body surface
        // near the top edge, before icons/text, rather than leaving the new branch transparent.
        RectF body=form.content(new RectF(source),w,h); int x=Math.max(source.left,Math.min(source.right-1,Math.round(body.centerX())));
        for(int y=Math.max(source.top,(int)body.top);y<Math.min(source.bottom,(int)body.centerY());y++) {
            int color=atlas.getPixel(x,y);
            if((color>>>24)>=250) return surface=color;
        }
        return surface=android.graphics.Color.TRANSPARENT;
    }
    void content(Canvas canvas, Paint paint, RectF bounds) {
        destination.set(bounds.left, bounds.top, bounds.left + width, bounds.top + height);
        canvas.drawBitmap(atlas, source, destination, paint);
    }
    void background(Canvas canvas, Paint paint, RectF bounds) {
        if (background == null) return;
        int inset = Math.min((Math.min(background.width(), background.height()) - 1) / 2, Math.max(1, (int) Math.ceil(radius + 2)));
        float dx = Math.min(inset, bounds.width() / 2), dy = Math.min(inset, bounds.height() / 2);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int l = edge(col, background.left, background.right, inset), r = edge(col + 1, background.left, background.right, inset);
            int t = edge(row, background.top, background.bottom, inset), b = edge(row + 1, background.top, background.bottom, inset);
            if (r <= l || b <= t) continue;
            piece.set(l, t, r, b);
            destination.set(edge(col, bounds.left, bounds.right, dx), edge(row, bounds.top, bounds.bottom, dy),
                    edge(col + 1, bounds.left, bounds.right, dx), edge(row + 1, bounds.top, bounds.bottom, dy));
            canvas.drawBitmap(atlas, piece, destination, paint);
        }
    }
    private static int edge(int n, int low, int high, int inset) { return n == 0 ? low : n == 1 ? low + inset : n == 2 ? high - inset : high; }
    private static float edge(int n, float low, float high, float inset) { return n == 0 ? low : n == 1 ? low + inset : n == 2 ? high - inset : high; }
    private static float corner(Bitmap bitmap, Rect r) {
        // Infer the real native corner from alpha; no theme radius is invented by the editor.
        int half = Math.min(r.width(), r.height()) / 2;
        for (int y = 0; y < Math.min(half, 4); y++) for (int x = 0; x <= half; x++) {
            if ((bitmap.getPixel(r.left + x, r.top + y) >>> 24) >= 128) {
                float px = x + .5f, py = y + .5f;
                return x == 0 ? 0 : Math.min(half, px + py + (float) Math.sqrt(2 * px * py));
            }
        }
        return half;
    }
}
