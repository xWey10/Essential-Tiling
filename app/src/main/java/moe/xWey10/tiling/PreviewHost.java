package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

/** Full-width native grid. Long pages scroll instead of shrinking their tiles. */
final class PreviewHost extends ScrollView {
    private ImageView frozen;
    private int heldScroll,pendingScroll=-1;
    PreviewHost(Context c) { super(c);setFillViewport(false);setClipChildren(true);setClipToPadding(true);setHorizontalScrollBarEnabled(false); }
    void hold(PreviewView view) {
        if(view.getWidth()<1||view.getHeight()<1) { removeView(view);return; }
        frozen=snapshot(view);
        heldScroll=getScrollY();pendingScroll=heldScroll;removeView(view);addView(frozen,new FrameLayout.LayoutParams(view.getWidth(),view.getHeight()));
    }
    static ImageView snapshot(View view) {
        Bitmap image=Bitmap.createBitmap(Math.max(1,view.getWidth()),Math.max(1,view.getHeight()),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        ImageView copy=new ImageView(view.getContext());copy.setImageBitmap(image);copy.setScaleType(ImageView.ScaleType.FIT_XY);copy.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return copy;
    }
    void releaseFrame() { if(frozen!=null) { pendingScroll=heldScroll;removeView(frozen);frozen.setImageDrawable(null);frozen=null; } }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) { super.onLayout(changed,l,t,r,b);if(pendingScroll>=0) { int scroll=pendingScroll;pendingScroll=-1;scrollTo(0,scroll); } }
    RectF screenBounds() {
        View view=getChildCount()>0?getChildAt(0):this;int[] xy=new int[2];view.getLocationOnScreen(xy);
        return new RectF(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());
    }
    RectF viewportBounds() { int[] xy=new int[2];getLocationOnScreen(xy);return new RectF(xy[0],xy[1],xy[0]+getWidth(),xy[1]+getHeight()); }
}
