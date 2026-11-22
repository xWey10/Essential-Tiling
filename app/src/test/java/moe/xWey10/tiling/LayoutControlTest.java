package moe.xWey10.tiling;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LayoutControlTest {
    private String draft() { return QsLayoutTest.source(QsLayoutTest.tile("a", 0, 0, 1, 1)).json(); }
    @Test public void saveIsClaimedOnceAndAckCannotPrecedeClaimOrBelongToAnotherRequest() {
        LayoutControl control = new LayoutControl(); long id = control.request(draft(), 100).getLong("id");
        control.complete(id, "", 101); assertEquals("pending", control.status(id, 101).getString("state"));
        Bundle claimed = control.claim(102); assertEquals(draft(), claimed.getString("layout"));
        assertNull(control.claim(103)); control.complete(id + 1, "", 104);
        assertEquals("saving", control.status(id, 104).getString("state"));
        assertThrows(IllegalStateException.class, () -> control.request(draft(), 105));
        control.complete(id, "", 106); assertEquals("saved", control.status(id, 106).getString("state"));
    }
    @Test public void pendingExpiresAndNewProviderDoesNotReplayWrites() {
        LayoutControl control = new LayoutControl(); long id = control.request(draft(), 0).getLong("id");
        assertNull(control.claim(5001)); assertEquals("error", control.status(id, 5001).getString("state"));
        assertNull(new LayoutControl().claim(5002));
        long second = control.request(draft(), 6000).getLong("id"); assertTrue(second > id);
        assertEquals("expired", control.status(id, 6001).getString("state"));
    }
    @Test public void nativeFailureIsReportedAndNotAcknowledgedAsSaved() {
        LayoutControl control = new LayoutControl(); long id = control.request(draft(), 0).getLong("id"); control.claim(1);
        control.complete(id, "Ошибка Settings.Secure", 2);
        assertEquals("error", control.status(id, 2).getString("state"));
        assertEquals("Ошибка Settings.Secure", control.status(id, 2).getString("error"));
    }
}
