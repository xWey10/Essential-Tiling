package moe.xWey10.tiling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.os.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class PreviewLifecycleTest {
  public static class Bridge extends PreviewClientTest.Bridge {
    volatile CountDownLatch requestGate, resultGate;
    final CountDownLatch enteredRequest = new CountDownLatch(1),
        enteredResult = new CountDownLatch(1);
    final AtomicInteger reads = new AtomicInteger();

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
      if (method.equals("preview.request")) {
        enteredRequest.countDown();
        await(requestGate);
      }
      if (method.equals("preview.result")) {
        reads.incrementAndGet();
        enteredResult.countDown();
        await(resultGate);
      }
      Bundle reply = super.call(method, arg, extras);
      if (method.equals("preview.request") && result != null) result.putInt("pages", 1);
      return reply;
    }

    static void await(CountDownLatch gate) {
      try {
        if (gate != null && !gate.await(5, TimeUnit.SECONDS))
          throw new IllegalStateException("Fixture blocked");
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static void pump(BooleanSupplier done) throws Exception {
    for (int n = 0; n < 300 && !done.getAsBoolean(); n++) {
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
      Thread.sleep(3);
    }
    assertTrue("Preview flow must finish", done.getAsBoolean());
  }

  private Bridge bridge() {
    var bridge = Robolectric.buildContentProvider(Bridge.class).create().get();
    ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings", bridge);
    return bridge;
  }

  private static class Display implements PreviewClient.Listener {
    int shown, loading, errors;

    public void loading() {
      loading++;
    }

    public void frame(Bitmap b, Bundle m) {
      shown++;
    }

    public void error(String text) {
      errors++;
    }
  }

  @Test
  public void resumeUsesTheExistingFrameAndRetainsALocalDraftWithoutAnotherLoadingCycle()
      throws Exception {
    Bridge b = bridge();
    Display d = new Display();
    PreviewClient client = new PreviewClient(RuntimeEnvironment.getApplication(), d);
    try {
      client.start();
      pump(() -> d.shown == 1);
      int captures = b.requests.size(), busy = d.loading;
      client.stop();
      client.start();
      assertEquals(2, d.shown);
      assertEquals(busy, d.loading);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
      assertEquals(captures, b.requests.size());
      client.setLayout(QsLayoutTest.source(QsLayoutTest.tile("t:bt", 1, 1, 1, 1)).json());
      client.stop();
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));
      client.start();
      assertEquals(3, d.shown);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
      assertEquals(captures, b.requests.size());
      assertEquals(0, d.errors);
      client.refresh();
      pump(() -> d.shown == 4);
      assertEquals(captures + 1, b.requests.size());
    } finally {
      client.stop();
    }
  }

  @Test
  public void notificationBurstDoesNotDecodeTheSameAtlasRepeatedly() throws Exception {
    Bridge b = bridge();
    b.resultGate = new CountDownLatch(1);
    Display d = new Display();
    PreviewClient client = new PreviewClient(RuntimeEnvironment.getApplication(), d);
    try {
      client.start();
      pump(() -> b.enteredResult.getCount() == 0);
      for (int n = 0; n < 100; n++)
        RuntimeEnvironment.getApplication()
            .getContentResolver()
            .notifyChange(PreviewProtocol.RESULT, null);
      shadowOf(Looper.getMainLooper()).idle();
      b.resultGate.countDown();
      pump(() -> d.shown == 1);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
      Thread.sleep(25);
      shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, b.reads.get());
      assertEquals(1, d.shown);
      assertEquals(0, d.errors);
    } finally {
      b.resultGate.countDown();
      client.stop();
    }
  }

  @Test
  public void deadlineCoversAProviderCallThatHasNotReturnedAndIgnoresItsLateAcceptance()
      throws Exception {
    Bridge b = bridge();
    b.requestGate = new CountDownLatch(1);
    Display d = new Display();
    PreviewClient client = new PreviewClient(RuntimeEnvironment.getApplication(), d);
    try {
      client.start();
      pump(() -> b.enteredRequest.getCount() == 0);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8_001));
      assertEquals(1, d.errors);
      assertEquals(0, d.shown);
      b.requestGate.countDown();
      pump(() -> b.requests.size() == 1);
      Thread.sleep(25);
      shadowOf(Looper.getMainLooper()).idle();
      assertEquals(0, d.shown);
      client.refresh();
      pump(() -> d.shown == 1);
      assertEquals(1, d.errors);
    } finally {
      b.requestGate.countDown();
      client.stop();
    }
  }
}
