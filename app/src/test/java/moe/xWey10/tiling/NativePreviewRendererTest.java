package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativePreviewRendererTest {
    @Test public void rendersActualChildrenAndDraftThenRestoresNativeBoundsAndStyle() {
        Fixture f = new Fixture();
        int width = f.tile.getWidth(), left = f.tile.getLeft();
        float alpha = f.tile.getAlpha();
        NativePreviewServer.Frame frame = f.render(() -> {
            f.drawable.setCornerRadius(0);
            f.tile.getLayoutParams().width = 60;
            f.tile.setPaddingRelative(6, 2, 7, 3);
        }, () -> { });
        assertEquals(1, frame.pages);
        assertEquals(Color.RED, frame.bitmap.getPixel(25, 25));
        assertEquals(Color.BLACK, frame.bitmap.getPixel(100, 110));
        assertEquals(width, f.tile.getWidth()); assertEquals(left, f.tile.getLeft());
        assertEquals(alpha, f.tile.getAlpha(), 0f);
        assertEquals(20f, f.drawable.getCornerRadius(), 0f);
        assertEquals("My actual tile", f.tile.getText().toString());
        assertSame(f.tile, f.page.getChildAt(0));
        assertFalse(f.tile.isPivotSet());
        assertFalse(f.tile.isPaddingRelative());
        frame.bitmap.recycle();
    }

    @Test public void renderFailureStillRestoresTreeAndInvokesAppliedConfigRestore() {
        Fixture f = new Fixture();
        AtomicBoolean restored = new AtomicBoolean();
        int originalWidth = f.tile.getLayoutParams().width;
        assertThrows(IllegalStateException.class, () -> f.render(() -> {
            f.drawable.setCornerRadius(3);
            f.tile.getLayoutParams().width = 40;
            f.tile.setTranslationX(19);
            throw new IllegalStateException("controlled render error");
        }, () -> restored.set(true)));
        assertTrue(restored.get());
        assertEquals(originalWidth, f.tile.getLayoutParams().width);
        assertEquals(0f, f.tile.getTranslationX(), 0f);
        assertEquals(20f, f.drawable.getCornerRadius(), 0f);
        assertSame(f.tile, f.page.getChildAt(0));
    }

    @Test public void selectedPageUsesExistingPageAndRestoresScroll() {
        Fixture f = new Fixture();
        FrameLayout other = new FrameLayout(f.context);
        View marker = new View(f.context); marker.setBackgroundColor(Color.GREEN);
        other.addView(marker, new FrameLayout.LayoutParams(200, 150));
        f.workspace.addView(other, new LinearLayout.LayoutParams(200, 150));
        f.measure();
        NativePreviewServer.Frame frame = NativePreviewRenderer.render(f.panel, f.workspace,
                List.of(f.tile), null, List.of(), moe.xWey10.tiling.Config.DEFAULT, 1, f.background,
                () -> { }, () -> { });
        assertEquals(2, frame.pages); assertEquals(1, frame.page);
        assertEquals(Color.GREEN, frame.bitmap.getPixel(30, 50));
        assertEquals(0, f.workspace.getScrollX());
        frame.bitmap.recycle();
    }

    @Test public void absentNativeSourceIsAnErrorAndDoesNotApplyDraft() {
        Fixture f = new Fixture();
        AtomicBoolean applied = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> NativePreviewRenderer.render(f.panel, f.workspace,
                List.of(f.tile), null, List.of(), moe.xWey10.tiling.Config.DEFAULT, 0, null,
                () -> applied.set(true), () -> { }));
        assertFalse(applied.get());
    }

    @Test public void draftFrameMatchesSameTreeAfterApplyingSameGeometry() {
        Fixture f = new Fixture();
        Runnable style = () -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) f.tile.getLayoutParams();
            p.width = TileGeometry.size(100, 85, 4, 48);
            p.height = TileGeometry.size(100, 85, 4, 48);
            p.leftMargin = TileGeometry.centeredOrigin(20, 100, p.width);
            p.topMargin = TileGeometry.centeredOrigin(20, 100, p.height);
            f.drawable.setCornerRadius(12);
            f.tile.requestLayout();
        };
        NativePreviewServer.Frame preview = f.render(style, () -> { });
        // Independent application path: style the same tree, then do normal measure/layout/draw.
        style.run(); f.tile.setAlpha(1); f.measure();
        Bitmap applied = NativeFrameCapture.capture(f.panel, f.background, 1, 200, 150);
        assertTrue(preview.bitmap.sameAs(applied));
        preview.bitmap.recycle(); applied.recycle();
    }

    private static final class Fixture {
        final Context context = RuntimeEnvironment.getApplication();
        final LinearLayout panel = new LinearLayout(context);
        final LinearLayout workspace = new LinearLayout(context);
        final FrameLayout page = new FrameLayout(context);
        final TextView tile = new TextView(context);
        final GradientDrawable drawable = new GradientDrawable();
        final Bitmap background = Bitmap.createBitmap(200, 180, Bitmap.Config.ARGB_8888);
        Fixture() {
            background.eraseColor(Color.BLACK);
            panel.setOrientation(LinearLayout.VERTICAL); workspace.setOrientation(LinearLayout.HORIZONTAL);
            panel.addView(workspace, new LinearLayout.LayoutParams(200, 150));
            workspace.addView(page, new LinearLayout.LayoutParams(200, 150));
            drawable.setColor(Color.RED); drawable.setCornerRadius(20);
            tile.setBackground(drawable); tile.setText("My actual tile"); tile.setTextColor(Color.WHITE);
            tile.setAlpha(.8f);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(100, 100);
            p.leftMargin = 20; p.topMargin = 20;
            page.addView(tile, p); measure();
        }
        void measure() {
            panel.forceLayout();
            panel.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            panel.layout(0, 0, panel.getMeasuredWidth(), panel.getMeasuredHeight());
        }
        NativePreviewServer.Frame render(Runnable apply, Runnable restore) {
            return NativePreviewRenderer.render(panel, workspace, List.of(tile), null, List.of(),
                    moe.xWey10.tiling.Config.DEFAULT, 0, background, apply, restore);
        }
    }
}
