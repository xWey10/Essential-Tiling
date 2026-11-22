package moe.xWey10.tiling;

import static org.junit.Assert.*;

import android.view.MotionEvent;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class HistoryProfileTest {
  @Before
  public void clear() {
    RuntimeEnvironment.getApplication()
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("profiles", 0)
        .edit()
        .clear()
        .commit();
  }

  @Test
  public void undoRedoAreLocalAndANewEditDropsTheRedoBranch() throws Exception {
    try (var f = new GridGestureTest.Fixture()) {
      f.touch(MotionEvent.ACTION_DOWN, 50, 50);
      f.touch(MotionEvent.ACTION_UP, 50, 50);
      f.view.moveSelected(1, 0);
      f.view.resizeSelected(2, 1);
      assertTrue(f.view.canUndo());
      assertEquals(2, f.view.getLayoutDraft().item("t:bt").w());
      f.view.undo();
      assertEquals(1, f.view.getLayoutDraft().item("t:bt").w());
      assertEquals(1, f.view.getLayoutDraft().item("t:bt").x());
      f.view.undo();
      assertEquals(0, f.view.getLayoutDraft().item("t:bt").x());
      assertFalse(f.view.canUndo());
      assertTrue(f.view.canRedo());
      f.view.redo();
      assertEquals(1, f.view.getLayoutDraft().item("t:bt").x());
      f.view.moveSelected(0, 1);
      assertFalse(f.view.canRedo());
      assertEquals(0, f.nativeTree.info.cellX);
      assertEquals(1, f.nativeTree.info.spanX);
    }
  }

  @Test
  public void aContinuousControlHasOneUndoStepAndSourceChangesClearOldHistory() {
    QsLayout source = QsLayoutTest.source(QsLayoutTest.tile("t:one", 0, 0, 1, 1));
    LayoutHistory h = new LayoutHistory();
    h.accept(source);
    h.begin();
    for (int n = 1; n < 4; n++) h.accept(source.edit("t:one", n, 0, 1, 1));
    h.end();
    assertEquals(0, h.undo().item("t:one").x());
    assertFalse(h.canUndo());
    assertEquals(3, h.redo().item("t:one").x());
    h.accept(QsLayoutTest.source(QsLayoutTest.tile("t:two", 0, 0, 1, 1)));
    assertFalse(h.canUndo());
    assertFalse(h.canRedo());
  }

  @Test
  public void settingsGeometryAndIntrinsicFormSurviveExportAndProfileStorage() {
    QsLayout source =
        QsLayoutTest.source(
            new QsLayout.Item(
                "t:one",
                "One",
                8,
                0,
                0,
                3,
                2,
                true,
                true,
                new TileForm(0, 0, 0, "1101", "0,0,2,1").base(2, 2).rotated(37)));
    LayoutProfile p =
        new LayoutProfile(
            "Рабочая", new moe.xWey10.tiling.Config(true, 88, 4, 12, true, 1), source);
    LayoutProfile copy = LayoutProfile.parse(p.json());
    assertEquals(p.name(), copy.name());
    assertEquals(88, copy.config().scalePercent);
    assertEquals(source.json(), copy.layout().json());
    assertTrue(ProfileStore.save(RuntimeEnvironment.getApplication(), copy));
    assertEquals(1, ProfileStore.read(RuntimeEnvironment.getApplication()).size());
    assertEquals(p.json(), ProfileStore.read(RuntimeEnvironment.getApplication()).get(0).json());
    assertTrue(ProfileStore.delete(RuntimeEnvironment.getApplication(), p.name()));
    assertTrue(ProfileStore.read(RuntimeEnvironment.getApplication()).isEmpty());
  }

  @Test
  public void profilesRebaseOntoCurrentFingerprintAndRejectAnotherTileSetBeforeChangingAnything() {
    QsLayout old = QsLayoutTest.source(QsLayoutTest.tile("t:one", 0, 0, 1, 1));
    QsLayout saved = old.edit("t:one", 1, 1, 2, 1);
    LayoutProfile p = new LayoutProfile("Тест", moe.xWey10.tiling.Config.DEFAULT, saved);
    QsLayout current = old.edit("t:one", 2, 2, 1, 1).asSource();
    QsLayout next = p.rebase(current);
    assertEquals(current.base, next.base);
    assertEquals(1, next.item("t:one").x());
    assertEquals(2, next.item("t:one").w());
    assertThrows(
        IllegalArgumentException.class,
        () -> p.rebase(QsLayoutTest.source(QsLayoutTest.tile("other", 0, 0, 1, 1))));
    assertEquals(2, current.item("t:one").x());
  }

  @Test
  public void invalidOrOversizedImportsCannotEnterTheProfileStore() {
    LayoutProfile profile = new LayoutProfile("Тест", moe.xWey10.tiling.Config.DEFAULT, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> LayoutProfile.parse(profile.json().replace("\"version\":1", "\"version\":2")));
    assertThrows(
        IllegalArgumentException.class,
        () -> LayoutProfile.parse(profile.json().replace("\"scale\":100", "\"scale\":400")));
    assertThrows(
        IllegalArgumentException.class,
        () -> LayoutProfile.parse(" ".repeat(LayoutProfile.MAX_BYTES + 1)));
    assertTrue(ProfileStore.read(RuntimeEnvironment.getApplication()).isEmpty());
  }

  @Test
  public void aLargeValidUnicodeLayoutCanBeExportedAndImportedWithoutChangingItsText() {
    String title = "字".repeat(70_000);
    QsLayout layout =
        QsLayoutTest.source(new QsLayout.Item("t:one", title, 8, 0, 0, 1, 1, true, true));
    assertTrue(layout.json().length() < QsLayout.MAX_JSON);
    LayoutProfile profile =
        new LayoutProfile("Большой профиль", moe.xWey10.tiling.Config.DEFAULT, layout);
    String json = profile.json();
    assertTrue(
        json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < LayoutProfile.MAX_BYTES);
    assertEquals(title, LayoutProfile.parse(json).layout().item("t:one").title());
  }

  @Test
  public void malformedDeepAndOverflowingNumbersAreRejectedBeforeTheyCanBeUsed() {
    LayoutProfile profile = new LayoutProfile("Тест", moe.xWey10.tiling.Config.DEFAULT, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> LayoutProfile.parse("{\"x\":".repeat(1000) + "0" + "}".repeat(1000)));
    assertThrows(
        IllegalArgumentException.class,
        () -> LayoutProfile.parse(profile.json().replace("\"scale\":100", "\"scale\":4294967396")));
  }
}
