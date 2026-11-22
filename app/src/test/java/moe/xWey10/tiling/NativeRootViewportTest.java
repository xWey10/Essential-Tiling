package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import com.android.launcher3.QSTileItemInfo;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeRootViewportTest {
    @org.junit.After public void clear() { de.robv.android.xposed.XposedBridge.hooks.clear(); NativeForms.draft.remove(); }
    @Test public void fullRootMeasurementKeepsTheLowerArmAndNativeContentInsideTheRecordedViewport() throws Exception {
        NativeFormHooksTest.install();
        QSTileViewImpl tile=new QSTileViewImpl(RuntimeEnvironment.getApplication()); tile.recordViewportFromNativeMeasure=true;
        QSTileItemInfo info=new QSTileItemInfo("flashlight"); info.spanX=info.spanY=2; tile.setTag(info);
        TileForm form=new TileForm(0,0,0,"0111","0,1,2,1"); NativeForms.draft.set(java.util.Map.of("t:flashlight",new QsLayout.Item("t:flashlight","Flashlight",8,0,0,2,2,true,true,form)));
        tile.setBackground(new Drawable() {
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            public void draw(Canvas c) { p.setColor(Color.DKGRAY); c.drawRoundRect(new RectF(0,0,tile.nativeMeasureWidth,tile.nativeMeasureHeight),44,44,p); }
            public void setAlpha(int a) { } public void setColorFilter(android.graphics.ColorFilter f) { }
            @SuppressWarnings("deprecation") public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        View glyph=new View(tile.getContext()); glyph.setBackgroundColor(Color.GREEN); tile.addView(glyph,new LinearLayout.LayoutParams(20,20));
        tile.measure(View.MeasureSpec.makeMeasureSpec(188,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(188,View.MeasureSpec.EXACTLY)); tile.layout(0,0,188,188);
        Bitmap image=Bitmap.createBitmap(188,188,Bitmap.Config.ARGB_8888); tile.draw(new Canvas(image));
        java.io.File directory=new java.io.File("build/test-artifacts"); directory.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"native-viewport-"+tile.nativeMeasureHeight+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        assertEquals("Root must receive the full shape height; repairing measuredDimension afterwards is too late",188,tile.nativeMeasureHeight);
        assertEquals(188,tile.nativeLayoutWidth); assertEquals(188,tile.nativeLayoutHeight);
        assertEquals(Color.DKGRAY,image.getPixel(40,145)); assertEquals(Color.GREEN,image.getPixel(94,141));
        assertEquals(0,image.getPixel(40,40)); assertEquals(20,glyph.getWidth()); assertEquals(20,glyph.getHeight()); image.recycle();
    }
    public static class Cells extends android.widget.FrameLayout {
        Cells(android.content.Context c) { super(c); }
        public int getCellWidth() { return 100; } public int getCellHeight() { return 100; }
    }
    @Test public void weightedNativeLabelsAndMatchParentChildrenAreMeasuredInsideTheBody() throws Exception {
        NativeFormHooksTest.install(); Cells parent=new Cells(RuntimeEnvironment.getApplication()); QSTileViewImpl tile=new QSTileViewImpl(parent.getContext()); parent.addView(tile);
        QSTileItemInfo tag=new QSTileItemInfo("flashlight"); tag.spanX=3; tag.spanY=2; tile.setTag(tag);
        TileForm form=new TileForm(0,0,0,"001111","0,1,2,1"); NativeForms.draft.set(java.util.Map.of("t:flashlight",new QsLayout.Item("t:flashlight","",8,0,0,3,2,true,true,form)));
        tile.setPadding(12,10,12,10); tile.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        View icon=new View(tile.getContext()); tile.addView(icon,new LinearLayout.LayoutParams(20,20));
        android.widget.TextView label=new android.widget.TextView(tile.getContext()); label.setText("Flashlight"); label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1); labelParams.setMarginStart(8); tile.addView(label,labelParams);
        for(boolean rtl:new boolean[]{false,true}) {
            tile.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR); tile.forceLayout();
            tile.measure(View.MeasureSpec.makeMeasureSpec(288,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(188,View.MeasureSpec.EXACTLY)); tile.layout(0,0,288,188);
            assertEquals(288,tile.nativeMeasureWidth); assertEquals(188,tile.nativeMeasureHeight); assertEquals(288,tile.nativeLayoutWidth); assertEquals(188,tile.nativeLayoutHeight);
            assertEquals(136,label.getMeasuredWidth()); assertEquals(68,label.getMeasuredHeight()); assertEquals(20,icon.getMeasuredWidth()); assertEquals(20,icon.getMeasuredHeight());
            assertEquals(144,icon.getTop()+10); assertTrue(label.getTop()>=100); assertTrue(label.getBottom()<=188);
            assertTrue(label.getLeft()>=(rtl?100:0)); assertTrue(label.getRight()<=(rtl?288:188));
            label.setText("Wi-Fi state changed with a much longer title");
        }
    }
    @Test public void aPreviousModuleVersionCannotSilentlyAcceptNewGeometryEdits() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            android.os.Bundle old=new android.os.Bundle(f.frame.metadata); old.putString("moduleVersion","0.7.3");
            f.view.setFrame(f.frame.bitmap,old); NativeGridTest.draw(f.view,400).recycle();
            f.touch(android.view.MotionEvent.ACTION_DOWN,50,50); f.touch(android.view.MotionEvent.ACTION_UP,50,50);
            f.view.formSelected(true); assertEquals(0,f.changes);
            assertTrue(f.view.capabilityMessage().contains("0.7.3")); assertTrue(f.view.capabilityMessage().contains(BuildConfig.VERSION_NAME));
            f.view.setFrame(f.frame.bitmap,f.frame.metadata); f.view.formSelected(true); assertEquals(1,f.changes);
        }
    }

}
