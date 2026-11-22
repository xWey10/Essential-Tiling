package moe.xWey10.tiling;

import android.graphics.*;
import java.util.*;

/** Missing connections between adjacent arms of one tile; the stem stays connected. */
final class TileSeams {
    record Joint(int x,int y,boolean horizontal) {
        String key() { return x+","+y+","+(horizontal?"h":"v"); }
    }
    static List<Joint> list(String text) {
        ArrayList<Joint> out=new ArrayList<>(); if(text.isEmpty()) return out;
        for(String entry:text.split(";")) { String[] a=entry.split(","); if(a.length!=3) throw new IllegalArgumentException("Некорректное соединение"); out.add(new Joint(Integer.parseInt(a[0]),Integer.parseInt(a[1]),switch(a[2]) { case "h"->true; case "v"->false; default->throw new IllegalArgumentException("Некорректное соединение"); })); }
        return out;
    }
    static boolean has(String text,int x,int y,boolean h) { if(text.isEmpty()) return false; return (";"+text+";").contains(";"+new Joint(x,y,h).key()+";"); }
    static String encode(Collection<Joint> joints) { TreeSet<String> keys=new TreeSet<>(); for(Joint j:joints) keys.add(j.key()); return String.join(";",keys); }
    static String remove(String text,Joint joint) { var all=list(text); all.remove(joint); return encode(all); }
    static boolean valid(TileForm f,int w,int h) {
        if(f.seams().length()>8192) return false;
        try { List<Joint> all=list(f.seams()); if(new HashSet<>(all).size()!=all.size()) return false;
            for(Joint j:all) if(f.diagonal()||!f.baseOccupied(j.x,j.y,w,h)||!f.baseOccupied(j.x+(j.horizontal?1:0),j.y+(j.horizontal?0:1),w,h)) return false;
            return true;
        } catch(IllegalArgumentException e) { return false; }
    }
    static TileForm legacy(TileForm form,int w,int h) {
        if(!form.cellular()||!form.seams().isEmpty()) return form;
        String[] parts=form.body().split(","); if(parts.length!=4) return form;
        try {
            int bx=Integer.parseInt(parts[0]),by=Integer.parseInt(parts[1]),bw=Integer.parseInt(parts[2]),bh=Integer.parseInt(parts[3]);
            ArrayList<Joint> joints=new ArrayList<>();
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(form.baseOccupied(x,y,w,h)) {
                if((y<by||y>=by+bh)&&form.baseOccupied(x+1,y,w,h)) joints.add(new Joint(x,y,true));
                if((x<bx||x>=bx+bw)&&form.baseOccupied(x,y+1,w,h)) joints.add(new Joint(x,y,false));
            }
            return form.seamed(encode(joints));
        } catch(NumberFormatException e) { return form; }
    }
    static RectF gap(TileForm f,Joint j,RectF b,int w,int h,float px,float py) {
        RectF a=TileCells.cell(b,w,h,j.x,j.y,px,py),n=TileCells.cell(b,w,h,j.x+(j.horizontal?1:0),j.y+(j.horizontal?0:1),px,py);
        return j.horizontal?new RectF(a.right,a.top,n.left,a.bottom):new RectF(a.left,a.bottom,a.right,n.top);
    }
    static Path drop(TileForm from,TileForm to,RectF b,int w,int h,float px,float py,float radius,float progress) {
        Path start=from.path(b,w,h,px,py,radius),end=to.path(b,w,h,px,py,radius); if(progress>=1) return end;
        Path added=new Path(end); added.op(start,Path.Op.DIFFERENCE); RectF box=new RectF(); added.computeBounds(box,true);
        Path reveal=new Path(); float r=(float)Math.hypot(box.width(),box.height())*.51f*progress;
        reveal.addCircle(box.centerX(),box.centerY(),r,Path.Direction.CW); added.op(reveal,Path.Op.INTERSECT); start.op(added,Path.Op.UNION); return start;
    }
}
