package moe.xWey10.tiling;

import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Real Activity/buttons/provider/preview drafts, not direct calls to resizeSelected. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FormEditorFlowTest {
    private TileRemovalFlowTest.Bridge bridge(boolean supported) throws Exception {
        TileRemovalFlowTest.Bridge bridge=Robolectric.buildContentProvider(TileRemovalFlowTest.Bridge.class).create().get();
        bridge.fixture=new NativeGridTest.Fixture();
        // A wide tile in the last row reproduces all larger presets being rejected below the page.
        bridge.fixture.info.cellY=3; bridge.fixture.info.spanX=2;
        NativeGridTest.Lp lp=(NativeGridTest.Lp)bridge.fixture.tile.getLayoutParams(); lp.cellY=3; lp.cellHSpan=2;
        NativeFormHooksTest.install();
        if(!supported) { NativeForms.supported=false; NativeForms.failure="NoSuchFieldException: View.mPaddingLeft"; }
        ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge); return bridge;
    }
    private void pump(java.util.function.BooleanSupplier done) throws Exception {
        for(int n=0;n<350&&!done.getAsBoolean();n++) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10)); Thread.sleep(4); }
        assertTrue("Editor flow must complete",done.getAsBoolean());
    }
    private PreviewView ready(MainActivity app) throws Exception {
        PreviewView view=ReflectionHelpers.getField(app,"preview"); pump(()->view.getLayoutDraft()!=null&&!ReflectionHelpers.<Boolean>getField(app,"previewBusy"));
        NativeGridTest.draw(view,400).recycle(); return view;
    }
    private void touch(PreviewView v,int action,float x,float y) {
        MotionEvent e=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0); v.onTouchEvent(e); e.recycle();
    }
    private Button find(View view,String label) {
        if(view instanceof Button b&&label.contentEquals(b.getText())) return b;
        if(view instanceof ViewGroup group) for(int n=0;n<group.getChildCount();n++) { Button b=find(group.getChildAt(n),label); if(b!=null) return b; }
        return null;
    }
    private void click(MainActivity app,String label) throws Exception {
        Button b=find(app.getWindow().getDecorView(),label); assertNotNull(label,b); assertTrue(label+" must be enabled",b.isEnabled()); assertTrue(b.performClick()); ready(app);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(260)); NativeGridTest.draw(ReflectionHelpers.getField(app,"preview"),400).recycle();
    }
    @org.junit.After public void reset() { de.robv.android.xposed.XposedBridge.hooks.clear(); de.robv.android.xposed.XposedBridge.failAt=""; }
    @Test public void allFourPresetButtonsWorkForATileInTheLastRow() throws Exception {
        bridge(true);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get(); PreviewView view=ready(app); touch(view,MotionEvent.ACTION_DOWN,150,350); touch(view,MotionEvent.ACTION_UP,150,350);
            click(app,"2×2"); QsLayout.Item item=view.getLayoutDraft().item("t:bt"); assertEquals(2,item.w()); assertEquals(2,item.h()); assertEquals(2,item.y());
            click(app,"1×3"); item=view.getLayoutDraft().item("t:bt"); assertEquals(1,item.w()); assertEquals(3,item.h()); assertEquals(1,item.y());
            click(app,"L"); assertTrue(view.getLayoutDraft().item("t:bt").form().cut());
            click(app,"Прямоугольник"); assertEquals(TileForm.RECT,view.getLayoutDraft().item("t:bt").form());
        }
    }
    @Test public void aHandleGrowsALocalBranchThroughTheActualActivityAndProvider() throws Exception {
        bridge(true);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get(); PreviewView view=ready(app);
            assertTrue(ReflectionHelpers.getField(view,"cellFormsSupported"));
            touch(view,MotionEvent.ACTION_DOWN,150,350); touch(view,MotionEvent.ACTION_UP,150,350);
            touch(view,MotionEvent.ACTION_DOWN,55,308); touch(view,MotionEvent.ACTION_MOVE,55,208); touch(view,MotionEvent.ACTION_UP,55,208);
            ready(app); QsLayout.Item grown=view.getLayoutDraft().item("t:bt"); assertEquals("1011",grown.form().cells()); assertEquals(2,grown.y());
            assertTrue(ReflectionHelpers.<String>getField(app,"layoutDraft").contains("1011"));
        }
    }
    @Test public void thePhoneClassNotFoundFailureRecoversBeforeTheRealEditorGrowsAnL() throws Exception {
        TileRemovalFlowTest.Bridge bridge=bridge(true);
        LiveFormBindingTest.EarlyLoader early=new LiveFormBindingTest.EarlyLoader();
        try { NativeFormHooks.install(early); fail("Reproduce the phone error first"); } catch(ClassNotFoundException expected) { }
        assertFalse(NativeForms.supported);
        bridge.fixture.page.container.removeView(bridge.fixture.tile);
        com.android.systemui.qs.tileimpl.QSTileViewImpl nativeTile=new com.android.systemui.qs.tileimpl.QSTileViewImpl(bridge.fixture.context);
        nativeTile.setTag(bridge.fixture.info); nativeTile.setBackgroundColor(android.graphics.Color.RED);
        NativeGridTest.Lp lp=new NativeGridTest.Lp(); lp.cellY=3; lp.cellHSpan=2; bridge.fixture.page.container.addView(nativeTile,lp);
        org.robolectric.util.ReflectionHelpers.setStaticField(NativeFormHooks.class,"watcher",null);
        org.robolectric.util.ReflectionHelpers.setStaticField(NativeFormHooks.class,"attemptedView",null);
        NativeFormHooks.watch(tile->{});
        java.lang.reflect.Method added=ViewGroup.class.getDeclaredMethod("onViewAdded",View.class);
        de.robv.android.xposed.XposedBridge.dispatch(added,bridge.fixture.page.container,new Object[]{nativeTile},args->null);
        assertTrue(NativeForms.supported); assertEquals("",NativeForms.failure);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get(); PreviewView view=ready(app);
            assertTrue(ReflectionHelpers.getField(view,"cellFormsSupported"));
            touch(view,MotionEvent.ACTION_DOWN,150,350); touch(view,MotionEvent.ACTION_UP,150,350);
            android.graphics.Bitmap before=NativeGridTest.draw(view,400); int accent=new ThemePalette(app).accent;
            assertEquals(accent,before.getPixel(55,308)); assertEquals(accent,before.getPixel(149,308)); before.recycle();
            touch(view,MotionEvent.ACTION_DOWN,55,308); touch(view,MotionEvent.ACTION_MOVE,55,208); touch(view,MotionEvent.ACTION_UP,55,208);
            ready(app); assertEquals("1011",view.getLayoutDraft().item("t:bt").form().cells());
            assertFalse(ReflectionHelpers.<TextView>getField(app,"previewStatus").getText().toString().contains("ClassNotFoundException"));
            click(app,"Прямоугольник"); assertFalse(view.getLayoutDraft().item("t:bt").form().cut());
        }
    }
    @Test public void theOldCapabilityErrorDisappearsWhenTheLiveModuleRecovers() throws Exception {
        bridge(false);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get(); PreviewView view=ready(app);
            touch(view,MotionEvent.ACTION_DOWN,150,350); touch(view,MotionEvent.ACTION_UP,150,350); click(app,"L");
            assertFalse(ReflectionHelpers.<String>getField(app,"previewActionError").isEmpty());
            NativeFormHooksTest.install();
            ReflectionHelpers.<PreviewClient>getField(app,"previewClient").refresh(); ready(app);
            assertEquals("",ReflectionHelpers.<String>getField(app,"previewActionError")); assertEquals("",view.capabilityMessage());
            touch(view,MotionEvent.ACTION_DOWN,55,308); touch(view,MotionEvent.ACTION_MOVE,55,208); touch(view,MotionEvent.ACTION_UP,55,208); ready(app);
            assertEquals("1011",view.getLayoutDraft().item("t:bt").form().cells());
        }
    }
    @Test public void unavailableHooksHaveAVisiblePersistentReasonAndButtonErrorsAreActuallyOnScreen() throws Exception {
        bridge(false);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get(); PreviewView view=ready(app); TextView status=ReflectionHelpers.getField(app,"previewStatus");
            assertNotNull(status.getParent()); assertEquals(View.VISIBLE,status.getVisibility()); assertTrue(status.getText().toString().contains("mPaddingLeft"));
            touch(view,MotionEvent.ACTION_DOWN,150,350); touch(view,MotionEvent.ACTION_UP,150,350);
            click(app,"1×3"); assertEquals(1,view.getLayoutDraft().item("t:bt").h());
            assertEquals(View.VISIBLE,status.getVisibility()); assertTrue(status.getText().toString().contains("mPaddingLeft"));
            touch(view,MotionEvent.ACTION_DOWN,55,308); touch(view,MotionEvent.ACTION_MOVE,55,208); touch(view,MotionEvent.ACTION_UP,55,208);
            assertEquals("Missing shapes must not silently turn a local drag into a whole 2x2",1,view.getLayoutDraft().item("t:bt").h());
        }
    }
}
