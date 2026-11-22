package moe.xWey10.tiling;
import android.graphics.*;

/** Total stable order and an opaque gutter around the upper silhouette. */
final class TileLayers {
    static boolean above(QsLayout.Item a,QsLayout.Item b) { return a.form().layer()!=b.form().layer()?a.form().layer()>b.form().layer():a.id().compareTo(b.id())>0; }
    static boolean stackable(QsLayout.Item a,QsLayout.Item b) { return a.editable()&&b.editable()&&(a.form().transformed()&&b.form().transformed()||(a.form().transformed()||b.form().transformed())&&a.form().layer()!=b.form().layer()); }
    static void subtract(Path lower,Path upper,float gap) { lower.op(TileRotation.expand(upper,gap),Path.Op.DIFFERENCE); }
    static boolean contains(Path shape,float x,float y) { Region clip=new Region(-100000,-100000,100000,100000),r=new Region(); r.setPath(shape,clip); return r.contains((int)x,(int)y); }
}
