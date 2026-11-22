package com.android.systemui.qs;
public interface QSHost {
 int getUserId(); void refreshTiles();
 default android.content.Context getContext() { return com.android.systemui.qs.customize.TileQueryHelper.context(); }
 default com.android.systemui.plugins.qs.QSTile createTile(String spec) { return com.android.systemui.qs.customize.TileQueryHelper.create(spec); }
}
