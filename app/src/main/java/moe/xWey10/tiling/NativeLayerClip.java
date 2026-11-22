package moe.xWey10.tiling;
import android.graphics.*;
import android.view.*;

final class NativeLayerClip {
    static final ThreadLocal<Boolean> rawFrames=new ThreadLocal<>();
    static Path own(View view,QsLayout.Item item) {
        float[] pitch=NativeFormContent.drawingPitch(view,item.w(),item.h());
        Path shape=item.form().path(new RectF(0,0,view.getWidth(),view.getHeight()),item.w(),item.h(),pitch[0],pitch[1],NativeFormHooks.radius(view));
        if(view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL) { Matrix mirror=new Matrix(); mirror.setScale(-1,1,view.getWidth()/2f,0); shape.transform(mirror); }
        return shape;
    }
    static Path visible(View view,QsLayout.Item item) {
        Path result;
        if(item.form().cut()) result=own(view,item);
        else { result=new Path(); result.addRect(0,0,view.getWidth(),view.getHeight(),Path.Direction.CW); }
        if(Boolean.TRUE.equals(rawFrames.get())||!(view.getParent() instanceof ViewGroup parent)) return result;
        Matrix lower=new Matrix(),inverse=new Matrix(); view.transformMatrixToGlobal(lower); if(!lower.invert(inverse)) return result;
        float[] pitch=NativeFormContent.drawingPitch(view,item.w(),item.h()); float gap=Math.max(1,Math.min(pitch[0],pitch[1])*.06f);
        for(int n=0;n<parent.getChildCount();n++) {
            View other=parent.getChildAt(n); if(other==view||other.getVisibility()!=View.VISIBLE) continue;
            QsLayout.Item upper=NativeForms.item(other); if(upper==null||!TileLayers.stackable(item,upper)||!TileLayers.above(upper,item)) continue;
            Matrix matrix=new Matrix(); other.transformMatrixToGlobal(matrix); matrix.postConcat(inverse);
            Path shape=own(other,upper); shape.transform(matrix); TileLayers.subtract(result,shape,gap);
        }
        return result;
    }
}
