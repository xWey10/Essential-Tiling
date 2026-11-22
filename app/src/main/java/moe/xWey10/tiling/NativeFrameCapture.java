package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.view.View;

/** Offscreen HWUI also supports hardware bitmaps used by native media/widgets. */
final class NativeFrameCapture {
    private NativeFrameCapture() { }
    static Bitmap capture(View panel, Bitmap background, float scale, int width, int height) {
        Boolean before=NativeLayerClip.rawFrames.get(); NativeLayerClip.rawFrames.set(true);
        try { return rawCapture(panel,background,scale,width,height); }
        finally { if(before==null) NativeLayerClip.rawFrames.remove(); else NativeLayerClip.rawFrames.set(before); }
    }
    private static Bitmap rawCapture(View panel, Bitmap background, float scale, int width, int height) {
        if (panel.isHardwareAccelerated()) {
            try { return hardware(panel, background, scale, width, height); }
            catch (RuntimeException e) {
                // Software is valid for QS implementations without hardware-only content.
                return software(panel, background, scale, width, height);
            }
        }
        return software(panel, background, scale, width, height);
    }
    private static void draw(Canvas canvas, View panel, Bitmap background, float scale) {
        if (background != null) canvas.drawBitmap(background, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.scale(scale, scale);
        panel.draw(canvas);
    }
    private static Bitmap software(View panel, Bitmap background, float scale, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try { draw(new Canvas(result), panel, background, scale); return result; }
        catch (RuntimeException | Error e) { result.recycle(); throw e; }
    }
    private static Bitmap hardware(View panel, Bitmap background, float scale, int width, int height) {
        RenderNode node = new RenderNode("EssentialTiling preview");
        node.setPosition(0, 0, width, height);
        RecordingCanvas canvas = node.beginRecording(width, height);
        try { draw(canvas, panel, background, scale); }
        finally { node.endRecording(); }
        HardwareRenderer renderer = new HardwareRenderer();
        try (ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)) {
            renderer.setName("EssentialTiling preview");
            renderer.setSurface(reader.getSurface()); renderer.setContentRoot(node); renderer.setOpaque(false);
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) throw new IllegalStateException("HWUI frame unavailable");
                try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                    if (buffer == null) throw new IllegalStateException("HWUI buffer unavailable");
                    Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                    if (hardware == null) throw new IllegalStateException("HWUI bitmap unavailable");
                    try {
                        Bitmap result = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        if (result == null) throw new IllegalStateException("HWUI copy failed");
                        return result;
                    } finally { hardware.recycle(); }
                }
            }
        } finally { renderer.destroy(); node.discardDisplayList(); }
    }
}
