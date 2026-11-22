package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
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
public class NativeFormHooksTest {
    @org.junit.Before public void resetRecorder() {
        XposedBridge.hooks.clear(); XposedBridge.failAt="";
        org.robolectric.util.ReflectionHelpers.<java.util.List<?>>getStaticField(NativeFormHooks.class,"hooks").clear();
    }
    @org.junit.After public void releaseRecorder() { XposedBridge.hooks.clear(); XposedBridge.failAt=""; NativeForms.draft.remove(); }
    static void install() throws Exception {
        XposedBridge.hooks.clear(); XposedBridge.failAt="";
        org.robolectric.util.ReflectionHelpers.<java.util.List<?>>getStaticField(NativeFormHooks.class,"hooks").clear();
        NativeForms.supported=false;
        NativeFormHooks.install(QSTileViewImpl.class.getClassLoader());
        assertTrue("Real installer must enable forms, without a forced success flag",NativeForms.supported);
        assertEquals("",NativeForms.failure); assertEquals(15,XposedBridge.hooks.size());
    }
    @Test public void completeInstallerRegistersAllTargetsAndRealCallbacksKeepTheNativeBodyInsideTheArm() throws Exception {
        install();
        for(boolean bluetooth:new boolean[]{false,true}) {
            QSTileViewImpl tile=bluetooth?new com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl(RuntimeEnvironment.getApplication()):new QSTileViewImpl(RuntimeEnvironment.getApplication());
            QSTileItemInfo info=new QSTileItemInfo("demo"); info.spanX=info.spanY=2; tile.setTag(info); tile.mSize=2; tile.setBackgroundColor(Color.RED); tile.setClickable(true); tile.setPadding(8,6,8,6);
            QsLayout.Item item=TileExtrusion.grow(new QsLayout.Item("t:demo","",8,0,1,2,1,true,true),new TileExtrusion.Handle(0,0,2),1);
            NativeForms.draft.set(java.util.Map.of(item.id(),item));
            View icon=new View(tile.getContext()); icon.setBackgroundColor(Color.GREEN); tile.addView(icon,new LinearLayout.LayoutParams(20,20));
            for(int n=0;n<2;n++) {
                tile.forceLayout(); tile.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY)); tile.layout(0,0,200,200);
                assertEquals(1,tile.mSize); assertEquals(200,tile.getMeasuredWidth()); assertEquals(200,tile.getMeasuredHeight());
                assertEquals(20,icon.getWidth()); assertEquals(100,icon.getLeft()+10); assertEquals(150,icon.getTop()+10); assertEquals(6,tile.getPaddingTop());
            }
            Bitmap image=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888); tile.draw(new Canvas(image));
            assertEquals(0,image.getPixel(150,50)); assertEquals(Color.RED,image.getPixel(50,50)); assertEquals(Color.GREEN,image.getPixel(100,150)); image.recycle();
            MotionEvent hole=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,150,50,0); assertFalse(tile.dispatchTouchEvent(hole)); hole.recycle();
            NativeForms.draft.remove();
        }
    }
    @Test public void aRejectedHookRollsBackAndReportsItsExactTarget() throws Exception {
        XposedBridge.failAt="draw";
        try { NativeFormHooks.install(QSTileViewImpl.class.getClassLoader()); fail("Must propagate install failure"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("android.view.View#draw")); }
        assertFalse(NativeForms.supported); assertTrue(XposedBridge.hooks.isEmpty());
        assertTrue(NativeForms.failure.contains("android.view.View#draw")); assertTrue(NativeForms.failure.contains("test hook rejection"));
        android.os.Bundle metadata=new android.os.Bundle(); metadata.putString("formsError",NativeForms.failure); metadata.putString("moduleVersion",BuildConfig.VERSION_NAME);
        assertTrue(PreviewView.capabilityMessage(metadata).contains("android.view.View#draw"));
    }
}
