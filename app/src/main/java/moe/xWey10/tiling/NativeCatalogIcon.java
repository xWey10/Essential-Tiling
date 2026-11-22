package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.util.function.Supplier;

/** Actual QS state glyph, rendered while still in the SystemUI resource context. */
final class NativeCatalogIcon {
    private NativeCatalogIcon() { }
    static String encode(Object state, Context context) {
        try {
            Object supplier = NativeGrid.field(state.getClass(), "iconSupplier").get(state);
            Object icon = supplier instanceof Supplier<?> ? ((Supplier<?>) supplier).get()
                    : NativeGrid.field(state.getClass(), "icon").get(state);
            if (icon == null) return "";
            Drawable original = (Drawable) NativeGrid.call(icon, "getDrawable", new Class<?>[]{Context.class}, context);
            if (original == null) return "";
            Drawable.ConstantState constant = original.getConstantState();
            Drawable drawable = constant == null ? original : constant.newDrawable(context.getResources()).mutate();
            Rect bounds = new Rect(drawable.getBounds());
            try {
                // Preserve source alpha; the settings app applies its own theme foreground.
                for (int side : new int[]{96, 72, 48, 32}) {
                    Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
                    try {
                        int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
                        float scale = width > 0 && height > 0 ? Math.min((float) side / width, (float) side / height) : 1;
                        int w = width > 0 && height > 0 ? Math.max(1, Math.round(width * scale)) : side;
                        int h = width > 0 && height > 0 ? Math.max(1, Math.round(height * scale)) : side;
                        drawable.setBounds((side - w) / 2, (side - h) / 2, (side + w) / 2, (side + h) / 2);
                        drawable.draw(new Canvas(bitmap));
                        int[] pixels = new int[side * side]; bitmap.getPixels(pixels, 0, side, 0, 0, side, side);
                        for (int n = 0; n < pixels.length; n++) pixels[n] = (pixels[n] & 0xff000000) | (Color.WHITE & 0x00ffffff);
                        bitmap.setPixels(pixels, 0, side, 0, 0, side, side);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                        if (bytes.size() <= 4096) return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
                    } finally { bitmap.recycle(); }
                }
            } finally { drawable.setBounds(bounds); }
        } catch (Exception | LinkageError e) {
            android.util.Log.w("EssentialTiling", "Catalogue icon unavailable", e);
        }
        return ""; // A bad glyph must not remove an otherwise available tile from the catalogue.
    }
}
