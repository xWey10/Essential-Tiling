package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CellExtrusionTest {
    @Test public void photoGestureGrowsOnlyTheLeftTopCellOfATwoByOne() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            load(f,0,2,2,1,false);
            f.touch(MotionEvent.ACTION_DOWN,150,250); f.touch(MotionEvent.ACTION_UP,150,250);
            f.touch(MotionEvent.ACTION_DOWN,55,208); f.touch(MotionEvent.ACTION_MOVE,55,168);
            Bitmap moving=NativeGridTest.draw(f.view,400);
            assertRed(moving,50,185); assertNotRed(moving,150,185); assertRed(moving,150,250); moving.recycle();
            assertEquals(0,f.changes);
            f.touch(MotionEvent.ACTION_MOVE,55,108); f.touch(MotionEvent.ACTION_UP,55,108);
            QsLayout.Item grown=f.edited.item("t:bt"); assertEquals(0,grown.x()); assertEquals(1,grown.y()); assertEquals(2,grown.w()); assertEquals(2,grown.h());
            assertEquals("1011",grown.form().cells()); assertEquals("0,1,2,1",grown.form().body());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));
            Bitmap snapped=NativeGridTest.draw(f.view,400); assertRed(snapped,50,150); assertNotRed(snapped,150,150); assertRed(snapped,150,250); snapped.recycle();
        }
    }
    @Test public void rightTopHandleAndRtlEachGrowTheGrabbedPhysicalSide() throws Exception {
        for(boolean rtl:new boolean[]{false,true}) try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            load(f,0,2,2,1,rtl);
            float left=rtl?208:8;
            f.touch(MotionEvent.ACTION_DOWN,left+140,250); f.touch(MotionEvent.ACTION_UP,left+140,250);
            f.touch(MotionEvent.ACTION_DOWN,left+141,208); f.touch(MotionEvent.ACTION_UP,left+141,108);
            QsLayout.Item grown=f.edited.item("t:bt");
            assertEquals(rtl?"1011":"0111",grown.form().cells()); assertEquals(1,grown.y());
        }
    }
    @Test public void cancellingNeverSavesABranch() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            load(f,0,2,2,1,false);
            f.touch(MotionEvent.ACTION_DOWN,150,250); f.touch(MotionEvent.ACTION_UP,150,250);
            f.touch(MotionEvent.ACTION_DOWN,55,208); f.touch(MotionEvent.ACTION_MOVE,55,100); f.touch(MotionEvent.ACTION_CANCEL,55,100);
            assertEquals(0,f.changes); assertFalse(f.view.getLayoutDraft().item("t:bt").form().cut());
        }
    }
    @Test public void everyDirectionKeepsOldCellsAndTheContentAtTheirOriginalCoordinates() {
        QsLayout.Item start=new QsLayout.Item("t:demo","",8,1,1,2,2,true,true);
        for(TileExtrusion.Handle handle:TileExtrusion.handles(start)) {
            QsLayout.Item next=TileExtrusion.grow(start,handle,1); assertTrue(next.form().valid(next.w(),next.h()));
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) assertTrue(next.form().occupied(start.x()+x-next.x(),start.y()+y-next.y(),next.w(),next.h()));
            RectF body=next.form().content(new RectF(next.x(),next.y(),next.x()+next.w(),next.y()+next.h()),next.w(),next.h());
            assertEquals(new RectF(1,1,3,3),body);
        }
    }
    @Test public void branchesCanGrowAgainIntoATAndNotJustACornerCut() {
        QsLayout.Item i=new QsLayout.Item("t:demo","",8,0,2,3,1,true,true);
        QsLayout.Item t=TileExtrusion.grow(i,new TileExtrusion.Handle(1,0,2),2);
        assertEquals("010010111",t.form().cells()); assertTrue(t.form().valid(3,3));
        QsLayout.Item fork=TileExtrusion.grow(t,new TileExtrusion.Handle(1,0,4),1);
        assertEquals("011010111",fork.form().cells()); assertTrue(fork.form().valid(3,3));
        QsLayout original=QsLayoutTest.source(i); QsLayout draft=original.edit(i.id(),fork.x(),fork.y(),fork.w(),fork.h(),fork.form());
        assertNotNull(draft); draft.validateAgainst(original); QsLayout decoded=QsLayout.parse(draft.json());
        assertFalse(decoded.differsFrom(draft)); assertNotEquals(original.fingerprint(),draft.fingerprint());
    }
    @Test public void clippingUsesTheActualUnionIncludingConcaveCornersAndHoles() {
        TileForm f=new TileForm(0,0,0,"111101111","0,2,3,1"); assertTrue(f.valid(3,3));
        Bitmap bitmap=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888); Paint paint=new Paint(); paint.setColor(Color.RED);
        new Canvas(bitmap).drawPath(f.path(new RectF(0,0,300,300),3,3,15),paint);
        assertEquals(0,bitmap.getPixel(150,150)); assertEquals(Color.RED,bitmap.getPixel(50,150)); assertEquals(Color.RED,bitmap.getPixel(150,50));
        assertFalse(f.contains(new RectF(0,0,300,300),3,3,15,150,150)); bitmap.recycle();
        assertFalse(new TileForm(0,0,0,"1001","0,0,1,1").valid(2,2));
    }
    @Test public void nativeBodyMeasurementPreservesIconSizeAndPlacesItInTheOriginalArm() throws Exception {
        TileForm form=new TileForm(0,0,0,"111010","1,0,1,1");
        QsLayout.Item item=new QsLayout.Item("t:demo","",8,0,0,3,2,true,true,form);
        FormBodyTestView view=new FormBodyTestView(RuntimeEnvironment.getApplication(),item); view.setGravity(Gravity.CENTER); view.setPadding(8,6,8,6);
        View icon=new View(view.getContext()); view.addView(icon,new LinearLayout.LayoutParams(20,20));
        view.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));
        view.layout(0,0,300,200);
        assertEquals(150,icon.getLeft()+10); assertEquals(50,icon.getTop()+10); assertEquals(20,icon.getWidth()); assertEquals(20,icon.getHeight());
        assertEquals(8,view.getPaddingLeft()); assertEquals(6,view.getPaddingTop());
    }
    @Test public void cellFormsSurviveReloadAndLegacyCornerFormsRemainReadable() {
        android.content.Context c=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(c,0);
        QsLayout.Item i=TileExtrusion.grow(new QsLayout.Item("t:demo","",8,0,2,2,1,true,true),new TileExtrusion.Handle(0,0,2),1);
        NativeForms.save(c,0,QsLayoutTest.source(i)); NativeForms.user(c,1); NativeForms.user(c,0);
        assertEquals(i.form(),NativeForms.form(i.id(),2,2));
        c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).edit().putString("u3","{\"t:old\":[2,2,2,1,1]}").commit();
        NativeForms.user(c,3); assertEquals(new TileForm(2,1,1),NativeForms.form("t:old",2,2));
    }
    @Test public void pageBoundariesClampGrowthAndProtectedWidgetsRejectIt() {
        QsLayout.Item i=new QsLayout.Item("t:demo","",8,0,1,2,1,true,true);
        assertEquals(1,TileExtrusion.maximum(i,new QsLayout.Page(8,4,4),new TileExtrusion.Handle(0,0,2)));
        QsLayout.Item widget=new QsLayout.Item("w:1","",8,0,0,1,1,false,false);
        QsLayout source=QsLayoutTest.source(i,widget); QsLayout.Item branch=TileExtrusion.grow(i,new TileExtrusion.Handle(0,0,2),1);
        assertNull(source.edit(i.id(),branch.x(),branch.y(),branch.w(),branch.h(),branch.form()));
    }
    @Test public void growingBackgroundDoesNotStretchOrMoveTheNativeGlyph() {
        Bitmap atlas=Bitmap.createBitmap(300,100,Bitmap.Config.ARGB_8888); Canvas source=new Canvas(atlas); Paint p=new Paint();
        p.setColor(Color.RED); source.drawRect(0,0,188,88,p); source.drawRect(200,0,288,88,p);
        p.setColor(Color.GREEN); source.drawRect(24,30,44,50,p);
        TileImage image=new TileImage(atlas,new android.graphics.Rect(0,0,188,88),null,2,1);
        TileImage empty=new TileImage(atlas,new android.graphics.Rect(200,0,288,88),new android.graphics.Rect(200,0,288,88),1,1);
        RectF original=new RectF(8,208,196,296); TileVisual visual=new TileVisual(original,image);
        QsLayout.Item item=new QsLayout.Item("t:demo","",8,0,2,2,1,true,true);
        TileExtrusion.Handle handle=new TileExtrusion.Handle(0,0,2);
        visual.form(2,2,0,0,0,true,empty); visual.shape(TileExtrusion.grow(item,handle,1).form(),false);
        visual.branch(TileExtrusion.rectangles(item,handle,.7f,original,false,100,100),original);
        visual.motion.move(new RectF(8,138,196,296),0,0);
        Bitmap live=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888); visual.draw(new Canvas(live),p);
        assertEquals(Color.GREEN,live.getPixel(32,238)); assertEquals(Color.GREEN,live.getPixel(51,257));
        assertEquals(Color.RED,live.getPixel(52,248)); assertEquals(Color.RED,live.getPixel(50,175)); assertEquals(0,live.getPixel(150,175));
        live.recycle(); atlas.recycle();
    }
    @Test public void aBranchIsVisibleBeforeTheNativeEmptyLayerArrives() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);
            NativePreviewServer.Frame frame=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",0,2,2,1).json()),()->{});
            assertEquals("[]",frame.metadata.getString("variants","[]"));
            f.view.setFrame(frame.bitmap,frame.metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); NativeGridTest.draw(f.view,400).recycle();
            f.touch(MotionEvent.ACTION_DOWN,150,250); f.touch(MotionEvent.ACTION_UP,150,250);
            f.touch(MotionEvent.ACTION_DOWN,55,208); f.touch(MotionEvent.ACTION_MOVE,55,168);
            Bitmap moving=NativeGridTest.draw(f.view,400); assertRed(moving,50,185); assertNotRed(moving,150,185); moving.recycle();
        }
    }
    private static void load(GridGestureTest.Fixture f,int x,int y,int w,int h,boolean rtl) throws Exception {
        NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace); grid.focus="t:bt";
        NativePreviewServer.Frame frame=f.nativeTree.render(grid,grid.new Session(grid.source.edit("t:bt",x,y,w,h).json()),()->{});
        android.os.Bundle metadata=new android.os.Bundle(frame.metadata); metadata.putBoolean("rtl",rtl);
        // The real OEM frame mirrors native bounds too. Our test fixture has a LTR-only CellLayout.
        if(rtl) { org.json.JSONArray regions=new org.json.JSONArray(metadata.getString("regions")); org.json.JSONObject r=regions.getJSONObject(0);
            double l=r.getDouble("left"),rr=r.getDouble("right"); r.put("left",400-rr).put("right",400-l); metadata.putString("regions",regions.toString()); }
        f.view.setFrame(frame.bitmap,metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); NativeGridTest.draw(f.view,400).recycle();
    }
    private static void assertRed(Bitmap b,int x,int y) { int c=b.getPixel(x,y); assertTrue("Expected native red at "+x+","+y,Color.red(c)>Color.green(c)+60&&Color.red(c)>Color.blue(c)+60); }
    private static void assertNotRed(Bitmap b,int x,int y) { int c=b.getPixel(x,y); assertFalse("Unexpected native red at "+x+","+y,Color.red(c)>Color.green(c)+60&&Color.red(c)>Color.blue(c)+60); }
}
