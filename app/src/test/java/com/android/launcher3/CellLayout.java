package com.android.launcher3;
public class CellLayout extends android.widget.FrameLayout {
    public final com.android.launcher3.util.GridOccupancy mOccupied=new com.android.launcher3.util.GridOccupancy(4,4);
    final ShortcutAndWidgetContainer container;
    public CellLayout(android.content.Context c) { super(c); container=new ShortcutAndWidgetContainer(c); addView(container); }
    public ShortcutAndWidgetContainer getShortcutsAndWidgets() { return container; }
    public int getCellWidth() { return 100; } public int getCellHeight() { return 100; }
    public void commitTempPlacement() {
        de.robv.android.xposed.XposedBridge.dispatch(com.android.systemui.qs.tileimpl.QSTileViewImpl.method(CellLayout.class,"commitTempPlacement"),this,new Object[]{},args->{
            for(int n=0;n<container.getChildCount();n++) { moe.xWey10.tiling.NativeGridTest.Lp lp=(moe.xWey10.tiling.NativeGridTest.Lp)container.getChildAt(n).getLayoutParams(); mOccupied.markCells(lp.cellX,lp.cellY,lp.cellHSpan,lp.cellVSpan,true); } return null;
        });
    }
    public void markCellsAsOccupiedForView(android.view.View v) { marker("markCellsAsOccupiedForView",v,true); }
    public void markCellsAsUnoccupiedForView(android.view.View v) { marker("markCellsAsUnoccupiedForView",v,false); }
    private void marker(String method,android.view.View view,boolean value) {
        de.robv.android.xposed.XposedBridge.dispatch(com.android.systemui.qs.tileimpl.QSTileViewImpl.method(CellLayout.class,method,android.view.View.class),this,new Object[]{view},args->{ moe.xWey10.tiling.NativeGridTest.Lp lp=(moe.xWey10.tiling.NativeGridTest.Lp)view.getLayoutParams(); mOccupied.markCells(lp.cellX,lp.cellY,lp.cellHSpan,lp.cellVSpan,value); return null; });
    }
}
