package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShapeRenderingTest {
    private static final TileForm UP_RIGHT=new TileForm(0,0,0,"0111","0,1,2,1");
    @org.junit.After public void clean() { de.robv.android.xposed.XposedBridge.hooks.clear(); NativeForms.draft.remove(); }
    @Test public void nativeInsetsGiveEqualThicknessAndTheSameRoundEndsAsTheReference() {
        RectF bounds=new RectF(6,6,194,194);
        assertEquals(new RectF(6,106,194,194),UP_RIGHT.content(bounds,2,2,100,100));
        Bitmap image=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.DKGRAY);
        new Canvas(image).drawPath(UP_RIGHT.path(bounds,2,2,100,100,44),paint);
        assertEquals(0,image.getPixel(100,50)); // Branch starts after the native inset, not at the grid line.
        assertEquals(Color.DKGRAY,image.getPixel(150,50)); assertEquals(Color.DKGRAY,image.getPixel(50,150));
        assertEquals(0,image.getPixel(105,105)); assertEquals(Color.DKGRAY,image.getPixel(106,105)); assertEquals(Color.DKGRAY,image.getPixel(105,106));
        int horizontal=0,vertical=0;
        for(int n=0;n<200;n++) { if((image.getPixel(150,n)>>>24)>200&&n>=106) horizontal++; if((image.getPixel(n,50)>>>24)>200) vertical++; }
        assertEquals(88,horizontal); assertEquals(88,vertical);
        java.io.File directory=new java.io.File("build/test-artifacts"); directory.mkdirs();
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(directory,"L-contour.png"))) { image.compress(Bitmap.CompressFormat.PNG,100,output); }
        catch(java.io.IOException e) { throw new AssertionError(e); } image.recycle();
    }
    @Test public void everyFilledTwoByTwoHasNoHairlineHoleBetweenNativeCells() {
        Bitmap image=Bitmap.createBitmap(188,188,Bitmap.Config.ARGB_8888); Paint paint=new Paint(); paint.setColor(Color.RED);
        new Canvas(image).drawPath(TileOutline.path(TileForm.RECT.rectangles(new RectF(0,0,188,188),2,2,100,100),44),paint);
        for(int y=85;y<=103;y++) for(int x=85;x<=103;x++) assertEquals(Color.RED,image.getPixel(x,y)); image.recycle();
    }
    @Test public void theNativeCaptureIgnoresCollapsedHeightAndRestoresItAfterwards() throws Exception {
        NativeFormHooksTest.install(); NativeGridTest.Fixture fixture=new NativeGridTest.Fixture(); fixture.page.container.removeView(fixture.tile);
        QSTileViewImpl tile=new QSTileViewImpl(fixture.context); tile.setTag(fixture.info); fixture.info.spanX=2; fixture.info.cellY=2;
        NativeGridTest.Lp lp=new NativeGridTest.Lp(); lp.cellHSpan=2; lp.cellY=2; fixture.page.container.addView(tile,lp);
        GradientDrawable background=new GradientDrawable(); background.setColor(Color.DKGRAY); background.setCornerRadius(44); tile.setBackground(background);
        View glyph=new View(tile.getContext()); glyph.setBackgroundColor(Color.GREEN); tile.addView(glyph,new LinearLayout.LayoutParams(20,20));
        tile.heightOverride=88; tile.squishinessFraction=.5f;
        fixture.panel.forceLayout(); fixture.panel.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY)); fixture.panel.layout(0,0,400,500);
        assertEquals(44,tile.getHeight()); assertEquals(22,tile.getScrollY());
        NativeGrid grid=new NativeGrid(fixture.panel,fixture.workspace); grid.focus="t:bt";
        QsLayout.Item original=grid.source.item("t:bt"),grown=TileExtrusion.grow(original,new TileExtrusion.Handle(1,0,2),1);
        QsLayout draft=grid.source.edit(grown.id(),grown.x(),grown.y(),grown.w(),grown.h(),grown.form());
        NativePreviewServer.Frame frame=fixture.render(grid,grid.new Session(draft.json()),()->{});
        org.json.JSONObject region=new org.json.JSONArray(frame.metadata.getString("regions")).getJSONObject(0);
        assertEquals(188,region.getInt("sh")); assertEquals(188,region.getInt("sw")); int x=region.getInt("sx"),y=region.getInt("sy");
        assertEquals(0,frame.bitmap.getPixel(x+50,y+50)); assertEquals(Color.DKGRAY,frame.bitmap.getPixel(x+144,y+44)); assertEquals(Color.GREEN,frame.bitmap.getPixel(x+94,y+144));
        PreviewView preview=new PreviewView(fixture.context); preview.setFrame(frame.bitmap,frame.metadata);
        Bitmap shown=NativeGridTest.draw(preview,400);
        assertEquals(Color.GREEN,shown.getPixel(102,252)); assertEquals(0,Color.alpha(frame.bitmap.getPixel(x+50,y+50)));
        TileImage image=org.robolectric.util.ReflectionHelpers.<java.util.Map<String,TileImage>>getField(preview,"images").get("t:bt/2x2");
        assertEquals("Warm variants must not replace the actual shaped sprite",grown.form(),image.form); assertEquals(188,image.height,0);
        assertEquals(88,tile.heightOverride); assertEquals(.5f,tile.squishinessFraction,0); assertEquals(44,tile.getHeight()); assertEquals(22,tile.getScrollY());
        shown.recycle(); frame.bitmap.recycle(); fixture.background.recycle();
    }
    @Test public void expandedPoseCanBeRestoredWithoutChangingNativeScrollOrAnimatorValues() throws Exception {
        NativeFormHooksTest.install(); QSTileViewImpl tile=new QSTileViewImpl(RuntimeEnvironment.getApplication()); tile.heightOverride=60; tile.squishinessFraction=.3f; tile.scrollTo(1,12);
        NativePreviewPose pose=new NativePreviewPose(tile); pose.expanded(); assertEquals(-1,tile.heightOverride); assertEquals(1,tile.squishinessFraction,0); pose.restore();
        assertEquals(60,tile.heightOverride); assertEquals(.3f,tile.squishinessFraction,0); assertEquals(12,tile.getScrollY()); assertEquals(1,tile.getScrollX());
    }
    @Test public void lPresetKeepsTheOldRowAndGrowsTheRightArmUpwards() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            f.touch(android.view.MotionEvent.ACTION_DOWN,50,50); f.touch(android.view.MotionEvent.ACTION_UP,50,50);
            f.view.resizeSelected(2,1); f.view.moveSelected(0,2); f.view.formSelected(true);
            QsLayout.Item l=f.view.getLayoutDraft().item("t:bt"); assertEquals(UP_RIGHT,l.form()); assertEquals(1,l.y());
            assertEquals(new RectF(0,2,2,3),l.form().content(new RectF(0,l.y(),2,l.y()+l.h()),2,2));
        }
    }
    @Test public void snappingAndWarmNativeImagesKeepTheGlyphInItsOriginalBottomRow() {
        TileImage image=TileAnimationTest.image(188,88,24,Color.GREEN),empty=TileAnimationTest.image(88,88,24,Color.GREEN);
        RectF original=new RectF(8,208,196,296),endpoint=new RectF(8,108,196,296);
        QsLayout.Item anchor=new QsLayout.Item("t:demo","",8,0,2,2,1,true,true); TileExtrusion.Handle handle=new TileExtrusion.Handle(1,0,2);
        TileVisual visual=new TileVisual(original,image); visual.form(2,2,0,0,0,true,empty); visual.shape(UP_RIGHT,false); visual.grid(100,100,endpoint);
        visual.branch(TileExtrusion.rectangles(anchor,handle,.6f,original,false,100,100),original); visual.motion.move(new RectF(8,148,196,296),0,0);
        visual.settle(anchor,handle,.6f,1,original,original,original,100,100,false,0,220); visual.shape(UP_RIGHT,false); visual.motion.move(endpoint,0,220);
        Bitmap source=Bitmap.createBitmap(188,188,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(source); Paint p=new Paint(); p.setColor(Color.DKGRAY); c.drawPath(UP_RIGHT.path(new RectF(0,0,188,188),2,2,100,100,30),p); p.setColor(Color.GREEN); c.drawRect(24,120,32,128,p);
        TileImage warm=new TileImage(source,new android.graphics.Rect(0,0,188,188),null,2,2); warm.form=UP_RIGHT;
        for(int time:new int[]{0,60,90,140,219,240,300}) {
            if(time==90) visual.image(warm,time,true); visual.advance(time);
            Bitmap frame=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888); visual.draw(new Canvas(frame),p);
            int left=400,top=400,right=-1,bottom=-1;
            for(int y=0;y<400;y++) for(int x=0;x<400;x++) { int color=frame.getPixel(x,y); if(Color.green(color)>Color.red(color)+60&&Color.green(color)>Color.blue(color)+60) { left=Math.min(left,x); top=Math.min(top,y); right=Math.max(right,x); bottom=Math.max(bottom,y); } }
            assertEquals("Glyph must not move or stretch at "+time+" ms",new android.graphics.Rect(32,228,40,236),new android.graphics.Rect(left,top,right+1,bottom+1)); frame.recycle();
        }
        image.atlas.recycle(); empty.atlas.recycle(); source.recycle();
    }

    @Test public void anAnimatingShortViewportKeepsAVisibleShapeInsteadOfNegativeCellSizes() throws Exception {
        NativeFormHooksTest.install(); NativeGridTest.Fixture fixture=new NativeGridTest.Fixture(); fixture.page.container.removeView(fixture.tile);
        QSTileViewImpl tile=new QSTileViewImpl(fixture.context); fixture.info.spanX=fixture.info.spanY=2; tile.setTag(fixture.info); tile.setBackgroundColor(Color.RED);
        NativeGridTest.Lp lp=new NativeGridTest.Lp(); lp.cellHSpan=lp.cellVSpan=2; fixture.page.container.addView(tile,lp);
        NativeForms.draft.set(java.util.Map.of("t:bt",new QsLayout.Item("t:bt","",8,0,0,2,2,true,true,UP_RIGHT)));
        tile.heightOverride=88; tile.squishinessFraction=.5f;
        tile.measure(View.MeasureSpec.makeMeasureSpec(188,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(188,View.MeasureSpec.EXACTLY)); tile.layout(0,0,188,188);
        assertEquals(44,tile.getHeight()); float[] pitch=NativeFormContent.drawingPitch(tile,2,2);
        assertTrue(pitch[1]>12); Bitmap image=Bitmap.createBitmap(188,44,Bitmap.Config.ARGB_8888); tile.draw(new Canvas(image));
        int visible=0; for(int y=0;y<44;y++) for(int x=0;x<188;x++) if((image.getPixel(x,y)>>>24)>0) visible++;
        assertTrue(visible>500); image.recycle(); fixture.background.recycle();
    }

}
