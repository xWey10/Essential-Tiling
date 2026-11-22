package moe.xWey10.tiling;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/** A branch grows normal to one exposed cell edge, leaving every other cell and the body in place. */
final class TileExtrusion {
    record Handle(int x,int y,int edge) { } // Logical L/T/R/B = 1/2/4/8.
    static List<Handle> handles(QsLayout.Item item) {
        ArrayList<Handle> result=new ArrayList<>(); TileForm f=item.form(); int w=f.columns(item.w()),h=f.rows(item.h());
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(f.baseOccupied(x,y,w,h)) {
            if(!f.baseOccupied(x-1,y,w,h)) result.add(new Handle(x,y,1));
            if(!f.baseOccupied(x,y-1,w,h)) result.add(new Handle(x,y,2));
            if(!f.baseOccupied(x+1,y,w,h)) result.add(new Handle(x,y,4));
            if(!f.baseOccupied(x,y+1,w,h)) result.add(new Handle(x,y,8));
        }
        return result;
    }
    static int maximum(QsLayout.Item i,QsLayout.Page p,Handle handle) {
        return switch(handle.edge) { case 1->i.x()+handle.x; case 2->i.y()+handle.y; case 4->p.columns()-i.x()-handle.x-1; default->p.rows()-i.y()-handle.y-1; };
    }
    static QsLayout.Item grow(QsLayout.Item i,Handle handle,int count) {
        if(count<0) return trim(i,handle,-count);
        if(count==0) return i;
        int dx=handle.edge==1?-1:handle.edge==4?1:0,dy=handle.edge==2?-1:handle.edge==8?1:0;
        int endX=handle.x+dx*count,endY=handle.y+dy*count;
        int left=Math.min(0,endX),top=Math.min(0,endY),w=Math.max(i.w(),endX+1)-left,h=Math.max(i.h(),endY+1)-top;
        boolean[][] rows=new boolean[h][w];
        for(int y=0;y<i.h();y++) for(int x=0;x<i.w();x++) rows[y-top][x-left]=i.form().baseOccupied(x,y,i.w(),i.h());
        for(int n=1;n<=count;n++) rows[handle.y+dy*n-top][handle.x+dx*n-left]=true;
        RectF body=i.form().content(new RectF(0,0,i.w(),i.h()),i.w(),i.h());
        java.util.ArrayList<TileSeams.Joint> seams=new java.util.ArrayList<>();
        for(TileSeams.Joint j:TileSeams.list(i.form().seams())) seams.add(new TileSeams.Joint(j.x()-left,j.y()-top,j.horizontal()));
        for(int n=1;n<=count;n++) {
            int x=handle.x+dx*n,y=handle.y+dy*n;
            for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                int nx=x+d[0],ny=y+d[1]; if(!i.form().baseOccupied(nx,ny,i.w(),i.h())||nx==handle.x&&ny==handle.y) continue;
                seams.add(new TileSeams.Joint(Math.min(x,nx)-left,Math.min(y,ny)-top,d[0]!=0));
            }
        }
        TileForm f=TileForm.of(rows,Math.round(body.left)-left,Math.round(body.top)-top,Math.round(body.width()),Math.round(body.height()));
        if(!seams.isEmpty()&&!f.cellular()) { StringBuilder bits=new StringBuilder(); for(boolean[] row:rows) for(boolean value:row) bits.append(value?'1':'0'); f=new TileForm(0,0,0,bits.toString(),Math.round(body.left-left)+","+Math.round(body.top-top)+","+Math.round(body.width())+","+Math.round(body.height())); }
        f=f.attributes(i.form()).seamed(TileSeams.encode(seams));
        return new QsLayout.Item(i.id(),i.title(),i.page(),i.x()+left,i.y()+top,w,h,i.editable(),i.tall(),f);
    }
    static int minimum(QsLayout.Item item,Handle handle) {
        QsLayout.Item base=TileFrame.intrinsic(item); int count=0;
        for(int n=1;n<=Math.max(base.w(),base.h());n++) { if(trim(base,handle,n)==null) break; count=n; } return count;
    }
    private static QsLayout.Item trim(QsLayout.Item item,Handle handle,int count) {
        int w=item.w(),h=item.h(),dx=handle.edge==1?1:handle.edge==4?-1:0,dy=handle.edge==2?1:handle.edge==8?-1:0;
        RectF body=item.form().content(new RectF(0,0,w,h),w,h); boolean[][] rows=new boolean[h][w];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) rows[y][x]=item.form().baseOccupied(x,y,w,h);
        for(int n=0;n<count;n++) { int x=handle.x+dx*n,y=handle.y+dy*n;
            if(x<0||x>=w||y<0||y>=h||!rows[y][x]||body.contains(x+.5f,y+.5f)) return null; rows[y][x]=false;
        }
        int l=w,t=h,r=0,b=0; for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(rows[y][x]) { l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x+1);b=Math.max(b,y+1); }
        if(r<=l||b<=t) return null; boolean[][] cropped=new boolean[b-t][r-l]; for(int y=t;y<b;y++) System.arraycopy(rows[y],l,cropped[y-t],0,r-l);
        java.util.ArrayList<TileSeams.Joint> seams=new java.util.ArrayList<>();
        for(TileSeams.Joint j:TileSeams.list(item.form().seams())) { int nx=j.x()+(j.horizontal()?1:0),ny=j.y()+(j.horizontal()?0:1); if(rows[j.y()][j.x()]&&rows[ny][nx]) seams.add(new TileSeams.Joint(j.x()-l,j.y()-t,j.horizontal())); }
        TileForm f=TileForm.of(cropped,Math.round(body.left)-l,Math.round(body.top)-t,Math.round(body.width()),Math.round(body.height()));
        if(!seams.isEmpty()&&!f.cellular()) { StringBuilder bits=new StringBuilder(); for(boolean[] row:cropped) for(boolean v:row) bits.append(v?'1':'0'); f=new TileForm(0,0,0,bits.toString(),Math.round(body.left-l)+","+Math.round(body.top-t)+","+Math.round(body.width())+","+Math.round(body.height())); }
        f=f.attributes(item.form()).seamed(TileSeams.encode(seams)); if(!f.valid(r-l,b-t)) return null;
        return new QsLayout.Item(item.id(),item.title(),item.page(),item.x()+l,item.y()+t,r-l,b-t,item.editable(),item.tall(),f);
    }
    static RectF point(QsLayout.Item i,Handle handle,RectF b,boolean rtl) { return point(i,handle,b,rtl,b.width()/i.w(),b.height()/i.h()); }
    static RectF point(QsLayout.Item i,Handle handle,RectF b,boolean rtl,float pitchX,float pitchY) {
        RectF cell=cell(i,handle.x,handle.y,b,rtl,pitchX,pitchY);
        int edge=rtl?(handle.edge==1?4:handle.edge==4?1:handle.edge):handle.edge;
        float x=edge==1?cell.left:edge==4?cell.right:cell.centerX(),y=edge==2?cell.top:edge==8?cell.bottom:cell.centerY();
        return TileRotation.point(TileRotation.extra(i.form(),b,i.w(),i.h(),pitchX,pitchY,rtl),x,y);
    }
    static List<RectF> rectangles(QsLayout.Item i,Handle handle,float count,RectF b,boolean rtl,float stepX,float stepY) {
        // Use cell pitch, including the native tile inset, so the original body never shifts during growth.
        ArrayList<RectF> result=new ArrayList<>(i.form().rectangles(b,i.w(),i.h(),stepX,stepY));
        if(rtl) for(RectF rect:result) rect.set(b.left+b.right-rect.right,rect.top,b.left+b.right-rect.left,rect.bottom);
        if(count<0) {
            float inward=-count; int dx=handle.edge==1?1:handle.edge==4?-1:0,dy=handle.edge==2?1:handle.edge==8?-1:0;
            for(int n=0;n<(int)Math.ceil(inward);n++) {
                int xx=handle.x+dx*n,yy=handle.y+dy*n; RectF selected=cell(i,xx,yy,b,rtl,stepX,stepY); float amount=Math.min(1,inward-n);
                int edge=rtl?(handle.edge==1?4:handle.edge==4?1:handle.edge):handle.edge;
                float gapX=Math.max(0,i.w()*stepX-b.width()),gapY=Math.max(0,i.h()*stepY-b.height());
                RectF remove=new RectF(selected);
                switch(edge) {
                    case 1->{ remove.right=selected.left+stepX*amount;remove.left-=gapX; }
                    case 4->{ remove.left=selected.right-stepX*amount;remove.right+=gapX; }
                    case 2->{ remove.bottom=selected.top+stepY*amount;remove.top-=gapY; }
                    default->{ remove.top=selected.bottom-stepY*amount;remove.bottom+=gapY; }
                }
                result=subtract(result,remove);
            }
        }
        if(count>0) {
            RectF cell=cell(i,handle.x,handle.y,b,rtl,stepX,stepY);
            int physical=rtl?(handle.edge==1?4:handle.edge==4?1:handle.edge):handle.edge;
            RectF extension=switch(physical) {
                case 1->new RectF(cell.left-count*stepX,cell.top,cell.left,cell.bottom);
                case 4->new RectF(cell.right,cell.top,cell.right+count*stepX,cell.bottom);
                case 2->new RectF(cell.left,cell.top-count*stepY,cell.right,cell.top);
                default->new RectF(cell.left,cell.bottom,cell.right,cell.bottom+count*stepY);
            };
            result.add(extension);
        }
        return result;
    }
    private static ArrayList<RectF> subtract(List<RectF> source,RectF cut) {
        ArrayList<RectF> out=new ArrayList<>(); for(RectF r:source) {
            if(!RectF.intersects(r,cut)) { out.add(r);continue; }
            add(out,new RectF(r.left,r.top,Math.min(r.right,cut.left),r.bottom)); add(out,new RectF(Math.max(r.left,cut.right),r.top,r.right,r.bottom));
            float l=Math.max(r.left,cut.left),right=Math.min(r.right,cut.right); add(out,new RectF(l,r.top,right,Math.min(r.bottom,cut.top))); add(out,new RectF(l,Math.max(r.top,cut.bottom),right,r.bottom));
        } return out;
    }
    private static void add(List<RectF> list,RectF r) { if(!r.isEmpty()) list.add(r); }
    private static RectF cell(QsLayout.Item i,int x,int y,RectF b,boolean rtl,float pitchX,float pitchY) {
        RectF cell=TileCells.cell(i.form().intrinsic(b,i.w(),i.h(),pitchX,pitchY),i.form().columns(i.w()),i.form().rows(i.h()),x,y,pitchX,pitchY);
        if(rtl) cell.set(b.left+b.right-cell.right,cell.top,b.left+b.right-cell.left,cell.bottom);
        return cell;
    }
}
