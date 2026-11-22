package com.android.launcher3.util;
public class GridOccupancy {
    public final boolean[][] cells;
    public GridOccupancy(int x,int y) { cells=new boolean[x][y]; }
    public void markCells(int x,int y,int w,int h,boolean value) { for(int col=Math.max(0,x);col<Math.min(cells.length,x+w);col++) for(int row=Math.max(0,y);row<Math.min(cells[col].length,y+h);row++) cells[col][row]=value; }
    public void markCells(com.android.launcher3.ItemInfo info,boolean value) {
        de.robv.android.xposed.XposedBridge.dispatch(com.android.systemui.qs.tileimpl.QSTileViewImpl.method(GridOccupancy.class,"markCells",com.android.launcher3.ItemInfo.class,boolean.class),this,new Object[]{info,value},args->{ markCells(info.cellX,info.cellY,info.spanX,info.spanY,(Boolean)args[1]); return null; });
    }
    public boolean isRegionVacant(int x,int y,int w,int h) { for(int col=x;col<x+w;col++) for(int row=y;row<y+h;row++) if(cells[col][row]) return false; return true; }
}
