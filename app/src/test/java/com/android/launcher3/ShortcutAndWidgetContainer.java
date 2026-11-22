package com.android.launcher3;
public class ShortcutAndWidgetContainer extends android.view.ViewGroup {
    public ShortcutAndWidgetContainer(android.content.Context c) { super(c); }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) { }
    public android.view.View getChildAt(int x,int y) {
        return (android.view.View)de.robv.android.xposed.XposedBridge.dispatch(com.android.systemui.qs.tileimpl.QSTileViewImpl.method(ShortcutAndWidgetContainer.class,"getChildAt",int.class,int.class),this,new Object[]{x,y},args->{
            for(int n=0;n<getChildCount();n++) { android.view.View v=getChildAt(n); moe.xWey10.tiling.NativeGridTest.Lp lp=(moe.xWey10.tiling.NativeGridTest.Lp)v.getLayoutParams(); if(x>=lp.cellX&&y>=lp.cellY&&x<lp.cellX+lp.cellHSpan&&y<lp.cellY+lp.cellVSpan) return v; } return null;
        });
    }
}
