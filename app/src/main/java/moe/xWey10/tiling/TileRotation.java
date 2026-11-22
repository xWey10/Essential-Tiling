package moe.xWey10.tiling;
import android.graphics.*;

/** One transform for the whole native content group and the exact same preview pixels. */
final class TileRotation {
    static float baseAngle(TileForm f,RectF b,int w,int h,float px,float py) { return f.diagonal()?DiagonalTile.geometry(f.intrinsic(b,w,h,px,py),f.columns(w),f.rows(h),px,py,f.corner()==6).angle():0; }
    static Matrix extra(TileForm f,RectF b,int w,int h,float px,float py) { Matrix m=new Matrix(); m.setRotate(f.rotation(),b.centerX(),b.centerY()); return m; }
    static Matrix extra(TileForm f,RectF b,int w,int h,float px,float py,boolean rtl) {
        Matrix m=extra(f,b,w,h,px,py); if(rtl) { Matrix mirror=new Matrix(); mirror.setScale(-1,1,b.centerX(),b.centerY()); m.preConcat(mirror); m.postConcat(mirror); } return m;
    }
    static RectF point(Matrix matrix,float x,float y) { float[] p={x,y}; matrix.mapPoints(p); return new RectF(p[0],p[1],p[0],p[1]); }
    static Matrix content(TileForm f,RectF b,int w,int h,float px,float py,boolean rtl) {
        Matrix result=extra(f,b,w,h,px,py); result.preRotate(baseAngle(f,b,w,h,px,py),b.centerX(),b.centerY());
        // Flip the whole icon/label group around its own body, never around the shape's frame.
        // An off-centre L must keep its caption in the same arm when the baseline crosses vertical.
        float angle=f.rotation()+baseAngle(f,b,w,h,px,py);
        if(Math.cos(Math.toRadians(angle))<-.0001) { RectF body=f.content(b,w,h,px,py);result.preRotate(180,body.centerX(),body.centerY()); }
        if(rtl) { Matrix mirror=new Matrix(); mirror.setScale(-1,1,b.centerX(),b.centerY()); result.preConcat(mirror); result.postConcat(mirror); }
        return result;
    }
    static float snap(float angle) { float nearest=Math.round(angle/45f)*45f;return Math.abs(angle-nearest)<=3?nearest:angle; }
    static Path expand(Path shape,float gap) { Path out=new Path(); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(2*gap); p.setStrokeJoin(Paint.Join.ROUND); p.getFillPath(shape,out); return out; }
}
