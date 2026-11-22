package com.android.launcher3;

/** Test double of the audited OEM data object; never packaged in the APK. */
public class QSTileItemInfo extends ItemInfo {
    private final String tileSpec;
    public QSTileItemInfo(String spec) { tileSpec = spec; }
    public QSTileItemInfo(QSTileItemInfo other) {
        tileSpec = other.tileSpec; id = other.id; screenId = other.screenId;
        cellX = other.cellX; cellY = other.cellY; spanX = other.spanX; spanY = other.spanY; title = other.title;
    }
    public String getTileSpec() { return tileSpec; }
    public boolean isAllowExtendHeight() { return !tileSpec.equals("ringer"); }
}
