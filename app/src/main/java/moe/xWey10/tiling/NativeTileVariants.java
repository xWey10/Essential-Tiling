package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Real OEM endpoints plus their empty native backgrounds, for content-preserving size morphs. */
final class NativeTileVariants {
    record Image(int w, int h, Bitmap full, Bitmap background) {
        void recycle() { full.recycle(); background.recycle(); }
    }
    static ArrayList<Image> capture(View tile, QsLayout.Item item, float scale, Consumer<View> beforeResize)
            throws ReflectiveOperationException {
        ArrayList<Image> images = new ArrayList<>();
        ViewTreeState saved = new ViewTreeState(tile);
        Object tag = tile.getTag(), lp = tile.getLayoutParams();
        ViewGroup parent = (ViewGroup) tile.getParent();
        BluetoothPreviewState bluetooth = isBluetooth(tile) ? new BluetoothPreviewState(tile) : null;
        java.util.Map<String,QsLayout.Item> previousForms=NativeForms.draft.get();
        try {
            int[][] sizes = item.tall() ? new int[][]{{1, 1}, {2, 1}, {2, 2}} : new int[][]{{1, 1}, {2, 1}};
            for (int[] size : sizes) {
                java.util.HashMap<String,QsLayout.Item> forms=new java.util.HashMap<>(previousForms==null?java.util.Map.of():previousForms);
                forms.put(item.id(),item.at(item.x(),item.y(),size[0],size[1]).shaped(TileForm.RECT)); NativeForms.draft.set(forms);
                Object copy = tag.getClass().getConstructor(tag.getClass()).newInstance(tag);
                NativeGrid.field(copy.getClass(), "spanX").setInt(copy, size[0]);
                NativeGrid.field(copy.getClass(), "spanY").setInt(copy, size[1]);
                tile.setTag(copy);
                NativeGrid.field(lp.getClass(), "cellHSpan").setInt(lp, size[0]);
                NativeGrid.field(lp.getClass(), "cellVSpan").setInt(lp, size[1]);
                beforeResize.accept(tile);
                NativeGrid.call(tile, "applySize", new Class<?>[]{int.class, int.class}, size[0], size[1]);
                resources(tile);
                force(tile);
                NativeGrid.call(parent, "measureChild", new Class<?>[]{View.class}, tile);
                tile.layout(tile.getLeft(), tile.getTop(), tile.getLeft() + tile.getMeasuredWidth(), tile.getTop() + tile.getMeasuredHeight());
                tile.setVisibility(View.VISIBLE);
                int width = Math.max(1, Math.round(tile.getWidth() * scale));
                int height = Math.max(1, Math.round(tile.getHeight() * scale));
                Bitmap full = NativeFrameCapture.capture(tile, null, scale, width, height);
                Bitmap background = null;
                try {
                    background = background(tile, scale, width, height);
                    images.add(new Image(size[0], size[1], full, background));
                } catch (RuntimeException | Error e) { full.recycle(); if (background != null) background.recycle(); throw e; }
            }
            return images;
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            for (Image image : images) image.recycle();
            throw e;
        } finally {
            if(previousForms==null) NativeForms.draft.remove(); else NativeForms.draft.set(previousForms);
            NativeForms.forget(tile);
            try {
                tile.setTag(tag);
                NativeGrid.field(lp.getClass(), "cellHSpan").setInt(lp, item.w());
                NativeGrid.field(lp.getClass(), "cellVSpan").setInt(lp, item.h());
                try {
                    beforeResize.accept(tile);
                    NativeGrid.call(tile, "applySize", new Class<?>[]{int.class, int.class}, item.w(), item.h());
                    resources(tile);
                    if (bluetooth != null) bluetooth.restore();
                } finally { saved.restoreProperties(); saved.restoreGeometry(); }
            } catch (ReflectiveOperationException | RuntimeException | Error e) {
                for (Image image : images) image.recycle(); throw e;
            }
        }
    }
    private static Bitmap background(View tile, float scale, int width, int height) {
        if (!(tile instanceof ViewGroup)) return NativeFrameCapture.capture(tile, null, scale, width, height);
        ViewGroup group = (ViewGroup) tile;
        int[] visibility = new int[group.getChildCount()];
        for (int n = 0; n < visibility.length; n++) {
            View child = group.getChildAt(n); visibility[n] = child.getVisibility();
            if (visibility[n] != View.GONE) child.setVisibility(View.INVISIBLE);
        }
        try { return NativeFrameCapture.capture(tile, null, scale, width, height); }
        finally { for (int n = 0; n < visibility.length; n++) group.getChildAt(n).setVisibility(visibility[n]); }
    }
    private static void resources(View tile) throws ReflectiveOperationException {
        NativeGrid.call(tile, "updateResources"); NativeGrid.call(tile, "updateLayout");
    }
    private static void force(View view) {
        view.forceLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) force(group.getChildAt(n));
        }
    }
    private static boolean isBluetooth(View view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl")) return true;
        }
        return false;
    }
}
