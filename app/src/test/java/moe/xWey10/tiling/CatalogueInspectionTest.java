package moe.xWey10.tiling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h873dp-port-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CatalogueInspectionTest {
  private static void measure(View root) {
    TilePickerViewTest.measure(root, 1179, 2619);
  }

  private static void touch(PreviewView v, int action, float x, float y) {
    float scale = v.getWidth() / 400f;
    MotionEvent e =
        MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x * scale, y * scale, 0);
    v.onTouchEvent(e);
    e.recycle();
  }

  private static void tap(PreviewView v, float x, float y) {
    touch(v, MotionEvent.ACTION_DOWN, x, y);
    touch(v, MotionEvent.ACTION_UP, x, y);
  }

  private static final class Fixture implements AutoCloseable {
    final org.robolectric.android.controller.ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    final MainActivity app = controller.get();
    final NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture();
    final NativePreviewServer.Frame frame;
    final PreviewView preview = ReflectionHelpers.getField(app, "preview");

    Fixture() throws Exception {
      ReflectionHelpers.<PreviewClient>getField(app, "previewClient").stop();
      app.showScreen(1);
      NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
      grid.focus = "t:bt";
      frame = nativeTree.render(grid, grid.new Session(""), () -> {});
      PreviewClient.Listener listener =
          ReflectionHelpers.getField(ReflectionHelpers.getField(app, "previewClient"), "listener");
      listener.frame(frame.bitmap, frame.metadata);
      measure(app.getWindow().getDecorView());
    }

    TilePickerDialog catalogue() {
      return ReflectionHelpers.getField(app, "tileSheet");
    }

    void openByEmptyTap() {
      tap(preview, 250, 150);
      assertNotNull(catalogue());
      measure(catalogue().getWindow().getDecorView());
      ReflectionHelpers.<TilePickerView>getField(app, "tilePicker")
          .getViewTreeObserver()
          .dispatchOnPreDraw();
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      assertTrue(preview.isEnabled());
    }

    public void close() {
      controller.close();
      frame.bitmap.recycle();
    }
  }

  @Test
  public void tappingExistingTileClosesCatalogueThenOpensItsInspectorAndKeepsTheDraft()
      throws Exception {
    try (var f = new Fixture()) {
      tap(f.preview, 50, 50);
      f.preview.moveSelected(1, 0);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
      String draft = f.preview.getLayoutDraft().json();
      Object parent = f.preview.getParent();
      f.openByEmptyTap();
      TilePickerDialog catalogue = f.catalogue();
      tap(f.preview, 150, 50);
      assertNull(
          "Wait for reverse transition before opening another window",
          ReflectionHelpers.getField(f.app, "propertiesSheet"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      assertFalse(catalogue.isShowing());
      assertNull(f.catalogue());
      assertSame(parent, f.preview.getParent());
      android.app.Dialog inspector = ReflectionHelpers.getField(f.app, "propertiesSheet");
      assertNotNull(inspector);
      assertTrue(inspector.isShowing());
      assertEquals("t:bt", ReflectionHelpers.<QsLayout.Item>getField(f.app, "selectedItem").id());
      assertEquals(draft, f.preview.getLayoutDraft().json());
      assertEquals(0, f.nativeTree.info.cellX);
      inspector.dismiss();
      f.openByEmptyTap();
      tap(f.preview, 150, 50);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      assertNotNull(ReflectionHelpers.getField(f.app, "propertiesSheet"));
    }
  }

  @Test
  public void accessibleTileClickUsesTheSameReturnBeforeOpeningTheInspector() throws Exception {
    try (var f = new Fixture()) {
      f.openByEmptyTap();
      assertTrue(
          f.preview
              .getAccessibilityNodeProvider()
              .performAction(1, AccessibilityNodeInfo.ACTION_CLICK, Bundle.EMPTY));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      assertNull(f.catalogue());
      assertNotNull(ReflectionHelpers.getField(f.app, "propertiesSheet"));
    }
  }

  @Test
  public void holdingAndDraggingInCatalogueDoesNotOpenTheInspector() throws Exception {
    try (var f = new Fixture()) {
      f.openByEmptyTap();
      TilePickerDialog catalogue = f.catalogue();
      touch(f.preview, MotionEvent.ACTION_DOWN, 50, 50);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(310));
      touch(f.preview, MotionEvent.ACTION_MOVE, 150, 50);
      touch(f.preview, MotionEvent.ACTION_UP, 150, 50);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
      assertSame(catalogue, f.catalogue());
      assertTrue(catalogue.isShowing());
      assertNull(ReflectionHelpers.getField(f.app, "propertiesSheet"));
      assertEquals(1, f.preview.getLayoutDraft().item("t:bt").x());
    }
  }

  @Test
  public void disabledAnimationsStillReturnToTheInspectorWithoutNullAnimatorAccess()
      throws Exception {
    ReflectionHelpers.callStaticMethod(
        android.animation.ValueAnimator.class,
        "setDurationScale",
        ReflectionHelpers.ClassParameter.from(float.class, 0f));
    try (var f = new Fixture()) {
      f.openByEmptyTap();
      tap(f.preview, 50, 50);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      assertNull(f.catalogue());
      assertNotNull(ReflectionHelpers.getField(f.app, "propertiesSheet"));
    } finally {
      ReflectionHelpers.callStaticMethod(
          android.animation.ValueAnimator.class,
          "setDurationScale",
          ReflectionHelpers.ClassParameter.from(float.class, 1f));
    }
  }

  @Test
  public void destroyingDuringTheReturnDoesNotOpenAnInspectorOnTheDestroyedActivity()
      throws Exception {
    Fixture f = new Fixture();
    try {
      f.openByEmptyTap();
      tap(f.preview, 50, 50);
      assertNotNull(ReflectionHelpers.getField(f.app, "pendingTileInspection"));
      f.controller.pause().stop().destroy();
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
      assertTrue(f.app.isDestroyed());
      assertNull(ReflectionHelpers.getField(f.app, "propertiesSheet"));
      assertNull(ReflectionHelpers.getField(f.app, "pendingTileInspection"));
    } finally {
      if (!f.app.isDestroyed()) f.controller.close();
      f.frame.bitmap.recycle();
    }
  }
}
