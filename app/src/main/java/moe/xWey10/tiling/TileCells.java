package moe.xWey10.tiling;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/** Native cell pitch and outer inset stay constant when a shape gains another cell. */
final class TileCells {
    static RectF cell(RectF b,int w,int h,int x,int y,float pitchX,float pitchY) {
        float gapX=Math.max(0,w*pitchX-b.width()),gapY=Math.max(0,h*pitchY-b.height());
        return new RectF(b.left+x*pitchX,b.top+y*pitchY,b.left+(x+1)*pitchX-gapX,b.top+(y+1)*pitchY-gapY);
    }
    static RectF area(RectF b,int w,int h,RectF cells,float pitchX,float pitchY) {
        float gapX=Math.max(0,w*pitchX-b.width()),gapY=Math.max(0,h*pitchY-b.height());
        return new RectF(b.left+cells.left*pitchX,b.top+cells.top*pitchY,b.left+cells.right*pitchX-gapX,b.top+cells.bottom*pitchY-gapY);
    }
    static List<RectF> rectangles(TileForm form,RectF b,int w,int h,float pitchX,float pitchY) {
        ArrayList<RectF> result=new ArrayList<>();
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(form.baseOccupied(x,y,w,h)) {
            RectF cell=cell(b,w,h,x,y,pitchX,pitchY); result.add(cell);
            if(form.baseOccupied(x+1,y,w,h)&&!form.split(x,y,true)) { RectF right=cell(b,w,h,x+1,y,pitchX,pitchY); add(result,new RectF(cell.right,cell.top,right.left,cell.bottom)); }
            if(form.baseOccupied(x,y+1,w,h)&&!form.split(x,y,false)) { RectF below=cell(b,w,h,x,y+1,pitchX,pitchY); add(result,new RectF(cell.left,cell.bottom,cell.right,below.top)); }
            if(form.baseOccupied(x+1,y,w,h)&&form.baseOccupied(x,y+1,w,h)&&form.baseOccupied(x+1,y+1,w,h)&&!form.split(x,y,true)&&!form.split(x,y,false)&&!form.split(x+1,y,false)&&!form.split(x,y+1,true)) {
                RectF diagonal=cell(b,w,h,x+1,y+1,pitchX,pitchY); add(result,new RectF(cell.right,cell.bottom,diagonal.left,diagonal.top));
            }
        }
        return result;
    }
    private static void add(List<RectF> list,RectF rect) { if(!rect.isEmpty()) list.add(rect); }
}
