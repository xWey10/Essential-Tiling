package moe.xWey10.tiling;

import android.content.Context;
import android.widget.LinearLayout;

/** Executes the same measure/layout body operations used by the Xposed callbacks. */
class FormBodyTestView extends LinearLayout {
    QsLayout.Item item;
    FormBodyTestView(Context context,QsLayout.Item item) { super(context); this.item=item; }
    @Override protected void onMeasure(int w,int h) {
        super.onMeasure(w,h);
        NativeFormContent.Box box=NativeFormContent.box(this,item,MeasureSpec.getSize(w),MeasureSpec.getSize(h));
        if(box!=null) box.measure(this);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        super.onLayout(changed,l,t,r,b);
        NativeFormContent.Box box=NativeFormContent.box(this,item,r-l,b-t);
        if(box!=null) box.position(this);
    }
}
