package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import de.robv.android.xposed.XposedBridge;

/** OEM signature fixture with explicit test callback dispatch into the real module hooks. */
public class QSTileViewImpl extends LinearLayout {
    public int mSize;
    public int nativeMeasureWidth,nativeMeasureHeight;
    public int nativeLayoutWidth,nativeLayoutHeight;
    public boolean recordViewportFromNativeMeasure;
    public int heightOverride=-1;
    public float squishinessFraction=1;
    public QSTileViewImpl(Context c) { super(c); setGravity(android.view.Gravity.CENTER); }
    public void applySize(int w,int h) {
        XposedBridge.dispatch(method(QSTileViewImpl.class,"applySize",int.class,int.class),this,new Object[]{w,h},a->{ mSize=(Integer)a[1]>=2?2:(Integer)a[0]>=2?1:0; return null; });
    }
    public android.widget.TextView getLabel() { return null; }
    public void updateResources() { }
    public void updateLayout() { }
    @Override protected void onMeasure(int w,int h) {
        XposedBridge.dispatch(method(QSTileViewImpl.class,"onMeasure",int.class,int.class),this,new Object[]{w,h},a->{ nativeMeasureWidth=MeasureSpec.getSize((Integer)a[0]); nativeMeasureHeight=MeasureSpec.getSize((Integer)a[1]); super.onMeasure((Integer)a[0],(Integer)a[1]); return null; });
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        XposedBridge.dispatch(method(QSTileViewImpl.class,"onLayout",boolean.class,int.class,int.class,int.class,int.class),this,new Object[]{changed,l,t,r,b},a->{ nativeLayoutWidth=(Integer)a[3]-(Integer)a[1]; nativeLayoutHeight=(Integer)a[4]-(Integer)a[2]; super.onLayout((Boolean)a[0],(Integer)a[1],(Integer)a[2],(Integer)a[3],(Integer)a[4]);
            int height=heightOverride<0?getMeasuredHeight():heightOverride; setBottom(getTop()+Math.round(height*squishinessFraction)); setScrollY((height-getHeight())/2); return null; });
    }
    @Override protected boolean drawChild(Canvas c,View child,long time) {
        return (Boolean)XposedBridge.dispatch(method(ViewGroup.class,"drawChild",Canvas.class,View.class,long.class),this,new Object[]{c,child,time},a->super.drawChild((Canvas)a[0],(View)a[1],(Long)a[2]));
    }
    @Override public void draw(Canvas canvas) {
        XposedBridge.dispatch(method(View.class,"draw",Canvas.class),this,new Object[]{canvas},a->{ Canvas c=(Canvas)a[0]; int save=c.save();
            if(recordViewportFromNativeMeasure) c.clipRect(0,0,nativeMeasureWidth,nativeMeasureHeight);
            super.draw(c); c.restoreToCount(save); return null; });
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return (Boolean)XposedBridge.dispatch(method(View.class,"onTouchEvent",MotionEvent.class),this,new Object[]{event},a->super.onTouchEvent((MotionEvent)a[0]));
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return (Boolean)XposedBridge.dispatch(method(ViewGroup.class,"dispatchTouchEvent",MotionEvent.class),this,new Object[]{event},a->super.dispatchTouchEvent((MotionEvent)a[0]));
    }
    public static java.lang.reflect.Method method(Class<?> type,String name,Class<?>... args) {
        try { return type.getDeclaredMethod(name,args); } catch(Exception e) { throw new AssertionError(e); }
    }
}
