package moe.xWey10.tiling;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Map;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeContentSizeTest {
  @After
  public void clear() {
    de.robv.android.xposed.XposedBridge.hooks.clear();
    NativeForms.draft.remove();
  }

  @Test
  public void rotatingALargeTileKeepsItsNativeTwoByTwoContentRatherThanChangingToAWideRow()
      throws Exception {
    NativeFormHooksTest.install();
    var context = RuntimeEnvironment.getApplication();
    NativeRootViewportTest.Cells parent = new NativeRootViewportTest.Cells(context);
    SizeAwareTile tile = new SizeAwareTile(context);
    parent.addView(tile);
    var tag = new com.android.launcher3.QSTileItemInfo("flashlight");
    tag.spanX = tag.spanY = 3;
    tile.setTag(tag);
    tile.setPadding(12, 12, 12, 12);
    tile.setBackgroundColor(Color.DKGRAY);
    View icon = new View(context);
    icon.setBackgroundColor(Color.GREEN);
    tile.addView(icon, new LinearLayout.LayoutParams(20, 20));
    TextView label = new TextView(context);
    label.setText("Screen record");
    label.setTextColor(Color.BLUE);
    tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
    for (TileForm form :
        new TileForm[] {
          TileForm.RECT.base(2, 2).rotated(30), TileForm.RECT.base(2, 2).rotated(120)
        }) {
      NativeForms.draft.set(
          Map.of(
              "t:flashlight",
              new QsLayout.Item("t:flashlight", "", 8, 0, 0, 3, 3, true, true, form)));
      tile.applySize(3, 3);
      tile.forceLayout();
      tile.measure(
          View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY));
      tile.layout(0, 0, 288, 288);
      assertEquals(2, tile.mSize);
      assertEquals(LinearLayout.VERTICAL, tile.getOrientation());
      assertTrue(label.getTop() >= icon.getBottom());
      assertEquals(20, icon.getWidth());
      assertEquals(20, icon.getHeight());
    }
    QsLayout.Item wide =
        new QsLayout.Item(
            "t:flashlight",
            "",
            8,
            0,
            0,
            3,
            3,
            true,
            true,
            new TileForm(0, 0, 0, "0111", "0,1,2,1").base(2, 2).rotated(120));
    NativeForms.draft.set(Map.of(wide.id(), wide));
    tile.applySize(3, 3);
    assertEquals(1, tile.mSize);
    assertEquals(LinearLayout.HORIZONTAL, tile.getOrientation());
  }

  @Test
  public void previewChoosesTheLargeNativeVariantForARotatedLargeBody() throws Exception {
    try (var f = new GridGestureTest.Fixture()) {
      NativeGrid grid = new NativeGrid(f.nativeTree.panel, f.nativeTree.workspace);
      grid.focus = "t:bt";
      var frame = f.nativeTree.render(grid, grid.new Session(""), () -> {});
      f.view.setFrame(frame.bitmap, frame.metadata);
      f.touch(android.view.MotionEvent.ACTION_DOWN, 50, 50);
      f.touch(android.view.MotionEvent.ACTION_UP, 50, 50);
      f.view.resizeSelected(2, 2);
      f.view.moveSelected(1, 1);
      f.view.rotateSelected(30);
      var image = RetractionRotationTest.visual(f.view).image();
      assertEquals(2, image.w);
      assertEquals(2, image.h);
      assertTrue(f.view.getLayoutDraft().item("t:bt").form().rotation() != 0);
    }
  }

  public static class SizeAwareTile extends com.android.systemui.qs.tileimpl.QSTileViewImpl {
    SizeAwareTile(android.content.Context c) {
      super(c);
    }

    @Override
    public void applySize(int w, int h) {
      super.applySize(w, h);
      setOrientation(mSize == 2 ? VERTICAL : HORIZONTAL);
      setGravity(
          mSize == 2 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
      if (getChildCount() > 1) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) getChildAt(1).getLayoutParams();
        p.width = mSize == 2 ? -1 : 0;
        p.weight = mSize == 2 ? 0 : 1;
      }
    }
  }
}
