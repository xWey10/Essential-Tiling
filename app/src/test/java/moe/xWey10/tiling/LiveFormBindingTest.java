package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;
import com.android.launcher3.QSTileItemInfo;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import de.robv.android.xposed.XposedBridge;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveFormBindingTest {
    static final String TILE="com.android.systemui.qs.tileimpl.QSTileViewImpl";
    static final class EarlyLoader extends ClassLoader {
        int missing;
        EarlyLoader() { super(QSTileViewImpl.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(name.equals(TILE)) { missing++; throw new ClassNotFoundException(name); }
            return super.loadClass(name,resolve);
        }
    }
    @org.junit.Before public void reset() {
        XposedBridge.hooks.clear(); XposedBridge.failAt="";
        org.robolectric.util.ReflectionHelpers.<java.util.List<?>>getStaticField(NativeFormHooks.class,"hooks").clear();
        NativeForms.supported=false;
        org.robolectric.util.ReflectionHelpers.setStaticField(NativeFormHooks.class,"attemptedView",null);
        org.robolectric.util.ReflectionHelpers.setStaticField(NativeFormHooks.class,"watcher",null);
    }
    @org.junit.After public void release() { XposedBridge.hooks.clear(); XposedBridge.failAt=""; NativeForms.draft.remove(); }
    private void earlyFailure(EarlyLoader loader) throws Exception {
        try { NativeFormHooks.install(loader); fail("Reproduce the phone's ClassNotFoundException"); }
        catch(ClassNotFoundException e) { assertEquals(TILE,e.getMessage()); }
        assertFalse(NativeForms.supported); assertTrue(NativeForms.failure.contains("ClassNotFoundException")); assertTrue(XposedBridge.hooks.isEmpty());
    }
    @Test public void reusesKnownNativeClassesWhenTheEarlyLoaderCannotFindTheTile() throws Exception {
        EarlyLoader loader=new EarlyLoader(); earlyFailure(loader); int lookups=loader.missing;
        NativeFormHooks.install(loader,QSTileViewImpl.class,com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl.class);
        assertEquals("Do not search the known class by name again",lookups,loader.missing);
        assertTrue(NativeForms.supported); assertEquals("",NativeForms.failure); assertEquals(15,XposedBridge.hooks.size());
    }
    @Test public void liveTileRecoversTheExactClassNotFoundFailureAndReallyClipsAnL() throws Exception {
        EarlyLoader loader=new EarlyLoader(); earlyFailure(loader);
        QSTileViewImpl tile=new QSTileViewImpl(RuntimeEnvironment.getApplication()); tile.setBackgroundColor(Color.RED);
        QSTileItemInfo tag=new QSTileItemInfo("flashlight"); tag.spanX=tag.spanY=2; tile.setTag(tag);
        assertTrue(NativeFormHooks.ensure(tile,loader)); assertTrue(NativeForms.supported); assertEquals("",NativeForms.failure);
        QsLayout.Item l=TileExtrusion.grow(new QsLayout.Item("t:flashlight","Flashlight",8,0,2,2,1,true,true),new TileExtrusion.Handle(0,0,2),1);
        NativeForms.draft.set(java.util.Map.of(l.id(),l));
        tile.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY)); tile.layout(0,0,200,200);
        Bitmap image=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888); tile.draw(new Canvas(image));
        assertEquals(Color.RED,image.getPixel(50,50)); assertEquals(0,image.getPixel(150,50)); assertEquals(Color.RED,image.getPixel(150,150)); image.recycle();
        assertEquals(15,XposedBridge.hooks.size()); assertTrue(NativeFormHooks.ensure(tile,loader)); assertEquals("No duplicate hooks on the next native layout",15,XposedBridge.hooks.size());
    }
    @Test public void workspaceTraversalFindsARealTileAndLoadsTheActiveUserAfterLateBinding() throws Exception {
        EarlyLoader loader=new EarlyLoader(); earlyFailure(loader);
        FrameLayout workspace=new FrameLayout(RuntimeEnvironment.getApplication()),page=new FrameLayout(workspace.getContext()); workspace.addView(page);
        com.android.launcher3.Launcher launcher=new com.android.launcher3.Launcher(); launcher.currentUserId=7; com.android.launcher3.Launcher.instance=launcher;
        QsLayout.Item l=TileExtrusion.grow(new QsLayout.Item("t:flashlight","Flashlight",8,0,2,2,1,true,true),new TileExtrusion.Handle(0,0,2),1);
        NativeForms.save(workspace.getContext(),7,QsLayoutTest.source(l)); NativeForms.user(workspace.getContext(),0);
        QSTileViewImpl tile=new QSTileViewImpl(workspace.getContext()); page.addView(tile); assertTrue(NativeFormHooks.ensureTree(workspace));
        assertEquals(l.form(),NativeForms.form(l.id(),2,2));
        com.android.launcher3.Launcher.instance=null;
    }
    @Test public void theFrameworkViewAddedWatcherRecoversAfterTheEarlyClassLookupFailed() throws Exception {
        EarlyLoader loader=new EarlyLoader(); earlyFailure(loader); int[] ready={0};
        NativeFormHooks.watch(tile->ready[0]++);
        FrameLayout parent=new FrameLayout(RuntimeEnvironment.getApplication());
        QSTileViewImpl tile=new QSTileViewImpl(parent.getContext()); parent.addView(tile);
        java.lang.reflect.Method added=android.view.ViewGroup.class.getDeclaredMethod("onViewAdded",View.class);
        XposedBridge.dispatch(added,parent,new Object[]{tile},args->null);
        assertTrue(NativeForms.supported); assertEquals(1,ready[0]); assertEquals(16,XposedBridge.hooks.size());
        XposedBridge.dispatch(added,parent,new Object[]{tile},args->null);
        assertEquals("Repeated child events do not reinstall hooks",1,ready[0]); assertEquals(16,XposedBridge.hooks.size());
    }
    @Test public void theTwoTopCellDotsArePaintedEvenBeforeCapabilitiesRecover() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);
            NativePreviewServer.Frame wide=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",0,2,2,1).json()),()->{});
            android.os.Bundle data=new android.os.Bundle(wide.metadata); data.putBoolean("formsSupported",false); data.putBoolean("cellFormsSupported",false); data.putString("formsError","ClassNotFoundException: "+TILE);
            f.view.setFrame(wide.bitmap,data);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(260)); NativeGridTest.draw(f.view,400).recycle();
            android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,0,150,250,0),up=android.view.MotionEvent.obtain(0,0,1,150,250,0); f.view.onTouchEvent(down); f.view.onTouchEvent(up); down.recycle(); up.recycle();
            Bitmap image=NativeGridTest.draw(f.view,400); int accent=new ThemePalette(f.view.getContext()).accent;
            assertEquals("Left top cell handle",accent,image.getPixel(55,208)); assertEquals("Right top cell handle",accent,image.getPixel(149,208));
            // A point has a solid visible area, rather than only the one-pixel grid line.
            assertEquals(accent,image.getPixel(55,210)); assertEquals(accent,image.getPixel(149,210)); image.recycle();
        }
    }
}
