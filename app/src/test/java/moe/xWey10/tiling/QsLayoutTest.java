package moe.xWey10.tiling;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class QsLayoutTest {
    static QsLayout.Item tile(String id, int x, int y, int w, int h) {
        return new QsLayout.Item(id, id, 8, x, y, w, h, true, true);
    }
    static QsLayout source(QsLayout.Item... items) {
        return new QsLayout("", true, List.of(new QsLayout.Page(8, 4, 4)), List.of(items)).asSource();
    }
    @Test public void serializesRealPageIdsAndCustomSpecsWithoutLoss() {
        QsLayout source = source(tile("t:custom(com.example/.Tile)", 1, 2, 2, 1));
        QsLayout parsed = QsLayout.parse(source.json());
        assertFalse(parsed.differsFrom(source)); assertEquals(source.base, parsed.base);
        parsed.validateAgainst(source); assertEquals(8, parsed.items.get(0).page());
    }
    @Test public void movesAndSwapsTilesInsteadOfOverlappingThem() {
        QsLayout source = source(tile("a", 0, 0, 1, 1), tile("b", 1, 0, 1, 1));
        QsLayout next = source.edit("a", 1, 0, 1, 1);
        assertNotNull(next); next.validateAgainst(source);
        assertEquals(0, next.item("b").x()); assertEquals(1, next.item("a").x());
        assertEquals(0, source.item("a").x());
    }
    @Test public void resizesAndRelocatesIntersectingTilesOnly() {
        QsLayout source = source(tile("a", 0, 0, 1, 1), tile("b", 1, 0, 1, 1), tile("c", 3, 3, 1, 1));
        QsLayout next = source.edit("a", 0, 0, 2, 2);
        assertNotNull(next); next.validateAgainst(source);
        assertEquals(2, next.item("a").h()); assertFalse(next.item("a").overlaps(next.item("b")));
        assertTrue(source.item("c").samePlace(next.item("c")));
    }
    @Test public void rejectsFullPageExpansionAndOutOfBoundsWithoutDroppingTiles() {
        java.util.ArrayList<QsLayout.Item> items = new java.util.ArrayList<>();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) items.add(tile(x + ":" + y, x, y, 1, 1));
        QsLayout source = source(items.toArray(new QsLayout.Item[0]));
        assertNull(source.edit("0:0", 0, 0, 2, 2)); assertNull(source.edit("0:0", -1, 0, 1, 1));
        assertNull(source.edit("0:0", 3, 3, 2, 1)); assertEquals(16, source.items.size());
    }
    @Test public void allowsCustomSizesAndPreservesLockedWidgetObstacles() {
        QsLayout.Item ringer = new QsLayout.Item("ringer", "Звук", 8, 0, 0, 1, 1, true, false);
        QsLayout.Item widget = new QsLayout.Item("widget", "Виджет", 8, 1, 0, 2, 2, false, false);
        QsLayout source = source(ringer, widget);
        assertNotNull(source.edit("ringer", 0, 0, 1, 2)); assertNull(source.edit("ringer", 0, 0, 2, 2)); // A protected widget blocks the wider size.
        assertNull(source.edit("ringer", 1, 0, 1, 1)); assertNull(source.edit("widget", 0, 2, 2, 2));
        assertNotNull(source.edit("ringer", 0, 3, 2, 1));
    }
    @Test public void rejectsStaleOrForgedDraftAndLandscapeWrites() {
        QsLayout original = source(tile("a", 0, 0, 1, 1));
        QsLayout changed = original.edit("a", 1, 0, 1, 1);
        assertThrows(IllegalStateException.class, () -> changed.validateAgainst(changed.asSource()));
        QsLayout forged = new QsLayout(original.base, true, original.pages, List.of(tile("other", 0, 0, 1, 1)));
        assertThrows(IllegalStateException.class, () -> forged.validateAgainst(original));
        QsLayout landscape = new QsLayout("", false, original.pages, original.items).asSource();
        assertNull(landscape.edit("a", 1, 0, 1, 1));
    }
    @Test public void labelStateChangesDoNotInvalidateLayoutFingerprint() {
        QsLayout original = source(tile("a", 0, 0, 1, 1));
        QsLayout other = source(new QsLayout.Item("a", "Новое устройство", 8, 0, 0, 1, 1, true, true));
        assertEquals(original.base, other.base);
    }
}
