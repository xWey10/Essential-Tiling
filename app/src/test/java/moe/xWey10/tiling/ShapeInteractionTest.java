package moe.xWey10.tiling;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.android.launcher3.QSTileItemInfo;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShapeInteractionTest {
    @After public void clear() { de.robv.android.xposed.XposedBridge.hooks.clear(); NativeForms.draft.remove(); NativeLayerClip.rawFrames.remove(); }
    static QsLayout.Item arms() {
        QsLayout.Item base=new QsLayout.Item("t:bt","",8,0,2,2,1,true,true);
        QsLayout.Item left=TileExtrusion.grow(base,new TileExtrusion.Handle(0,0,2),1);
        return TileExtrusion.grow(left,new TileExtrusion.Handle(1,1,2),1);
    }
    @Test public void adjacentExtrusionsKeepTheirSlotUntilPlusMergesThemWithADrop() throws Exception {
        QsLayout.Item item=arms(); assertEquals("1111",item.form().cells()); assertEquals("0,1,2,1",item.form().body());
        assertEquals("0,0,h",item.form().seams()); assertTrue(item.form().valid(2,2));
        RectF bounds=new RectF(8,108,196,296); TileSeams.Joint joint=TileSeams.list(item.form().seams()).get(0);
        TileForm joined=item.form().joined(joint);
        assertFalse(item.form().contains(bounds,2,2,100,100,30,102,145)); assertTrue(joined.contains(bounds,2,2,100,100,30,102,145));
        Path early=TileSeams.drop(item.form(),joined,bounds,2,2,100,100,30,.1f),half=TileSeams.drop(item.form(),joined,bounds,2,2,100,100,30,.5f);
        assertTrue(TileLayers.contains(early,102,152)); assertFalse(TileLayers.contains(early,102,120)); assertTrue(TileLayers.contains(half,102,145));
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            load(f,item); f.touch(MotionEvent.ACTION_DOWN,150,250); f.touch(MotionEvent.ACTION_UP,150,250);
            f.touch(MotionEvent.ACTION_DOWN,102,152); assertEquals(1,f.changes); assertEquals("",f.edited.item("t:bt").form().seams());
            f.touch(MotionEvent.ACTION_UP,102,152); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350)); NativeGridTest.draw(f.view,400).recycle();
            assertEquals(1,f.changes);
        }
    }
    @Test public void theCircleRotatesContinuouslyAndCancelRestoresTheOriginalAngle() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            QsLayout.Item item=new QsLayout.Item("t:bt","",8,1,1,2,1,true,true); load(f,item);
            f.touch(MotionEvent.ACTION_DOWN,150,150); f.touch(MotionEvent.ACTION_UP,150,150);
            f.touch(MotionEvent.ACTION_DOWN,202,88); f.touch(MotionEvent.ACTION_MOVE,266,152);
            java.util.Map<String,TileVisual> visuals=ReflectionHelpers.getField(f.view,"visuals");
            assertEquals(0,f.changes); assertEquals(90,ReflectionHelpers.<TileForm>getField(visuals.get("t:bt"),"shape").rotation(),1);
            TileVisual visual=visuals.get("t:bt");
            assertTrue(TileLayers.contains(visual.outline(visual.motion.bounds,30),202,180));
            assertFalse("A rotated rectangle must turn its silhouette as well as its text",TileLayers.contains(visual.outline(visual.motion.bounds,30),130,152));
            f.touch(MotionEvent.ACTION_CANCEL,266,152); assertEquals(0,f.view.getLayoutDraft().item("t:bt").form().rotation(),.01f);
            f.touch(MotionEvent.ACTION_DOWN,202,88); f.touch(MotionEvent.ACTION_UP,266,152); assertEquals(90,f.edited.item("t:bt").form().rotation(),1);
            f.view.resetRotation(); assertEquals(0,f.edited.item("t:bt").form().rotation(),.01f);
        }
    }
    @Test public void drawingRotatesTheWholeRealTextGroupEvenWhenNativePropertiesAreReset() throws Exception {
        NativeFormHooksTest.install(); QSTileViewImpl tile=nativeTile("text",TileForm.diagonal(true,false),3,3,Color.DKGRAY);
        View icon=new View(tile.getContext()); icon.setBackgroundColor(Color.GREEN); tile.addView(icon,new LinearLayout.LayoutParams(20,20));
        TextView label=new TextView(tile.getContext()); label.setText("Flashlight"); label.setTextColor(Color.WHITE); label.setTextSize(18); tile.addView(label,new LinearLayout.LayoutParams(135,32));
        measure(tile,288,288); Bitmap actual=bitmap(tile);
        Bitmap expected=Bitmap.createBitmap(288,288,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(expected);
        QsLayout.Item item=NativeForms.item(tile); c.clipPath(NativeLayerClip.own(tile,item)); c.drawColor(Color.DKGRAY);
        float[] pitch=NativeFormContent.drawingPitch(tile,3,3); c.concat(TileRotation.content(item.form(),new RectF(0,0,288,288),3,3,pitch[0],pitch[1],false));
        for(int n=0;n<tile.getChildCount();n++) { View child=tile.getChildAt(n); c.save(); c.translate(child.getLeft(),child.getTop()); child.draw(c); c.restore(); }
        write("rotation-actual.png",actual); write("rotation-reference.png",expected);
        int differences=0; for(int y=0;y<288;y++) for(int x=0;x<288;x++) if(actual.getPixel(x,y)!=expected.getPixel(x,y)) differences++;
        assertTrue("The two drawing paths agree on >99% of pixels, including real text; only native child clipping AA may differ: "+differences,differences<500);
        assertEquals(-45,textAngle(actual),4);
        ViewTreeState saved=new ViewTreeState(tile); label.setRotation(0); icon.setRotation(0); saved.restoreProperties(); saved.restoreGeometry();
        Bitmap after=bitmap(tile); assertTrue(actual.sameAs(after)); assertEquals(0,label.getRotation(),.01f);
        write("rotated-native-text.png",actual); actual.recycle(); expected.recycle(); after.recycle();
    }
    @Test public void arbitraryRotationKeepsTextAlignedAndLeavesOrdinaryStockCornersIntact() throws Exception {
        NativeFormHooksTest.install();
        for(float angle:new float[]{37,90,130}) {
            QSTileViewImpl tile=nativeTile("angle",TileForm.diagonal(true,false).rotated(angle),3,3,Color.DKGRAY);
            TextView label=new TextView(tile.getContext());label.setText("Flashlight");label.setTextSize(18);label.setTextColor(Color.WHITE);tile.addView(label,new LinearLayout.LayoutParams(135,32));measure(tile,288,288);
            Bitmap b=bitmap(tile);assertEquals(-45+angle,textAngle(b),4);b.recycle();
        }
        QSTileViewImpl stock=nativeTileWithDraft("plain",1,1,Color.GREEN);measure(stock,88,88);Bitmap b=bitmap(stock);assertEquals(Color.GREEN,b.getPixel(0,0));b.recycle();
    }
    @Test public void nativeTouchUsesTheRotatedChildButKeepsRootClicksAtPhysicalCoordinates() throws Exception {
        NativeFormHooksTest.install();
        try(var controller=Robolectric.buildActivity(android.app.Activity.class).setup()) { QSTileViewImpl tile=nativeTile("touch",TileForm.diagonal(false,false),3,3,Color.RED);
        controller.get().setContentView(tile,new ViewGroup.LayoutParams(288,288));
        View child=new View(tile.getContext()); tile.addView(child,new LinearLayout.LayoutParams(20,20)); int[] clicks={0}; tile.setOnClickListener(v->clicks[0]++); measure(tile,288,288);
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}) { MotionEvent e=MotionEvent.obtain(0,10,action,244,244,0); assertTrue(tile.dispatchTouchEvent(e)); assertEquals(244,e.getX(),0); assertEquals(244,e.getY(),0); e.recycle(); }
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,clicks[0]);
        child.setOnClickListener(v->clicks[0]+=10);
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) { MotionEvent e=MotionEvent.obtain(20,30,action,144,144,0); assertTrue(tile.dispatchTouchEvent(e)); e.recycle(); }
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(11,clicks[0]);
        }
    }
    @Test public void layersKeepTheLowerLineOutOfTheUpperSilhouetteAndHitTheChosenTopTile() throws Exception {
        NativeFormHooksTest.install(); FrameLayout parent=new FrameLayout(RuntimeEnvironment.getApplication());
        TileForm down=TileForm.diagonal(false,false).layered(1),up=TileForm.diagonal(true,false).layered(2);
        NativeForms.draft.set(Map.of("t:a",new QsLayout.Item("t:a","",8,0,0,3,3,true,true,down),"t:b",new QsLayout.Item("t:b","",8,0,0,3,3,true,true,up)));
        QSTileViewImpl a=nativeTileWithDraft("a",3,3,Color.RED),b=nativeTileWithDraft("b",3,3,Color.BLUE); parent.addView(a);parent.addView(b); measure(a,288,288);measure(b,288,288);
        QsLayout.Item ia=NativeForms.item(a),ib=NativeForms.item(b); Path lower=NativeLayerClip.visible(a,ia);
        assertFalse(TileLayers.contains(lower,144,144)); assertTrue(TileLayers.contains(lower,45,45)); assertTrue(TileLayers.contains(NativeLayerClip.visible(b,ib),144,144));
        a.setClickable(true); b.setClickable(true); MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,144,144,0); assertFalse(a.dispatchTouchEvent(event)); assertTrue(b.dispatchTouchEvent(event)); event.recycle();
        Bitmap red=bitmap(a); assertEquals(0,red.getPixel(144,144)); assertEquals(Color.RED,red.getPixel(45,45)); red.recycle();
        Bitmap raw=NativeFrameCapture.capture(a,null,1,288,288); assertEquals(Color.RED,raw.getPixel(144,144)); raw.recycle(); // Raw snapshots must permit later layer changes.
        QsLayout model=QsLayoutTest.source(new QsLayout.Item("t:a","",8,0,0,3,3,true,true,down),new QsLayout.Item("t:b","",8,1,1,3,3,true,true,up)); model.validateAgainst(model);
        QsLayout overlapping=model.edit("t:b",0,0,3,3); assertNotNull(overlapping); overlapping.validateAgainst(model); assertEquals(0,overlapping.item("t:a").x());
    }
    @Test public void editorCanSelectTheVisibleTopLineThenReverseTheLayersWithoutReloadingImages() throws Exception {
        try(GridGestureTest.Fixture f=new GridGestureTest.Fixture()) {
            QsLayout layout=QsLayoutTest.source(new QsLayout.Item("t:a","",8,0,0,3,3,true,true,TileForm.diagonal(false,false).layered(1)),new QsLayout.Item("t:b","",8,0,0,3,3,true,true,TileForm.diagonal(true,false).layered(2)));
            Bitmap atlas=Bitmap.createBitmap(400,976,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(atlas); canvas.drawColor(Color.BLACK); Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawRect(0,400,288,688,paint);paint.setColor(Color.BLUE);canvas.drawRect(0,688,288,976,paint);
            android.os.Bundle metadata=new android.os.Bundle(f.frame.metadata); metadata.putString("layout",layout.json());metadata.putString("variants","[]");metadata.putString("regions","[{\"id\":\"t:a\",\"sx\":0,\"sy\":400,\"sw\":288,\"sh\":288,\"left\":8,\"top\":8,\"right\":296,\"bottom\":296},{\"id\":\"t:b\",\"sx\":0,\"sy\":688,\"sw\":288,\"sh\":288,\"left\":8,\"top\":8,\"right\":296,\"bottom\":296}]");
            f.view.setFrame(atlas,metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260)); Bitmap before=NativeGridTest.draw(f.view,400);assertEquals(Color.BLUE,before.getPixel(152,152));before.recycle();
            f.touch(MotionEvent.ACTION_DOWN,152,152);f.touch(MotionEvent.ACTION_UP,152,152);assertEquals("t:b",f.selected.id());
            f.view.layerSelected(false);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));Bitmap after=NativeGridTest.draw(f.view,400);assertEquals(Color.RED,after.getPixel(152,152));after.recycle();
            f.touch(MotionEvent.ACTION_DOWN,152,152);f.touch(MotionEvent.ACTION_UP,152,152);assertEquals("t:a",f.selected.id());assertEquals(1,f.changes);atlas.recycle();
        }
    }
    @Test public void oldMasksGainSeparateArmsButAnExplicitlyJoinedNewMaskStaysJoined() throws Exception {
        var c=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(c,0);
        c.createDeviceProtectedStorageContext().getSharedPreferences("nothing_qs_forms",0).edit().putString("u9","{\"t:old\":[4,2,0,0,0,\"01101111\",\"0,1,4,1\",false]}").commit();
        NativeForms.user(c,9); TileForm old=NativeForms.form("t:old",4,2); assertEquals("1,0,h",old.seams());
        TileForm joined=old.joined(new TileSeams.Joint(1,0,true)); QsLayout model=QsLayoutTest.source(new QsLayout.Item("t:old","",8,0,0,4,2,true,true,joined)); NativeForms.save(c,9,model); NativeForms.user(c,0);NativeForms.user(c,9);
        assertEquals("",NativeForms.form("t:old",4,2).seams());
    }
    @Test public void rotationsJoinsAndLayerOrderSurviveBothStoresAndKeepTheSameFingerprint() throws Exception {
        var context=RuntimeEnvironment.getApplication(); NativeForms.supported=true; NativeForms.user(context,0);
        QsLayout.Item item=arms().shaped(arms().form().rotated(37).layered(3)); QsLayout model=QsLayoutTest.source(item);
        QsLayout decoded=QsLayout.parse(model.json()); assertEquals(model.fingerprint(),decoded.fingerprint()); NativeForms.save(context,0,decoded); NativeForms.user(context,1);NativeForms.user(context,0);
        assertEquals(item.form(),NativeForms.form(item.id(),item.w(),item.h()));
    }
    static float textAngle(Bitmap b) {
        double xsum=0,ysum=0; int n=0;
        for(int y=0;y<b.getHeight();y++) for(int x=0;x<b.getWidth();x++) { int c=b.getPixel(x,y); if(Color.red(c)>200&&Color.green(c)>200&&Color.blue(c)>200) { xsum+=x;ysum+=y;n++; } }
        assertTrue(n>100); double mx=xsum/n,my=ysum/n,xx=0,yy=0,xy=0;
        for(int y=0;y<b.getHeight();y++) for(int x=0;x<b.getWidth();x++) { int c=b.getPixel(x,y); if(Color.red(c)>200&&Color.green(c)>200&&Color.blue(c)>200) { xx+=(x-mx)*(x-mx);yy+=(y-my)*(y-my);xy+=(x-mx)*(y-my); } }
        return (float)Math.toDegrees(.5*Math.atan2(2*xy,xx-yy));
    }
    static void load(GridGestureTest.Fixture f,QsLayout.Item item) throws Exception {
        NativeGrid grid=new NativeGrid(f.nativeTree.panel,f.nativeTree.workspace);grid.focus="t:bt"; QsLayout desired=grid.source.edit("t:bt",item.x(),item.y(),item.w(),item.h(),item.form());
        NativePreviewServer.Frame frame=f.nativeTree.render(grid,grid.new Session(desired.json()),()->{}); f.view.setFrame(frame.bitmap,frame.metadata); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260));NativeGridTest.draw(f.view,400).recycle();
    }
    static QSTileViewImpl nativeTile(String spec,TileForm f,int w,int h,int color) { NativeForms.draft.set(Map.of("t:"+spec,new QsLayout.Item("t:"+spec,"",8,0,0,w,h,true,true,f))); return nativeTileWithDraft(spec,w,h,color); }
    static QSTileViewImpl nativeTileWithDraft(String spec,int w,int h,int color) { QSTileViewImpl tile=new QSTileViewImpl(RuntimeEnvironment.getApplication()); QSTileItemInfo tag=new QSTileItemInfo(spec); tag.spanX=w; tag.spanY=h; tile.setTag(tag); tile.setBackgroundColor(color); return tile; }
    static void measure(View view,int w,int h) { view.forceLayout();view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h); }
    static Bitmap bitmap(View v) { Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b; }
    static void write(String name,Bitmap b) throws Exception { java.io.File dir=new java.io.File("build/test-artifacts");dir.mkdirs();try(var out=new java.io.FileOutputStream(new java.io.File(dir,name))) { b.compress(Bitmap.CompressFormat.PNG,100,out); } }
}
