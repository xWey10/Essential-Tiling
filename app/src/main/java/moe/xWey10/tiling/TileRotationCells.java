package moe.xWey10.tiling;

/** Pure math occupancy for rotated shapes; also runs in the plain JVM layout validator. */
final class TileRotationCells {
    static boolean occupied(TileForm f,int x,int y,int w,int h) {
        int bw=f.columns(w),bh=f.rows(h); double a=Math.toRadians(f.rotation()),c=Math.cos(a),s=Math.sin(a);
        if(f.diagonal()) {
            double ax=.5,ay=f.corner()==6?bh-.5:.5,bx=bw-.5,by=f.corner()==6?.5:bh-.5;
            double[] p=map(ax,ay,bw,bh,w,h,c,s),q=map(bx,by,bw,bh,w,h,c,s);
            return segmentCell(p[0],p[1],q[0],q[1],.5,x,y);
        }
        for(int yy=0;yy<bh;yy++) for(int xx=0;xx<bw;xx++) if(f.baseOccupied(xx,yy,bw,bh)) {
            double[][] p={map(xx,yy,bw,bh,w,h,c,s),map(xx+1,yy,bw,bh,w,h,c,s),map(xx+1,yy+1,bw,bh,w,h,c,s),map(xx,yy+1,bw,bh,w,h,c,s)};
            if(polygons(p,new double[][]{{x,y},{x+1,y},{x+1,y+1},{x,y+1}})) return true;
        }
        return false;
    }
    private static double[] map(double x,double y,int bw,int bh,int w,int h,double c,double s) { x-=bw/2.0;y-=bh/2.0;return new double[]{w/2.0+c*x-s*y,h/2.0+s*x+c*y}; }
    private static boolean polygons(double[][] a,double[][] b) {
        for(double[][] polygon:new double[][][]{a,b}) for(int n=0;n<polygon.length;n++) {
            double[] p=polygon[n],q=polygon[(n+1)%polygon.length];double ax=p[1]-q[1],ay=q[0]-p[0],amin=Double.MAX_VALUE,amax=-amin,bmin=amin,bmax=-amin;
            for(double[] v:a) { double k=v[0]*ax+v[1]*ay;amin=Math.min(amin,k);amax=Math.max(amax,k); } for(double[] v:b) { double k=v[0]*ax+v[1]*ay;bmin=Math.min(bmin,k);bmax=Math.max(bmax,k); }
            if(amax<=bmin+1e-8||bmax<=amin+1e-8) return false;
        } return true;
    }
    private static boolean segmentCell(double ax,double ay,double bx,double by,double radius,int x,int y) {
        // Rounded end caps are conservatively included by a convex 24-sided capsule.
        double angle=Math.atan2(by-ay,bx-ax);double[][] p=new double[26][2];
        for(int n=0;n<=12;n++) { double a=angle-Math.PI/2+Math.PI*n/12;p[n]=new double[]{bx+radius*Math.cos(a),by+radius*Math.sin(a)}; }
        for(int n=0;n<=12;n++) { double a=angle+Math.PI/2+Math.PI*n/12;p[13+n]=new double[]{ax+radius*Math.cos(a),ay+radius*Math.sin(a)}; }
        return polygons(p,new double[][]{{x,y},{x+1,y},{x+1,y+1},{x,y+1}});
    }
}
