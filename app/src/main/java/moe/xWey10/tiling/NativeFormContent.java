package moe.xWey10.tiling;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/** The native root always measures/layouts at its full size. Only child content uses the body. */
final class NativeFormContent {
    private static final java.util.WeakHashMap<View,java.lang.ref.WeakReference<View>> cells=new java.util.WeakHashMap<>();
    record Box(Rect body,int width,int height,float angle) {
        void measure(View view) {
            if(!(view instanceof android.widget.LinearLayout group)) return;
            boolean horizontal=group.getOrientation()==android.widget.LinearLayout.HORIZONTAL;
            int innerW=Math.max(0,body.width()-group.getPaddingLeft()-group.getPaddingRight());
            int innerH=Math.max(0,body.height()-group.getPaddingTop()-group.getPaddingBottom());
            int fixed=0; float weights=0;
            for(int n=0;n<group.getChildCount();n++) {
                View child=group.getChildAt(n); if(child.getVisibility()==View.GONE) continue;
                android.widget.LinearLayout.LayoutParams lp=params(child,group.getLayoutDirection());
                fixed+=horizontal?lp.leftMargin+lp.rightMargin:lp.topMargin+lp.bottomMargin;
                int base=horizontal?lp.width:lp.height;
                if(lp.weight>0) { weights+=lp.weight; fixed+=Math.max(0,base); }
                else fixed+=horizontal?child.getMeasuredWidth():child.getMeasuredHeight();
            }
            int remaining=Math.max(0,(horizontal?innerW:innerH)-fixed);
            for(int n=0;n<group.getChildCount();n++) {
                View child=group.getChildAt(n); if(child.getVisibility()==View.GONE) continue;
                android.widget.LinearLayout.LayoutParams lp=params(child,group.getLayoutDirection());
                int w=ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(body.width(),View.MeasureSpec.EXACTLY),group.getPaddingLeft()+group.getPaddingRight()+lp.leftMargin+lp.rightMargin,lp.width);
                int h=ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(body.height(),View.MeasureSpec.EXACTLY),group.getPaddingTop()+group.getPaddingBottom()+lp.topMargin+lp.bottomMargin,lp.height);
                if(lp.weight>0&&weights>0) {
                    int allocated=Math.max(0,Math.max(0,horizontal?lp.width:lp.height)+Math.round(remaining*lp.weight/weights));
                    if(horizontal) w=View.MeasureSpec.makeMeasureSpec(allocated,View.MeasureSpec.EXACTLY); else h=View.MeasureSpec.makeMeasureSpec(allocated,View.MeasureSpec.EXACTLY);
                }
                child.forceLayout(); child.measure(w,h);
            }
        }
        @android.annotation.SuppressLint("RtlHardcoded") // These are physical comparisons after getAbsoluteGravity has resolved START/END for RTL.
        void position(View view) {
            if(!(view instanceof android.widget.LinearLayout group)) return;
            boolean horizontal=group.getOrientation()==android.widget.LinearLayout.HORIZONTAL;
            int dir=group.getLayoutDirection(),gravity=group.getGravity(),innerW=Math.max(0,body.width()-group.getPaddingLeft()-group.getPaddingRight()),innerH=Math.max(0,body.height()-group.getPaddingTop()-group.getPaddingBottom());
            int total=0;
            for(int n=0;n<group.getChildCount();n++) {
                View child=group.getChildAt(n); if(child.getVisibility()==View.GONE) continue;
                android.widget.LinearLayout.LayoutParams lp=params(child,dir);
                total+=horizontal?child.getMeasuredWidth()+lp.leftMargin+lp.rightMargin:child.getMeasuredHeight()+lp.topMargin+lp.bottomMargin;
            }
            int mainGravity=horizontal?android.view.Gravity.getAbsoluteGravity(gravity,dir)&android.view.Gravity.HORIZONTAL_GRAVITY_MASK:gravity&android.view.Gravity.VERTICAL_GRAVITY_MASK;
            int cursor=horizontal?body.left+group.getPaddingLeft():body.top+group.getPaddingTop();
            if(mainGravity==(horizontal?android.view.Gravity.CENTER_HORIZONTAL:android.view.Gravity.CENTER_VERTICAL)) cursor+=((horizontal?innerW:innerH)-total)/2;
            else if(mainGravity==(horizontal?android.view.Gravity.RIGHT:android.view.Gravity.BOTTOM)) cursor+=(horizontal?innerW:innerH)-total;
            for(int index=0;index<group.getChildCount();index++) {
                int n=horizontal&&dir==View.LAYOUT_DIRECTION_RTL?group.getChildCount()-1-index:index;
                View child=group.getChildAt(n); if(child.getVisibility()==View.GONE) continue;
                android.widget.LinearLayout.LayoutParams lp=params(child,dir); int childGravity=lp.gravity<0?gravity:lp.gravity;
                int x,y,w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                if(horizontal) {
                    cursor+=lp.leftMargin; x=cursor;
                    y=body.top+group.getPaddingTop()+lp.topMargin;
                    int vertical=childGravity&android.view.Gravity.VERTICAL_GRAVITY_MASK;
                    if(vertical==android.view.Gravity.CENTER_VERTICAL) y=body.top+group.getPaddingTop()+(innerH-h)/2+lp.topMargin-lp.bottomMargin;
                    else if(vertical==android.view.Gravity.BOTTOM) y=body.bottom-group.getPaddingBottom()-h-lp.bottomMargin;
                    cursor+=w+lp.rightMargin;
                } else {
                    cursor+=lp.topMargin; y=cursor;
                    x=body.left+group.getPaddingLeft()+lp.leftMargin;
                    int absolute=android.view.Gravity.getAbsoluteGravity(childGravity,dir)&android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
                    if(absolute==android.view.Gravity.CENTER_HORIZONTAL) x=body.left+group.getPaddingLeft()+(innerW-w)/2+lp.leftMargin-lp.rightMargin;
                    else if(absolute==android.view.Gravity.RIGHT) x=body.right-group.getPaddingRight()-w-lp.rightMargin;
                    cursor+=h+lp.bottomMargin;
                }
                child.layout(x,y,x+w,y+h);
            }
            android.graphics.drawable.Drawable background=group.getBackground();
            if(background!=null) background.setBounds(0,0,group.getWidth(),group.getHeight());
            group.invalidate();
        }
        private static android.widget.LinearLayout.LayoutParams params(View child,int direction) {
            android.widget.LinearLayout.LayoutParams lp=(android.widget.LinearLayout.LayoutParams)child.getLayoutParams(); lp.resolveLayoutDirection(direction); return lp;
        }
    }
    static Box box(View view,QsLayout.Item item,int width,int height) {
        if(!item.form().cut()) return null;
        float[] pitch=pitch(view,item.w(),item.h(),width,height);
        RectF area=TileContentSize.body(item.form(),new RectF(0,0,width,height),item.w(),item.h(),pitch[0],pitch[1]);
        if(view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL) area.set(width-area.right,area.top,width-area.left,area.bottom);
        float angle=TileRotation.baseAngle(item.form(),new RectF(0,0,width,height),item.w(),item.h(),pitch[0],pitch[1]);
        if(view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL) angle=-angle;
        return new Box(new Rect(Math.round(area.left),Math.round(area.top),Math.round(area.right),Math.round(area.bottom)),width,height,angle);
    }
    static float[] drawingPitch(View view,int w,int h) {
        float[] base=pitch(view,w,h,view.getMeasuredWidth(),view.getMeasuredHeight());
        float gapX=Math.min(Math.max(0,w*base[0]-view.getMeasuredWidth()),(float)view.getWidth()/w);
        float gapY=Math.min(Math.max(0,h*base[1]-view.getMeasuredHeight()),(float)view.getHeight()/h);
        return new float[]{(view.getWidth()+gapX)/w,(view.getHeight()+gapY)/h};
    }
    static float[] pitch(View view,int w,int h,float width,float height) {
        Config config=QsModule.currentConfig(); float factor=config.enabled?config.scalePercent/100f:1;
        java.lang.ref.WeakReference<View> old=cells.get(view); View known=old==null?null:old.get();
        if(known!=null&&NativePreviewRenderer.contains(known,view)) {
            float[] value=readPitch(known,factor); if(value!=null) return value;
        }
        for(android.view.ViewParent parent=view.getParent();parent instanceof View cell;parent=parent.getParent()) {
            float[] value=readPitch(cell,factor);
            if(value!=null) { cells.put(view,new java.lang.ref.WeakReference<>(cell)); return value; }
        }
        return new float[]{width/w,height/h};
    }
    private static float[] readPitch(View cell,float factor) {
        try {
            int x=(Integer)NativeGrid.call(cell,"getCellWidth"),y=(Integer)NativeGrid.call(cell,"getCellHeight");
            if(x>0&&y>0) return new float[]{x*factor,y*factor};
        } catch(ReflectiveOperationException ignored) { }
        return null;
    }
}
