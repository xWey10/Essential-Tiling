package moe.xWey10.tiling;

import android.view.View;

/** Keep Nothing's pager in its native coordinates; Android maps touch through this transform. */
final class BluetoothContent {
    private BluetoothContent() { }

    static float scale(int targetWidth, int targetHeight, int nativeWidth, int nativeHeight) {
        if (nativeWidth <= 0 || nativeHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1f;
        return Math.min(1f, Math.min((float) targetWidth / nativeWidth, (float) targetHeight / nativeHeight));
    }

    static void measure(View container, int nativeWidth, int nativeHeight) {
        if (nativeWidth <= 0 || nativeHeight <= 0) return;
        container.measure(View.MeasureSpec.makeMeasureSpec(nativeWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(nativeHeight, View.MeasureSpec.EXACTLY));
    }

    static void layout(View tile, View container, int nativeWidth, int nativeHeight) {
        if (nativeWidth <= 0 || nativeHeight <= 0) return;
        int width = tile.getMeasuredWidth(), height = tile.getMeasuredHeight();
        float scale = scale(width, height, nativeWidth, nativeHeight);
        int left = (width - nativeWidth) / 2, top = (height - nativeHeight) / 2;
        container.layout(left, top, left + nativeWidth, top + nativeHeight);
        container.setPivotX(nativeWidth / 2f);
        container.setPivotY(nativeHeight / 2f);
        container.setScaleX(scale);
        container.setScaleY(scale);
        // Rotation/rotationY, pager state, alpha and all gesture listeners remain native.
    }
}
