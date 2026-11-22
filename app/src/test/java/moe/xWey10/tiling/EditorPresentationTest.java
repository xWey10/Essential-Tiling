package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w393dp-h873dp-port-xxhdpi")
public class EditorPresentationTest {
    @Before public void reset() { ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,1f));SettingsStore.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit(); }
    public static class RestartBridge extends PreviewClientTest.Bridge {
        volatile moe.xWey10.tiling.Config savedAtRestart;volatile int restarts;
        @Override public Bundle call(String method,String arg,Bundle extras) {
            if(method.equals("restart.request")) { savedAtRestart=SettingsStore.read(getContext());restarts++;Bundle b=new Bundle();b.putString("state","unavailable");return b; }
            Bundle response=super.call(method,arg,extras);if(method.equals("preview.request")) result.putString("source",QsLayoutTest.source(QsLayoutTest.tile("t:bt",0,0,1,1)).json());return response;
        }
    }
    @Test public void globalRestartSavesCurrentSettingsAndRequestsTheModuleFromEveryPage() throws Exception {
        RestartBridge bridge=Robolectric.buildContentProvider(RestartBridge.class).create().get();ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=controller.get();ReflectionHelpers.<MaterialSwitch>getField(app,"enabled").setChecked(true);ReflectionHelpers.<Slider>getField(app,"size").setValue(87);
            MenuItem restart=ReflectionHelpers.getField(app,"restartAction");assertTrue(restart.isVisible());assertEquals(R.id.restart_systemui,restart.getItemId());assertNotNull(restart.getIcon());
            for(int page=0;page<4;page++) {
                app.showScreen(page);androidx.appcompat.widget.Toolbar toolbar=find(app.getWindow().getDecorView(),androidx.appcompat.widget.Toolbar.class);assertTrue(toolbar.getMenu().performIdentifierAction(R.id.restart_systemui,0));int expected=page+1;pump(()->bridge.restarts==expected&&!ReflectionHelpers.<Boolean>getField(app,"committing"));
                assertTrue(bridge.savedAtRestart.enabled);assertEquals(87,bridge.savedAtRestart.scalePercent);
            }
        }
    }
    @Test public void scaleAndSpacingRequestAnUnappliedNativePreviewWithGridStillVisible() throws Exception {
        RestartBridge bridge=Robolectric.buildContentProvider(RestartBridge.class).create().get();ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge);
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();app.showScreen(1);View root=app.getWindow().getDecorView();measure(root);PreviewView preview=ReflectionHelpers.getField(app,"preview");Slider size=ReflectionHelpers.getField(app,"size"),gap=ReflectionHelpers.getField(app,"gap");
            assertTrue(preview.isShown());assertTrue(size.isShown());assertTrue(gap.isShown());assertTrue(bounds((View)preview.getParent()).bottom<=bounds(size).top);assertTrue(bounds(gap).bottom<root.getHeight());
            ReflectionHelpers.<MaterialSwitch>getField(app,"enabled").setChecked(true);size.setValue(86);gap.setValue(7);
            pump(()->{ synchronized(bridge.requests) { return bridge.requests.stream().anyMatch(b->b.getBoolean("enabled")&&b.getInt("scale")==86&&b.getInt("gap")==7); } });
            assertFalse(SettingsStore.read(app).enabled);assertEquals(100,SettingsStore.read(app).scalePercent);assertEquals(0,SettingsStore.read(app).gapDp);
        }
    }
    @Test public void catalogueScalesTheSameGridAndReturnsWithoutLosingDraftOrChangingEditorBounds() throws Exception {
        NativeGridTest.Fixture f=new NativeGridTest.Fixture();NativeGrid grid=new NativeGrid(f.panel,f.workspace);var frame=f.render(grid,grid.new Session(""),()->{});
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();ReflectionHelpers.<PreviewClient>getField(app,"previewClient").stop();app.showScreen(1);PreviewView preview=ReflectionHelpers.getField(app,"preview");preview.setFrame(frame.bitmap,frame.metadata);ReflectionHelpers.callInstanceMethod(app,"setPreviewBusy",ReflectionHelpers.ClassParameter.from(boolean.class,false));View root=app.getWindow().getDecorView();measure(root);
            Object parent=preview.getParent();RectF before=bounds(preview);String draft=preview.getLayoutDraft().json();org.robolectric.shadows.ShadowChoreographer.setPaused(true);
            ReflectionHelpers.callInstanceMethod(app,"showTilePicker",ReflectionHelpers.ClassParameter.from(long.class,8L),ReflectionHelpers.ClassParameter.from(int.class,2),ReflectionHelpers.ClassParameter.from(int.class,1));
            TilePickerDialog dialog=(TilePickerDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();View window=dialog.getWindow().getDecorView();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(17));measure(window);ReflectionHelpers.<TilePickerView>getField(app,"tilePicker").getViewTreeObserver().dispatchOnPreDraw();
            TilePickerView picker=ReflectionHelpers.getField(app,"tilePicker");assertSame(preview,find(window,PreviewView.class));assertEquals(0,dialog.getWindow().getAttributes().windowAnimations);assertFalse("scale="+preview.getScaleX()+" tx="+preview.getTranslationX()+" ready="+ReflectionHelpers.getField(picker,"transitionReady")+" animator="+ReflectionHelpers.getField(picker,"transition")+" enabledAnimations="+android.animation.ValueAnimator.areAnimatorsEnabled()+" attached="+picker.isAttachedToWindow(),preview.isEnabled());
            RectF start=bounds(preview);assertEquals("before="+before+" actual="+start+" scale="+preview.getScaleX()+" translation="+preview.getTranslationX()+","+preview.getTranslationY()+" local="+preview.getLeft()+","+preview.getTop(),before.left,start.left,1);assertEquals(before.top,start.top,1);assertEquals(before.width(),start.width()*preview.getScaleX(),1);
            float initial=preview.getScaleX();assertTrue("The catalogue uses a smaller grid",initial>1);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(140));assertTrue(preview.getScaleX()<initial);assertTrue(preview.getScaleX()>1);float progress=ReflectionHelpers.getField(preview,"catalogueProgress");assertTrue(progress>0&&progress<1);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));assertEquals(1,preview.getScaleX(),.001);assertTrue(preview.isEnabled());assertEquals(1,ReflectionHelpers.<Float>getField(preview,"catalogueProgress"),.001);assertEquals(draft,preview.getLayoutDraft().json());
            assertNotSame(parent,preview.getParent());dialog.dismiss();assertTrue(dialog.isShowing());shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));measure(root);
            assertFalse("Closing must finish within the catalogue transition duration",dialog.isShowing());assertSame(parent,preview.getParent());assertEquals(before,bounds(preview));assertEquals(draft,preview.getLayoutDraft().json());assertEquals(0,ReflectionHelpers.<Float>getField(preview,"catalogueProgress"),.001);assertTrue(preview.isEnabled());
        } finally { frame.bitmap.recycle(); }
    }
    @Test public void closingBeforeTheFirstAnimationFrameRestoresTheEditorAndItsInput() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();app.showScreen(1);View root=app.getWindow().getDecorView();measure(root);PreviewView preview=ReflectionHelpers.getField(app,"preview");Object parent=preview.getParent();ReflectionHelpers.callInstanceMethod(app,"showTilePicker",ReflectionHelpers.ClassParameter.from(long.class,8L),ReflectionHelpers.ClassParameter.from(int.class,1),ReflectionHelpers.ClassParameter.from(int.class,1));
            org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss();shadowOf(Looper.getMainLooper()).idle();assertSame(parent,preview.getParent());assertTrue(preview.isEnabled());assertEquals(1,preview.getScaleX(),0);
        }
    }
    @Test public void compactCatalogueMinusDeletesWithoutSelectingOrResizingFirst() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            String[] removed={null};f.view.setListener(new PreviewView.Listener() {
                public void edited(QsLayout layout) { fail("Minus must use native membership action"); }public void selected(QsLayout.Item item) {}public void message(String text) {}public void remove(String id){removed[0]=id;}
            });f.view.setCatalogueProgress(1);f.touch(MotionEvent.ACTION_DOWN,10,10);f.touch(MotionEvent.ACTION_UP,10,10);assertEquals("t:bt",removed[0]);
        }
    }
    private static void pump(BooleanSupplier done) throws Exception { for(int n=0;n<250&&!done.getAsBoolean();n++){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));Thread.sleep(4);}assertTrue(done.getAsBoolean()); }
    private static void measure(View root){TilePickerViewTest.measure(root,1179,2619);}
    private static RectF bounds(View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return new RectF(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
    private static <T extends View>T find(View root,Class<T> type){return TilePickerViewTest.find(root,type);}
}
