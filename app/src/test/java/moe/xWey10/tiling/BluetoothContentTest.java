package moe.xWey10.tiling;

import android.content.Context;
import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BluetoothContentTest {
    @Test public void nativeCoordinatesFitSmallWideAndLargeTilesWithoutAccumulatingScale() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout tile = new FrameLayout(context);
        FrameLayout pagerContainer = new FrameLayout(context);
        tile.addView(pagerContainer, new FrameLayout.LayoutParams(-1, -1));
        pagerContainer.setRotation(-90);
        pagerContainer.setRotationY(180);
        pagerContainer.setAlpha(.65f);
        for (int[] bounds : new int[][]{{74, 74, 48, 48}, {156, 74, 113, 48},
                {156, 156, 113, 113}, {156, 156, 156, 156}}) {
            layout(tile, pagerContainer, bounds[0], bounds[1], bounds[2], bounds[3]);
            assertEquals(bounds[0], pagerContainer.getMeasuredWidth());
            assertEquals(bounds[1], pagerContainer.getMeasuredHeight());
            float scale = pagerContainer.getScaleX();
            assertTrue(bounds[0] * scale <= bounds[2] + .001f);
            assertTrue(bounds[1] * scale <= bounds[3] + .001f);
            assertEquals(bounds[2] / 2f, pagerContainer.getLeft() + bounds[0] / 2f, .5f);
            assertEquals(bounds[3] / 2f, pagerContainer.getTop() + bounds[1] / 2f, .5f);
            assertEquals(-90f, pagerContainer.getRotation(), 0f);
            assertEquals(180f, pagerContainer.getRotationY(), 0f);
            assertEquals(.65f, pagerContainer.getAlpha(), 0f);
        }
        assertEquals(1f, pagerContainer.getScaleX(), 0f);
        assertSame(pagerContainer, tile.getChildAt(0));
    }

    @Test public void nativeChildStillReceivesClickThroughScaledAndRotatedContainer() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible()) {
        Context context = controller.get();
        FrameLayout tile = new FrameLayout(context);
        FrameLayout pagerContainer = new FrameLayout(context);
        tile.addView(pagerContainer, new FrameLayout.LayoutParams(-1, -1));
        View nativeButton = new View(context);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(40, 40);
        p.leftMargin = 20; p.topMargin = 20;
        pagerContainer.addView(nativeButton, p);
        AtomicInteger clicks = new AtomicInteger();
        nativeButton.setOnClickListener(v -> clicks.incrementAndGet());
        controller.get().setContentView(tile);
        shadowOf(Looper.getMainLooper()).idle();
        layout(tile, pagerContainer, 156, 156, 112, 112);
        float scale = pagerContainer.getScaleX();
        float offset = (40 - 78) * scale;
        tap(tile, 56 + offset, 56 + offset);
        assertEquals(1, clicks.get());
        pagerContainer.setRotation(-90);
        tap(tile, 56 + offset, 56 - offset);
        assertEquals(2, clicks.get());
        assertSame(nativeButton, pagerContainer.getChildAt(0));
        }
    }

    private static void layout(FrameLayout tile, View container, int nativeWidth, int nativeHeight,
                               int targetWidth, int targetHeight) {
        tile.measure(View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY));
        BluetoothContent.measure(container, nativeWidth, nativeHeight);
        tile.layout(0, 0, targetWidth, targetHeight);
        BluetoothContent.layout(tile, container, nativeWidth, nativeHeight);
    }

    private static void tap(View tile, float x, float y) {
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0);
        try {
            assertTrue(tile.dispatchTouchEvent(down));
            assertTrue(tile.dispatchTouchEvent(up));
            shadowOf(Looper.getMainLooper()).idle();
        } finally { down.recycle(); up.recycle(); }
    }
}
