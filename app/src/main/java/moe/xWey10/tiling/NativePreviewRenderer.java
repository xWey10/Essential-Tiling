package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Draws the actual QSPanel with actual children; a finally block restores the live tree. */
final class NativePreviewRenderer {
    // Keep text, icons and tile endpoints at the real QS resolution. The settings View
    // performs the single final fit to its available width; a 720px intermediate blurred it.
    static float scale(View panel) { return 1f; }

    static Bitmap captureBackground(View panel, View scrim) {
        if (scrim == null || panel.getMeasuredWidth() <= 0 || panel.getMeasuredHeight() <= 0) return null;
        float scale = scale(panel);
        int extra = Math.round(80 * panel.getResources().getDisplayMetrics().density);
        Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(panel.getMeasuredWidth() * scale)),
                Math.max(1, Math.round((panel.getMeasuredHeight() + extra) * scale)), Bitmap.Config.ARGB_8888);
        int[] origin = new int[2], source = new int[2];
        panel.getLocationOnScreen(origin); scrim.getLocationOnScreen(source);
        Canvas canvas = new Canvas(image); canvas.scale(scale, scale);
        canvas.translate(source[0] - origin[0], source[1] - origin[1]);
        scrim.draw(canvas);
        return image;
    }

    static NativePreviewServer.Frame render(ViewGroup panel, ViewGroup workspace, List<View> tiles,
                                            View brightness, List<View> batteries, Config draft,
                                            int requestedPage, Bitmap background,
                                            Runnable applyDraft, Runnable restoreApplied) {
        return render(panel, workspace, tiles, brightness, batteries, draft, requestedPage, background,
                applyDraft, restoreApplied, null, null);
    }

    static NativePreviewServer.Frame render(ViewGroup panel, ViewGroup workspace, List<View> tiles,
                                            View brightness, List<View> batteries, Config draft,
                                            int requestedPage, Bitmap background, Runnable applyDraft,
                                            Runnable restoreApplied, NativeGrid grid, NativeGrid.Session session) {
        if (background == null || panel.getMeasuredWidth() <= 0 || workspace.getChildCount() == 0) {
            throw new IllegalStateException("Полностью откройте шторку и закройте её, затем нажмите «Обновить».");
        }
        int width = panel.getMeasuredWidth(), pages = workspace.getChildCount();
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        ViewTreeState saved = new ViewTreeState(panel);
        NativePreviewPose pose=new NativePreviewPose(panel);
        ArrayList<View> removed = new ArrayList<>();
        ArrayList<ViewGroup> parents = new ArrayList<>();
        ArrayList<Integer> indexes = new ArrayList<>();
        BatteryRow temporary = null;
        Bitmap bitmap = null;
        try {
            applyDraft.run(); pose.expanded();
            show(panel);
            for (View v = workspace; v != panel && v.getParent() instanceof View; v = (View) v.getParent()) show(v);
            for (int i = 0; i < workspace.getChildCount(); i++) show(workspace.getChildAt(i));
            for (View tile : tiles) if (tile.getVisibility() != View.GONE) show(tile);
            showGridChildren(workspace);
            if (brightness != null) show(brightness);
            View footer = find(panel, "qs_footer");
            if (footer != null) show(footer);
            // Reinsert the same live row after rendering; a temporary native TextView is used if needed.
            for (View row : batteries) if (contains(panel, row) && row.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) row.getParent();
                removed.add(row); parents.add(parent); indexes.add(parent.indexOfChild(row)); parent.removeView(row);
            }
            if (draft.enabled && draft.battery && brightness != null && brightness.getParent() instanceof LinearLayout) {
                LinearLayout parent = (LinearLayout) brightness.getParent();
                temporary = new BatteryRow(brightness.getContext());
                temporary.refreshBattery();
                parent.addView(temporary, parent.indexOfChild(brightness) + 1, new LinearLayout.LayoutParams(-1, -2));
            }
            forceLayout(panel);
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            panel.layout(panel.getLeft(), panel.getTop(), panel.getLeft() + width,
                    panel.getTop() + panel.getMeasuredHeight());
            int scroll;
            try { scroll = (Integer) workspace.getClass().getMethod("getScrollForPage", int.class).invoke(workspace, page); }
            catch (ReflectiveOperationException e) { scroll = workspace.getChildAt(page).getLeft() - workspace.getPaddingLeft(); }
            workspace.scrollTo(scroll, workspace.getScrollY());
            float scale = scale(panel);
            int imageHeight = Math.round(panel.getMeasuredHeight() * scale);
            if (imageHeight <= 0 || imageHeight > 4096) throw new IllegalStateException("Некорректная высота шторки");
            if (grid != null && session != null) {
                return grid.capture(session, page, background, scale, Math.max(1, Math.round(width * scale)), imageHeight);
            }
            bitmap = NativeFrameCapture.capture(panel, background, scale,
                    Math.max(1, Math.round(width * scale)), imageHeight);
            return new NativePreviewServer.Frame(bitmap, page, pages);
        } catch (RuntimeException | LinkageError | OutOfMemoryError e) {
            if (bitmap != null) bitmap.recycle();
            throw e;
        } finally {
            try {
                if (temporary != null && temporary.getParent() instanceof ViewGroup) {
                    ((ViewGroup) temporary.getParent()).removeView(temporary);
                }
                for (int i = 0; i < removed.size(); i++) {
                    parents.get(i).addView(removed.get(i), Math.min(indexes.get(i), parents.get(i).getChildCount()));
                }
                saved.restoreProperties();
            } finally {
                try { restoreApplied.run(); }
                finally { pose.restore(); saved.restoreGeometry(); }
            }
        }
    }

    static boolean contains(View root, View child) {
        for (View v = child; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == root) return true;
        }
        return false;
    }
    @SuppressLint("DiscouragedApi") // IDs belong to the installed SystemUI, not the settings APK.
    static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : root.findViewById(id);
    }
    private static void show(View view) {
        view.setVisibility(View.VISIBLE); view.setAlpha(1); view.setScaleX(1); view.setScaleY(1);
        view.setTranslationX(0); view.setTranslationY(0);
        view.setClipBounds(null);
    }
    private static void forceLayout(View view) {
        view.forceLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forceLayout(group.getChildAt(i));
        }
    }
    private static void showGridChildren(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        boolean grid = view.getClass().getName().equals("com.android.launcher3.ShortcutAndWidgetContainer");
        if (grid) show(group);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (grid && child.getVisibility() != View.GONE) show(child);
            else {
                if (child.getClass().getName().endsWith("UniqueObjectHostView")
                        && child.getVisibility() != View.GONE) show(child);
                showGridChildren(child);
            }
        }
    }
}
