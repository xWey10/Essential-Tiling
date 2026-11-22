package moe.xWey10.tiling;

import android.graphics.*;
import android.view.*;
import android.widget.LinearLayout;
import android.os.Looper;
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
public class DiagonalShapeTest {
    @org.junit.After public void clean() { de.robv.android.xposed.XposedBridge.hooks.clear(); NativeForms.draft.remove(); }
    @Test public void diagonalIsASmoothCapsuleAndOnlyReservesCellsTouchedByTheLine() {
        for(boolean rising:new boolean[]{false,true}) {
            TileForm form=TileForm.diagonal(rising,false); RectF bounds=new RectF(0,0,288,288);
            assertTrue(form.valid(3,3)); assertTrue(form.contains(bounds,3,3,100,100,44,144,144));
            assertFalse(form.contains(bounds,3,3,100,100,44,250,rising?250:30));
            // A point away from the smooth line remains empty inside a reserved edge cell.
            assertTrue(form.occupied(1,rising?2:0,3,3));
            assertFalse(form.contains(bounds,3,3,100,100,44,175,rising?278:10));
            int count=0; for(int y=0;y<3;y++) for(int x=0;x<3;x++) if(form.occupied(x,y,3,3)) count++;
            assertEquals(7,count); assertFalse(form.occupied(0,rising?0:2,3,3));
            // computeBounds includes rotated curve control points; inspect the actual filled pixels.
            Region pixels=new Region(); pixels.setPath(form.path(bounds,3,3,100,100,44),new Region(-500,-500,500,500));
            Rect actual=pixels.getBounds(); assertEquals(0,actual.left,1); assertEquals(288,actual.right,1);
            assertEquals(0,actual.top,1); assertEquals(288,actual.bottom,1);
        }
    }
    @Test public void diagonalAndNubsSurviveBothJsonAndTheNativePerUserStore() throws Exception {
        var c=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(c,0);
        for(TileForm form:new TileForm[]{TileForm.diagonal(true,true),TileForm.diagonal(false,false),ShapeOccupancyTest.fourCells().nubbed(true),TileForm.RECT.nubbed(true)}) {
            QsLayout source=QsLayoutTest.source(new QsLayout.Item("t:shape","",8,0,0,3,2,true,true,form));
            QsLayout decoded=QsLayout.parse(source.json()); assertEquals(form,decoded.items.get(0).form());
            NativeForms.save(c,0,decoded); NativeForms.user(c,1); NativeForms.user(c,0);
            assertEquals(form,NativeForms.form("t:shape",3,2));
        }
    }
    @Test public void roundNubsAreVisibleAndDoNotOccupyTheEmptyCellsOfAnL() {
        TileForm form=ShapeOccupancyTest.fourCells().nubbed(true); RectF b=new RectF(0,0,288,188);
        Path plain=ShapeOccupancyTest.fourCells().path(b,3,2,100,100,30),nubbed=form.path(b,3,2,100,100,30);
        Path difference=new Path(plain); assertTrue(difference.op(nubbed,Path.Op.XOR)); assertFalse(difference.isEmpty());
        RectF actual=new RectF(); nubbed.computeBounds(actual,true); assertTrue(b.contains(actual));
        assertFalse(form.occupied(0,0,3,2)); assertFalse(form.occupied(1,0,3,2));
        assertFalse(form.contains(b,3,2,100,100,30,50,40)); assertTrue(form.contains(b,3,2,100,100,30,44,101));
        write("nubbed-L.png",nubbed,288,188);
        write("diagonal.png",TileForm.diagonal(true,false).path(new RectF(0,0,288,288),3,3,100,100,44),288,288);
    }
    @Test public void realNativeChildrenRotateWithoutScalingAndRestoreOnTheNextLayout() throws Exception {
        NativeFormHooksTest.install();
        var tile=new com.android.systemui.qs.tileimpl.QSTileViewImpl(RuntimeEnvironment.getApplication());
        var tag=new com.android.launcher3.QSTileItemInfo("shape"); tag.spanX=tag.spanY=3; tile.setTag(tag); tile.setBackgroundColor(Color.RED);
        View icon=new View(tile.getContext()); icon.setBackgroundColor(Color.GREEN); tile.addView(icon,new LinearLayout.LayoutParams(20,20));
        for(int repeat=0;repeat<3;repeat++) {
            NativeForms.draft.set(java.util.Map.of("t:shape",new QsLayout.Item("t:shape","",8,0,0,3,3,true,true,TileForm.diagonal(true,false))));
            measure(tile,288,288); assertEquals("Native child properties stay intact; the draw callback rotates the group",0,icon.getRotation(),.01f); assertEquals(20,icon.getWidth()); assertEquals(20,icon.getHeight());
            assertEquals(288,tile.nativeMeasureWidth); assertEquals(288,tile.nativeLayoutHeight);
            Bitmap b=Bitmap.createBitmap(288,288,Bitmap.Config.ARGB_8888); tile.draw(new Canvas(b));
            assertEquals(Color.GREEN,b.getPixel(144,144)); assertEquals(0,b.getPixel(20,20)); b.recycle();
        }
        NativeForms.draft.set(java.util.Map.of("t:shape",new QsLayout.Item("t:shape","",8,0,0,3,3,true,true)));
        measure(tile,288,288); assertEquals(0,icon.getRotation(),.01f);
    }
    @Test public void arrowButtonsCreateBothDiagonalsAndTheEndHandleFollowsTheFinger() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            f.touch(MotionEvent.ACTION_DOWN,50,50); f.touch(MotionEvent.ACTION_UP,50,50);
            f.view.diagonalSelected(false); assertEquals(5,f.edited.item("t:bt").form().corner());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); NativeGridTest.draw(f.view,400).recycle();
            f.touch(MotionEvent.ACTION_DOWN,152,152); f.touch(MotionEvent.ACTION_MOVE,192,152);
            java.util.Map<String,TileVisual> visuals=org.robolectric.util.ReflectionHelpers.getField(f.view,"visuals");
            RectF live=visuals.get("t:bt").motion.bounds; assertEquals(8,live.left,.1f);assertEquals(8,live.top,.1f);assertEquals(236,live.right,.1f);assertEquals(196,live.bottom,.1f);
            f.touch(MotionEvent.ACTION_UP,252,252); assertEquals(3,f.edited.item("t:bt").w()); assertEquals(3,f.edited.item("t:bt").h());
            f.view.diagonalSelected(true); assertEquals(6,f.edited.item("t:bt").form().corner());
            f.view.nubsSelected(); assertTrue(f.edited.item("t:bt").form().nubs());
            f.view.nubsSelected(); assertFalse(f.edited.item("t:bt").form().nubs());
        }
    }
    private static void measure(View tile,int w,int h) { tile.forceLayout(); tile.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); tile.layout(0,0,w,h); }
    private static void write(String name,Path path,int w,int h) {
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.DKGRAY); new Canvas(b).drawPath(path,p);
        java.io.File directory=new java.io.File("build/test-artifacts"); directory.mkdirs();
        try(var out=new java.io.FileOutputStream(new java.io.File(directory,name))) { b.compress(Bitmap.CompressFormat.PNG,100,out); } catch(java.io.IOException e) { throw new AssertionError(e); } b.recycle();
    }
}
