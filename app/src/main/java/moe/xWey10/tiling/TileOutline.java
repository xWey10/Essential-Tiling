package moe.xWey10.tiling;

import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/** Rounded contour of a union of rectangles; shared by live extrusion and saved cell shapes. */
final class TileOutline {
    private record Edge(int x,int y,int xx,int yy,int dir) { }
    static Path path(List<RectF> rectangles,float radius) {
        Path path=new Path(); if(rectangles.isEmpty()) return path;
        TreeSet<Float> xs=new TreeSet<>(),ys=new TreeSet<>();
        for(RectF r:rectangles) if(!r.isEmpty()) { xs.add(r.left); xs.add(r.right); ys.add(r.top); ys.add(r.bottom); }
        Float[] xx=xs.toArray(new Float[0]),yy=ys.toArray(new Float[0]);
        int w=xx.length-1,h=yy.length-1; if(w<1||h<1) return path;
        boolean[][] cells=new boolean[h][w];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) for(RectF r:rectangles)
            if(r.contains((xx[x]+xx[x+1])/2,(yy[y]+yy[y+1])/2)) { cells[y][x]=true; break; }
        HashMap<Integer,ArrayList<Edge>> edges=new HashMap<>();
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(cells[y][x]) {
            if(y==0||!cells[y-1][x]) add(edges,w,new Edge(x,y,x+1,y,0));
            if(x==w-1||!cells[y][x+1]) add(edges,w,new Edge(x+1,y,x+1,y+1,1));
            if(y==h-1||!cells[y+1][x]) add(edges,w,new Edge(x+1,y+1,x,y+1,2));
            if(x==0||!cells[y][x-1]) add(edges,w,new Edge(x,y+1,x,y,3));
        }
        while(!edges.isEmpty()) {
            Edge start=edges.values().iterator().next().get(0),edge=start;
            ArrayList<float[]> points=new ArrayList<>();
            do {
                points.add(new float[]{xx[edge.x],yy[edge.y]});
                int key=edge.y*(w+1)+edge.x; ArrayList<Edge> list=edges.get(key); list.remove(edge); if(list.isEmpty()) edges.remove(key);
                if(edge.xx==start.x&&edge.yy==start.y) break;
                ArrayList<Edge> next=edges.get(edge.yy*(w+1)+edge.xx);
                if(next==null) throw new IllegalStateException("Open tile contour");
                Edge best=next.get(0); int score=5;
                for(Edge candidate:next) { int turn=(candidate.dir-edge.dir+4)%4,rank=turn==1?0:turn==0?1:turn==3?2:3;
                    if(rank<score) { score=rank; best=candidate; }
                }
                edge=best;
            } while(true);
            // Remove collinear grid vertices before rounding, so long edges have no artificial seams.
            boolean changed=true;
            while(changed&&points.size()>3) { changed=false;
                for(int n=0;n<points.size();n++) { float[] a=points.get((n+points.size()-1)%points.size()),b=points.get(n),c=points.get((n+1)%points.size());
                    if(a[0]==b[0]&&b[0]==c[0]||a[1]==b[1]&&b[1]==c[1]) { points.remove(n); changed=true; break; }
                }
            }
            rounded(path,points,radius);
            path.close();
        }
        return path;
    }
    static void rounded(Path path,List<float[]> points,float radius) {
        int count=points.size(); float[] radii=new float[count];
        for(int n=0;n<count;n++) {
            float[] a=points.get((n+count-1)%count),b=points.get(n),c=points.get((n+1)%count);
            float cross=(b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]); radii[n]=cross>0?radius:0;
        }
        for(int n=0;n<count;n++) {
            int next=(n+1)%count; float[] a=points.get(n),b=points.get(next);
            float length=(float)Math.hypot(a[0]-b[0],a[1]-b[1]),sum=radii[n]+radii[next];
            if(sum>length&&sum>0) { float factor=length/sum; radii[n]*=factor; radii[next]*=factor; }
        }
        for(int n=0;n<count;n++) {
            float[] a=points.get((n+count-1)%count),b=points.get(n),c=points.get((n+1)%count);
            float ab=(float)Math.hypot(a[0]-b[0],a[1]-b[1]),bc=(float)Math.hypot(c[0]-b[0],c[1]-b[1]),k=radii[n];
            float x=b[0]+(a[0]-b[0])*k/Math.max(.001f,ab),y=b[1]+(a[1]-b[1])*k/Math.max(.001f,ab);
            if(n==0) path.moveTo(x,y); else path.lineTo(x,y);
            float nx=b[0]+(c[0]-b[0])*k/Math.max(.001f,bc),ny=b[1]+(c[1]-b[1])*k/Math.max(.001f,bc);
            path.cubicTo(x+(b[0]-x)*.55228475f,y+(b[1]-y)*.55228475f,nx+(b[0]-nx)*.55228475f,ny+(b[1]-ny)*.55228475f,nx,ny);
        }
    }
    private static void add(HashMap<Integer,ArrayList<Edge>> edges,int width,Edge edge) {
        edges.computeIfAbsent(edge.y*(width+1)+edge.x,key->new ArrayList<>()).add(edge);
    }
}
