package moe.xWey10.tiling;

import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.List;

/** Native GridOccupancy stores only the occupied cells of the saved shape. */
@android.annotation.SuppressLint("PrivateApi") // Audited OEM launcher APIs, executed inside this firmware's SystemUI.
final class NativeOccupancyHooks {
    static void install(ClassLoader loader,List<XC_MethodHook.Unhook> installed) throws Exception {
        Class<?> cell=Class.forName("com.android.launcher3.CellLayout",false,loader);
        Class<?> occupancy=Class.forName("com.android.launcher3.util.GridOccupancy",false,loader);
        Class<?> info=Class.forName("com.android.launcher3.ItemInfo",false,loader);
        // Resolve the data fields before registering any hook; caller rolls back the whole batch.
        NativeGrid.field(cell,"mOccupied"); NativeGrid.field(occupancy,"cells");
        XC_MethodHook viewMarker=new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                View view=(View)p.args[0]; QsLayout.Item item=position(view);
                if(item==null||!NativeForms.hasCuts()) return;
                ViewGroup container=(ViewGroup)NativeGrid.call(p.thisObject,"getShortcutsAndWidgets");
                if(view.getParent()!=container) return;
                Object grid=NativeGrid.field(cell,"mOccupied").get(p.thisObject);
                if(p.method.getName().equals("markCellsAsOccupiedForView")) mark(grid,item,true);
                else { boolean[][] bits=(boolean[][])NativeGrid.field(grid.getClass(),"cells").get(grid); for(boolean[] column:bits) java.util.Arrays.fill(column,false); for(int n=0;n<container.getChildCount();n++) { View other=container.getChildAt(n); if(other!=view) { QsLayout.Item value=position(other); if(value!=null) mark(grid,value,true); } } }
                p.setResult(null);
            }
        };
        hook(installed,cell,"markCellsAsOccupiedForView",viewMarker,View.class);
        hook(installed,cell,"markCellsAsUnoccupiedForView",viewMarker,View.class);
        hook(installed,occupancy,"markCells",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                Object value=p.args[0];
                QsLayout.Item item=fromInfo(value); if(item==null||!NativeForms.hasCuts()) return;
                mark(p.thisObject,item,(Boolean)p.args[1]); p.setResult(null);
            }
        },info,boolean.class);
        Class<?> container=Class.forName("com.android.launcher3.ShortcutAndWidgetContainer",false,loader);
        hook(installed,container,"getChildAt",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                ViewGroup group=(ViewGroup)p.thisObject; int x=(Integer)p.args[0],y=(Integer)p.args[1];
                boolean custom=false; for(int n=0;n<group.getChildCount();n++) { QsLayout.Item i=position(group.getChildAt(n)); if(i!=null&&i.form().cut()) { custom=true; break; } }
                if(!custom) return;
                View best=null; QsLayout.Item winner=null;
                for(int n=group.getChildCount()-1;n>=0;n--) {
                    View view=group.getChildAt(n); if(view.getVisibility()==View.GONE) continue;
                    QsLayout.Item i=position(view); if(i!=null&&i.occupies(x,y)&&(winner==null||TileLayers.above(i,winner))) { best=view; winner=i; }
                }
                p.setResult(best);
            }
        },int.class,int.class);
        hook(installed,cell,"commitTempPlacement",new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if(p.hasThrowable()||!NativeForms.hasCuts()) return;
                ViewGroup group=(ViewGroup)NativeGrid.call(p.thisObject,"getShortcutsAndWidgets");
                rebuild(p.thisObject,group);
            }
        });
    }
    static QsLayout.Item position(View view) {
        if(view==null||view.getLayoutParams()==null) return null;
        try {
            Object lp=view.getLayoutParams(); int x=NativeGrid.integer(lp,"cellX"),y=NativeGrid.integer(lp,"cellY"),w=NativeGrid.integer(lp,"cellHSpan"),h=NativeGrid.integer(lp,"cellVSpan");
            QsLayout.Item nativeItem=NativeForms.item(view);
            return nativeItem==null?new QsLayout.Item("widget","",0,x,y,w,h,false,false):new QsLayout.Item(nativeItem.id(),"",0,x,y,w,h,true,nativeItem.tall(),nativeItem.form());
        } catch(ReflectiveOperationException e) { return null; }
    }
    private static QsLayout.Item fromInfo(Object info) {
        try {
            String id="t:"+NativeGrid.call(info,"getTileSpec"); int w=NativeGrid.integer(info,"spanX"),h=NativeGrid.integer(info,"spanY");
            return new QsLayout.Item(id,"",0,NativeGrid.integer(info,"cellX"),NativeGrid.integer(info,"cellY"),w,h,true,true,NativeForms.form(id,w,h));
        } catch(ReflectiveOperationException e) { return null; }
    }
    static void mark(Object grid,QsLayout.Item item,boolean value) throws ReflectiveOperationException {
        boolean[][] cells=(boolean[][])NativeGrid.field(grid.getClass(),"cells").get(grid);
        for(int y=0;y<item.h();y++) for(int x=0;x<item.w();x++) {
            int col=item.x()+x,row=item.y()+y;
            if(col>=0&&col<cells.length&&row>=0&&row<cells[col].length&&item.form().occupied(x,y,item.w(),item.h())) cells[col][row]=value;
        }
    }
    static void refresh(View tile) {
        try {
            if(!(tile.getParent() instanceof ViewGroup container)||!(container.getParent() instanceof View cell)) return;
            rebuild(cell,container);
        } catch(ReflectiveOperationException ignored) { }
    }
    private static void rebuild(Object cell,ViewGroup container) throws ReflectiveOperationException {
        Object grid=NativeGrid.field(cell.getClass(),"mOccupied").get(cell);
        boolean[][] cells=(boolean[][])NativeGrid.field(grid.getClass(),"cells").get(grid);
        for(boolean[] column:cells) java.util.Arrays.fill(column,false);
        for(int n=0;n<container.getChildCount();n++) { QsLayout.Item item=position(container.getChildAt(n)); if(item!=null) mark(grid,item,true); }
    }
    private static void hook(List<XC_MethodHook.Unhook> installed,Class<?> type,String name,XC_MethodHook callback,Class<?>... args) throws Exception {
        try { Method method=type.getDeclaredMethod(name,args); method.setAccessible(true); installed.add(XposedBridge.hookMethod(method,callback)); }
        catch(Exception|LinkageError e) { throw new IllegalStateException(type.getName()+"#"+name+": "+NativeFormHooks.reason(e),e); }
    }
}
