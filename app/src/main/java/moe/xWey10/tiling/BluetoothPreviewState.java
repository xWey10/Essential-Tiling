package moe.xWey10.tiling;

import android.view.View;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

/** OEM applySize changes the selected pager page and cached references, including detached pages. */
final class BluetoothPreviewState {
    private final Object tile, pager;
    private final int page;
    private final ArrayList<Field> refs = new ArrayList<>();
    private final ArrayList<Object> values = new ArrayList<>();
    private final ArrayList<ViewTreeState> pages = new ArrayList<>();
    BluetoothPreviewState(Object tile) throws ReflectiveOperationException {
        this.tile = tile; Class<?> c = tile.getClass();
        pager = NativeGrid.field(c, "mPager").get(tile);
        page = pager == null ? 0 : (Integer) NativeGrid.call(pager, "getCurrentItem");
        for (String name : new String[]{"mLabel", "mSecondaryLabel", "mIconContainer", "mIconView", "mClickView"}) {
            Field f = NativeGrid.field(c, name); refs.add(f); values.add(f.get(tile));
        }
        Map<?, ?> map = (Map<?, ?>) NativeGrid.field(c, "mPagesViewList").get(tile);
        for (Object view : map.values()) if (view instanceof View) pages.add(new ViewTreeState((View) view));
    }
    void restore() {
        try {
            if (pager != null) NativeGrid.call(pager, "setCurrentItem", new Class<?>[]{int.class, boolean.class}, page, false);
            for (int n = 0; n < refs.size(); n++) refs.get(n).set(tile, values.get(n));
            for (ViewTreeState state : pages) { state.restoreProperties(); state.restoreGeometry(); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Восстановление страниц Bluetooth", e); }
    }
}
