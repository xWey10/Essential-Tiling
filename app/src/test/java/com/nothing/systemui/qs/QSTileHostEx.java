package com.nothing.systemui.qs;
import com.android.launcher3.QSTileItemInfo;
import java.util.Collection;
public class QSTileHostEx {
    public static String tileItemInfoToNTString(Collection<QSTileItemInfo> infos) {
        StringBuilder value = new StringBuilder("native-format:");
        for (QSTileItemInfo i : infos) value.append(i.getTileSpec()).append(':').append(i.screenId).append(':')
                .append(i.cellX).append(':').append(i.cellY).append(':').append(i.spanX).append(':').append(i.spanY).append(';');
        return value.toString();
    }
    public static String tileItemInfoToString(Collection<QSTileItemInfo> infos) {
        StringBuilder value = new StringBuilder(); for (QSTileItemInfo i : infos) value.append(i.getTileSpec()).append(','); return value.toString();
    }
}
