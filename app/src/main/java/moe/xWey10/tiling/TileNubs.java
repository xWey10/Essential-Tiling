package moe.xWey10.tiling;

import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;

/** Small round protrusions stay inside the original native cell footprint. */
final class TileNubs {
    static Path path(TileForm form,RectF b,int w,int h,float pitchX,float pitchY,float radius) {
        float size=Math.max(0,Math.min(b.width()-(w-1)*pitchX,b.height()-(h-1)*pitchY)),nub=size*.09f;
        RectF[][] base=new RectF[h][w]; ArrayList<RectF> rects=new ArrayList<>(); Path circles=new Path();
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(form.baseOccupied(x,y,w,h)) {
            RectF original=TileCells.cell(b,w,h,x,y,pitchX,pitchY),r=new RectF(original);
            if(!form.baseOccupied(x-1,y,w,h)||form.split(x-1,y,true)) { r.left+=nub; circles.addCircle(original.left+nub,original.centerY(),nub,Path.Direction.CW); }
            if(!form.baseOccupied(x+1,y,w,h)||form.split(x,y,true)) { r.right-=nub; circles.addCircle(original.right-nub,original.centerY(),nub,Path.Direction.CW); }
            if(!form.baseOccupied(x,y-1,w,h)||form.split(x,y-1,false)) { r.top+=nub; circles.addCircle(original.centerX(),original.top+nub,nub,Path.Direction.CW); }
            if(!form.baseOccupied(x,y+1,w,h)||form.split(x,y,false)) { r.bottom-=nub; circles.addCircle(original.centerX(),original.bottom-nub,nub,Path.Direction.CW); }
            base[y][x]=r; rects.add(r);
        }
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(base[y][x]!=null) {
            RectF r=base[y][x];
            if(x+1<w&&base[y][x+1]!=null&&!form.split(x,y,true)) { RectF n=base[y][x+1]; add(rects,new RectF(r.right,Math.max(r.top,n.top),n.left,Math.min(r.bottom,n.bottom))); }
            if(y+1<h&&base[y+1][x]!=null&&!form.split(x,y,false)) { RectF n=base[y+1][x]; add(rects,new RectF(Math.max(r.left,n.left),r.bottom,Math.min(r.right,n.right),n.top)); }
            if(x+1<w&&y+1<h&&base[y][x+1]!=null&&base[y+1][x]!=null&&base[y+1][x+1]!=null&&!form.split(x,y,true)&&!form.split(x,y,false)&&!form.split(x+1,y,false)&&!form.split(x,y+1,true)) { RectF n=base[y+1][x+1]; add(rects,new RectF(r.right,r.bottom,n.left,n.top)); }
        }
        Path result=TileOutline.path(rects,Math.min(radius,Math.max(0,size/2-nub))); result.op(circles,Path.Op.UNION); return result;
    }
    private static void add(ArrayList<RectF> list,RectF r) { if(!r.isEmpty()) list.add(r); }
}
