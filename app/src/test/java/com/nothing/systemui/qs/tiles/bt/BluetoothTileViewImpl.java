package com.nothing.systemui.qs.tiles.bt;
public class BluetoothTileViewImpl extends com.android.systemui.qs.tileimpl.QSTileViewImpl {
    public BluetoothTileViewImpl(android.content.Context c) { super(c); }
    @Override public void applySize(int w,int h) {
        de.robv.android.xposed.XposedBridge.dispatch(method(BluetoothTileViewImpl.class,"applySize",int.class,int.class),this,new Object[]{w,h},a->{ super.applySize((Integer)a[0],(Integer)a[1]); return null; });
    }
}
