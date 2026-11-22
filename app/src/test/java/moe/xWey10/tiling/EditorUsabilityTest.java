package moe.xWey10.tiling;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class EditorUsabilityTest {
  @Before
  public void clean() {
    Context c = RuntimeEnvironment.getApplication();
    SettingsStore.prefs(c).edit().clear().commit();
    DraftStore.clear(c);
    c.createDeviceProtectedStorageContext()
        .getSharedPreferences("runtime", 0)
        .edit()
        .clear()
        .commit();
  }

  private <T> T f(MainActivity a, String n) {
    return ReflectionHelpers.getField(a, n);
  }

  @Test
  public void applyReflectsActualChangesAndSwitchesToRestartOnlyAfterSaving() {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      Button apply = f(a, "apply"), discard = f(a, "discard");
      assertFalse(apply.isEnabled());
      assertEquals(View.INVISIBLE, discard.getVisibility());
      var runtime = a.createDeviceProtectedStorageContext().getSharedPreferences("runtime", 0);
      runtime
          .edit()
          .putLong("time", System.currentTimeMillis())
          .putLong("loadedRevision", 0)
          .commit();
      ReflectionHelpers.<Slider>getField(a, "size").setValue(90);
      assertTrue(apply.isEnabled());
      assertEquals(View.VISIBLE, discard.getVisibility());
      assertEquals("Применить", apply.getText().toString());
      apply.performClick();
      assertEquals(90, SettingsStore.read(a).scalePercent);
      assertTrue(apply.isEnabled());
      assertEquals("Перезапустить", apply.getText().toString());
      runtime.edit().putLong("loadedRevision", SettingsStore.read(a).revision).commit();
      ReflectionHelpers.callInstanceMethod(a, "refreshStatus");
      assertFalse(apply.isEnabled());
      assertEquals("Применить", apply.getText().toString());
    }
  }

  @Test
  public void largeFontKeepsFooterAndSliderLabelsInsideTheirBounds() {
    RuntimeEnvironment.setQualifiers("w320dp-h640dp-port-hdpi");
    RuntimeEnvironment.setFontScale(1.6f);
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      a.showScreen(1);
      ReflectionHelpers.<PreviewClient>getField(a, "previewClient").stop();
      ReflectionHelpers.<Slider>getField(a, "size").setValue(85);
      ReflectionHelpers.<Button>getField(a, "apply").performClick();
      View root = a.getWindow().getDecorView();
      float density = a.getResources().getDisplayMetrics().density;
      int width = Math.round(320 * density), height = Math.round(640 * density);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, width, height);
      Button apply = f(a, "apply");
      assertEquals("Перезапустить", apply.getText().toString());
      assertTrue(
          apply.getLineHeight()
              <= apply.getHeight()
                  - apply.getCompoundPaddingTop()
                  - apply.getCompoundPaddingBottom());
      for (String name : new String[] {"sizeLabel", "gapLabel"}) {
        TextView label = f(a, name);
        assertEquals(1, label.getLineCount());
      }
      TextView feedback = f(a, "message");
      assertTrue(feedback.getHeight() >= feedback.getLineHeight() * 2);
    } finally {
      RuntimeEnvironment.setFontScale(1);
    }
  }

  @Test
  public void discardRestoresAppearanceAndGeometryLocallyWithoutWritingTheSystem()
      throws Exception {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      ReflectionHelpers.<PreviewClient>getField(a, "previewClient").stop();
      NativeGridTest.Fixture n = new NativeGridTest.Fixture();
      NativeGrid g = new NativeGrid(n.panel, n.workspace);
      var frame = n.render(g, g.new Session(""), () -> {});
      PreviewClient.Listener updates =
          ReflectionHelpers.getField(f(a, "previewClient"), "listener");
      updates.frame(frame.bitmap, frame.metadata);
      PreviewView v = f(a, "preview");
      NativeGridTest.draw(v, 400).recycle();
      v.getAccessibilityNodeProvider().performAction(1, R.id.access_move_right, Bundle.EMPTY);
      ReflectionHelpers.<Slider>getField(a, "size").setValue(85);
      ReflectionHelpers.<MaterialSwitch>getField(a, "enabled").setChecked(true);
      assertEquals(1, v.getLayoutDraft().item("t:bt").x());
      assertTrue(v.canUndo());
      ReflectionHelpers.<Button>getField(a, "discard").performClick();
      assertEquals(0, v.getLayoutDraft().item("t:bt").x());
      assertEquals(100, ReflectionHelpers.<Slider>getField(a, "size").getValue(), 0);
      assertFalse(ReflectionHelpers.<MaterialSwitch>getField(a, "enabled").isChecked());
      assertFalse(v.canUndo());
      assertEquals("", f(a, "layoutDraft"));
      assertEquals(0, n.info.cellX);
      assertEquals(0, SettingsStore.read(a).revision);
      frame.bitmap.recycle();
    }
  }

  @Test
  public void virtualNodesExposeTilesAndEmptyCellsAndSupportEditingWithoutDragging()
      throws Exception {
    try (var fixture = new GridGestureTest.Fixture()) {
      PreviewView v = fixture.view;
      AccessibilityNodeProvider p = v.getAccessibilityNodeProvider();
      assertNotNull(p);
      assertTrue(p.createAccessibilityNodeInfo(View.NO_ID).getChildCount() > 1);
      AccessibilityNodeInfo tile = p.createAccessibilityNodeInfo(1);
      assertTrue(tile.getContentDescription().toString().length() > 0);
      assertTrue(tile.getActionList().stream().anyMatch(x -> x.getId() == R.id.access_move_right));
      assertTrue(p.performAction(1, R.id.access_move_right, Bundle.EMPTY));
      assertEquals(1, v.getLayoutDraft().item("t:bt").x());
      String[] removed = {null};
      int[] empty = {-1, -1};
      v.setListener(
          new PreviewView.Listener() {
            public void edited(QsLayout l) {}

            public void selected(QsLayout.Item i) {}

            public void message(String text) {}

            public void remove(String id) {
              removed[0] = id;
            }

            public void empty(long page, int x, int y) {
              empty[0] = x;
              empty[1] = y;
            }
          });
      assertTrue(p.performAction(10_000, AccessibilityNodeInfo.ACTION_CLICK, Bundle.EMPTY));
      assertArrayEquals(new int[] {0, 0}, empty);
      assertTrue(p.performAction(1, R.id.access_remove, Bundle.EMPTY));
      assertEquals("t:bt", removed[0]);
      v.setBusy(true);
      assertFalse(p.performAction(1, R.id.access_move_left, Bundle.EMPTY));
      assertFalse(p.createAccessibilityNodeInfo(1).isEnabled());
    }
  }

  @Test
  public void keyboardUndoRedoAndAccessibleClickOpenTheRealTileInspector() throws Exception {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      ReflectionHelpers.<PreviewClient>getField(a, "previewClient").stop();
      a.showScreen(1);
      NativeGridTest.Fixture n = new NativeGridTest.Fixture();
      NativeGrid g = new NativeGrid(n.panel, n.workspace);
      var frame = n.render(g, g.new Session(""), () -> {});
      PreviewClient.Listener updates =
          ReflectionHelpers.getField(f(a, "previewClient"), "listener");
      updates.frame(frame.bitmap, frame.metadata);
      PreviewView v = f(a, "preview");
      NativeGridTest.draw(v, 400).recycle();
      v.getAccessibilityNodeProvider().performAction(1, R.id.access_move_right, Bundle.EMPTY);
      KeyEvent z =
          new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
      assertTrue(a.onKeyShortcut(z.getKeyCode(), z));
      assertEquals(0, v.getLayoutDraft().item("t:bt").x());
      KeyEvent y =
          new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON);
      assertTrue(a.onKeyShortcut(y.getKeyCode(), y));
      assertEquals(1, v.getLayoutDraft().item("t:bt").x());
      assertTrue(
          v.getAccessibilityNodeProvider()
              .performAction(1, AccessibilityNodeInfo.ACTION_CLICK, Bundle.EMPTY));
      assertNotNull(f(a, "propertiesSheet"));
      ((android.app.Dialog) f(a, "propertiesSheet")).dismiss();
      frame.bitmap.recycle();
    }
  }
}
