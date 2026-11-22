package moe.xWey10.tiling;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.button.MaterialButtonToggleGroup;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class SettingsFlowTest {
    @Before public void cleanStorage() { Context c=RuntimeEnvironment.getApplication();SettingsStore.prefs(c).edit().clear().commit();c.createDeviceProtectedStorageContext().getSharedPreferences("runtime",Context.MODE_PRIVATE).edit().clear().commit(); }
    @Test public void settingsNavigationUsesSeparatePagesAndBackPreservesTheDraft() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();View root=app.getWindow().getDecorView();assertTrue(field(app,"enabled") instanceof MaterialSwitch);
            clickRow(root,"Оформление");assertEquals(2,(int)field(app,"screen"));assertFalse(((View)field(app,"size")).isShown());assertTrue(((View)field(app,"shape")).isShown());assertTrue(field(app,"shape") instanceof MaterialButtonToggleGroup);
            app.getOnBackPressedDispatcher().onBackPressed();clickRow(root,"Редактор шторки");assertEquals(1,(int)field(app,"screen"));Slider size=field(app,"size");assertTrue(size.isShown());assertTrue(((View)field(app,"gap")).isShown());assertTrue(((View)field(app,"preview")).isShown());size.setValue(87);app.getOnBackPressedDispatcher().onBackPressed();assertEquals(0,(int)field(app,"screen"));
            clickRow(root,"Редактор шторки");assertEquals(87,size.getValue(),0);assertFalse(SettingsStore.read(app).enabled);
        }
    }
    @Test public void refreshAndNavigationAreAboveTheEditorAndSelectionDoesNotReflowTheGrid() throws Exception {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();app.showScreen(1);View root=app.getWindow().getDecorView();measure(root);PreviewView preview=field(app,"preview");int[] grid=position(preview);
            for(Button b:find(root,Button.class)) if(List.of("‹","›").contains(b.getText().toString())||"Обновить предпросмотр".contentEquals(b.getContentDescription()==null?"":b.getContentDescription())) assertTrue(position(b)[1]<grid[1]);
            NativeGridTest.Fixture nativeTree=new NativeGridTest.Fixture();NativeGrid g=new NativeGrid(nativeTree.panel,nativeTree.workspace);var frame=nativeTree.render(g,g.new Session(""),()->{});preview.setFrame(frame.bitmap,frame.metadata);preview.setBusy(false);ReflectionHelpers.callInstanceMethod(app,"setPreviewBusy",ReflectionHelpers.ClassParameter.from(boolean.class,false));measure(root);grid=position(preview);
            float scale=preview.getWidth()/400f;touch(preview,MotionEvent.ACTION_DOWN,50*scale,50*scale);touch(preview,MotionEvent.ACTION_UP,50*scale,50*scale);measure(root);assertArrayEquals(grid,position(preview));assertEquals(View.GONE,ReflectionHelpers.<LinearLayout>getField(app,"selectionControls").getVisibility());
            assertFalse(texts(root).contains("Предпросмотр"));frame.bitmap.recycle();
        }
    }
    @Test public void draggingMaterialSliderKeepsVisibleTextAndItsPositionStable() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();app.showScreen(1);View root=app.getWindow().getDecorView();shadowOf(Looper.getMainLooper()).idle();measure(root);Slider slider=field(app,"size");
            touch(slider,MotionEvent.ACTION_DOWN,slider.getWidth()/2f,slider.getHeight()/2f);touch(slider,MotionEvent.ACTION_MOVE,slider.getWidth()/2f+30,slider.getHeight()/2f);
            assertTrue(ReflectionHelpers.<Boolean>getField(app,"trackingControl"));int[] before=position(slider);List<String> labels=texts(root);slider.setValue(80);slider.setValue(97);
            PreviewClient.Listener updates=ReflectionHelpers.getField(field(app,"previewClient"),"listener");updates.loading();updates.error("Длинное сообщение SystemUI, которое не должно двигать настройки.");shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2100));measure(root);
            assertEquals(labels,texts(root));assertArrayEquals(before,position(slider));assertEquals(97,ReflectionHelpers.<PreviewView>getField(app,"preview").getConfig().scalePercent);
            touch(slider,MotionEvent.ACTION_UP,slider.getWidth()/2f+30,slider.getHeight()/2f);assertEquals("Масштаб",ReflectionHelpers.<TextView>getField(app,"sizeLabel").getText().toString());
        }
    }
    @Test public void settingsAreStagedPersistedAndResetFromTheModulePage() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity app=c.get();MaterialSwitch enabled=field(app,"enabled"),battery=field(app,"battery");enabled.setChecked(true);app.showScreen(2);battery.setChecked(true);ReflectionHelpers.<Slider>getField(app,"size").setValue(90);ReflectionHelpers.<Slider>getField(app,"gap").setValue(4);
            assertFalse(SettingsStore.read(app).enabled);ReflectionHelpers.<Button>getField(app,"apply").performClick();var saved=SettingsStore.read(app);assertTrue(saved.enabled);assertTrue(saved.battery);assertEquals(90,saved.scalePercent);assertEquals(4,saved.gapDp);assertTrue(saved.revision>0);
            app.showScreen(3);ReflectionHelpers.<Button>getField(app,"reset").performClick();saved=SettingsStore.read(app);assertFalse(saved.enabled);assertFalse(saved.battery);assertEquals(100,saved.scalePercent);assertEquals(-1,saved.radiusDp);
        }
    }
    @Test public void recreationKeepsTheScreenAndUnsavedControlValues() {
        Bundle state=new Bundle();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) { MainActivity app=c.get();ReflectionHelpers.<MaterialSwitch>getField(app,"enabled").setChecked(true);app.showScreen(2);ReflectionHelpers.<Slider>getField(app,"size").setValue(85);c.saveInstanceState(state); }
        try(var c=Robolectric.buildActivity(MainActivity.class).setup(state)) { MainActivity app=c.get();assertEquals(2,(int)field(app,"screen"));assertTrue(ReflectionHelpers.<MaterialSwitch>getField(app,"enabled").isChecked());assertEquals(85,ReflectionHelpers.<Slider>getField(app,"size").getValue(),0);assertFalse(SettingsStore.read(app).enabled); }
    }
    @Test public void shapeAndEveryAppearanceControlStillUpdateTheStagedPreview() {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) { MainActivity app=c.get();app.showScreen(2);ReflectionHelpers.<MaterialSwitch>getField(app,"enabled").setChecked(true);ReflectionHelpers.<Slider>getField(app,"size").setValue(87);ReflectionHelpers.<Slider>getField(app,"gap").setValue(6);ReflectionHelpers.<MaterialButtonToggleGroup>getField(app,"shape").check(R.id.shape_custom);ReflectionHelpers.<Slider>getField(app,"radius").setValue(12);ReflectionHelpers.<MaterialSwitch>getField(app,"battery").setChecked(true);
            var draft=ReflectionHelpers.<PreviewView>getField(app,"preview").getConfig();assertTrue(draft.enabled);assertTrue(draft.battery);assertEquals(87,draft.scalePercent);assertEquals(6,draft.gapDp);assertEquals(12,draft.radiusDp);assertFalse(SettingsStore.read(app).enabled);
        }
    }
    @Test public void gridDraftSurvivesRecreationAndCanBeDiscardedWithoutWritingSystemUi() throws Exception {
        Bundle state=new Bundle();NativeGridTest.Fixture f=new NativeGridTest.Fixture();NativeGrid grid=new NativeGrid(f.panel,f.workspace);var frame=f.render(grid,grid.new Session(""),()->{});
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()) { MainActivity app=c.get();app.showScreen(1);PreviewView view=field(app,"preview");view.setFrame(frame.bitmap,frame.metadata);view.setBusy(false);NativeGridTest.draw(view,400).recycle();touch(view,MotionEvent.ACTION_DOWN,50,50);touch(view,MotionEvent.ACTION_UP,50,50);view.moveSelected(1,0);c.saveInstanceState(state);assertFalse(state.getString("layout_draft","").isEmpty());assertEquals(0,f.info.cellX); }
        try(var c=Robolectric.buildActivity(MainActivity.class).setup(state)) { MainActivity app=c.get();Bundle restored=new Bundle();c.saveInstanceState(restored);assertEquals(state.getString("layout_draft"),restored.getString("layout_draft"));ReflectionHelpers.<Button>getField(app,"discard").performClick();c.saveInstanceState(restored);assertEquals("",restored.getString("layout_draft"));assertEquals(0,f.info.cellX); }
        frame.bitmap.recycle();
    }
    private static <T>T field(MainActivity app,String name) { return ReflectionHelpers.getField(app,name); }
    private static void touch(View v,int action,float x,float y) { MotionEvent e=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0);v.onTouchEvent(e);e.recycle(); }
    private static int[] position(View v) { int[] p=new int[2];v.getLocationOnScreen(p);return p; }
    private static void measure(View root) {root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280);}
    private static List<String> texts(View root) { List<String> out=new ArrayList<>();for(TextView v:find(root,TextView.class)) if(v.isShown()) out.add(v.getText().toString());return out; }
    private static void clickRow(View root,String title) { for(TextView t:find(root,TextView.class)) if(t.isShown()&&title.contentEquals(t.getText())) { View v=t;while(!v.isClickable()) v=(View)v.getParent();v.performClick();return; }throw new AssertionError(title); }
    private static <T extends View>List<T> find(View root,Class<T> type) {List<T> out=new ArrayList<>();if(type.isInstance(root)) out.add(type.cast(root));if(root instanceof ViewGroup g) for(int n=0;n<g.getChildCount();n++) out.addAll(find(g.getChildAt(n),type));return out;}
}
