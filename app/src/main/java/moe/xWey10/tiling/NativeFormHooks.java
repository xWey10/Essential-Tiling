package moe.xWey10.tiling;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Shapes clip the existing View; its controller, listeners, ripple and content remain native. */
@android.annotation.SuppressLint({"PrivateApi","DiscouragedApi"}) // Firmware-specific OEM hooks/resources, verified against the supplied DEX.
final class NativeFormHooks {
    private static final String TILE="com.android.systemui.qs.tileimpl.QSTileViewImpl";
    private static final ArrayList<XC_MethodHook.Unhook> hooks=new ArrayList<>();
    private static final String BT="com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl";
    private static final String TRANSFORMER="com.android.launcher3.util.ItemInfoTransformer";
    private static final String LAUNCHER="com.android.launcher3.Launcher";
    private static final ThreadLocal<MotionEvent[]> mappedTouches=new ThreadLocal<>();
    private static final class TilePress {
        final float x, y;
        boolean rootPress, moved;

        TilePress(MotionEvent down) {
            x = down.getRawX();
            y = down.getRawY();
        }

        boolean pastSlop(MotionEvent event, int slop) {
            float dx = event.getRawX() - x, dy = event.getRawY() - y;
            return dx * dx + dy * dy > (float) slop * slop;
        }
    }
    private static Class<?> attemptedView;
    private static long attemptedAt;
    record Types(Class<?> tile,Class<?> bluetooth,Class<?> transformer,Class<?> launcher) { }
    private static XC_MethodHook.Unhook watcher;
    /** Android SDK entry point is available before SystemUI's secondary classes are visible. */
    static void watch(java.util.function.Consumer<View> onReady) throws Exception {
        if(watcher!=null) return;
        Method added=android.view.ViewGroup.class.getDeclaredMethod("onViewAdded",View.class);
        watcher=XposedBridge.hookMethod(added,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if(NativeForms.supported||p.hasThrowable()) return;
                View child=(View)p.args[0];
                if(ensure(child,child.getContext().getClassLoader())) onReady.accept(child);
            }
        });
    }
    static Class<?> tileType(View view) { return ancestor(view.getClass(),TILE); }
    static void install(ClassLoader loader) throws Exception { install(loader,null,null); }
    static void install(ClassLoader loader,Class<?> tile,Class<?> bluetooth) throws Exception {
        NativeForms.supported=false; NativeForms.failure="Инициализация форм";
        try {
            Class<?> base=tile==null?Class.forName(TILE,false,loader):tile;
            Types types=new Types(base,bluetooth==null?resolve(BT,loader,base.getClassLoader()):bluetooth,
                    resolve(TRANSFORMER,loader,base.getClassLoader()),resolve(LAUNCHER,loader,base.getClassLoader()));
            install(types);
        } catch(Exception|LinkageError e) { failed(e); throw e; }
    }
    /** Bind to the class objects of actual SystemUI views, not the early package class loader. */
    static boolean ensure(View view,ClassLoader fallback) {
        if(NativeForms.supported) return true;
        Class<?> base=ancestor(view.getClass(),TILE); if(base==null) return false;
        long now=android.os.SystemClock.uptimeMillis();
        if(attemptedView==view.getClass()&&now-attemptedAt<1000) return false;
        attemptedView=view.getClass(); attemptedAt=now;
        try {
            ClassLoader own=view.getClass().getClassLoader(),context=view.getContext().getClassLoader();
            Class<?> bt=ancestor(view.getClass(),BT);
            Types types=new Types(base,bt==null?resolve(BT,own,context,base.getClassLoader(),fallback):bt,
                    resolve(TRANSFORMER,own,context,base.getClassLoader(),fallback),resolve(LAUNCHER,own,context,base.getClassLoader(),fallback));
            install(types);
            // A late installation may miss the first resetQSTileInfos, so load the active profile now.
            Object launcher=types.launcher().getMethod("getLauncher",android.content.Context.class).invoke(null,view.getContext());
            if(launcher!=null) NativeForms.user(view.getContext(),NativeGrid.integer(launcher,"currentUserId"));
            return true;
        } catch(Exception|LinkageError e) {
            if(!NativeForms.supported) failed(e);
            android.util.Log.w("EssentialTiling","Bind forms to live SystemUI tile "+view.getClass().getName(),e);
            return NativeForms.supported;
        }
    }
    static boolean ensureTree(View view) {
        if(NativeForms.supported) return true;
        if(ancestor(view.getClass(),TILE)!=null&&ensure(view,view.getContext().getClassLoader())) return true;
        if(view instanceof android.view.ViewGroup group) for(int n=0;n<group.getChildCount();n++)
            if(ensureTree(group.getChildAt(n))) return true;
        return false;
    }
    private static Class<?> ancestor(Class<?> type,String name) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) if(c.getName().equals(name)) return c;
        return null;
    }
    private static Class<?> resolve(String name,ClassLoader... candidates) throws ClassNotFoundException {
        java.util.LinkedHashSet<ClassLoader> tried=new java.util.LinkedHashSet<>();
        for(ClassLoader loader:candidates) if(loader!=null&&tried.add(loader)) {
            try { return Class.forName(name,false,loader); } catch(ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException(name+" (загрузчики SystemUI и живой плитки)");
    }
    private static void install(Types types) throws Exception {
        NativeForms.supported=false;
        try { installAll(types); NativeForms.supported=true; NativeForms.failure=""; }
        catch(Exception|LinkageError e) { failed(e); throw e; }
    }
    private static void failed(Throwable e) {
        NativeForms.supported=false; NativeForms.failure=reason(e);
        for(var hook:hooks) try { hook.unhook(); } catch(RuntimeException ignored) { }
        hooks.clear();
    }
    private static void installAll(Types types) throws Exception {
        Class<?> tile=types.tile();
        NativeOccupancyHooks.install(types.launcher().getClassLoader(),hooks);
        XC_MethodHook size=new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                NativeForms.forget((View)p.thisObject);
                int w=(Integer)p.args[0],h=(Integer)p.args[1]; QsLayout.Item i=NativeForms.item((View)p.thisObject);
                boolean custom=i!=null&&NativeForms.custom(i) || !(w==1&&h==1 || w==2&&(h==1||h==2));
                if(custom) { TileContentSize contentSize=i==null?TileContentSize.of(w,h,true):TileContentSize.of(i);p.args[0]=contentSize.width();p.args[1]=contentSize.height(); }
            }
        };
        hook(tile,"applySize",size,int.class,int.class);
        hook(types.bluetooth(),"applySize",size,int.class,int.class);
        XC_MethodHook content=new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                View v=(View)p.thisObject; NativeForms.views.add(v); NativeForms.forget(v);
                QsLayout.Item i=NativeForms.item(v);
                if(i!=null&&NativeForms.custom(i)) {
                    int expected=TileContentSize.of(i).mode();
                    if(NativeGrid.integer(v,"mSize")!=expected) NativeGrid.call(v,"applySize",new Class<?>[]{int.class,int.class},i.w(),i.h());
                }
                if(i==null||!i.form().cut()) return;
                boolean measure=p.args.length==2;
                int width=measure?View.MeasureSpec.getSize((Integer)p.args[0]):(Integer)p.args[3]-(Integer)p.args[1];
                int height=measure?View.MeasureSpec.getSize((Integer)p.args[1]):(Integer)p.args[4]-(Integer)p.args[2];
                NativeFormContent.Box box=NativeFormContent.box(v,i,width,height);
                p.setObjectExtra("nqsFormBody",box);
                // Keep the root's specs and bounds intact: HWUI/backgrounds must see the full tile.
            }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                NativeFormContent.Box box=(NativeFormContent.Box)p.getObjectExtra("nqsFormBody");
                if(box==null||p.hasThrowable()) return;
                if(p.args.length==2) box.measure((View)p.thisObject); else box.position((View)p.thisObject);
            }
        };
        hook(tile,"onMeasure",content,int.class,int.class);
        hook(tile,"onLayout",content,boolean.class,int.class,int.class,int.class,int.class);
        hook(android.view.ViewGroup.class,"drawChild",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if(!tile.isInstance(p.thisObject)||!NativeForms.hasCuts()) return;
                View view=(View)p.thisObject; QsLayout.Item item=NativeForms.item(view);
                if(item==null||!item.form().transformed()) return;
                Canvas canvas=(Canvas)p.args[0]; p.setObjectExtra("nqsContentCanvas",canvas.save());
                float[] pitch=NativeFormContent.drawingPitch(view,item.w(),item.h());
                canvas.concat(TileRotation.content(item.form(),new RectF(0,0,view.getWidth(),view.getHeight()),item.w(),item.h(),pitch[0],pitch[1],view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL));
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Integer save=(Integer)p.getObjectExtra("nqsContentCanvas"); if(save!=null) ((Canvas)p.args[0]).restoreToCount(save);
            }
        },Canvas.class,View.class,long.class);
        hook(View.class,"draw",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if(!tile.isInstance(p.thisObject)||!NativeForms.hasCuts()) return; View v=(View)p.thisObject;
                QsLayout.Item i=NativeForms.item(v); if(i==null||v.getWidth()<1||v.getHeight()<1) return;
                Canvas c=(Canvas)p.args[0]; p.setObjectExtra("nqsFormCanvas",c.save());
                android.graphics.Path path=NativeLayerClip.visible(v,i);
                c.clipPath(path);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Integer save=(Integer)p.getObjectExtra("nqsFormCanvas"); if(save!=null) ((Canvas)p.args[0]).restoreToCount(save);
            }
        },Canvas.class);
        WeakHashMap<View, TilePress> presses = new WeakHashMap<>();
        hook(android.view.ViewGroup.class,"dispatchTouchEvent",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!tile.isInstance(p.thisObject)) return;
                View view = (View) p.thisObject;
                MotionEvent event = (MotionEvent) p.args[0];
                int action = event.getActionMasked();
                QsLayout.Item item = NativeForms.hasCuts() ? NativeForms.item(view) : null;

                if (action == MotionEvent.ACTION_DOWN) {
                    presses.remove(view);
                    if (item != null && !TileLayers.contains(NativeLayerClip.visible(view, item),
                            event.getX(), event.getY())) {
                        p.setResult(false);
                        return;
                    }
                    presses.put(view, new TilePress(event));
                } else {
                    TilePress press = presses.get(view);
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        presses.remove(view);
                    }
                    if (press != null && press.rootPress && press.moved) {
                        // The parent pager still receives the real swipe. The tile has finished.
                        p.setResult(true);
                        return;
                    }
                    if (press != null && !press.moved && crossedTouchSlop(view, event, press)) {
                        press.moved = true;
                        if (press.rootPress) {
                            // The native root still considers the finger inside a large tile.
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            replaceTouch(p, event, cancel, false);
                            return;
                        }
                        // Embedded controls such as the Bluetooth pager need the real MOVE.
                        view.cancelPendingInputEvents();
                    }
                }

                if (item == null || !item.form().transformed()) return;
                float[] pitch = NativeFormContent.drawingPitch(view, item.w(), item.h());
                android.graphics.Matrix inverse = new android.graphics.Matrix();
                TileRotation.content(item.form(), new RectF(0, 0, view.getWidth(), view.getHeight()),
                        item.w(), item.h(), pitch[0], pitch[1],
                        view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL).invert(inverse);
                MotionEvent mapped = MotionEvent.obtain(event);
                mapped.transform(inverse);
                replaceTouch(p, event, mapped, true);
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                MotionEvent copy = (MotionEvent) p.getObjectExtra("nqsTouchCopy");
                if (copy != null) {
                    p.args[0] = p.getObjectExtra("nqsOriginalTouch");
                    MotionEvent[] previous = (MotionEvent[]) p.getObjectExtra("nqsPreviousTouch");
                    if (previous == null) mappedTouches.remove();
                    else mappedTouches.set(previous);
                    copy.recycle();
                }
                if (tile.isInstance(p.thisObject)
                        && ((MotionEvent) p.args[0]).getActionMasked() == MotionEvent.ACTION_DOWN
                        && !Boolean.TRUE.equals(p.getResult())) {
                    presses.remove((View) p.thisObject);
                }
            }
        },MotionEvent.class);
        hook(View.class,"onTouchEvent",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!tile.isInstance(p.thisObject)) return;
                TilePress press = presses.get((View) p.thisObject);
                if (press != null && ((MotionEvent) p.args[0]).getActionMasked() == MotionEvent.ACTION_DOWN) {
                    press.rootPress = true;
                }
                MotionEvent[] touch = mappedTouches.get();
                if (touch == null) return;
                p.setObjectExtra("nqsRootTouch", p.args[0]);
                p.args[0] = touch[1];
            }
            @Override protected void afterHookedMethod(MethodHookParam p) { Object event=p.getObjectExtra("nqsRootTouch"); if(event!=null) p.args[0]=event; }
        },MotionEvent.class);
        hook(types.transformer(),"transformByOrientation",new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if(p.hasThrowable()||NativeGrid.integer(p.thisObject,"workspaceOrientation")==1) return;
                for(Object info:(java.util.List<?>)p.getResult()) {
                    if((Boolean)NativeGrid.call(info,"isInvalid")) continue;
                    int w=NativeGrid.integer(info,"spanX"),h=NativeGrid.integer(info,"spanY");
                    String id="t:"+NativeGrid.call(info,"getTileSpec");
                    QsLayout.Item i=new QsLayout.Item(id,"",0,0,0,w,h,true,(Boolean)NativeGrid.call(info,"isAllowExtendHeight"),NativeForms.form(id,w,h));
                    if(NativeForms.custom(i)) { NativeGrid.field(info.getClass(),"spanX").setInt(info,Math.min(2,w)); NativeGrid.field(info.getClass(),"spanY").setInt(info,1); }
                }
            }
        },java.util.List.class);
        hook(types.launcher(),"resetQSTileInfos",new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    android.content.Context c=(android.content.Context)NativeGrid.call(p.thisObject,"getContext"); NativeForms.user(c,(Integer)p.args[0]);
                } catch(Exception e) { android.util.Log.w("EssentialTiling","Load per-user tile forms",e); }
            }
        },int.class,java.util.List.class);
    }
    private static boolean crossedTouchSlop(View view, MotionEvent event, TilePress press) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) return true;
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) return false;
        return press.pastSlop(event, ViewConfiguration.get(view.getContext()).getScaledTouchSlop());
    }

    private static void replaceTouch(XC_MethodHook.MethodHookParam call, MotionEvent original,
            MotionEvent copy, boolean rotated) {
        call.args[0] = copy;
        call.setObjectExtra("nqsTouchCopy", copy);
        call.setObjectExtra("nqsOriginalTouch", original);
        call.setObjectExtra("nqsPreviousTouch", mappedTouches.get());
        if (rotated) mappedTouches.set(new MotionEvent[]{copy, original});
        else mappedTouches.remove();
    }

    static float radius(View v) {
        Config config=QsModule.currentConfig();
        if(config.enabled&&config.radiusDp>=0) return config.radiusDp*v.getResources().getDisplayMetrics().density;
        try { int id=v.getResources().getIdentifier("qs_corner_radius","dimen","com.android.systemui"); return id==0?Math.min(v.getWidth(),v.getHeight())/4f:v.getResources().getDimension(id); }
        catch(RuntimeException e) { return 0; }
    }
    static String reason(Throwable e) {
        String message=e.getMessage();
        return e.getClass().getSimpleName()+(message==null||message.isBlank()?"":": "+message);
    }
    private static void hook(Class<?> type,String name,XC_MethodHook callback,Class<?>...args) throws Exception {
        String target=type.getName()+"#"+name;
        try { Method method=type.getDeclaredMethod(name,args); method.setAccessible(true); hooks.add(XposedBridge.hookMethod(method,callback)); }
        catch(Exception|LinkageError e) { throw new IllegalStateException(target+": "+reason(e),e); }
    }
}
