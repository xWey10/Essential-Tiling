package moe.xWey10.tiling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class OperationDeadlineTest {
  public static class Bridge extends ContentProvider {
    volatile CountDownLatch gate;
    final CountDownLatch entered = new CountDownLatch(1);
    volatile String blocked = "";
    boolean failLayout;
    final AtomicInteger requests = new AtomicInteger();

    public boolean onCreate() {
      return true;
    }

    public Bundle call(String method, String arg, Bundle extras) {
      if (method.equals(blocked)) {
        entered.countDown();
        try {
          if (gate != null && !gate.await(5, TimeUnit.SECONDS))
            throw new IllegalStateException("Fixture blocked");
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
      Bundle b = new Bundle();
      switch (method) {
        case "layout.request":
          requests.incrementAndGet();
          b.putLong("id", 1);
          break;
        case "layout.status":
          b.putString("state", failLayout ? "error" : "saved");
          if (failLayout) b.putString("error", "fixture save failure");
          break;
        case "restart.request":
          requests.incrementAndGet();
          b.putString("id", "id");
          b.putString("session", "old");
          break;
        case "restart.status":
          b.putInt("protocol", RestartControl.PROTOCOL);
          b.putString("session", "new");
          break;
        default:
          return null;
      }
      return b;
    }

    public Cursor query(Uri u, String[] p, String s, String[] a, String order) {
      return null;
    }

    public String getType(Uri u) {
      return null;
    }

    public Uri insert(Uri u, ContentValues v) {
      return null;
    }

    public int delete(Uri u, String s, String[] a) {
      return 0;
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
      return 0;
    }
  }

  private Bridge bridge() {
    var b = Robolectric.buildContentProvider(Bridge.class).create().get();
    ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings", b);
    return b;
  }

  private static void pump(BooleanSupplier done) throws Exception {
    for (int n = 0; n < 300 && !done.getAsBoolean(); n++) {
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
      Thread.sleep(3);
    }
    assertTrue(done.getAsBoolean());
  }

  @Test
  public void layoutTimeoutReleasesTheUiBeforeRequestReturnsAndCompletesOnlyOnce()
      throws Exception {
    Bridge b = bridge();
    b.blocked = "layout.request";
    b.gate = new CountDownLatch(1);
    LayoutClient client = new LayoutClient(RuntimeEnvironment.getApplication());
    int[] callbacks = {0}, successes = {0};
    LayoutClient.Listener listener =
        (success, error) -> {
          callbacks[0]++;
          if (success) successes[0]++;
        };
    try {
      client.save("{}", listener);
      pump(() -> b.entered.getCount() == 0);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(15_001));
      assertEquals(1, callbacks[0]);
      assertEquals(0, successes[0]);
      b.gate.countDown();
      pump(() -> b.requests.get() == 1);
      Thread.sleep(20);
      shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, callbacks[0]);
      client.save("{}", listener);
      pump(() -> callbacks[0] == 2);
      assertEquals(1, successes[0]);
    } finally {
      b.gate.countDown();
      client.stop();
    }
  }

  @Test
  public void restartTimeoutAlsoCoversAcceptanceAndDoesNotReportLateSuccess() throws Exception {
    Bridge b = bridge();
    b.blocked = "restart.request";
    b.gate = new CountDownLatch(1);
    int[] callbacks = {0}, successes = {0};
    RestartClient client =
        new RestartClient(
            RuntimeEnvironment.getApplication(),
            (success, text) -> {
              callbacks[0]++;
              if (success) successes[0]++;
            });
    try {
      client.start();
      pump(() -> b.entered.getCount() == 0);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(15_001));
      assertEquals(1, callbacks[0]);
      assertEquals(0, successes[0]);
      b.gate.countDown();
      pump(() -> b.requests.get() == 1);
      Thread.sleep(20);
      shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, callbacks[0]);
      client.start();
      pump(() -> callbacks[0] == 2);
      assertEquals(1, successes[0]);
    } finally {
      b.gate.countDown();
      client.stop();
    }
  }

  @Test
  public void failedLayoutSaveLeavesBothAppearanceAndGeometryStaged() throws Exception {
    Bridge b = bridge();
    b.failLayout = true;
    Context context = RuntimeEnvironment.getApplication();
    SettingsStore.prefs(context).edit().clear().commit();
    DraftStore.clear(context);
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      ReflectionHelpers.<PreviewClient>getField(a, "previewClient").stop();
      NativeGridTest.Fixture n = new NativeGridTest.Fixture();
      NativeGrid g = new NativeGrid(n.panel, n.workspace);
      var frame = n.render(g, g.new Session(""), () -> {});
      PreviewClient.Listener updates =
          ReflectionHelpers.getField(ReflectionHelpers.getField(a, "previewClient"), "listener");
      updates.frame(frame.bitmap, frame.metadata);
      PreviewView v = ReflectionHelpers.getField(a, "preview");
      NativeGridTest.draw(v, 400).recycle();
      v.getAccessibilityNodeProvider().performAction(1, R.id.access_move_right, Bundle.EMPTY);
      ReflectionHelpers.<Slider>getField(a, "size").setValue(85);
      ReflectionHelpers.<MaterialSwitch>getField(a, "enabled").setChecked(true);
      ReflectionHelpers.<Button>getField(a, "apply").performClick();
      pump(() -> !ReflectionHelpers.<Boolean>getField(a, "committing"));
      assertEquals(0, SettingsStore.read(a).revision);
      assertEquals(100, SettingsStore.read(a).scalePercent);
      assertEquals(85, ReflectionHelpers.<Slider>getField(a, "size").getValue(), 0);
      assertFalse(ReflectionHelpers.<String>getField(a, "layoutDraft").isEmpty());
      assertTrue(ReflectionHelpers.<Button>getField(a, "apply").isEnabled());
      assertEquals(
          "fixture save failure",
          ReflectionHelpers.<TextView>getField(a, "message").getText().toString());
      frame.bitmap.recycle();
    }
  }
}
