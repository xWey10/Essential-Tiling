package moe.xWey10.tiling;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.util.ArrayList;

/** The editor captures the expanded QS pose, even while QSAnimator keeps a closed-panel height. */
final class NativePreviewPose {
    private record State(View view,Field height,Field squish,int oldHeight,float oldSquish,int scrollX,int scrollY) { }
    private final ArrayList<State> states=new ArrayList<>();
    NativePreviewPose(View root) { collect(root); }
    private void collect(View view) {
        if(NativeFormHooks.tileType(view)!=null) {
            try {
                Field height=NativeGrid.field(view.getClass(),"heightOverride"),squish=NativeGrid.field(view.getClass(),"squishinessFraction");
                states.add(new State(view,height,squish,height.getInt(view),squish.getFloat(view),view.getScrollX(),view.getScrollY()));
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Поза предпросмотра QS",e); }
        }
        if(view instanceof ViewGroup group) for(int n=0;n<group.getChildCount();n++) collect(group.getChildAt(n));
    }
    void expanded() {
        try { for(State state:states) { state.height.setInt(state.view,-1); state.squish.setFloat(state.view,1); state.view.scrollTo(0,0); } }
        catch(IllegalAccessException e) { throw new IllegalStateException("Развёрнутая высота QS",e); }
    }
    void restore() {
        try { for(State state:states) { state.height.setInt(state.view,state.oldHeight); state.squish.setFloat(state.view,state.oldSquish); state.view.scrollTo(state.scrollX,state.scrollY); } }
        catch(IllegalAccessException e) { throw new IllegalStateException("Восстановление высоты QS",e); }
    }
}
