package moe.xWey10.tiling;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class PreviewClientTest {
    public static class Bridge extends ContentProvider {
        final List<Bundle> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        volatile Bundle result; long sequence;
        @Override public boolean onCreate() { return true; }
        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (method.equals("preview.request")) {
                long id = ++sequence; requests.add(new Bundle(extras));
                try {
                    Bitmap bitmap = Bitmap.createBitmap(144, 200, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(0xff000000 | extras.getInt("page"));
                    try (FileOutputStream file = new FileOutputStream(file(id))) { PreviewBitmapIO.write(bitmap, file); }
                    bitmap.recycle();
                    org.robolectric.Shadows.shadowOf(getContext().getContentResolver()).registerInputStream(PreviewProtocol.image(id), new java.io.FileInputStream(file(id)));
                } catch (Exception e) { throw new IllegalStateException(e); }
                Bundle done = new Bundle(); done.putLong("id", id); done.putInt("page", extras.getInt("page")); done.putInt("pages", 3);
                done.putString("source", "same-native-source"); if(!extras.getString("focus", "").isEmpty()) done.putString("variants","[{\"id\":\""+extras.getString("focus")+"\"}]"); done.putString("imageFormat", PreviewBitmapIO.FORMAT); result = done;
                Bundle accepted = new Bundle(); accepted.putLong("id", id); return accepted;
            }
            return method.equals("preview.result") ? result : null;
        }
        private File file(long id) { return new File(getContext().getCacheDir(), "page-" + id); }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException { return ParcelFileDescriptor.open(file(Long.parseLong(uri.getLastPathSegment())), ParcelFileDescriptor.MODE_READ_ONLY); }
        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri u) { return "application/octet-stream"; }
        @Override public Uri insert(Uri u, ContentValues v) { return null; }
        @Override public int delete(Uri u, String s, String[] a) { return 0; }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    }
    private void pump(BooleanSupplier done) throws Exception {
        for (int n = 0; n < 250 && !done.getAsBoolean(); n++) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10)); Thread.sleep(4); }
        assertTrue("Asynchronous preview must complete", done.getAsBoolean());
    }
    @Test public void prefetchedPageAppearsSynchronouslyWithoutLoadingOrAnotherCapture() throws Exception {
        Bridge bridge = Robolectric.buildContentProvider(Bridge.class).create().get(); ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings", bridge);
        List<Integer> displayed = new ArrayList<>(); int[] loading = {0};
        PreviewClient client = new PreviewClient(RuntimeEnvironment.getApplication(), new PreviewClient.Listener() {
            public void loading() { loading[0]++; }
            public void frame(Bitmap bitmap, Bundle data) { assertEquals(144, bitmap.getWidth()); displayed.add(data.getInt("page")); }
            public void error(String message) { fail(message); }
        });
        client.start(); pump(() -> bridge.requests.size() >= 2);
        // Wait for the prefetched atlas to finish crossing the provider, then select it.
        pump(() -> org.robolectric.util.ReflectionHelpers.<java.util.Map<?,?>>getField(client, "frames").size() >= 2);
        int captures = bridge.requests.size(), before = loading[0];
        client.setPage(1); assertEquals(Integer.valueOf(1), displayed.get(displayed.size()-1));
        assertEquals(captures, bridge.requests.size()); assertEquals(before, loading[0]);
        client.setPage(0); assertEquals(Integer.valueOf(0), displayed.get(displayed.size()-1));
        assertTrue(bridge.requests.get(0).getBoolean("refreshStates")); assertFalse(bridge.requests.get(1).getBoolean("refreshStates"));
        client.stop();
    }
    @Test public void localLayoutEditsAndSourceNotificationsDoNotRequestAnotherFrameOrBlockTheEditor() throws Exception {
        Bridge bridge=Robolectric.buildContentProvider(Bridge.class).create().get();ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge);int[] shown={0},loading={0};
        PreviewClient client=new PreviewClient(RuntimeEnvironment.getApplication(),new PreviewClient.Listener() { public void loading(){loading[0]++;} public void frame(Bitmap b,Bundle data){shown[0]++;} public void error(String message){fail(message);} });
        client.start();pump(()->shown[0]>0);client.setFocus("t:bt");pump(()->shown[0]>=2);pump(()->org.robolectric.util.ReflectionHelpers.<Long>getField(client,"requestId")==0);
        int captures=bridge.requests.size(),busy=loading[0];
        for(int n=0;n<12;n++) { client.setLayout("");client.setLayout(QsLayoutTest.source(QsLayoutTest.tile("t:bt",n%3,n%2,1,1)).json()); }
        RuntimeEnvironment.getApplication().getContentResolver().notifyChange(PreviewProtocol.SOURCE,null);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertEquals(captures,bridge.requests.size());assertEquals(busy,loading[0]);client.stop();
    }
    @Test public void returningToAWarmedTileReusesItsNativeVariants() throws Exception {
        Bridge bridge=Robolectric.buildContentProvider(Bridge.class).create().get();ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge);int[] shown={0};
        PreviewClient client=new PreviewClient(RuntimeEnvironment.getApplication(),new PreviewClient.Listener() { public void loading(){} public void frame(Bitmap b,Bundle data){shown[0]++;} public void error(String message){fail(message);} });
        client.start();pump(()->shown[0]>0);client.setFocus("t:bt");pump(()->shown[0]>=2);client.setFocus("t:wifi");pump(()->shown[0]>=3);int captures=bridge.requests.size();client.setFocus("t:bt");shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));assertEquals(captures,bridge.requests.size());client.stop();
    }
    @Test public void styleChangesInvalidatePagesAndRapidNavigationDisplaysOnlyTheLastRequest() throws Exception {
        Bridge bridge = Robolectric.buildContentProvider(Bridge.class).create().get(); ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings", bridge);
        List<Integer> displayed = new ArrayList<>();
        PreviewClient client = new PreviewClient(RuntimeEnvironment.getApplication(), new PreviewClient.Listener() {
            public void loading() { }
            public void frame(Bitmap bitmap, Bundle data) { displayed.add(data.getInt("page")); }
            public void error(String message) { fail(message); }
        });
        client.start(); pump(() -> displayed.size() == 1);
        client.setConfig(new moe.xWey10.tiling.Config(true, 90, 2, 10, false, 0));
        int before = displayed.size(); client.setPage(1); client.setPage(2); assertEquals(before, displayed.size());
        pump(() -> displayed.size() > before); assertEquals(Integer.valueOf(2), displayed.get(displayed.size()-1));
        Bundle last = bridge.requests.get(bridge.requests.size()-1); assertEquals(2, last.getInt("page")); assertEquals(90, last.getInt("scale"));
        client.stop();
    }
}
