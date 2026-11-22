package moe.xWey10.tiling;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import com.android.launcher3.Launcher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileRemovalFlowTest {
    public static class Bridge extends ContentProvider {
        NativeGridTest.Fixture fixture; boolean neverComplete;
        final Handler main=new Handler(Looper.getMainLooper());
        volatile Bundle frame,status; long sequence;
        @Override public boolean onCreate() { return true; }
        @Override public Bundle call(String method,String arg,Bundle request) {
            if(method.equals("tiles.request")) {
                long id=++sequence; status=new Bundle(); status.putString("state","pending");
                if(!neverComplete) main.post(()-> {
                    try {
                        NativeTileActions.prepare(new NativeGrid(fixture.panel,fixture.workspace),request).run();
                        Bundle result=new Bundle(); result.putString("state","done"); result.putInt("count",0); status=result;
                    } catch(Exception e) { Bundle result=new Bundle(); result.putString("state","error"); result.putString("error",e.getMessage()); status=result; }
                });
                Bundle accepted=new Bundle(); accepted.putLong("id",id); return accepted;
            }
            if(method.equals("tiles.status")) return status;
            if(method.equals("preview.request")) {
                long id=++sequence; main.post(()->capture(id,request)); Bundle accepted=new Bundle(); accepted.putLong("id",id); return accepted;
            }
            return method.equals("preview.result")?frame:Bundle.EMPTY;
        }
        private void capture(long id,Bundle request) {
            try {
                NativeGrid grid=new NativeGrid(fixture.panel,fixture.workspace);
                NativePreviewServer.Frame capture=fixture.render(grid,grid.new Session(request.getString("layout","")),()->{});
                File path=new File(getContext().getCacheDir(),"flow-"+id);
                try(FileOutputStream output=new FileOutputStream(path)) { PreviewBitmapIO.write(capture.bitmap,output); }
                capture.bitmap.recycle();
                shadowOf(getContext().getContentResolver()).registerInputStream(PreviewProtocol.image(id),new FileInputStream(path));
                Bundle result=new Bundle(capture.metadata); result.putLong("id",id); result.putInt("page",capture.page); result.putInt("pages",capture.pages); result.putString("imageFormat",PreviewBitmapIO.FORMAT); frame=result;
                getContext().getContentResolver().notifyChange(PreviewProtocol.RESULT,null);
            } catch(Exception e) { throw new AssertionError(e); }
        }
        @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u,ContentValues v) { return null; }
        @Override public int delete(Uri u,String s,String[] a) { return 0; }
        @Override public int update(Uri u,ContentValues v,String s,String[] a) { return 0; }
    }
    private Bridge setup() {
        Bridge bridge=Robolectric.buildContentProvider(Bridge.class).create().get(); bridge.fixture=new NativeGridTest.Fixture();
        Launcher l=new Launcher(); Launcher.instance=l; l.workspace=bridge.fixture.workspace; l.mInfoManager.rawTileInfoList.add(bridge.fixture.info); l.getTileRecord("bt"); l.rejectEditorDelete=true;
        ShadowContentResolver.registerProviderInternal("moe.xWey10.tiling.settings",bridge); return bridge;
    }
    private void pump(java.util.function.BooleanSupplier done) throws Exception {
        for(int n=0;n<250&&!done.getAsBoolean();n++) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10)); Thread.sleep(4); }
        assertTrue("Flow must finish",done.getAsBoolean());
    }
    private void touch(PreviewView view,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0); view.onTouchEvent(event); event.recycle();
    }
    private PreviewView ready(MainActivity app) throws Exception {
        PreviewView view=ReflectionHelpers.getField(app,"preview"); pump(()->view.getLayoutDraft()!=null&&!ReflectionHelpers.<Boolean>getField(app,"previewBusy"));
        NativeGridTest.draw(view,400).recycle(); return view;
    }
    private void minus(PreviewView view) {
        touch(view,MotionEvent.ACTION_DOWN,50,50); touch(view,MotionEvent.ACTION_UP,50,50);
        touch(view,MotionEvent.ACTION_DOWN,10,10); touch(view,MotionEvent.ACTION_UP,10,10);
    }
    @Test public void tappingMinusRemovesTheActualTileAndReturnsAllControls() throws Exception {
        setup();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=controller.get(); PreviewView view=ready(app); minus(view);
            assertTrue(ReflectionHelpers.getField(app,"committing"));
            assertEquals("Удаление плитки…",ReflectionHelpers.<TextView>getField(app,"tileActionStatus").getText().toString());
            pump(()->!ReflectionHelpers.<Boolean>getField(app,"committing")&&view.getLayoutDraft()!=null&&view.getLayoutDraft().item("t:bt")==null);
            assertNull(Launcher.instance.getTileRecord("bt")); assertFalse("No unsaved changes: Apply stays disabled after membership operation",ReflectionHelpers.<Button>getField(app,"apply").isEnabled()); assertTrue(ReflectionHelpers.<Button>getField(app,"restart").isEnabled());
        } finally { Launcher.instance=null; }
    }
    @Test public void missingNativeReplyReleasesButtonsAndRestoresTheTileWithoutHidingTheError() throws Exception {
        Bridge bridge=setup(); bridge.neverComplete=true;
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=controller.get(); PreviewView view=ready(app); minus(view);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(15001));
            assertFalse(ReflectionHelpers.getField(app,"committing")); assertFalse("No unsaved changes: Apply stays disabled after membership operation",ReflectionHelpers.<Button>getField(app,"apply").isEnabled()); assertTrue(ReflectionHelpers.<Button>getField(app,"restart").isEnabled());
            pump(()->!ReflectionHelpers.<Boolean>getField(app,"previewBusy"));
            assertNotNull(view.getLayoutDraft().item("t:bt")); assertNotNull(Launcher.instance.getTileRecord("bt"));
            assertTrue(ReflectionHelpers.<TextView>getField(app,"tileActionStatus").getText().toString().contains("подтверждения"));
        } finally { Launcher.instance=null; }
    }
}
