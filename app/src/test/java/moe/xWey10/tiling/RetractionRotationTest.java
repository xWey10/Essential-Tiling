package moe.xWey10.tiling;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.os.Looper;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RetractionRotationTest {
    @After public void clean() { NativeForms.draft.remove();de.robv.android.xposed.XposedBridge.hooks.clear(); }
    static QsLayout.Item photo() { return new QsLayout.Item("t:bt","",8,0,0,2,3,true,true,new TileForm(0,0,0,"110101","0,0,2,1")); }
    @Test public void savedLegRetractsOneCellAtATimeAndStopsAtTheContentBody() {
        var item=photo();var tip=new TileExtrusion.Handle(1,2,8);assertEquals(2,TileExtrusion.minimum(item,tip));
        var one=TileExtrusion.grow(item,tip,-1);assertNotNull(one);assertEquals(2,one.h());assertEquals("1101",one.form().cells());assertEquals("0,0,2,1",one.form().body());
        var all=TileExtrusion.grow(item,tip,-2);assertNotNull(all);assertEquals(2,all.w());assertEquals(1,all.h());assertEquals(TileForm.RECT,all.form());assertNull(TileExtrusion.grow(item,tip,-3));
    }
    @Test public void everyTipCanRetractWithoutDeletingTheOtherArmsOrBreakingConnections() {
        QsLayout.Item base=new QsLayout.Item("t:demo","",8,1,1,2,2,true,true);
        for(TileExtrusion.Handle h:TileExtrusion.handles(base)) {
            QsLayout.Item grown=TileExtrusion.grow(base,h,2);int dx=h.edge()==1?-1:h.edge()==4?1:0,dy=h.edge()==2?-1:h.edge()==8?1:0;
            TileExtrusion.Handle tip=new TileExtrusion.Handle(base.x()+h.x()+dx*2-grown.x(),base.y()+h.y()+dy*2-grown.y(),h.edge());
            QsLayout.Item restored=TileExtrusion.grow(grown,tip,-2);assertNotNull(restored);assertEquals(base.x(),restored.x());assertEquals(base.y(),restored.y());assertEquals(base.w(),restored.w());assertEquals(base.h(),restored.h());assertEquals(TileForm.RECT,restored.form());
        }
        var fork=ShapeInteractionTest.arms();var trimmed=TileExtrusion.grow(fork,new TileExtrusion.Handle(1,0,2),-1);assertNotNull(trimmed);assertEquals("1011",trimmed.form().cells());assertEquals("",trimmed.form().seams());
    }
    @Test public void photoBottomTipFollowsTheFingerInwardAndCommitsAfterRelease() throws Exception {
        try(var f=new GridGestureTest.Fixture()) {
            ShapeInteractionTest.load(f,photo());f.touch(MotionEvent.ACTION_DOWN,150,50);f.touch(MotionEvent.ACTION_UP,150,50);
            f.touch(MotionEvent.ACTION_DOWN,152,296);f.touch(MotionEvent.ACTION_MOVE,152,256);assertEquals(0,f.changes);
            Bitmap moving=NativeGridTest.draw(f.view,400);assertRed(moving,152,220);assertNotRed(moving,152,280);assertRed(moving,50,50);moving.recycle();
            f.touch(MotionEvent.ACTION_UP,152,96);assertEquals(1,f.changes);assertEquals(1,f.edited.item("t:bt").h());assertEquals(2,f.edited.item("t:bt").w());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));Bitmap after=NativeGridTest.draw(f.view,400);assertRed(after,50,50);assertNotRed(after,152,150);after.recycle();
        }
    }
    @Test public void naturalRotationPreservesLengthAreaAndNativeGlyphSize() throws Exception {
        QsLayout.Item start=new QsLayout.Item("t:bt","",8,1,1,2,1,true,true);RectF bounds=new RectF(108,108,296,196);
        QsLayout.Item rotated=TileFrame.reframe(start,TileFrame.intrinsic(start).shaped(TileForm.RECT.rotated(90)),new QsLayout.Page(8,4,4),bounds,100,100,100,100,44,false,true);
        assertNotNull(rotated);assertEquals(1,rotated.w());assertEquals(2,rotated.h());assertEquals(2,rotated.form().baseW());assertEquals(1,rotated.form().baseH());
        RectF nativeFrame=new RectF(0,0,88,188);RectF body=rotated.form().content(nativeFrame,1,2,100,100);assertEquals(188,body.width(),.01f);assertEquals(88,body.height(),.01f);
        float[] v={100,0};TileRotation.content(rotated.form(),nativeFrame,1,2,100,100,false).mapVectors(v);assertEquals(100,Math.hypot(v[0],v[1]),.001);
        NativeFormHooksTest.install();var tile=ShapeInteractionTest.nativeTile("size",rotated.form(),1,2,Color.RED);NativeRootViewportTest.Cells parent=new NativeRootViewportTest.Cells(tile.getContext());parent.addView(tile);
        View icon=new View(tile.getContext());icon.setBackgroundColor(Color.GREEN);tile.addView(icon,new LinearLayout.LayoutParams(20,20));ShapeInteractionTest.measure(tile,88,188);assertEquals(20,icon.getWidth());assertEquals(20,icon.getHeight());assertEquals(188,tile.nativeLayoutHeight);
        Bitmap b=ShapeInteractionTest.bitmap(tile);assertEquals(Color.GREEN,b.getPixel(44,94));assertEquals(Color.RED,b.getPixel(44,20));ShapeInteractionTest.write("natural-rotation-90.png",b);b.recycle();
    }
    @Test public void aRotatedBranchCanGrowAndRetractUsingTheSameVisibleTip() throws Exception {
        for(boolean rtl:new boolean[]{false,true}) try(var f=new GridGestureTest.Fixture()) {
            QsLayout.Item root=new QsLayout.Item("t:bt","",8,1,1,2,2,true,true,new TileForm(0,0,0,"1101","0,0,2,1").rotated(90).base(2,2));
            ShapeInteractionTest.load(f,root);ReflectionHelpers.setField(f.view,"rtl",rtl);ReflectionHelpers.callInstanceMethod(f.view,"syncVisuals",ReflectionHelpers.ClassParameter.from(long.class,0L));NativeGridTest.draw(f.view,400).recycle();
            f.touch(MotionEvent.ACTION_DOWN,rtl?152:252,202);f.touch(MotionEvent.ACTION_UP,rtl?152:252,202);
            assertNotNull("Selected rotated tile, rtl="+rtl,f.selected);
            var visual=visual(f.view);RectF tip=TileExtrusion.point(root,new TileExtrusion.Handle(1,1,8),visual.motion.bounds,rtl,100,100);
            Matrix inverse=new Matrix();TileRotation.extra(root.form(),visual.motion.bounds,2,2,100,100,rtl).invert(inverse);
            float[] outward={0,100};TileRotation.extra(root.form(),visual.motion.bounds,2,2,100,100,rtl).mapVectors(outward);
            f.touch(MotionEvent.ACTION_DOWN,tip.left,tip.top);assertEquals("Tip should begin extrusion, rtl="+rtl,32,(int)ReflectionHelpers.<Integer>getField(f.view,"edges"));
            f.touch(MotionEvent.ACTION_MOVE,tip.left+outward[0],tip.top+outward[1]);
            QsLayout.Item proposed=ReflectionHelpers.getField(f.view,"proposed");
            assertFalse("Valid reframe "+proposed+", count="+ReflectionHelpers.getField(f.view,"branchCount")+", rtl="+rtl,ReflectionHelpers.<Boolean>getField(f.view,"invalid"));
            f.touch(MotionEvent.ACTION_UP,tip.left+outward[0],tip.top+outward[1]);assertNotNull("Committed growth "+proposed+", rtl="+rtl,f.edited);var changed=f.edited.item("t:bt");assertEquals(90,changed.form().rotation(),.01);assertEquals(3,changed.form().baseH());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));NativeGridTest.draw(f.view,400).recycle();
            RectF nextTip=TileExtrusion.point(changed,new TileExtrusion.Handle(1,2,8),visual(f.view).motion.bounds,rtl,100,100);
            f.touch(MotionEvent.ACTION_DOWN,nextTip.left,nextTip.top);f.touch(MotionEvent.ACTION_UP,nextTip.left-outward[0]*2,nextTip.top-outward[1]*2);assertEquals(1,f.edited.item("t:bt").form().baseH());assertEquals(90,f.edited.item("t:bt").form().rotation(),.01);
        }
    }
    @Test public void aRotatedRectangleResizesItsIntrinsicLongSideWithoutResettingTheAngle() throws Exception {
        try(var f=new GridGestureTest.Fixture()) {
            QsLayout.Item item=new QsLayout.Item("t:bt","",8,1,1,1,2,true,true,TileForm.RECT.rotated(90).base(2,1));ShapeInteractionTest.load(f,item);
            f.touch(MotionEvent.ACTION_DOWN,152,202);f.touch(MotionEvent.ACTION_UP,152,202);
            f.touch(MotionEvent.ACTION_DOWN,152,296);f.touch(MotionEvent.ACTION_MOVE,152,346);assertEquals(0,f.changes);
            f.touch(MotionEvent.ACTION_UP,152,396);assertNotNull(f.edited);var changed=f.edited.item("t:bt");assertEquals(90,changed.form().rotation(),.01);assertEquals(3,changed.form().baseW());assertEquals(1,changed.form().baseH());assertEquals(1,changed.w());assertEquals(3,changed.h());
            f.view.resizeSelected(1,3);assertEquals(1,f.edited.item("t:bt").form().baseW());assertEquals(3,f.edited.item("t:bt").form().baseH());assertEquals(90,f.edited.item("t:bt").form().rotation(),.01);
        }
    }
    @Test public void aRotatedDiagonalKeepsItsExtraAngleWhileAnEndIsExtended() throws Exception {
        try(var f=new GridGestureTest.Fixture()) {
            QsLayout.Item item=new QsLayout.Item("t:bt","",8,1,1,2,2,true,true,TileForm.diagonal(false,false).rotated(90).base(2,2));ShapeInteractionTest.load(f,item);
            f.touch(MotionEvent.ACTION_DOWN,202,202);f.touch(MotionEvent.ACTION_UP,202,202);
            f.touch(MotionEvent.ACTION_DOWN,152,252);f.touch(MotionEvent.ACTION_UP,52,352);assertNotNull(f.edited);var changed=f.edited.item("t:bt");assertEquals(90,changed.form().rotation(),.01);assertEquals(3,changed.form().baseW());assertEquals(3,changed.form().baseH());assertTrue(changed.form().diagonal());
        }
    }
    @Test public void aCancelledInwardDragKeepsTheOriginalMaskAndNativeTag() throws Exception {
        try(var f=new GridGestureTest.Fixture()) {
            ShapeInteractionTest.load(f,photo());f.touch(MotionEvent.ACTION_DOWN,152,50);f.touch(MotionEvent.ACTION_UP,152,50);Object tag=f.nativeTree.tile.getTag();
            f.touch(MotionEvent.ACTION_DOWN,152,296);f.touch(MotionEvent.ACTION_MOVE,152,196);f.touch(MotionEvent.ACTION_CANCEL,152,196);assertEquals(0,f.changes);assertEquals(photo().form(),f.view.getLayoutDraft().item("t:bt").form());assertSame(tag,f.nativeTree.tile.getTag());
        }
    }
    @Test public void oldShrunkenRotationIsReframedOnceWithoutLosingItsIntrinsicSize() throws Exception {
        try(var f=new GridGestureTest.Fixture()) {
            ShapeInteractionTest.load(f,new QsLayout.Item("t:bt","",8,1,1,2,1,true,true,TileForm.RECT.rotated(90)));f.view.repairRotationFrames();assertEquals(1,f.changes);var repaired=f.edited.item("t:bt");assertEquals(2,repaired.form().baseW());assertEquals(1,repaired.form().baseH());assertEquals(1,repaired.w());assertEquals(2,repaired.h());f.view.repairRotationFrames();assertEquals(1,f.changes);
        }
    }
    @Test public void intrinsicDimensionsAndMaskSurviveTheActualPreferenceAndJsonRoundTrip() throws Exception {
        var item=new QsLayout.Item("t:intrinsic","",8,0,0,3,2,true,true,new TileForm(0,0,0,"1101","0,0,2,1").rotated(37).base(2,2));var model=QsLayoutTest.source(item);var parsed=QsLayout.parse(model.json());assertEquals(item.form(),parsed.items.get(0).form());parsed.validateAgainst(model);
        NativeForms.supported=true;var c=RuntimeEnvironment.getApplication();NativeForms.user(c,0);NativeForms.save(c,0,parsed);NativeForms.user(c,1);NativeForms.user(c,0);assertEquals(item.form(),NativeForms.form(item.id(),3,2));
    }
    static TileVisual visual(PreviewView view) { Map<String,TileVisual> m=ReflectionHelpers.getField(view,"visuals");return m.get("t:bt"); }
    static void assertRed(Bitmap b,int x,int y) { int c=b.getPixel(x,y);assertTrue("Red at "+x+","+y,Color.red(c)>Color.blue(c)+60&&Color.red(c)>Color.green(c)+60); }
    static void assertNotRed(Bitmap b,int x,int y) { int c=b.getPixel(x,y);assertFalse("No red at "+x+","+y,Color.red(c)>Color.blue(c)+60&&Color.red(c)>Color.green(c)+60); }
}
