package moe.xWey10.tiling;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.GraphicsMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemePaletteTest {
    private Context theme(boolean dark) {
        Context app = RuntimeEnvironment.getApplication(); Configuration c = new Configuration(app.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(app.createConfigurationContext(c), R.style.Theme_EssentialTiling);
    }
    @Test public void lightPaletteUsesDeviceNeutralAndAccentResources() throws Exception {
        Context c = theme(false); ThemePalette p = new ThemePalette(c);
        assertEquals(c.getColor(android.R.color.system_neutral1_10), p.background);
        assertEquals(c.getColor(android.R.color.system_accent1_600), p.accent);
        assertTrue(Color.luminance(p.background) > Color.luminance(p.text));
        capture("notnight", "light");
    }
    @Test public void darkPaletteUsesDeviceColorsAndReversesTextContrast() throws Exception {
        Context c = theme(true); ThemePalette p = new ThemePalette(c);
        assertEquals(c.getColor(android.R.color.system_neutral1_1000), p.background);
        assertEquals(c.getColor(android.R.color.system_accent1_200), p.accent);
        assertTrue(Color.luminance(p.background) < Color.luminance(p.text));
        capture("night", "dark");
    }
    private void capture(String mode, String name) throws Exception {
        RuntimeEnvironment.setQualifiers("w393dp-h873dp-port-" + mode + "-xxhdpi");
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            org.robolectric.util.ReflectionHelpers.<PreviewClient>getField(controller.get(),"previewClient").stop();
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"setPreviewBusy",org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class,false));
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2619, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1179, 2619);
            Bitmap bitmap = Bitmap.createBitmap(1179, 2619, Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
            File directory = new File("build/test-previews"); assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            bitmap.recycle();
            // A test-only native tree supplies a visible upper grid; this is not a phone capture.
            NativeGridTest.Fixture fixture = new NativeGridTest.Fixture();
            ThemePalette palette = new ThemePalette(controller.get()); fixture.background.eraseColor(palette.background);
            android.graphics.drawable.GradientDrawable tileBackground = new android.graphics.drawable.GradientDrawable();
            tileBackground.setColor(palette.surface); tileBackground.setCornerRadius(44);
            fixture.tile.setBackground(tileBackground); fixture.tile.setText("Bluetooth"); fixture.tile.setTextSize(8); fixture.tile.setTextColor(palette.text);
            fixture.tile.setGravity(android.view.Gravity.CENTER);
            NativeGrid nativeGrid = new NativeGrid(fixture.panel, fixture.workspace);
            NativePreviewServer.Frame frame = fixture.render(nativeGrid, nativeGrid.new Session(nativeGrid.source.edit("t:bt", 0, 0, 2, 1).json()), () -> { });
            PreviewView editor = org.robolectric.util.ReflectionHelpers.getField(controller.get(), "preview");
            editor.setFrame(frame.bitmap, frame.metadata);org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"setPreviewStatus",org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,""));
            for(int page=1;page<=3;page++) { controller.get().showScreen(page);captureWindow(root,directory,new String[]{"","editor-","appearance-","module-"}[page]+name); }
            controller.get().showScreen(1);captureWindow(root,directory,"editor-"+name);
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"setPreviewBusy",org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class,false));
            float scale=editor.getWidth()/400f;android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,100*scale,50*scale,0),up=android.view.MotionEvent.obtain(0,1,android.view.MotionEvent.ACTION_UP,100*scale,50*scale,0);editor.onTouchEvent(down);editor.onTouchEvent(up);down.recycle();up.recycle();
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"openTileProperties");android.app.Dialog inspector=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(inspector.isShowing());captureWindow(inspector.getWindow().getDecorView(),directory,"tile-properties-"+name);inspector.dismiss();
            ProfileStore.save(controller.get(),new LayoutProfile("Основной",moe.xWey10.tiling.Config.DEFAULT,null));
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"openProfiles");android.app.Dialog profiles=org.robolectric.shadows.ShadowDialog.getLatestDialog();captureWindow(profiles.getWindow().getDecorView(),directory,"profiles-"+name);profiles.dismiss();
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(), "showTilePicker",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(long.class, 8L),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, 1),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, 1));
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertTrue(sheet.isShowing());
            View sheetRoot = sheet.getWindow().getDecorView();
            TilePickerView picker = org.robolectric.util.ReflectionHelpers.getField(controller.get(), "tilePicker");
            assertNotNull(picker);
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(), "setCommitting", org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, false));
            // Styling fixtures only; production glyphs are exported from QSTile.State in SystemUI.
            android.graphics.Bitmap glyph = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(android.graphics.Color.WHITE); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(6);
            new Canvas(glyph).drawCircle(48, 48, 25, paint);
            java.util.List<TileClient.Entry> entries = new java.util.ArrayList<>();
            String[] labels = {"Wi-Fi", "Bluetooth", "Фонарик", "Вибрация", "Кошелёк", "Управление домом", "Авиарежим", "Будильник", "Тёмная тема", "Запись экрана", "Экономия заряда", "Передача данных"};
            for (int n = 0; n < labels.length; n++) entries.add(new TileClient.Entry("fixture" + n, labels[n], n == 9 ? "Приложение" : "", glyph));
            picker.entries(entries);
            sheetRoot.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2619, View.MeasureSpec.EXACTLY));
            sheetRoot.layout(0, 0, 1179, 2619);
            picker.getViewTreeObserver().dispatchOnPreDraw();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));
            Bitmap menu = Bitmap.createBitmap(1179, 2619, Bitmap.Config.ARGB_8888); sheetRoot.draw(new Canvas(menu));
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "catalog-" + name + ".png"))) { menu.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            menu.recycle(); sheet.dismiss();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));
        }
    }
    private void captureWindow(View root,File directory,String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2619,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,2619);Bitmap b=Bitmap.createBitmap(1179,2619,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))) { b.compress(Bitmap.CompressFormat.PNG,100,out); }b.recycle();
    }

}
