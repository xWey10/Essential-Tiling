package moe.xWey10.tiling;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class TileClientTest {
    public static class Bridge extends ContentProvider {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final List<Long> cancelled=java.util.Collections.synchronizedList(new ArrayList<>());
        boolean block=true; long sequence;
        @Override public boolean onCreate() { return true; }
        @Override public Bundle call(String method,String arg,Bundle request) {
            if(method.equals("tiles.request")) {
                if(block) { entered.countDown(); try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new IllegalStateException(e); } block=false; }
                Bundle b=new Bundle(); b.putLong("id",++sequence); return b;
            }
            if(method.equals("tiles.cancel")) { cancelled.add(request.getLong("id")); return Bundle.EMPTY; }
            Bundle b=new Bundle(); b.putString("state","done"); b.putInt("count",0); return b;
        }
        @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u,ContentValues v) { return null; }
        @Override public int delete(Uri u,String s,String[] a) { return 0; }
        @Override public int update(Uri u,ContentValues v,String s,String[] a) { return 0; }
    }
    private Bridge bridge() { Bridge p=Robolectric.buildContentProvider(Bridge.class).create().get(); ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",p); return p; }
    private Bundle request(String action) { Bundle b=new Bundle(); b.putString("action",action); return b; }
    private void pump(java.util.function.BooleanSupplier condition) throws Exception {
        for(int n=0;n<200&&!condition.getAsBoolean();n++) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10)); Thread.sleep(4); }
        assertTrue(condition.getAsBoolean());
    }
    @Test public void blockedBinderStillDeliversOneTimeoutOnTheUiLooper() throws Exception {
        Bridge p=bridge(); TileClient client=new TileClient(RuntimeEnvironment.getApplication()); List<String> replies=new ArrayList<>();
        client.request(request("remove"),(entries,error)->replies.add(error)); assertTrue(p.entered.await(2,TimeUnit.SECONDS));
        try {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(15001));
            assertEquals(1,replies.size()); assertTrue(replies.get(0).contains("подтверждения"));
        } finally { p.release.countDown(); }
        client.request(request("remove"),(entries,error)->replies.add(error)); pump(()->replies.size()==2);
        assertEquals("",replies.get(1)); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(16)); assertEquals(2,replies.size()); client.stop();
    }
    @Test public void lateCatalogueCancellationCannotAddressTheFollowingRemoval() throws Exception {
        Bridge p=bridge(); TileClient client=new TileClient(RuntimeEnvironment.getApplication()); List<String> mutations=new ArrayList<>();
        client.request(request("catalog"),(entries,error)->fail("Cancelled catalogue must not deliver")); assertTrue(p.entered.await(2,TimeUnit.SECONDS));
        client.cancelCatalog(); client.request(request("remove"),(entries,error)->mutations.add(error)); p.release.countDown();
        pump(()->!mutations.isEmpty()); assertEquals("",mutations.get(0)); assertEquals(java.util.List.of(1L),p.cancelled); client.stop();
    }
}
