package moe.xWey10.tiling;

import android.os.Bundle;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class TileControlTest {
    private Bundle request(String action) { Bundle b = new Bundle(); b.putString("action", action); return b; }
    @Test public void iconHeavyCatalogueUsesBoundedBinderPagesWithoutDroppingEntries() {
        TileControl c = new TileControl(); long id = c.request(request("catalog"), 0).getLong("id"); c.claim(1);
        ArrayList<String> rows = new ArrayList<>(); for (int n = 0; n < 300; n++) rows.add(n + ":" + "x".repeat(6000));
        for (int offset = 0; offset < rows.size(); ) {
            int end = TileControl.pageEnd(rows, offset); Bundle page = new Bundle(); page.putLong("id", id);
            page.putStringArrayList("rows", new ArrayList<>(rows.subList(offset, end)));
            android.os.Parcel parcel = android.os.Parcel.obtain(); parcel.writeBundle(page); assertTrue(parcel.dataSize() < 220_000); parcel.recycle();
            c.page(page); offset = end;
        }
        Bundle done = new Bundle(); done.putLong("id", id); c.complete(done);
        ArrayList<String> received = new ArrayList<>();
        while (received.size() < rows.size()) {
            Bundle page = c.read(id, received.size(), 2);
            android.os.Parcel parcel = android.os.Parcel.obtain(); parcel.writeBundle(page); assertTrue(parcel.dataSize() < 220_000); parcel.recycle();
            received.addAll(page.getStringArrayList("rows"));
        }
        assertEquals(rows, received);
    }
    @Test public void catalogueHasNoFixedTileLimitAndTransfersEveryPage() {
        TileControl control = new TileControl(); long id = control.request(request("catalog"), 0).getLong("id");
        assertNotNull(control.claim(1)); assertNull(control.claim(2));
        ArrayList<String> expected = new ArrayList<>();
        for (int offset = 0; offset < 333; offset += 64) {
            Bundle b = new Bundle(); b.putLong("id", id); ArrayList<String> page = new ArrayList<>();
            for (int n = offset; n < Math.min(333, offset + 64); n++) page.add("custom(app" + n + "/.Tile)");
            expected.addAll(page); b.putStringArrayList("rows", page); control.page(b);
        }
        Bundle done = new Bundle(); done.putLong("id", id); control.complete(done);
        assertEquals(333, control.status(id, 3).getInt("count"));
        ArrayList<String> received = new ArrayList<>();
        for (int offset = 0; offset < 333; offset += 64) received.addAll(control.read(id, offset, 4).getStringArrayList("rows"));
        assertEquals(expected, received);
    }
    @Test public void writesAreOneShotAndOldResultsCannotReplaceANewRequest() {
        TileControl c = new TileControl(); long first = c.request(request("remove"), 0).getLong("id");
        Bundle premature = new Bundle(); premature.putLong("id", first); c.complete(premature);
        assertEquals("pending", c.status(first, 1).getString("state"));
        c.claim(2); assertNull(c.claim(3)); c.complete(premature);
        long next = c.request(request("add"), 4).getLong("id"); c.claim(5); c.complete(premature);
        assertEquals("pending", c.status(next, 6).getString("state"));
        assertEquals("expired", c.status(first, 6).getString("state"));
        assertNull(new TileControl().claim(7));
    }
    @Test public void unconfirmedRequestsExpireAndDoNotClaimSuccess() {
        TileControl c = new TileControl(); long id = c.request(request("add"), 0).getLong("id"); c.claim(1);
        assertEquals("error", c.status(id, 30_001).getString("state"));
        Bundle done = new Bundle(); done.putLong("id", id); c.complete(done);
        assertEquals("error", c.status(id, 30_002).getString("state"));
    }
    @Test public void dismissingCatalogueReleasesQueueButCannotCancelAMutation() {
        TileControl c = new TileControl(); long id = c.request(request("catalog"), 0).getLong("id"); c.claim(1); c.cancel(id);
        assertEquals("error", c.status(id, 2).getString("state"));
        long add = c.request(request("add"), 3).getLong("id"); c.claim(4); c.cancel(add);
        assertEquals("pending", c.status(add, 5).getString("state"));
    }
}
