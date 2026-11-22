package moe.xWey10.tiling;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
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

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w393dp-h873dp-port-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CataloguePolishTest {
    @Before public void reset() { SettingsStore.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit(); }
    @Test public void longNativePageKeepsFullWidthAndScrollPositionAcrossTheCatalogueWithoutAnEmptyFrame() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();ReflectionHelpers.<PreviewClient>getField(app,"previewClient").stop();app.showScreen(1);PreviewView preview=ReflectionHelpers.getField(app,"preview");PreviewHost host=ReflectionHelpers.getField(app,"previewHost");Bitmap frame=Bitmap.createBitmap(400,1000,Bitmap.Config.ARGB_8888);frame.eraseColor(Color.RED);preview.setFrame(frame);View root=app.getWindow().getDecorView();measure(root);
            assertEquals(host.getWidth(),preview.getWidth());assertTrue(preview.getHeight()>host.getHeight());host.scrollTo(0,240);RectF before=host.screenBounds();assertEquals(240,host.getScrollY());
            org.robolectric.shadows.ShadowChoreographer.setPaused(true);open(app,2,1);TilePickerDialog dialog=(TilePickerDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(ReflectionHelpers.<LinearLayout>getField(app,"editorContent").getChildAt(0) instanceof ImageView);assertEquals(1,host.getChildCount());assertTrue(host.getChildAt(0) instanceof ImageView);assertNotNull(((ImageView)host.getChildAt(0)).getDrawable());assertEquals(0,preview.getAlpha(),0);assertEquals(0,dialog.getWindow().getAttributes().windowAnimations);assertEquals(0,dialog.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(17));measure(dialog.getWindow().getDecorView());ReflectionHelpers.<TilePickerView>getField(app,"tilePicker").getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(1,preview.getAlpha(),0);assertNotNull(preview.getClipBounds());assertTrue(preview.getClipBounds().height()<preview.getHeight());assertEquals(before,host.screenBounds());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));dialog.dismiss();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));measure(root);
            assertSame(host,preview.getParent());assertEquals(1,host.getChildCount());assertEquals(240,host.getScrollY());assertEquals(before,host.screenBounds());assertNull(preview.getClipBounds());frame.recycle();
        }
    }
    @Test public void systemAndApplicationTabsUseTileOriginAndKeepTheChosenInsertionCell() throws Exception {
        try(Fixture f=new Fixture()) {
            f.picker.target(8,2,1);f.picker.entries(List.of(new TileClient.Entry("bt","Bluetooth",""),new TileClient.Entry("wifi","Wi-Fi",""),new TileClient.Entry("flashlight","Фонарик",""),new TileClient.Entry("custom(app.one/.Tile)","Одна","Приложение"),new TileClient.Entry("custom(app.two/.Tile)","Вторая","")));
            measure(f.picker);assertEquals(2,f.grid().getCount());for(int n=0;n<f.grid().getCount();n++) assertFalse(((TileClient.Entry)f.grid().getItemAtPosition(n)).spec().startsWith("custom("));
            f.picker.applications(true);measure(f.picker);assertEquals(2,f.grid().getCount());for(int n=0;n<f.grid().getCount();n++) assertTrue(((TileClient.Entry)f.grid().getItemAtPosition(n)).spec().startsWith("custom("));
            TilePickerViewTest.find(f.picker,EditText.class).setText("Вторая");measure(f.picker);assertEquals(1,f.grid().getCount());f.grid().performItemClick(f.grid().getChildAt(0),0,0);assertEquals("custom(app.two/.Tile)",f.added.spec());assertEquals(new TilePickerView.Cell(8,2,1),f.cell);
            assertEquals(2,(int)ReflectionHelpers.getField(f.preview,"insertionX"));assertEquals(1,(int)ReflectionHelpers.getField(f.preview,"insertionY"));
            f.picker.target(8,3,2);assertEquals(3,(int)ReflectionHelpers.getField(f.preview,"insertionX"));assertEquals(2,(int)ReflectionHelpers.getField(f.preview,"insertionY"));
        }
    }
    @Test public void catalogueDrawsGridLinesAndMarksOnlyTheChosenFreeCell() throws Exception {
        try(Fixture f=new Fixture()) {
            f.preview.setCatalogueProgress(1);f.picker.target(8,2,1);Bitmap image=NativeGridTest.draw(f.preview,400);int line=image.getPixel(200,350),empty=image.getPixel(198,350);assertNotEquals("Grid remains visible in catalogue mode",line,empty);int marked=image.getPixel(250,150);assertNotEquals(marked,image.getPixel(350,150));image.recycle();
            f.preview.setInsertionCell(-1,-1,-1);Bitmap clear=NativeGridTest.draw(f.preview,400);assertNotEquals(marked,clear.getPixel(250,150));clear.recycle();
        }
    }
    @Test public void scrollingRowsFadeIntoTheSeparatorAndLabelsNeverOverlapTheirIcons() throws Exception {
        try(Fixture f=new Fixture()) {
            ArrayList<TileClient.Entry> entries=new ArrayList<>();for(int n=0;n<100;n++) entries.add(new TileClient.Entry("stock"+n,"Очень длинное название плитки "+n,""));f.picker.entries(entries);f.picker.cancelTransition();measure(f.picker);GridView grid=f.grid();grid.setSelectionFromTop(8,-dp(24));measure(f.picker);assertTrue(grid.canScrollVertically(-1));
            for(int n=0;n<grid.getChildCount();n++) { View row=grid.getChildAt(n);View circle=ReflectionHelpers.getField(row,"circle");TextView title=ReflectionHelpers.getField(row,"title");assertTrue(circle.getBottom()<=title.getTop());assertTrue(title.getBottom()<=row.getHeight());assertTrue(title.getLineCount()<=3); }
            Bitmap image=Bitmap.createBitmap(grid.getWidth(),grid.getHeight(),Bitmap.Config.ARGB_8888);grid.draw(new Canvas(image));boolean partial=false;int x=grid.getChildAt(0).getLeft()+grid.getChildAt(0).getWidth()/2;
            for(int y=dp(4);y<dp(32);y++) { int alpha=Color.alpha(image.getPixel(x,y));if(alpha>10&&alpha<235) partial=true; }assertTrue("Rows enter the line through a translucent edge",partial);image.recycle();
            int[] top=new int[2];f.preview.getLocationInWindow(top);grid.setSelection(60);measure(f.picker);int[] after=new int[2];f.preview.getLocationInWindow(after);assertArrayEquals(top,after);
        }
    }
    @Test public void largeFontAndKeyboardKeepLabelsInsideRowsAndLeaveScrollableResults() throws Exception {
        var resources=RuntimeEnvironment.getApplication().getResources();var config=new android.content.res.Configuration(resources.getConfiguration());config.fontScale=1.4f;resources.updateConfiguration(config,resources.getDisplayMetrics());
        try(Fixture f=new Fixture()) {
            ArrayList<TileClient.Entry> entries=new ArrayList<>();for(int n=0;n<60;n++) entries.add(new TileClient.Entry("stock"+n,"Длинная подпись "+n,""));f.picker.entries(entries);f.picker.cancelTransition();TilePickerViewTest.measure(f.picker,1179,1500);assertTrue(f.grid().getHeight()>=dp(140));
            for(int n=0;n<f.grid().getChildCount();n++) { View row=f.grid().getChildAt(n);TextView title=ReflectionHelpers.getField(row,"title");View circle=ReflectionHelpers.getField(row,"circle");assertTrue(title.getTop()>=circle.getBottom());assertTrue(title.getBottom()<=row.getHeight()); }
        }
    }
    private static void open(MainActivity app,int x,int y) {ReflectionHelpers.callInstanceMethod(app,"showTilePicker",ReflectionHelpers.ClassParameter.from(long.class,8L),ReflectionHelpers.ClassParameter.from(int.class,x),ReflectionHelpers.ClassParameter.from(int.class,y));}
    private static int dp(float n){return Math.round(n*RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density);}
    private static void measure(View view){TilePickerViewTest.measure(view,1179,2619);}
    private static final class Fixture implements AutoCloseable {
        final NativeGridTest.Fixture nativeTree=new NativeGridTest.Fixture();final PreviewView preview=new PreviewView(nativeTree.context);final NativePreviewServer.Frame frame;final TilePickerView picker;TileClient.Entry added;TilePickerView.Cell cell;
        Fixture() throws Exception {NativeGrid grid=new NativeGrid(nativeTree.panel,nativeTree.workspace);frame=nativeTree.render(grid,grid.new Session(""),()->{});preview.setFrame(frame.bitmap,frame.metadata);picker=new TilePickerView(nativeTree.context,preview,new LinearLayout(nativeTree.context),()->0,()->{},(e,c)->{added=e;cell=c;});}
        GridView grid(){return TilePickerViewTest.find(picker,GridView.class);}
        public void close(){picker.cancelTransition();frame.bitmap.recycle();}
    }
}
