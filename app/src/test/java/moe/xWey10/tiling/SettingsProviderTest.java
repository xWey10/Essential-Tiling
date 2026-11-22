package moe.xWey10.tiling;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsProviderTest {
    @Test public void layoutAndMembershipWritesCannotRaceAcrossActivities() {
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle layout = new Bundle(); layout.putString("layout", QsLayoutTest.source(QsLayoutTest.tile("a", 0, 0, 1, 1)).json());
        Bundle add = new Bundle(); add.putString("action", "add");
        provider.call("layout.request", null, layout);
        assertThrows(IllegalStateException.class, () -> provider.call("tiles.request", null, add));
        SettingsProvider fresh = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        fresh.call("tiles.request", null, add);
        assertThrows(IllegalStateException.class, () -> fresh.call("layout.request", null, layout));
    }
    @Test public void tileOperationsRequireAppRequestsAndSystemUiResults() {
        Context app = RuntimeEnvironment.getApplication();
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle b = new Bundle(); b.putString("action", "catalog");
        long id = provider.call("tiles.request", null, b).getLong("id");
        assertThrows(SecurityException.class, () -> provider.call("tiles.claim", null, null));
        assertThrows(SecurityException.class, () -> provider.call("tiles.publish", null, b));
        ShadowBinder.setCallingUid(24321); Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321, new String[]{"com.android.systemui"});
        assertThrows(SecurityException.class, () -> provider.call("tiles.request", null, b));
        assertNotNull(provider.call("tiles.claim", null, null)); assertNull(provider.call("tiles.claim", null, null));
        Bundle page = new Bundle(); page.putLong("id", id); page.putStringArrayList("rows", new java.util.ArrayList<>(java.util.List.of("wifi", "custom(app/.Tile)")));
        provider.call("tiles.publish", null, page); provider.call("tiles.complete", null, page);
        assertThrows(SecurityException.class, () -> provider.call("tiles.cancel", null, page));
        ShadowBinder.reset(); assertEquals(2, provider.call("tiles.status", null, page).getInt("count"));
        assertEquals(2, provider.call("tiles.read", null, page).getStringArrayList("rows").size());
        ShadowBinder.setCallingUid(24322);
        for (String action : new String[]{"request", "claim", "publish", "complete", "status", "read", "cancel"})
            assertThrows(SecurityException.class, () -> provider.call("tiles." + action, null, page));
    }
    @After public void resetCaller() { ShadowBinder.reset(); }

    @Test public void layoutWritesRequireAppRequestAndSystemUiAcknowledgement() {
        Context app = RuntimeEnvironment.getApplication();
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle draft = new Bundle(); draft.putString("layout", QsLayoutTest.source(QsLayoutTest.tile("a", 0, 0, 1, 1)).json());
        Bundle accepted = provider.call("layout.request", null, draft); long id = accepted.getLong("id");
        assertThrows(SecurityException.class, () -> provider.call("layout.claim", null, null));
        assertThrows(SecurityException.class, () -> provider.call("layout.complete", null, accepted));
        ShadowBinder.setCallingUid(24321);
        Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321, new String[]{"com.android.systemui"});
        assertThrows(SecurityException.class, () -> provider.call("layout.request", null, draft));
        assertNotNull(provider.call("layout.claim", null, null)); assertNull(provider.call("layout.claim", null, null));
        Bundle done = new Bundle(); done.putLong("id", id); done.putString("error", ""); provider.call("layout.complete", null, done);
        ShadowBinder.reset(); Bundle status = new Bundle(); status.putLong("id", id);
        assertEquals("saved", provider.call("layout.status", null, status).getString("state"));
        ShadowBinder.setCallingUid(24322);
        for (String method : new String[]{"layout.request", "layout.status", "layout.claim", "layout.complete"})
            assertThrows(SecurityException.class, () -> provider.call(method, null, draft));
    }

    @Test public void systemUiReadsDeviceProtectedSnapshot() {
        Context app = RuntimeEnvironment.getApplication();
        SettingsStore.write(app, new moe.xWey10.tiling.Config(true, 88, 6, 12, true, 42));
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowBinder.setCallingUid(24321);
        Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321,
                new String[]{"com.android.systemui"});
        try (Cursor row = provider.query(SettingsProvider.URI, SettingsProvider.COLUMNS, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals(1, row.getInt(0)); assertEquals(88, row.getInt(1));
            assertEquals(6, row.getInt(2)); assertEquals(12, row.getInt(3));
            assertEquals(1, row.getInt(4)); assertEquals(42, row.getLong(5));
            assertFalse(row.moveToNext());
        }
    }

    @Test public void unrelatedUidCannotReadSettingsOrForgeReport() {
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowBinder.setCallingUid(24322);
        assertThrows(SecurityException.class,
                () -> provider.query(SettingsProvider.URI, null, null, null, null));
        assertThrows(SecurityException.class, () -> provider.call("report", null, new Bundle()));
    }

    @Test public void providerDoesNotOfferSettingsWrites() {
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        assertThrows(UnsupportedOperationException.class,
                () -> provider.update(SettingsProvider.URI, null, null, null));
        assertThrows(UnsupportedOperationException.class,
                () -> provider.delete(SettingsProvider.URI, null, null));
    }

    @Test public void previewDraftIsClampedAndDoesNotApplySettings() {
        Context app = RuntimeEnvironment.getApplication();
        SettingsStore.write(app, moe.xWey10.tiling.Config.DEFAULT);
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle draft = new Bundle(); draft.putBoolean("enabled", true); draft.putInt("scale", 5);
        draft.putInt("gap", 50); draft.putInt("radius", 100);
        Bundle request = provider.call("preview.request", null, draft);
        assertEquals(80, request.getInt("scale")); assertEquals(12, request.getInt("gap"));
        assertEquals(48, request.getInt("radius")); assertTrue(request.getLong("id") > 0);
        assertFalse(SettingsStore.read(app).enabled);
    }

    @Test public void systemUiPublishesImageAndOnlySettingsAppCanReadIt() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle request = provider.call("preview.request", null, PreviewProtocol.request(moe.xWey10.tiling.Config.DEFAULT, 0));
        long id = request.getLong("id");
        assertThrows(SecurityException.class, () -> provider.openFile(PreviewProtocol.image(id), "w"));
        ShadowBinder.setCallingUid(24321);
        Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321, new String[]{"com.android.systemui"});
        assertEquals(id, provider.call("preview.pending", null, null).getLong("id"));
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(provider.openFile(PreviewProtocol.image(id), "w"))) {
            output.write(new byte[]{1, 2, 3});
        }
        Bundle done = new Bundle(); done.putLong("id", id); done.putInt("pages", 2);
        provider.call("preview.complete", null, done);
        assertThrows(SecurityException.class, () -> provider.openFile(PreviewProtocol.image(id), "r"));
        ShadowBinder.reset();
        assertEquals(2, provider.call("preview.result", null, null).getInt("pages"));
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(PreviewProtocol.image(id), "r"))) {
            assertArrayEquals(new byte[]{1, 2, 3}, input.readAllBytes());
        }
    }

    @Test public void obsoleteResponseCannotReplaceNewDraft() {
        Context app = RuntimeEnvironment.getApplication();
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle first = provider.call("preview.request", null, PreviewProtocol.request(moe.xWey10.tiling.Config.DEFAULT, 0));
        Bundle second = provider.call("preview.request", null, PreviewProtocol.request(moe.xWey10.tiling.Config.DEFAULT, 1));
        assertTrue(second.getLong("id") > first.getLong("id"));
        assertThrows(FileNotFoundException.class, () -> provider.openFile(PreviewProtocol.image(first.getLong("id")), "r"));
        ShadowBinder.setCallingUid(24321);
        Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321, new String[]{"com.android.systemui"});
        first.putString("error", "old failure"); provider.call("preview.complete", null, first);
        ShadowBinder.reset();
        assertNull(provider.call("preview.result", null, null));
    }

    @Test public void unrelatedUidCannotRequestOrReadNativePreview() {
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowBinder.setCallingUid(24322);
        assertThrows(SecurityException.class, () -> provider.call("preview.request", null, new Bundle()));
        assertThrows(SecurityException.class, () -> provider.call("preview.pending", null, null));
        assertThrows(SecurityException.class, () -> provider.call("preview.result", null, null));
        assertThrows(SecurityException.class, () -> provider.call("preview.complete", null, new Bundle()));
    }

    @Test public void restartRequiresAppRequestAndSystemUiClaimThenConfirmsNewSession() {
        Context app = RuntimeEnvironment.getApplication();
        app.createDeviceProtectedStorageContext().getSharedPreferences("systemui_control", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsStore.write(app, new moe.xWey10.tiling.Config(true, 90, 4, 12, false, 42));
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        Bundle hello = new Bundle(); hello.putString("session", "session-A"); hello.putInt("protocol", 1);
        assertThrows(SecurityException.class, () -> provider.call("restart.register", null, hello));
        assertThrows(SecurityException.class, () -> provider.call("restart.claim", null, hello));
        ShadowBinder.setCallingUid(24321); ShadowBinder.setCallingPid(5321);
        Shadows.shadowOf(app.getPackageManager()).setPackagesForUid(24321, new String[]{"com.android.systemui"});
        provider.call("restart.register", null, hello);
        assertThrows(SecurityException.class, () -> provider.call("restart.request", null, null));
        ShadowBinder.reset();
        Bundle request = provider.call("restart.request", null, null);
        assertEquals("pending", request.getString("state"));
        ShadowBinder.setCallingUid(24321); ShadowBinder.setCallingPid(5321);
        assertEquals("accepted", provider.call("restart.claim", null, hello).getString("state"));
        assertNull(provider.call("restart.claim", null, hello));
        hello.putString("session", "session-B"); ShadowBinder.setCallingPid(5322);
        provider.call("restart.register", null, hello);
        ShadowBinder.reset();
        Bundle status = provider.call("restart.status", null, request);
        assertEquals("restarted", status.getString("state"));
        assertEquals("session-B", status.getString("session"));
        assertEquals(42, SettingsStore.read(app).revision);
        assertEquals(90, SettingsStore.read(app).scalePercent);
    }

    @Test public void unrelatedUidCannotUseAnyRestartOperation() {
        SettingsProvider provider = Robolectric.buildContentProvider(SettingsProvider.class).create().get();
        ShadowBinder.setCallingUid(24322);
        for (String operation : new String[]{"restart.request", "restart.status", "restart.register", "restart.claim"}) {
            assertThrows(SecurityException.class, () -> provider.call(operation, null, new Bundle()));
        }
    }
}
