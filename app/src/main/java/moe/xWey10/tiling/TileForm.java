package moe.xWey10.tiling;

import android.graphics.Path;
import android.graphics.RectF;

/** Cell shape with a native content area. Legacy corner cuts remain readable. */
record TileForm(int corner, int cutW, int cutH, String cells, String body,boolean nubs,float rotation,String seams,int layer,int baseW,int baseH) {
    TileForm(int corner,int cutW,int cutH,String cells,String body,boolean nubs,float rotation,String seams,int layer) { this(corner,cutW,cutH,cells,body,nubs,rotation,seams,layer,0,0); }
    int columns(int span) { return baseW>0?baseW:span; }
    int rows(int span) { return baseH>0?baseH:span; }
    TileForm base(int w,int h) { return new TileForm(corner,cutW,cutH,cells,body,nubs,rotation,seams,layer,w,h); }
    RectF intrinsic(RectF b,int w,int h,float px,float py) { float width=b.width()+(columns(w)-w)*px,height=b.height()+(rows(h)-h)*py; return new RectF(b.centerX()-width/2,b.centerY()-height/2,b.centerX()+width/2,b.centerY()+height/2); }
    TileForm(int corner,int cutW,int cutH,String cells,String body,boolean nubs) { this(corner,cutW,cutH,cells,body,nubs,0,"",0); }
    TileForm rotated(float angle) { angle=((angle+180)%360+360)%360-180; return new TileForm(corner,cutW,cutH,cells,body,nubs,angle,seams,layer,baseW,baseH); }
    TileForm seamed(String value) { return new TileForm(corner,cutW,cutH,cells,body,nubs,rotation,value,layer,baseW,baseH); }
    TileForm layered(int value) { return new TileForm(corner,cutW,cutH,cells,body,nubs,rotation,seams,value,baseW,baseH); }
    TileForm attributes(TileForm from) { return nubbed(from.nubs).rotated(from.rotation).layered(from.layer); }
    boolean transformed() { return diagonal()||rotation!=0; }
    boolean split(int x,int y,boolean horizontal) { return TileSeams.has(seams,x,y,horizontal); }
    TileForm joined(TileSeams.Joint j) { return seamed(TileSeams.remove(seams,j)); }
    TileForm(int corner,int cutW,int cutH,String cells,String body) { this(corner,cutW,cutH,cells,body,false); }
    boolean diagonal() { return corner==5||corner==6; }
    TileForm nubbed(boolean value) { return new TileForm(corner,cutW,cutH,cells,body,value,rotation,seams,layer,baseW,baseH); }
    static TileForm diagonal(boolean rising,boolean nubs) { return new TileForm(rising?6:5,0,0,"","",nubs); }
    boolean geometry() { return diagonal()||cellular()||corner>0&&corner<=4&&cutW>0&&cutH>0; }
    TileForm(int corner,int cutW,int cutH) { this(corner,cutW,cutH,"",""); }
    boolean cellular() { return !cells.isEmpty(); }
    boolean occupied(int x,int y,int w,int h) {
        if(x<0||y<0||x>=w||y>=h) return false;
        if(rotation!=0||baseW>0) return TileRotationCells.occupied(this,x,y,w,h);
        return baseOccupied(x,y,w,h);
    }
    boolean baseOccupied(int x,int y,int w,int h) {
        if(x<0||y<0||x>=w||y>=h) return false;
        if(diagonal()) return DiagonalTile.occupied(x,y,w,h,corner==6);
        if(cellular()) return cells.length()==w*h && cells.charAt(y*w+x)=='1';
        return !geometry() || !( (left()?x<cutW:x>=w-cutW) && (top()?y<cutH:y>=h-cutH));
    }
    java.util.List<RectF> rectangles(RectF b,int w,int h) { return rectangles(b,w,h,b.width()/w,b.height()/h); }
    java.util.List<RectF> rectangles(RectF b,int w,int h,float pitchX,float pitchY) {
        return TileCells.rectangles(this,intrinsic(b,w,h,pitchX,pitchY),columns(w),rows(h),pitchX,pitchY);
    }
    static TileForm of(boolean[][] rows,int bx,int by,int bw,int bh) {
        int h=rows.length,w=rows[0].length; StringBuilder bits=new StringBuilder(); boolean full=true;
        for(boolean[] row:rows) for(boolean cell:row) { bits.append(cell?'1':'0'); full&=cell; }
        if(full) return RECT;
        return new TileForm(0,0,0,bits.toString(),bx+","+by+","+bw+","+bh);
    }
    private int[] contentCells(int w,int h) {
        try { String[] a=body.split(","); if(a.length==4) return new int[]{Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3])}; }
        catch(NumberFormatException ignored) { }
        return new int[]{0,0,w,h};
    }
    private boolean validCells(int w,int h) {
        if(w<1||h<1||w>24||h>24||cells.length()!=w*h||body.length()>20) return false;
        int count=0,first=-1; boolean l=false,t=false,r=false,b=false;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            char c=cells.charAt(y*w+x); if(c!='0'&&c!='1') return false;
            if(c=='1') { count++; first=y*w+x; l|=x==0; r|=x==w-1; t|=y==0; b|=y==h-1; }
        }
        if(!l||!r||!t||!b) return false;
        java.util.HashSet<Integer> seen=new java.util.HashSet<>(); java.util.ArrayDeque<Integer> queue=new java.util.ArrayDeque<>(); queue.add(first); seen.add(first);
        while(!queue.isEmpty()) { int n=queue.remove(),x=n%w,y=n/w;
            for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) { int xx=x+d[0],yy=y+d[1],nn=yy*w+xx;
                if(baseOccupied(xx,yy,w,h)&&seen.add(nn)) queue.add(nn);
            }
        }
        if(seen.size()!=count) return false;
        int[] area=contentCells(w,h); if(area[0]<0||area[1]<0||area[2]<1||area[3]<1||area[0]+area[2]>w||area[1]+area[3]>h) return false;
        for(int y=area[1];y<area[1]+area[3];y++) for(int x=area[0];x<area[0]+area[2];x++) if(!baseOccupied(x,y,w,h)) return false;
        return true;
    }

    static final TileForm RECT = new TileForm(0, 0, 0);
    boolean cut() { return nubs||geometry()||rotation!=0||baseW>0||!seams.isEmpty(); }
    boolean stored() { return cut()||layer!=0; }
    boolean valid(int w, int h) { if(baseW<0||baseH<0||baseW>24||baseH>24||(baseW==0)!=(baseH==0)) return false; w=columns(w); h=rows(h); if(!Float.isFinite(rotation)||Math.abs(rotation)>180||Math.abs((long)layer)>10000||!TileSeams.valid(this,w,h)) return false; if(diagonal()) return w>=1&&h>=1&&w<=24&&h<=24&&w*h>1&&cells.isEmpty(); return cellular() ? validCells(w,h) : corner==0&&cutW==0&&cutH==0 || corner >= 1 && corner <= 4 && cutW > 0 && cutH > 0 && cutW < w && cutH < h; }
    TileForm fit(int w, int h) { if(baseW>0) return this; if(!geometry()) return this; if(diagonal()) return w*h>1?this:RECT.attributes(this); if(cellular()) return cells.length()==w*h && valid(w,h)?this:RECT.attributes(this); return !cut() || w < 2 || h < 2 ? RECT : new TileForm(corner, Math.min(cutW, w - 1), Math.min(cutH, h - 1)).attributes(this); }
    boolean left() { return corner == 1 || corner == 4; }
    boolean top() { return corner == 1 || corner == 2; }
    RectF notch(RectF b, float w, float h) { return notch(b, w, h, cutW, cutH); }
    RectF notch(RectF b, float w, float h, float cw, float ch) {
        float x = b.width() * cw / w, y = b.height() * ch / h;
        return new RectF(left() ? b.left : b.right - x, top() ? b.top : b.bottom - y,
                left() ? b.left + x : b.right, top() ? b.top + y : b.bottom);
    }
    RectF content(RectF b,int w,int h) { return content(b,w,h,b.width()/w,b.height()/h); }
    RectF content(RectF b,int w,int h,float pitchX,float pitchY) {
        b=intrinsic(b,w,h,pitchX,pitchY); w=columns(w); h=rows(h);
        if(diagonal()) return DiagonalTile.geometry(b,w,h,pitchX,pitchY,corner==6).body();
        if(!geometry()) return new RectF(b);
        RectF cells;
        if(cellular()) { int[] c=contentCells(w,h); cells=new RectF(c[0],c[1],c[0]+c[2],c[1]+c[3]); }
        else cells=new RectF(0,top()?cutH:0,w,top()?h:h-cutH);
        return TileCells.area(b,w,h,cells,pitchX,pitchY);
    }
    Path path(RectF b,int w,int h,float pitchX,float pitchY,float radius) {
        Path result=basePath(b,w,h,pitchX,pitchY,radius);
        if(rotation!=0) result.transform(TileRotation.extra(this,b,w,h,pitchX,pitchY));
        return result;
    }
    Path basePath(RectF b,int w,int h,float pitchX,float pitchY,float radius) {
        b=intrinsic(b,w,h,pitchX,pitchY); w=columns(w); h=rows(h);
        return rawPath(b,w,h,pitchX,pitchY,radius);
    }
    Path rawPath(RectF b,int w,int h,float pitchX,float pitchY,float radius) {
        if(diagonal()) return DiagonalTile.path(DiagonalTile.geometry(b,w,h,pitchX,pitchY,corner==6),nubs);
        if(nubs) return TileNubs.path(this,b,w,h,pitchX,pitchY,radius);
        if(!geometry()&&!nubs) { Path path=new Path(); path.addRoundRect(b,radius,radius,Path.Direction.CW); return path; }
        float cellWidth=b.width()-(w-1)*pitchX,cellHeight=b.height()-(h-1)*pitchY;
        return TileOutline.path(TileCells.rectangles(this,b,w,h,pitchX,pitchY),Math.min(radius,Math.max(0,Math.min(cellWidth,cellHeight)/2)));
    }
    boolean contains(RectF b,int w,int h,float pitchX,float pitchY,float radius,float x,float y) {
        return contains(path(b,w,h,pitchX,pitchY,radius),b,x,y);
    }
    private static boolean contains(Path path,RectF b,float x,float y) {
        android.graphics.Region region=new android.graphics.Region();
        region.setPath(path,new android.graphics.Region((int)b.left,(int)b.top,(int)Math.ceil(b.right),(int)Math.ceil(b.bottom)));
        return region.contains((int)x,(int)y);
    }
    boolean contains(RectF bounds,int w,int h,float radius,float x,float y) {
        android.graphics.Region region=new android.graphics.Region();
        region.setPath(path(bounds,w,h,radius),new android.graphics.Region((int)bounds.left,(int)bounds.top,(int)Math.ceil(bounds.right),(int)Math.ceil(bounds.bottom)));
        return region.contains((int)x,(int)y);
    }
    Path path(RectF b, float w, float h, float radius) { if(diagonal()||nubs||rotation!=0||baseW>0||!seams.isEmpty()) return path(b,(int)w,(int)h,b.width()/w,b.height()/h,radius); return cellular()?TileOutline.path(rectangles(b,(int)w,(int)h),radius):path(b, w, h, cutW, cutH, radius); }
    Path path(RectF b, float w, float h, float cw, float ch, float radius) {
        Path path = new Path();
        if (corner == 0 || cw <= 0 || ch <= 0) { path.addRoundRect(b, radius, radius, Path.Direction.CW); return path; }
        RectF n = notch(b, w, h, cw, ch);
        float l=b.left,t=b.top,r=b.right,bt=b.bottom;
        float[][] points = switch(corner) {
            case 1 -> new float[][]{{n.right,t},{r,t},{r,bt},{l,bt},{l,n.bottom},{n.right,n.bottom}};
            case 2 -> new float[][]{{l,t},{n.left,t},{n.left,n.bottom},{r,n.bottom},{r,bt},{l,bt}};
            case 3 -> new float[][]{{l,t},{r,t},{r,n.top},{n.left,n.top},{n.left,bt},{l,bt}};
            default -> new float[][]{{l,t},{r,t},{r,bt},{n.right,bt},{n.right,n.top},{l,n.top}};
        };
        TileOutline.rounded(path,java.util.Arrays.asList(points),radius);
        path.close(); return path;
    }
}
