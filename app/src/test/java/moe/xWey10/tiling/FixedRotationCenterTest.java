package moe.xWey10.tiling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.*;
import android.os.Looper;
import android.view.MotionEvent;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class FixedRotationCenterTest {
  @Test
  public void rapidAngleChangesKeepTheExactCenterAndIntrinsicPixelSizeWithoutWaitingForAnimations()
      throws Exception {
    for (boolean rtl : new boolean[] {false, true})
      try (var f = new GridGestureTest.Fixture()) {
        // Preview RTL is independent from the fixture container's native implementation.
        QsLayout.Item item = new QsLayout.Item("t:bt", "", 8, 1, 1, 2, 1, true, true);
        ShapeInteractionTest.load(f, item);
        ReflectionHelpers.setField(f.view, "rtl", rtl);
        f.touch(MotionEvent.ACTION_DOWN, 202, 152);
        f.touch(MotionEvent.ACTION_UP, 202, 152);
        float cx = RetractionRotationTest.visual(f.view).motion.bounds.centerX(),
            cy = RetractionRotationTest.visual(f.view).motion.bounds.centerY();
        f.view.beginEdit();
        for (int angle = -180; angle <= 180; angle += 3) {
          f.view.rotateSelected(angle);
          QsLayout.Item changed = f.view.getLayoutDraft().item("t:bt");
          assertEquals(angle == 180 ? -180 : angle, changed.form().rotation(), .01);
          assertEquals(2, changed.x() + changed.w() / 2f, 0);
          assertEquals(1.5f, changed.y() + changed.h() / 2f, 0);
          TileVisual visual = RetractionRotationTest.visual(f.view);
          assertEquals(cx, visual.motion.bounds.centerX(), .01);
          assertEquals(cy, visual.motion.bounds.centerY(), .01);
          RectF body =
              changed.form().intrinsic(visual.motion.bounds, changed.w(), changed.h(), 100, 100);
          assertEquals(188, body.width(), .01);
          assertEquals(88, body.height(), .01);
        }
        f.view.endEdit();
        f.view.resetRotation();
        assertEquals(cx, RetractionRotationTest.visual(f.view).motion.bounds.centerX(), .01);
        assertEquals(cy, RetractionRotationTest.visual(f.view).motion.bounds.centerY(), .01);
      }
  }

  @Test
  public void rotationAtTheEdgeIsRejectedInsteadOfClampingTheTileToAnotherPosition()
      throws Exception {
    try (var f = new GridGestureTest.Fixture()) {
      ShapeInteractionTest.load(f, new QsLayout.Item("t:bt", "", 8, 0, 0, 2, 1, true, true));
      f.touch(MotionEvent.ACTION_DOWN, 100, 50);
      f.touch(MotionEvent.ACTION_UP, 100, 50);
      QsLayout original = f.view.getLayoutDraft();
      RectF before = new RectF(RetractionRotationTest.visual(f.view).motion.bounds);
      f.view.rotateSelected(90);
      assertFalse(f.view.getLayoutDraft().differsFrom(original));
      assertEquals(before, RetractionRotationTest.visual(f.view).motion.bounds);
    }
  }

  @Test
  public void roundHandleKeepsItsPivotAtEveryMoveAndOnRelease() throws Exception {
    try (var f = new GridGestureTest.Fixture()) {
      ShapeInteractionTest.load(f, new QsLayout.Item("t:bt", "", 8, 1, 1, 2, 1, true, true));
      f.touch(MotionEvent.ACTION_DOWN, 150, 150);
      f.touch(MotionEvent.ACTION_UP, 150, 150);
      f.touch(MotionEvent.ACTION_DOWN, 202, 88);
      for (int a = 0; a <= 90; a += 5) {
        double r = Math.toRadians(a - 90);
        f.touch(
            MotionEvent.ACTION_MOVE,
            202 + (float) Math.cos(r) * 64,
            152 + (float) Math.sin(r) * 64);
        QsLayout.Item p = ReflectionHelpers.getField(f.view, "proposed");
        assertEquals(2, p.x() + p.w() / 2f, 0);
        assertEquals(1.5f, p.y() + p.h() / 2f, 0);
      }
      f.touch(MotionEvent.ACTION_UP, 266, 152);
      assertEquals(90, f.view.getLayoutDraft().item("t:bt").form().rotation(), .01);
      assertEquals(202, RetractionRotationTest.visual(f.view).motion.bounds.centerX(), .01);
      assertEquals(152, RetractionRotationTest.visual(f.view).motion.bounds.centerY(), .01);
      QsLayout.Item current = f.view.getLayoutDraft().item("t:bt");
      RectF handle =
          ReflectionHelpers.callInstanceMethod(
              f.view,
              "rotationPoint",
              ReflectionHelpers.ClassParameter.from(QsLayout.Item.class, current),
              ReflectionHelpers.ClassParameter.from(
                  RectF.class, new RectF(RetractionRotationTest.visual(f.view).motion.bounds)));
      assertEquals(266, handle.left, .01);
      assertEquals(152, handle.top, .01);
      f.view.rotateSelected(30);
      RectF visible =
          TileFrame.bounds(
              RetractionRotationTest.visual(f.view)
                  .outline(RetractionRotationTest.visual(f.view).motion.bounds, 30));
      assertTrue(
          "A new angle must not keep the old vertical gesture pose",
          visible.width() > visible.height());
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
      NativeGridTest.draw(f.view, 400).recycle();
      assertEquals(202, RetractionRotationTest.visual(f.view).motion.bounds.centerX(), .01);
    }
  }

  @Test
  public void lAndLargeTileKeepTheirCenterThroughSerializationAndFurtherRotation() {
    for (TileForm form : new TileForm[] {TileForm.RECT, new TileForm(0, 0, 0, "1101", "0,0,2,1")}) {
      QsLayout.Item item = new QsLayout.Item("t:bt", "", 8, 1, 1, 2, 2, true, true, form);
      QsLayout.Page page = new QsLayout.Page(8, 4, 4);
      for (int a : new int[] {30, 60, 90, 120, 180, -120, -60, 0}) {
        RectF frame =
            new RectF(
                item.x() * 100 + 8,
                item.y() * 100 + 8,
                (item.x() + item.w()) * 100 - 4,
                (item.y() + item.h()) * 100 - 4);
        item = TileFrame.rotate(item, a, page, frame, 100, 100, 100, 100, 30, false);
        assertNotNull(item);
        assertEquals(2, item.x() + item.w() / 2f, 0);
        assertEquals(2, item.y() + item.h() / 2f, 0);
        QsLayout model = QsLayoutTest.source(item);
        item = QsLayout.parse(model.json()).items.get(0);
        assertTrue(item.form().valid(item.w(), item.h()));
      }
    }
  }
}
