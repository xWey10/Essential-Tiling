package moe.xWey10.tiling;

import android.app.Dialog;
import android.content.Context;
import android.graphics.RectF;
import java.util.function.Supplier;

/** One shared preview moves between the editor and the compact catalogue, with no window slide. */
final class TilePickerDialog extends Dialog {
    private TilePickerView picker;private Supplier<RectF> target;private boolean closing,immediate;
    TilePickerDialog(Context context) { super(context,R.style.Theme_EssentialTiling); }
    void transition(TilePickerView picker,Supplier<RectF> target) { this.picker=picker;this.target=target; }
    @Override public void dismiss() {
        if(closing) return;
        if(immediate||!isShowing()||picker==null||target==null) { super.dismiss();return; }
        closing=true;picker.animateExit(target.get(),()->{ closing=false;immediate=true;super.dismiss(); });
    }
    void dismissImmediately() { immediate=true;if(picker!=null) picker.cancelTransition();super.dismiss(); }
}
