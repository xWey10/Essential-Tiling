package moe.xWey10.tiling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h873dp-port-xxhdpi")
public class ProfileFlowTest {
  @Before
  public void clean() {
    Context c = RuntimeEnvironment.getApplication();
    SettingsStore.prefs(c).edit().clear().commit();
    DraftStore.clear(c);
    c.createDeviceProtectedStorageContext()
        .getSharedPreferences("profiles", 0)
        .edit()
        .clear()
        .commit();
  }

  @Test
  public void closingTheAppKeepsUnappliedSettingsButNewerSavedSettingsWin() {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      ReflectionHelpers.<Slider>getField(c.get(), "size").setValue(86);
    }
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      assertEquals(86, ReflectionHelpers.<Slider>getField(c.get(), "size").getValue(), 0);
      assertEquals(100, SettingsStore.read(c.get()).scalePercent);
    }
    SettingsStore.write(
        RuntimeEnvironment.getApplication(),
        new moe.xWey10.tiling.Config(false, 94, 1, -1, false, 10));
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      assertEquals(94, ReflectionHelpers.<Slider>getField(c.get(), "size").getValue(), 0);
    }
  }

  @Test
  public void historySnapshotRestoresTheUndoAndRedoOrderWithinABoundedSavedState() {
    QsLayout source = QsLayoutTest.source(QsLayoutTest.tile("t:one", 0, 0, 1, 1));
    LayoutHistory history = new LayoutHistory();
    history.accept(source);
    history.accept(source.edit("t:one", 1, 0, 1, 1));
    history.accept(source.edit("t:one", 2, 0, 1, 1));
    history.undo();
    String state = history.snapshot();
    assertTrue(state.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 48000);
    LayoutHistory copy = new LayoutHistory();
    copy.restore(state);
    assertEquals(2, copy.redo().item("t:one").x());
    assertEquals(1, copy.undo().item("t:one").x());
    assertEquals(0, copy.undo().item("t:one").x());
    assertFalse(copy.canUndo());
  }

  @Test
  public void theProfileLibrarySavesAndLoadsAStagedConfigurationThroughRealMaterialDialogs() {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity app = c.get();
      ReflectionHelpers.<Slider>getField(app, "size").setValue(86);
      ReflectionHelpers.callInstanceMethod(app, "openProfiles");
      android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      button(sheet, "Сохранить").performClick();
      shadowOf(Looper.getMainLooper()).idle();
      androidx.appcompat.app.AlertDialog name =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      TilePickerViewTest.find(name.getWindow().getDecorView(), EditText.class).setText("Дом");
      name.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      assertEquals(1, ProfileStore.read(app).size());
      assertEquals(86, ProfileStore.read(app).get(0).config().scalePercent);
      assertEquals(100, SettingsStore.read(app).scalePercent);
      ReflectionHelpers.<Slider>getField(app, "size").setValue(98);
      TextView row = findText(sheet.getWindow().getDecorView(), "Дом");
      View click = row;
      while (!click.isClickable()) click = (View) click.getParent();
      click.performClick();
      assertEquals(86, ReflectionHelpers.<Slider>getField(app, "size").getValue(), 0);
      assertEquals(1, ReflectionHelpers.<Integer>getField(app, "screen").intValue());
      assertEquals(100, SettingsStore.read(app).scalePercent);
    }
  }

  @Test
  public void documentExportAndImportRoundTripWithoutApplyingTheFileToSystemUi() throws Exception {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity app = c.get();
      LayoutProfile profile =
          new LayoutProfile("Файл", new moe.xWey10.tiling.Config(true, 89, 5, 18, true, 0), null);
      Uri file = Uri.parse("content://test/profile.json");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      shadowOf(app.getContentResolver()).registerOutputStream(file, output);
      ReflectionHelpers.setField(app, "pendingExport", profile.json());
      ReflectionHelpers.callInstanceMethod(
          app, "writeProfile", ReflectionHelpers.ClassParameter.from(Uri.class, file));
      pump(() -> output.size() > 0);
      assertEquals(profile.json(), output.toString(java.nio.charset.StandardCharsets.UTF_8));
      shadowOf(app.getContentResolver())
          .registerInputStream(file, new ByteArrayInputStream(output.toByteArray()));
      ReflectionHelpers.callInstanceMethod(
          app, "readProfile", ReflectionHelpers.ClassParameter.from(Uri.class, file));
      pump(() -> ProfileStore.read(app).size() == 1);
      assertEquals(profile.json(), ProfileStore.read(app).get(0).json());
      assertFalse(SettingsStore.read(app).enabled);
      assertEquals(100, SettingsStore.read(app).scalePercent);
    }
  }

  @Test
  public void angleControlsKeepThePositiveEndpointAndUndoTheAcceptedRotation() throws Exception {
    NativeGridTest.Fixture nativeTree = new NativeGridTest.Fixture();
    NativeGrid grid = new NativeGrid(nativeTree.panel, nativeTree.workspace);
    var frame = nativeTree.render(grid, grid.new Session(""), () -> {});
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity app = c.get();
      ReflectionHelpers.<PreviewClient>getField(app, "previewClient").stop();
      app.showScreen(1);
      PreviewView view = ReflectionHelpers.getField(app, "preview");
      view.setFrame(frame.bitmap, frame.metadata);
      ReflectionHelpers.callInstanceMethod(
          app, "setPreviewBusy", ReflectionHelpers.ClassParameter.from(boolean.class, false));
      TilePickerViewTest.measure(app.getWindow().getDecorView(), 1179, 2619);
      float scale = view.getWidth() / 400f;
      android.view.MotionEvent
          down = android.view.MotionEvent.obtain(0, 0, 0, 50 * scale, 50 * scale, 0),
          up = android.view.MotionEvent.obtain(0, 1, 1, 50 * scale, 50 * scale, 0);
      view.onTouchEvent(down);
      view.onTouchEvent(up);
      down.recycle();
      up.recycle();
      ReflectionHelpers.callInstanceMethod(app, "openTileProperties");
      android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertEquals(
          0,
          dialog.getWindow().getAttributes().flags
              & android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      Slider angle = ReflectionHelpers.getField(app, "angle");
      angle.setValue(180);
      assertEquals(180, angle.getValue(), 0);
      assertEquals(-180, view.getLayoutDraft().item("t:bt").form().rotation(), .01);
      view.undo();
      assertEquals(0, view.getLayoutDraft().item("t:bt").form().rotation(), .01);
      assertEquals(0, angle.getValue(), 0);
      assertTrue(view.canRedo());
      assertFalse(SettingsStore.read(app).enabled);
    } finally {
      frame.bitmap.recycle();
    }
  }

  private static void pump(BooleanSupplier done) throws Exception {
    for (int n = 0; n < 250 && !done.getAsBoolean(); n++) {
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
      Thread.sleep(4);
    }
    assertTrue(done.getAsBoolean());
  }

  private static Button button(android.app.Dialog dialog, String text) {
    TextView result = findText(dialog.getWindow().getDecorView(), text);
    assertNotNull(text, result);
    return (Button) result;
  }

  private static TextView findText(View root, String text) {
    if (root instanceof TextView t && text.contentEquals(t.getText())) return t;
    if (root instanceof ViewGroup group)
      for (int n = 0; n < group.getChildCount(); n++) {
        TextView t = findText(group.getChildAt(n), text);
        if (t != null) return t;
      }
    return null;
  }
}
