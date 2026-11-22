package moe.xWey10.tiling;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.android.launcher3.QSTileItemInfo;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import java.util.Map;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReadableRotationTest {
  @After
  public void clear() {
    de.robv.android.xposed.XposedBridge.hooks.clear();
    NativeForms.draft.remove();
  }

  @Test
  public void readableCaptionUsesTheSameOrthonormalTransformForGlyphAndTextAtEveryAngle() {
    for (boolean rtl : new boolean[] {false, true})
      for (TileForm original :
          new TileForm[] {
            TileForm.RECT,
            new TileForm(0, 0, 0, "0111", "0,1,2,1"),
            TileForm.diagonal(true, false),
            TileForm.diagonal(false, false)
          })
        for (int angle = -180; angle <= 180; angle += 15) {
          TileForm form = original.rotated(angle).base(2, 2);
          RectF b = new RectF(0, 0, 288, 288);
          Matrix matrix = TileRotation.content(form, b, 3, 3, 100, 100, rtl);
          float[] axes = {1, 0, 0, 1};
          matrix.mapVectors(axes);
          assertTrue(
              "Text baseline faces right at " + angle + ", " + original + ", rtl=" + rtl,
              axes[0] >= -.001);
          assertEquals(1, Math.hypot(axes[0], axes[1]), .001);
          assertEquals(1, Math.hypot(axes[2], axes[3]), .001);
          assertEquals(0, axes[0] * axes[2] + axes[1] * axes[3], .001);
          RectF body = form.content(b, 3, 3, 100, 100);
          if (rtl) body.set(b.width() - body.right, body.top, b.width() - body.left, body.bottom);
          RectF actual = TileRotation.point(matrix, body.centerX(), body.centerY());
          Matrix expected = TileRotation.extra(form, b, 3, 3, 100, 100, rtl);
          float base = TileRotation.baseAngle(form, b, 3, 3, 100, 100) * (rtl ? -1 : 1);
          expected.preRotate(base, b.centerX(), b.centerY());
          RectF center = TileRotation.point(expected, body.centerX(), body.centerY());
          assertEquals(center.left, actual.left, .01);
          assertEquals(center.top, actual.top, .01);
        }
  }

  @Test
  public void actualNativeTextAndIconStayTogetherWithoutShrinkingOrClippingWhenTurnedPastVertical()
      throws Exception {
    NativeFormHooksTest.install();
    var context = RuntimeEnvironment.getApplication();
    NativeRootViewportTest.Cells parent = new NativeRootViewportTest.Cells(context);
    QSTileViewImpl tile = new QSTileViewImpl(context);
    parent.addView(tile);
    QSTileItemInfo tag = new QSTileItemInfo("flashlight");
    tag.spanX = tag.spanY = 3;
    tile.setTag(tag);
    tile.setPadding(12, 8, 12, 8);
    tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    View icon = new View(context);
    icon.setBackgroundColor(Color.GREEN);
    tile.addView(icon, new LinearLayout.LayoutParams(20, 20));
    TextView text = new TextView(context);
    text.setText("Flashlight");
    text.setTextSize(16);
    text.setTextColor(Color.BLUE);
    text.setSingleLine(true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = 8;
    tile.addView(text, lp);
    for (boolean rtl : new boolean[] {false, true})
      for (int angle : new int[] {0, 30, 60, 90, 120, 150, 180, -30, -120}) {
        tile.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TileForm form = TileForm.RECT.base(2, 1).rotated(angle);
        NativeForms.draft.set(
            Map.of(
                "t:flashlight",
                new QsLayout.Item("t:flashlight", "Flashlight", 8, 0, 0, 3, 3, true, true, form)));
        tile.forceLayout();
        tile.measure(
            View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY));
        tile.layout(0, 0, 288, 288);
        assertEquals(20, icon.getWidth());
        assertEquals(20, icon.getHeight());
        assertEquals(16, text.getTextSize(), .01);
        assertEquals(1, text.getLineCount());
        Matrix transform =
            TileRotation.content(form, new RectF(0, 0, 288, 288), 3, 3, 100, 100, rtl);
        RectF center =
            TileRotation.point(
                transform,
                (icon.getLeft() + icon.getRight()) / 2f,
                (icon.getTop() + icon.getBottom()) / 2f);
        Bitmap image = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888);
        tile.draw(new Canvas(image));
        assertEquals(
            "Icon and caption use one body transform, angle=" + angle + ", rtl=" + rtl,
            Color.GREEN,
            image.getPixel(Math.round(center.left), Math.round(center.top)));
        int ink = 0;
        for (int y = 0; y < 288; y++)
          for (int x = 0; x < 288; x++) {
            int c = image.getPixel(x, y);
            if (Color.blue(c) > Color.green(c) + 50 && Color.blue(c) > Color.red(c) + 50) ink++;
          }
        assertTrue("The real caption remains visible, " + angle + ", rtl=" + rtl, ink > 100);
        image.recycle();
      }
  }

  @Test
  public void localPreviewAndANewNativeCaptureKeepTheSameCaptionAndGlyphPose() throws Exception {
    NativeFormHooksTest.install();
    var context = RuntimeEnvironment.getApplication();
    NativeRootViewportTest.Cells parent = new NativeRootViewportTest.Cells(context);
    QSTileViewImpl tile = new QSTileViewImpl(context);
    parent.addView(tile);
    QSTileItemInfo tag = new QSTileItemInfo("flashlight");
    tag.spanX = tag.spanY = 3;
    tile.setTag(tag);
    tile.setPadding(12, 8, 12, 8);
    tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    tile.setBackgroundColor(Color.DKGRAY);
    View icon = new View(context);
    icon.setBackgroundColor(Color.GREEN);
    tile.addView(icon, new LinearLayout.LayoutParams(20, 20));
    TextView text = new TextView(context);
    text.setText("Flashlight");
    text.setTextSize(16);
    text.setTextColor(Color.BLUE);
    text.setSingleLine(true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = 8;
    tile.addView(text, lp);
    for (boolean rtl : new boolean[] {false, true})
      for (TileForm base :
          new TileForm[] {
            TileForm.RECT.base(2, 1), new TileForm(0, 0, 0, "0111", "0,1,2,1").base(2, 2)
          })
        for (int direction : new int[] {1, -1}) {
          tile.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
          TileForm from = base.rotated(direction > 0 ? 30 : 120),
              to = base.rotated(direction > 0 ? 120 : 30);
          Bitmap old = render(tile, from), expected = render(tile, to);
          TileImage image = new TileImage(old, new Rect(0, 0, 288, 288), null, 3, 3);
          image.form = from;
          RectF bounds = new RectF(0, 0, 288, 288);
          TileVisual visual = new TileVisual(bounds, image);
          visual.shape(to, rtl);
          visual.form(3, 3, to.corner(), 0, 0, true, null);
          visual.grid(100, 100, bounds);
          Bitmap actual = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888);
          visual.draw(
              new Canvas(actual), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
          for (int color : new int[] {Color.GREEN, Color.BLUE}) {
            float[] a = centroid(actual, color), b = centroid(expected, color);
            assertEquals(
                "Local and native content share an angle and pivot: " + base + ", rtl=" + rtl,
                b[0],
                a[0],
                1.5);
            assertEquals(b[1], a[1], 1.5);
            assertTrue(a[2] > b[2] * .75 && a[2] < b[2] * 1.25);
          }
          old.recycle();
          expected.recycle();
          actual.recycle();
        }
  }

  private static Bitmap render(QSTileViewImpl tile, TileForm form) {
    NativeForms.draft.set(
        Map.of(
            "t:flashlight",
            new QsLayout.Item("t:flashlight", "Flashlight", 8, 0, 0, 3, 3, true, true, form)));
    tile.forceLayout();
    tile.measure(
        View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY));
    tile.layout(0, 0, 288, 288);
    Bitmap image = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888);
    tile.draw(new Canvas(image));
    return image;
  }

  private static float[] centroid(Bitmap image, int color) {
    float x = 0, y = 0, n = 0;
    for (int row = 0; row < image.getHeight(); row++)
      for (int col = 0; col < image.getWidth(); col++) {
        int c = image.getPixel(col, row);
        boolean match =
            color == Color.BLUE
                ? Color.blue(c) > Color.red(c) + 60 && Color.blue(c) > Color.green(c) + 60
                : Color.green(c) > Color.red(c) + 60 && Color.green(c) > Color.blue(c) + 60;
        if (match) {
          x += col;
          y += row;
          n++;
        }
      }
    assertTrue("Native content remains visible", n > 50);
    return new float[] {x / n, y / n, n};
  }
}
