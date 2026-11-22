package moe.xWey10.tiling;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

/** A true continuous capsule between the centers of its end cells, not a stepped mask. */
final class DiagonalTile {
    record Geometry(float ax,float ay,float bx,float by,float thickness,float length,float angle,RectF body) { }
    static Geometry geometry(RectF bounds,int w,int h,float pitchX,float pitchY,boolean rising) {
        float cellW=Math.max(1,bounds.width()-(w-1)*pitchX),cellH=Math.max(1,bounds.height()-(h-1)*pitchY);
        float r=Math.min(cellW,cellH)/2;
        float ax=bounds.left+r,bx=bounds.right-r,ay=rising?bounds.bottom-r:bounds.top+r,by=rising?bounds.top+r:bounds.bottom-r;
        float length=(float)Math.hypot(bx-ax,by-ay)+2*r,angle=(float)Math.toDegrees(Math.atan2(by-ay,bx-ax));
        return new Geometry(ax,ay,bx,by,2*r,length,angle,new RectF(bounds.centerX()-length/2,bounds.centerY()-r,bounds.centerX()+length/2,bounds.centerY()+r));
    }
    static Path path(Geometry g,boolean nubs) {
        Path path=new Path(); RectF row=new RectF(g.body); float nub=nubs?g.thickness*.09f:0;
        row.inset(nub,nub); path.addRoundRect(row,row.height()/2,row.height()/2,Path.Direction.CW);
        if(nubs) for(float t:new float[]{.25f,.5f,.75f}) for(int side:new int[]{-1,1}) {
            Path circle=new Path(); circle.addCircle(g.body.left+g.length*t,g.body.centerY()+side*(g.thickness/2-nub),nub,Path.Direction.CW); path.op(circle,Path.Op.UNION);
        }
        Matrix rotation=new Matrix(); rotation.setRotate(g.angle,g.body.centerX(),g.body.centerY()); path.transform(rotation); return path;
    }
    static boolean occupied(int x,int y,int w,int h,boolean rising) {
        if(x<0||y<0||x>=w||y>=h) return false;
        double ax=.5,ay=rising?h-.5:.5,bx=w-.5,by=rising?.5:h-.5;
        if(intersects(ax,ay,bx,by,x,y,x+1,y+1)) return true;
        double distance=Math.min(pointRect(ax,ay,x,y,x+1,y+1),pointRect(bx,by,x,y,x+1,y+1));
        for(double xx:new double[]{x,x+1}) for(double yy:new double[]{y,y+1}) distance=Math.min(distance,pointSegment(xx,yy,ax,ay,bx,by));
        return distance<.25-1e-7;
    }
    private static double pointRect(double px,double py,double l,double t,double r,double b) {
        double dx=Math.max(l-px,Math.max(0,px-r)),dy=Math.max(t-py,Math.max(0,py-b)); return dx*dx+dy*dy;
    }
    private static double pointSegment(double x,double y,double ax,double ay,double bx,double by) {
        double dx=bx-ax,dy=by-ay,length=dx*dx+dy*dy,t=length==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/length));
        double xx=x-ax-t*dx,yy=y-ay-t*dy; return xx*xx+yy*yy;
    }
    private static boolean intersects(double ax,double ay,double bx,double by,double l,double t,double r,double b) {
        double lo=0,hi=1,dx=bx-ax,dy=by-ay;
        for(double[] edge:new double[][]{{-dx,ax-l},{dx,r-ax},{-dy,ay-t},{dy,b-ay}}) {
            if(Math.abs(edge[0])<1e-10) { if(edge[1]<0) return false; }
            else { double k=edge[1]/edge[0]; if(edge[0]<0) lo=Math.max(lo,k); else hi=Math.min(hi,k); }
        }
        return lo<=hi;
    }
}
