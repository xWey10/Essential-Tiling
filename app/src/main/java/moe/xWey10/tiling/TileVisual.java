package moe.xWey10.tiling;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;

/** Geometry, lift and native content transitions share the draw clock, with no IPC on frames. */
final class TileVisual {
    private static final long FADE = 180, LIFT = 120;
    private static final class Layer {
        final TileImage image;
        float start, weight;
        Layer(TileImage image, float weight) { this.image = image; start = this.weight = weight; }
    }
    final TileMotion motion;
    private final ArrayList<Layer> layers = new ArrayList<>();
    private final Path clip = new Path();
    private TileImage target;
    private long contentStarted, contentDuration, liftStarted;
    private float lift, liftStart, liftTarget;
    private int formW=1,formH=1,corner;
    private TileForm shape=TileForm.RECT;
    private boolean rtl,largeContent;
    void largeContent(boolean value){largeContent=value;}
    private void alignContent(Canvas canvas,RectF area,RectF old,TileImage source){boolean large=largeContent&&old.height()>=(source.height+insetY)/source.h*1.5f;canvas.translate(large?(rtl?area.right-old.right:area.left-old.left):area.centerX()-old.centerX(),large?area.top-old.top:area.centerY()-old.centerY());}
    private float insetX,insetY;
    void grid(float pitchX,float pitchY,RectF endpoint) { insetX=Math.max(0,formW*pitchX-endpoint.width()); insetY=Math.max(0,formH*pitchY-endpoint.height()); }
    private float pitchX(RectF b) { return (b.width()+insetX)/formW; }
    private float pitchY(RectF b) { return (b.height()+insetY)/formH; }
    private RectF body(TileImage image) {
        return TileContentSize.body(image.form,new RectF(0,0,image.width,image.height),image.w,image.h,(image.width+insetX)/image.w,(image.height+insetY)/image.h);
    }
    private java.util.List<RectF> branch;
    private RectF branchBody;
    private Matrix branchMatrix;
    private Path gesturePath;
    private record PoseTween(Path path,RectF body,Matrix matrix,float dx,float dy,long start,long duration) { }
    private PoseTween poseTween;
    private record Settling(QsLayout.Item anchor,TileExtrusion.Handle handle,float from,float to,RectF bounds,RectF body,RectF targetBody,float x,float y,boolean rtl,Matrix matrix,float dx,float dy,long start,long duration) { }
    private Settling settling;
    void settle(QsLayout.Item anchor,TileExtrusion.Handle handle,float from,float to,RectF bounds,RectF body,RectF targetBody,float x,float y,boolean rtl,long now,long duration) {
        settle(anchor,handle,from,to,bounds,body,targetBody,x,y,rtl,new Matrix(),new Matrix(),now,duration);
    }
    void settle(QsLayout.Item anchor,TileExtrusion.Handle handle,float from,float to,RectF bounds,RectF body,RectF targetBody,float x,float y,boolean rtl,Matrix matrix,Matrix targetMatrix,long now,long duration) {
        if(duration<=0) return; RectF a=TileRotation.point(matrix,body.centerX(),body.centerY()),b=TileRotation.point(targetMatrix,targetBody.centerX(),targetBody.centerY());
        settling=new Settling(anchor,handle,from,to,new RectF(bounds),new RectF(body),new RectF(targetBody),x,y,rtl,new Matrix(matrix),b.left-a.left,b.top-a.top,now,duration);
    }
    void pose(Path path,RectF body,Matrix matrix) { settling=null;poseTween=null;branch=null;gesturePath=new Path(path);branchBody=new RectF(body);branchMatrix=new Matrix(matrix); }
    void settlePose(float dx,float dy,long now,long duration) { if(gesturePath!=null&&duration>0) poseTween=new PoseTween(new Path(gesturePath),new RectF(branchBody),new Matrix(branchMatrix),dx,dy,now,duration); }
    private boolean advancePose(long now) {
        PoseTween t=poseTween;if(t==null) return false; float p=Math.min(1,Math.max(0,(now-t.start)/(float)t.duration));
        if(p>=1) { poseTween=null;gesturePath=null;branchBody=null;branchMatrix=null;return false; }
        float ease=1-(1-p)*(1-p)*(1-p);gesturePath=new Path(t.path);gesturePath.offset(t.dx*ease,t.dy*ease);branchBody=new RectF(t.body);branchMatrix=new Matrix(t.matrix);branchMatrix.postTranslate(t.dx*ease,t.dy*ease);return true;
    }
    private TileForm joinFrom,joinTo; private long joinStarted; private float joinProgress=1;
    void join(TileForm from,TileForm to,long now) { joinFrom=from; joinTo=to; joinStarted=now; joinProgress=0; }
    private boolean advanceJoin(long now) { if(joinFrom==null) return false; float t=Math.min(1,Math.max(0,(now-joinStarted)/320f)); joinProgress=t*t*(3-2*t); if(t>=1) joinFrom=null; return t<1; }
    private boolean advanceBranch(long now) {
        Settling s=settling; if(s==null) return false;
        float t=Math.max(0,Math.min(1,(float)(now-s.start)/s.duration)),ease=1-(1-t)*(1-t)*(1-t);
        if(t>=1) { settling=null; branch=null; branchBody=null; branchMatrix=null; return false; }
        branch=TileExtrusion.rectangles(s.anchor,s.handle,s.from+(s.to-s.from)*ease,s.bounds,s.rtl,s.x,s.y);
        branchBody=new RectF(s.body);branchMatrix=new Matrix(s.matrix);branchMatrix.postTranslate(s.dx*ease,s.dy*ease);return true;
    }
    void finishShapeAnimations() { settling=null;poseTween=null;branch=null;gesturePath=null;branchBody=null;branchMatrix=null;joinFrom=null;joinTo=null;joinProgress=1; }
    void shape(TileForm shape,boolean rtl) { this.shape=shape; this.rtl=rtl; if(settling==null&&poseTween==null) { branch=null;branchBody=null;branchMatrix=null;gesturePath=null; } }
    void branch(java.util.List<RectF> rectangles,RectF body) { branch(rectangles,body,new Matrix()); }
    void branch(java.util.List<RectF> rectangles,RectF body,Matrix matrix) { settling=null;poseTween=null;gesturePath=null;branch=rectangles;branchBody=new RectF(body);branchMatrix=new Matrix(matrix); }
    Path outline(RectF bounds,float radius) {
        if(joinFrom!=null) { Path result=TileSeams.drop(joinFrom,joinTo,bounds,formW,formH,pitchX(bounds),pitchY(bounds),radius,joinProgress); if(rtl) { android.graphics.Matrix m=new android.graphics.Matrix(); m.setScale(-1,1,bounds.centerX(),0); result.transform(m); } return result; }
        if(gesturePath!=null) return new Path(gesturePath);
        if(branch!=null) { Path path=TileOutline.path(branch,radius);if(branchMatrix!=null) path.transform(branchMatrix);return path; }
        TileForm f=shape.cellular()||shape.transformed()||shape.baseW()>0||shape.nubs()?shape:new TileForm(corner,Math.round(cutW),Math.round(cutH));
        Path result=shape.cellular()||cutW==Math.round(cutW)&&cutH==Math.round(cutH)?f.path(bounds,formW,formH,pitchX(bounds),pitchY(bounds),radius):new TileForm(corner,0,0).path(bounds,formW,formH,cutW,cutH,radius);
        if(rtl) { android.graphics.Matrix m=new android.graphics.Matrix(); m.setScale(-1,1,bounds.centerX(),0); result.transform(m); }
        return result;
    }
    private float cutW,cutH;
    private boolean custom;
    private TileImage empty;
    void form(int w,int h,int corner,float cw,float ch,boolean custom,TileImage empty) {
        formW=w;formH=h;this.corner=corner;cutW=cw;cutH=ch;this.custom=custom;
        this.empty=empty!=null&&empty.background!=null?empty:null;
    }
    TileVisual(RectF bounds, TileImage image) {
        motion = new TileMotion(bounds); target = image; layers.add(new Layer(image, 1));
    }
    TileImage image() { return target; }
    void image(TileImage next, long now, boolean animate) {
        if (target == next) return;
        content(now);
        Layer found = null;
        for (Layer layer : layers) { layer.start = layer.weight; if (layer.image == next) found = layer; }
        if (found == null) layers.add(new Layer(next, 0));
        target = next; contentStarted = now; contentDuration = animate ? FADE : 0; content(now);
    }
    void lift(boolean raised, long now, boolean animate) {
        advanceLift(now);
        float next = raised ? 1 : 0; if (next == liftTarget) return;
        liftStart = lift; liftTarget = next; liftStarted = now;
        if (!animate) lift = liftStart = liftTarget;
    }
    boolean advance(long now) { return motion.advance(now) | content(now) | advanceLift(now) | advanceBranch(now) | advanceJoin(now) | advancePose(now); }
    private boolean content(long now) {
        float t = contentDuration == 0 ? 1 : Math.min(1, Math.max(0, (float) (now - contentStarted) / contentDuration));
        for (int n = layers.size() - 1; n >= 0; n--) {
            Layer layer = layers.get(n); float end = layer.image == target ? 1 : 0;
            layer.weight = layer.start + (end - layer.start) * t;
            if (t == 1 && end == 0) layers.remove(n);
        }
        return t < 1;
    }
    private boolean advanceLift(long now) {
        float t = Math.min(1, Math.max(0, (float) (now - liftStarted) / LIFT));
        lift = liftStart + (liftTarget - liftStart) * (1 - (1 - t) * (1 - t));
        return t < 1 && liftStart != liftTarget;
    }
    void draw(Canvas canvas, Paint paint) {
        RectF b = motion.bounds; if (b.isEmpty()) return;
        canvas.save(); float scale = 1 + lift * .035f;
        canvas.scale(scale, scale, b.centerX(), b.centerY());
        // An unchanged endpoint uses the exact source, including its native anti-aliased edge.
        if (branch==null && gesturePath==null && !custom && corner==0 && layers.size() == 1 && Math.abs(b.width() - target.width) < .6f && Math.abs(b.height() - target.height) < .6f) {
            paint.setAlpha(255); target.content(canvas, paint, b); canvas.restore(); return;
        }
        float radius = 0; TileImage background = target.background == null ? null : target;
        for (Layer layer : layers) {
            radius += layer.image.radius * layer.weight;
            if (background == null && layer.image.background != null) background = layer.image;
        }
        if(custom&&empty!=null) { background=empty; radius=empty.radius; }
        if(branch!=null||gesturePath!=null||custom||corner>0) clip.set(outline(b,Math.min(radius,Math.min(pitchX(b)-insetX,pitchY(b)-insetY)/2)));
        else { clip.rewind(); clip.addRoundRect(b,Math.min(radius,b.width()/2),Math.min(radius,b.height()/2),Path.Direction.CW); }
        canvas.clipPath(clip);
        paint.setAlpha(255);
        if(background!=null) background.background(canvas,paint,b);
        else if(custom||branch!=null) canvas.drawColor(target.surface());
        for (Layer layer : layers) if (layer.weight > .001f) {
            paint.setAlpha(Math.round(255 * layer.weight));
            canvas.save();
            RectF contentBounds=b;
            if(branchBody!=null) {
                RectF old=body(layer.image),area=branchBody;
                if(rtl) old.set(layer.image.width-old.right,old.top,layer.image.width-old.left,old.bottom);
                if(branchMatrix!=null) canvas.concat(branchMatrix);
                alignContent(canvas,area,old,layer.image);
                Matrix inverse=new Matrix(); TileRotation.content(layer.image.form,new RectF(0,0,layer.image.width,layer.image.height),layer.image.w,layer.image.h,(layer.image.width+insetX)/layer.image.w,(layer.image.height+insetY)/layer.image.h,rtl).invert(inverse);canvas.concat(inverse);
                contentBounds=new RectF(0,0,layer.image.width,layer.image.height);
            } else if(shape.transformed()||layer.image.form.transformed()) {
                TileImage source=layer.image; RectF previous=body(source);
                RectF area=TileContentSize.body(shape,b,formW,formH,pitchX(b),pitchY(b));
                if(rtl) { area.set(b.left+b.right-area.right,area.top,b.left+b.right-area.left,area.bottom); previous.set(source.width-previous.right,previous.top,source.width-previous.left,previous.bottom); }
                canvas.concat(TileRotation.content(shape,b,formW,formH,pitchX(b),pitchY(b),rtl));
                alignContent(canvas,area,previous,source);
                android.graphics.Matrix inverse=new android.graphics.Matrix();
                TileRotation.content(source.form,new RectF(0,0,source.width,source.height),source.w,source.h,(source.width+insetX)/source.w,(source.height+insetY)/source.h,rtl).invert(inverse);
                canvas.concat(inverse); contentBounds=new RectF(0,0,source.width,source.height);
            } else if(custom&&(layer.image.w!=formW||layer.image.h!=formH||!layer.image.form.equals(shape))) {
                TileForm f=shape.cellular()||shape.transformed()||shape.baseW()>0||shape.nubs()?shape:new TileForm(corner,Math.round(cutW),Math.round(cutH));
                RectF area=f.cut()?f.content(b,formW,formH,pitchX(b),pitchY(b)):b;
                RectF old=body(layer.image); old.offset(b.left,b.top);
                if(rtl) { area.set(b.left+b.right-area.right,area.top,b.left+b.right-area.left,area.bottom); old.set(2*b.left+layer.image.width-old.right,old.top,2*b.left+layer.image.width-old.left,old.bottom); }
                alignContent(canvas,area,old,layer.image);
            }
            layer.image.content(canvas,paint,contentBounds); canvas.restore();
        }
        paint.setAlpha(255); canvas.restore();
    }
}
