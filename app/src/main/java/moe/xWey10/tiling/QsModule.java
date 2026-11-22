package moe.xWey10.tiling;

import android.app.Application;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Hooks for the supplied NothingSystemUI 16.0.0. No SystemUI code is bundled. */
@SuppressLint({"PrivateApi", "DiscouragedApi"}) // Audited vendor members and installed SystemUI resource IDs.
public final class QsModule implements IXposedHookLoadPackage {
    private static final String TILE = "com.android.systemui.qs.tileimpl.QSTileViewImpl";
    private static final String PANEL = "com.android.systemui.qs.QSPanel";
    private static final String BLUETOOTH = "com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl";
    private static volatile Config config = Config.DEFAULT;
    private static final ThreadLocal<Config> previewDraft = new ThreadLocal<>();
    private static final ThreadLocal<RuntimeException> previewFailure = new ThreadLocal<>();
    @SuppressLint("StaticFieldLeak") // One SystemUI Application and observer for the process lifetime.
    private static NativePreviewServer previewServer;
    @SuppressLint("StaticFieldLeak") // Process-lifetime observer; context is SystemUI Application.
    private static RestartServer restartServer;
    private static Bitmap previewBackground;
    private static int previewOrientation, previewPanelWidth;
    private static boolean previewWasExpanded;
    private static boolean previewWasClosed = true;
    private static WeakReference<ViewGroup> previewPanel = new WeakReference<>(null);
    private static final Object previewListeningOwner = new Object();
    private static final Set<Object> previewListeningTiles = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicBoolean started = new AtomicBoolean();
    private static final Set<View> tiles = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<LinearLayout> panels = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<BatteryRow> rows = Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<LinearLayout, WeakReference<BatteryRow>> panelRows = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> appliedSmallWidths = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, Float> backgroundDensities = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> nativeBluetoothSizes = new WeakHashMap<>();
    private static final ThreadLocal<View> measuring = new ThreadLocal<>();
    private static final String ROW_TAG = "moe.xWey10.tiling.battery";
    private static boolean geometryReady, cornersReady, batteryReady, bluetoothReady;
    private static boolean settingsLoaded;
    private static float expansion;
    private static boolean customizing;
    private static Class<?> tileClass, lpClass;
    private static Field x, y, smallWidth, isAnim, locked, fullscreen;
    private static Field tileRipple, tileFocus, tileBase;
    private static Field panelBrightness;
    private static Method updateCorner;
    private static Method updateLayout;
    private static Class<?> bluetoothClass;
    private static Field bluetoothNormalRadius, bluetoothLargeRadius, bluetoothContainer, bluetoothPager;
    private static final ArrayList<String> issues = new ArrayList<>();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.systemui".equals(p.packageName)
                || !"com.android.systemui".equals(p.processName)) return;
        ClassLoader loader = p.classLoader;
        try {
            tileClass = Class.forName(TILE, false, loader);
        } catch (Exception | LinkageError e) {
            issue("Класс плиток отсутствует", e);
        }
        try { NativeFormHooks.watch(QsModule::bindLiveStyles); }
        catch(Exception|LinkageError e) { issue("Наблюдение за живыми плитками",e); }
        installGeometry(loader);
        installCorners(loader);
        installBluetooth(loader);
        installBattery(loader);
        try { NativeFormHooks.install(loader,tileClass,bluetoothClass); } catch(Exception|LinkageError e) { android.util.Log.w("EssentialTiling","Waiting for live SystemUI classes for forms",e); }
        try {
            Class<?> query = Class.forName("com.android.systemui.qs.customize.TileQueryHelper", false, loader);
            de.robv.android.xposed.XposedBridge.hookAllConstructors(query, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    boolean primaryTracker = param.args.length > 1 && param.args[1] != null
                            && param.args[1].getClass().getName().equals("com.android.systemui.settings.UserTrackerImpl");
                    if (!param.hasThrowable() && (NativeTileCatalog.dependencies == null || primaryTracker)) {
                        NativeTileCatalog.type = query; NativeTileCatalog.dependencies = param.args.clone();
                    }
                }
            });
        } catch (Exception | LinkageError e) { issue("Каталог плиток недоступен", e); }
        try {
            hook(loader, "com.android.systemui.SystemUIApplication", "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable() && started.compareAndSet(false, true)) {
                        NativeForms.init((Application)param.thisObject);
                        recoverHooks((Application)param.thisObject);
                        loadConfig((Application) param.thisObject);
                        restartServer = new RestartServer((Application) param.thisObject);
                        previewServer = new NativePreviewServer((Application) param.thisObject, QsModule::renderPreview,
                                QsModule::refreshPreviewTileStates, QsModule::finishPreviewTileStates);
                        new NativeLayoutServer((Application) param.thisObject, json -> readyGrid().saveJob(QsLayout.parse(json)));
                        new NativeTileServer((Application) param.thisObject, QsModule::readyGrid);
                    }
                }
            });
        } catch (Exception | LinkageError e) {
            // Config remains disabled if this entry point differs from the APK.
            issue("Инициализация недоступна", e);
        }
    }

    private static void bindLiveStyles(View view) {
        tileClass=NativeFormHooks.tileType(view);
        ClassLoader loader=view.getClass().getClassLoader();
        if(!geometryReady) installGeometry(loader);
        if(!cornersReady) installCorners(loader);
        if(!bluetoothReady) installBluetooth(loader);
        if(!batteryReady) installBattery(loader);
        for(android.view.ViewParent parent=view.getParent();parent instanceof View;parent=parent.getParent()) {
            if(parent instanceof LinearLayout panel&&panel.getClass().getName().equals(PANEL)) panels.add(panel);
        }
        formBindingsReady(view.getContext(),true);
    }

    private static void formBindingsReady(Context context,boolean notifySource) {
        if(notifySource&&previewServer!=null) previewServer.sourceChanged();
        Context app=context.getApplicationContext();
        Thread reporter=new Thread(()->report(app,currentConfig()),"EssentialTiling-forms-ready");
        reporter.setDaemon(true); reporter.start();
    }

    private static void recoverHooks(Application app) {
        ClassLoader loader=app.getClassLoader();
        try { if(tileClass==null) tileClass=Class.forName(TILE,false,loader); }
        catch(Exception|LinkageError e) { issue("Класс плиток после onCreate",e); }
        if(!geometryReady) installGeometry(loader);
        if(!cornersReady) installCorners(loader);
        if(!bluetoothReady) installBluetooth(loader);
        if(!batteryReady) installBattery(loader);
        if(!NativeForms.supported) try { NativeFormHooks.install(loader,tileClass,bluetoothClass); }
        catch(Exception|LinkageError e) { android.util.Log.w("EssentialTiling","Waiting for live tile binding",e); }
    }

    private static void installGeometry(ClassLoader loader) {
        ArrayList<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            if (tileClass == null) throw new ClassNotFoundException(TILE);
            lpClass = Class.forName("com.android.launcher3.CellLayout$LayoutParams", false, loader);
            x = field(lpClass, "x"); y = field(lpClass, "y");
            smallWidth = field(lpClass, "smallSizeTileWidth");
            isAnim = field(lpClass, "isAnim"); locked = field(lpClass, "isLockedToGrid");
            fullscreen = field(lpClass, "isFullscreen");
            updateLayout = tileClass.getDeclaredMethod("updateLayout");
            updateLayout.setAccessible(true);
            bluetoothClass = Class.forName(BLUETOOTH, false, loader);
            XC_MethodHook scope = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    p.setObjectExtra("previousTile", measuring.get());
                    View child = (View) p.args[0];
                    if(!NativeForms.supported&&NativeFormHooks.ensure(child,loader)) formBindingsReady(child.getContext(),true);
                    measuring.set(tileClass.isInstance(child)
                            && (!isBluetooth(child) || bluetoothReady) ? child : null);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View previous = (View) p.getObjectExtra("previousTile");
                    if (previous == null) measuring.remove(); else measuring.set(previous);
                }
            };
            installed.add(hook(loader, "com.android.launcher3.ShortcutAndWidgetContainer",
                    "measureChild", scope, View.class));
            installed.add(hook(loader, "com.android.launcher3.ShortcutAndWidgetContainer",
                    "setupLp", scope, View.class));
            // Hook only the 8-argument overload. The 6-argument method delegates to it.
            installed.add(hook(loader, lpClass.getName(), "setup", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View child = measuring.get();
                    if (child == null || p.hasThrowable()) return;
                    tiles.add(child);
                    Config c = currentConfig();
                    if (!geometryReady) return;
                    if (!c.enabled || (c.scalePercent == 100 && c.gapDp == 0)) {
                        if (previewDraft.get() != null && !isBluetooth(child)) {
                            try { updateLayout.invoke(child); }
                            catch (ReflectiveOperationException e) { issue("Предпросмотр: раскладка", e); }
                        }
                        return;
                    }
                    Object lp = p.thisObject;
                    if (child.getLayoutParams() != lp) return;
                    try {
                        if (fullscreen.getBoolean(lp) || (!locked.getBoolean(lp) && !isAnim.getBoolean(lp))) return;
                        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) lp;
                        float density = child.getResources().getDisplayMetrics().density;
                        int gap = Math.round(c.gapDp * density), minimum = Math.round(48 * density);
                        int width = TileGeometry.size(params.width, c.scalePercent, gap, minimum);
                        int height = TileGeometry.size(params.height, c.scalePercent, gap, minimum);
                        int originX = TileGeometry.centeredOrigin(x.getInt(lp), params.width, width);
                        int originY = TileGeometry.centeredOrigin(y.getInt(lp), params.height, height);
                        boolean bluetooth = isBluetooth(child);
                        if (bluetooth) {
                            int[] nativeSize = nativeBluetoothSizes.get(child);
                            if (nativeSize == null) {
                                nativeSize = new int[2];
                                nativeBluetoothSizes.put(child, nativeSize);
                            }
                            nativeSize[0] = params.width;
                            nativeSize[1] = params.height;
                        }
                        // All reflection reads precede writes. These fields were checked during install.
                        float nativeSmall = smallWidth.getFloat(lp);
                        boolean animated = isAnim.getBoolean(lp);
                        x.setInt(lp, originX); y.setInt(lp, originY);
                        params.width = width; params.height = height;
                        if (!animated && nativeSmall > 0 && !bluetooth) {
                            int newSmallWidth = TileGeometry.size(Math.round(nativeSmall),
                                    c.scalePercent, gap, minimum);
                            smallWidth.setFloat(lp, newSmallWidth);
                            Integer previous = appliedSmallWidths.put(child, newSmallWidth);
                            // Native measureChild refreshes padding only on its first pass.
                            // Refresh it once when our new geometry changes.
                            if (previous == null || previous != newSmallWidth) updateLayout.invoke(child);
                        }
                        // scaleTranslationX/Y and cell spans remain native: expansion and drag use them.
                    } catch (Exception | LinkageError e) {
                        if (previewDraft.get() != null) {
                            previewFailure.set(new IllegalStateException("Геометрия предпросмотра", e)); return;
                        }
                        geometryReady = false;
                        issue("Геометрия отключена", e);
                    }
                }
            }, int.class, int.class, boolean.class, int.class, float.class, float.class,
                    int.class, boolean.class));
            geometryReady = true;
        } catch (Exception | LinkageError e) {
            for (XC_MethodHook.Unhook h : installed) h.unhook();
            issue("Геометрия недоступна", e);
        }
    }

    private static void installCorners(ClassLoader loader) {
        ArrayList<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            if (tileClass == null) throw new ClassNotFoundException(TILE);
            tileRipple = field(tileClass, "qsTileBackground");
            tileFocus = field(tileClass, "qsTileFocusBackground");
            tileBase = field(tileClass, "backgroundDrawable");
            updateCorner = tileClass.getDeclaredMethod("updateBackgroundCorner");
            updateCorner.setAccessible(true);
            installed.add(hook(loader, TILE, "changeCornerRadius", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (customCorners() && (!isBluetooth((View) p.thisObject) || bluetoothReady)) {
                        p.args[0] = radius((View) p.thisObject);
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) applyTileCorners((View) p.thisObject);
                }
            }, float.class));
            XC_MethodHook refresh = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) applyTileCorners((View) p.thisObject);
                }
            };
            // Public outer methods also cover builds where ART inlines the private helper.
            installed.add(hook(loader, TILE, "updateBackgroundCorner", refresh));
            installed.add(hook(loader, TILE, "updateResources", refresh));
            installed.add(hook(loader, TILE, "createTileBackground", refresh));
            installed.add(hook(loader, "com.nothing.systemui.qs.tileimpl.QSTileViewImplEx",
                    "changeCornerRadius", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (customCorners()) {
                                // Nothing inflates QS with its own density; system metrics can differ.
                                Float density = backgroundDensities.get((Drawable) p.args[0]);
                                if (density != null) p.args[1] = currentConfig().radiusDp * density;
                            }
                        }
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (customCorners() && !p.hasThrowable()
                                    && backgroundDensities.containsKey((Drawable) p.args[0])) {
                                round((Drawable) p.args[0], (Float) p.args[1], 0);
                            }
                        }
                    }, LayerDrawable.class, float.class));
            installed.add(hook(loader, "com.nothing.systemui.qs.tileimpl.QSTileViewImplEx",
                    "resizeAnimate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!p.hasThrowable()) applyTileCorners((View) p.args[5]);
                        }
                    }, int.class, int.class, float.class, android.content.res.Resources.class,
                    Class.forName("com.android.systemui.qs.tileimpl.IgnorableChildLinearLayout", false, loader),
                    tileClass, LayerDrawable.class,
                    Class.forName("com.android.systemui.qs.tileimpl.QSIconViewImpl", false, loader), boolean.class));
            cornersReady = true;
        } catch (Exception | LinkageError e) {
            for (XC_MethodHook.Unhook h : installed) h.unhook();
            issue("Скругление недоступно", e);
        }
    }

    private static boolean customCorners() {
        return cornersReady && currentConfig().enabled && currentConfig().radiusDp >= 0;
    }

    private static void applyTileCorners(View tile) {
        if (isBluetooth(tile) && !bluetoothReady) return;
        tiles.add(tile);
        try {
            Drawable base = (Drawable) tileBase.get(tile);
            if (base != null) backgroundDensities.put(base, tile.getResources().getDisplayMetrics().density);
            if (customCorners()) {
                float radius = radius(tile);
                round(tile.getBackground(), radius, 0);
                round((Drawable) tileRipple.get(tile), radius, 0);
                round((Drawable) tileFocus.get(tile), radius, 0);
                tile.invalidateOutline();
            }
            if (isBluetooth(tile)) applyBluetoothCorners(tile);
        } catch (Exception | LinkageError e) {
            if (previewDraft.get() != null) {
                previewFailure.set(new IllegalStateException("Скругление предпросмотра", e)); return;
            }
            cornersReady = false;
            issue("Скругление отключено", e);
        }
    }

    private static float radius(View tile) {
        return currentConfig().radiusDp * tile.getResources().getDisplayMetrics().density;
    }

    private static boolean isBluetooth(View tile) {
        // Class name also works if geometry installation failed before resolving the BT class.
        for (Class<?> cls = tile.getClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.getName().equals(BLUETOOTH)) return true;
        }
        return false;
    }

    private static void installBluetooth(ClassLoader loader) {
        ArrayList<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            bluetoothClass = Class.forName(BLUETOOTH, false, loader);
            bluetoothNormalRadius = field(bluetoothClass, "normalViewCornerRadius");
            bluetoothLargeRadius = field(bluetoothClass, "largeViewCornerRadius");
            bluetoothContainer = field(bluetoothClass, "mContainer");
            bluetoothPager = field(bluetoothClass, "mPager");
            XC_MethodHook refresh = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (bluetoothReady && !p.hasThrowable()) applyTileCorners((View) p.thisObject);
                }
            };
            installed.add(hook(loader, BLUETOOTH, "updateLayout", refresh));
            installed.add(hook(loader, BLUETOOTH, "updateResources", refresh));
            installed.add(hook(loader, BLUETOOTH, "handleStateChanged", refresh,
                    Class.forName("com.android.systemui.plugins.qs.QSTile$State", false, loader)));
            installed.add(hook(loader, BLUETOOTH, "dispatchDraw", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    applyBluetoothCorners((View) p.thisObject);
                }
            }, Canvas.class));
            installed.add(hook(loader, TILE, "onMeasure", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View tile = (View) p.thisObject;
                    if (!bluetoothReady || !currentConfig().enabled || !isBluetooth(tile) || p.hasThrowable() || NativeForms.custom(NativeForms.item(tile))) return;
                    int[] nativeSize = nativeBluetoothSizes.get(tile);
                    if (nativeSize == null) return;
                    try {
                        View container = (View) bluetoothContainer.get(tile);
                        if (container != null) BluetoothContent.measure(container, nativeSize[0], nativeSize[1]);
                    } catch (Exception | LinkageError e) { bluetoothFailure(e); }
                }
            }, int.class, int.class));
            installed.add(hook(loader, TILE, "onLayout", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View tile = (View) p.thisObject;
                    if (!bluetoothReady || !currentConfig().enabled || !isBluetooth(tile) || p.hasThrowable() || NativeForms.custom(NativeForms.item(tile))) return;
                    int[] nativeSize = nativeBluetoothSizes.get(tile);
                    try {
                        View container = (View) bluetoothContainer.get(tile);
                        if (container != null && nativeSize != null) {
                            BluetoothContent.layout(tile, container, nativeSize[0], nativeSize[1]);
                        }
                        applyBluetoothCorners(tile);
                    } catch (Exception | LinkageError e) { bluetoothFailure(e); }
                }
            }, boolean.class, int.class, int.class, int.class, int.class));
            bluetoothReady = true;
        } catch (Exception | LinkageError e) {
            for (XC_MethodHook.Unhook h : installed) h.unhook();
            issue("Bluetooth недоступен: оставлен штатным", e);
        }
    }

    private static void applyBluetoothCorners(View tile) {
        if (!bluetoothReady || !currentConfig().enabled) return;
        try {
            if (customCorners()) {
                float radius = radius(tile);
                // Native dispatchDraw interpolates these endpoints on every resize frame.
                bluetoothNormalRadius.setFloat(tile, radius);
                bluetoothLargeRadius.setFloat(tile, radius);
            }
            View pager = (View) bluetoothPager.get(tile);
            if (pager == null) return; // Base constructor may run before BT's container exists.
            int[] nativeSize = nativeBluetoothSizes.get(tile);
            float contentScale = nativeSize == null ? 1f : BluetoothContent.scale(
                    tile.getMeasuredWidth(), tile.getMeasuredHeight(), nativeSize[0], nativeSize[1]);
            float radius = bluetoothLargeRadius.getFloat(tile);
            // Pager is scaled, while Canvas/ripple/focus are in the outer tile's coordinates.
            // Avoid invalidating a drawable on every dispatchDraw: an idle QS must stay idle.
            if (round(pager.getBackground(), radius / contentScale, 0)) pager.invalidateOutline();
        } catch (Exception | LinkageError e) { bluetoothFailure(e); }
    }

    private static void bluetoothFailure(Throwable e) {
        if (previewDraft.get() != null) {
            previewFailure.set(new IllegalStateException("Bluetooth в предпросмотре", e)); return;
        }
        if (!bluetoothReady) return;
        bluetoothReady = false;
        issue("Bluetooth отключён до перезапуска SystemUI", e);
    }

    /** Mutate the existing fill, ripple mask, hover/focus selectors; retain native tints. */
    private static boolean round(Drawable d, float radius, int depth) {
        if (d == null || depth > 8) return false;
        if (d instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) d;
            if (gradient.getCornerRadius() == radius && gradient.getCornerRadii() == null) return false;
            gradient.setCornerRadius(radius);
            return true;
        }
        boolean changed = false;
        if (d instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) d;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) changed |= round(layers.getDrawable(i), radius, depth + 1);
        } else if (d instanceof StateListDrawable) {
            StateListDrawable states = (StateListDrawable) d;
            for (int i = 0; i < states.getStateCount(); i++) changed |= round(states.getStateDrawable(i), radius, depth + 1);
        } else if (d instanceof InsetDrawable) changed = round(((InsetDrawable) d).getDrawable(), radius, depth + 1);
        return changed;
    }

    private static void installBattery(ClassLoader loader) {
        ArrayList<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            panelBrightness = field(Class.forName(PANEL, false, loader), "mBrightnessView");
            installed.add(hook(loader, PANEL, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !p.thisObject.getClass().getName().equals(PANEL)) return;
                    LinearLayout panel = (LinearLayout) p.thisObject;
                    panels.add(panel);
                }
            }));
            installed.add(hook(loader, PANEL, "setBrightnessView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable() && p.thisObject.getClass().getName().equals(PANEL)) {
                        addBatteryRow((LinearLayout) p.thisObject);
                    }
                }
            }, View.class));
            installed.add(hook(loader, PANEL, "switchAllContentToParent", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable() && p.thisObject.getClass().getName().equals(PANEL)) {
                        addBatteryRow((LinearLayout) p.thisObject);
                    }
                }
            }, ViewGroup.class, Class.forName(PANEL + "$QSTileLayout", false, loader)));
            installed.add(hook(loader, "com.nothing.systemui.qs.QSImplEx",
                    "onQSExpansionChangedForAnimation", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            expansion = (Float) p.args[0];
                            customizing = (Boolean) p.args[4];
                            for (BatteryRow row : new ArrayList<>(rows)) updateRow(row);
                            updatePreviewSource();
                        }
                    }, float.class, boolean.class, View.class, View.class, boolean.class));
            batteryReady = true;
        } catch (Exception | LinkageError e) {
            for (XC_MethodHook.Unhook h : installed) h.unhook();
            issue("Строка батареи недоступна", e);
        }
    }

    private static void addBatteryRow(LinearLayout panel) {
        if (previewDraft.get() != null || !batteryReady || !config.enabled || !config.battery) return;
        try {
            View brightness = (View) panelBrightness.get(panel);
            if (brightness == null || !(brightness.getParent() instanceof LinearLayout)) return;
            LinearLayout parent = (LinearLayout) brightness.getParent();
            WeakReference<BatteryRow> reference = panelRows.get(panel);
            BatteryRow row = reference == null ? null : reference.get();
            if (row == null) {
                row = new BatteryRow(panel.getContext());
                row.setTag(ROW_TAG);
                rows.add(row);
                panelRows.put(panel, new WeakReference<>(row));
            }
            updateRow(row);
            // Follow the brightness view when Nothing reparents QS for landscape/media.
            if (row.getParent() == parent && parent.indexOfChild(row) == parent.indexOfChild(brightness) + 1) return;
            if (row.getParent() instanceof ViewGroup) ((ViewGroup) row.getParent()).removeView(row);
            parent.addView(row, parent.indexOfChild(brightness) + 1, new LinearLayout.LayoutParams(-1, -2));
        } catch (Exception | LinkageError e) {
            batteryReady = false;
            issue("Строка батареи отключена", e);
        }
    }

    private static void updateRow(BatteryRow row) {
        float alpha = customizing ? 0 : TileGeometry.batteryAlpha(expansion);
        row.setAlpha(alpha);
        // INVISIBLE retains measured height throughout expansion, preventing a layout jump.
        row.setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
        row.setImportantForAccessibility(alpha == 1 ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    static Config currentConfig() {
        Config draft = previewDraft.get();
        return draft == null ? config : draft;
    }

    private static void updatePreviewSource() {
        if (previewDraft.get() != null) return;
        if (expansion <= 0 && !previewWasClosed) {
            previewWasClosed = true;
            if (previewBackground != null && previewServer != null) previewServer.sourceChanged();
        } else if (expansion > 0) previewWasClosed = false;
        if (expansion < 1 || customizing) { previewWasExpanded = false; return; }
        if (previewWasExpanded || previewServer == null) return;
        previewWasExpanded = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (expansion < 1 || customizing) return;
            for (LinearLayout panel : new ArrayList<>(panels)) {
                if (!panel.isAttachedToWindow() || panel.getMeasuredWidth() <= 0) continue;
                View scrim = NativePreviewRenderer.find(panel.getRootView(), "scrim_behind");
                Bitmap background;
                try { background = NativePreviewRenderer.captureBackground(panel, scrim); }
                catch (RuntimeException | OutOfMemoryError e) { issue("Фон предпросмотра", e); return; }
                if (background == null) continue;
                Bitmap previous = previewBackground;
                previewBackground = background;
                previewPanel = new WeakReference<>(panel);
                previewOrientation = panel.getResources().getConfiguration().orientation;
                previewPanelWidth = panel.getMeasuredWidth();
                if (previous != null) previous.recycle();
                previewServer.sourceChanged();
                break;
            }
        }, 80);
    }

    private static NativeGrid readyGrid() throws ReflectiveOperationException {
        if (!settingsLoaded) throw new IllegalStateException("Модуль ещё не готов. Проверьте отчёт SystemUI.");
        ViewGroup panel = previewPanel.get();
        if (panel == null || !panel.isAttachedToWindow() || panel.getMeasuredWidth() != previewPanelWidth
                || panel.getResources().getConfiguration().orientation != previewOrientation) {
            throw new IllegalStateException("Полностью откройте шторку и закройте её, затем нажмите «Обновить».");
        }
        if (expansion > 0 || customizing) throw new IllegalStateException("Закройте шторку и её редактор перед применением.");
        View view = NativePreviewRenderer.find(panel, "qs_pager");
        if (!(view instanceof ViewGroup)) throw new IllegalStateException("Не найдена сетка QS");
        if(!NativeForms.supported&&NativeFormHooks.ensureTree(view)) formBindingsReady(panel.getContext(),false);
        try {
            Object launcher=Class.forName("com.android.launcher3.Launcher",false,panel.getClass().getClassLoader()).getMethod("getLauncher",Context.class).invoke(null,panel.getContext());
            NativeForms.user(panel.getContext(),NativeGrid.integer(launcher,"currentUserId"));
        } catch(ReflectiveOperationException e) { throw e; }
        return new NativeGrid(panel, (ViewGroup) view);
    }

    private static NativePreviewServer.Frame renderPreview(Config draft, int page, String layout, String focus) throws Exception {
        ViewGroup panel = previewPanel.get();
        if (panel == null || !panel.isAttachedToWindow() || previewBackground == null
                || panel.getMeasuredWidth() != previewPanelWidth
                || panel.getResources().getConfiguration().orientation != previewOrientation) {
            throw new IllegalStateException("Полностью откройте шторку и закройте её, затем нажмите «Обновить».");
        }
        if (customizing) throw new IllegalStateException("Закройте редактор шторки и нажмите «Обновить».");
        if (expansion > 0) throw new IllegalStateException("Закройте шторку для предпросмотра в приложении.");
        if (!geometryReady || !cornersReady) throw new IllegalStateException("Хуки оформления недоступны. Проверьте отчёт SystemUI.");
        View workspaceView = NativePreviewRenderer.find(panel, "qs_pager");
        if (!(workspaceView instanceof ViewGroup)) throw new IllegalStateException("Не найдена раскладка QS");
        NativeGrid availableGrid = null;
        try { availableGrid = readyGrid(); }
        catch (ReflectiveOperationException | RuntimeException e) {
            if (!layout.isEmpty()) throw new IllegalStateException("Редактор сетки недоступен: " + e.getMessage(), e);
            issue("Редактор сетки недоступен", e);
        }
        NativeGrid grid = availableGrid;
        if (grid != null) {
            grid.focus = focus;
            grid.beforeVariant = tile -> { nativeBluetoothSizes.remove(tile); appliedSmallWidths.remove(tile); };
        }
        NativeGrid.Session session = grid == null ? null : grid.new Session(layout);
        ArrayList<View> nativeTiles = new ArrayList<>();
        ArrayList<NativeFields> fields = new ArrayList<>();
        ArrayList<BluetoothPreviewState> bluetoothStates = new ArrayList<>();
        for (View tile : new ArrayList<>(tiles)) if (NativePreviewRenderer.contains(panel, tile)) {
            if (lpClass.isInstance(tile.getLayoutParams()) && isAnim.getBoolean(tile.getLayoutParams())) {
                throw new IllegalStateException("Дождитесь окончания изменения размера плитки.");
            }
            nativeTiles.add(tile); fields.add(new NativeFields(tile));
            if (isBluetooth(tile) && bluetoothReady) bluetoothStates.add(new BluetoothPreviewState(tile));
        }
        View indicator = NativePreviewRenderer.find(panel, "footer_page_indicator");
        if (indicator != null) fields.add(new NativeFields(indicator));
        Map<View, Integer> widths = new IdentityHashMap<>(appliedSmallWidths);
        Map<View, int[]> sizes = new IdentityHashMap<>();
        nativeBluetoothSizes.forEach((v, size) -> sizes.put(v, size.clone()));
        Method resources = tileClass.getDeclaredMethod("updateResources"); resources.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<View, Integer> layoutTops = (Map<View, Integer>) field(panel.getClass(), "mChildrenLayoutTop").get(panel);
        Map<View, Integer> oldTops = new IdentityHashMap<>(layoutTops);
        View brightness = (View) panelBrightness.get(panel);
        Runnable apply = () -> {
            previewDraft.set(draft);
            try {
                if (session != null) {
                    session.apply();
                    for (QsLayout.Item old : session.changed) nativeBluetoothSizes.remove(grid.views.get(old.id()));
                }
                for (View tile : nativeTiles) {
                    field(tileClass, "qsOrQqsPanelFullExpanded").setBoolean(tile, true);
                    if (isBluetooth(tile) && bluetoothReady) {
                        int normal = tile.getResources().getIdentifier("qs_corner_radius", "dimen", "com.android.systemui");
                        int large = tile.getResources().getIdentifier("bluetooth_tile_view_large_corner_radius", "dimen", "com.android.systemui");
                        bluetoothNormalRadius.setFloat(tile, tile.getResources().getDimension(normal));
                        bluetoothLargeRadius.setFloat(tile, tile.getResources().getDimension(large));
                        View container = (View) bluetoothContainer.get(tile);
                        if (container != null) { container.setScaleX(1); container.setScaleY(1); }
                    }
                    resources.invoke(tile); updateLayout.invoke(tile);
                }
                if (indicator != null) {
                    int selected = Math.max(0, Math.min(page, ((ViewGroup) workspaceView).getChildCount() - 1));
                    setIndicatorPage(indicator, selected);
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Отрисовка QS", e); }
        };
        Runnable restore = () -> {
            previewDraft.remove();
            if (session != null) {
                try { session.restore(); }
                catch (RuntimeException e) { issue("Восстановление сетки", e); }
            }
            fields.forEach(NativeFields::restore);
            appliedSmallWidths.clear(); appliedSmallWidths.putAll(widths);
            nativeBluetoothSizes.clear(); sizes.forEach((v, size) -> nativeBluetoothSizes.put(v, size.clone()));
            for (View tile : nativeTiles) {
                try { resources.invoke(tile); updateLayout.invoke(tile); }
                catch (ReflectiveOperationException e) { issue("Восстановление QS после предпросмотра", e); }
            }
            for (BluetoothPreviewState state : bluetoothStates) {
                try { state.restore(); }
                catch (RuntimeException e) { issue("Восстановление Bluetooth после предпросмотра", e); }
            }
        };
        try {
            previewFailure.remove();
            NativePreviewServer.Frame frame = NativePreviewRenderer.render(panel, (ViewGroup) workspaceView, nativeTiles, brightness,
                    new ArrayList<>(rows), draft, page, previewBackground, apply, restore, grid, session);
            RuntimeException failure = previewFailure.get();
            if (failure != null) { frame.bitmap.recycle(); throw failure; }
            return frame;
        } finally {
            previewDraft.remove();
            previewFailure.remove();
            layoutTops.clear(); layoutTops.putAll(oldTops);
            appliedSmallWidths.clear(); appliedSmallWidths.putAll(widths);
            nativeBluetoothSizes.clear(); sizes.forEach((v, size) -> nativeBluetoothSizes.put(v, size));
        }
    }

    private static void refreshPreviewTileStates() {
        finishPreviewTileStates();
        ViewGroup panel = previewPanel.get();
        if (panel == null || expansion > 0 || customizing) return;
        try {
            ClassLoader loader = panel.getClass().getClassLoader();
            Class<?> launcherClass = Class.forName("com.android.launcher3.Launcher", false, loader);
            Object launcher = launcherClass.getMethod("getLauncher", Context.class).invoke(null, panel.getContext());
            Method record = launcherClass.getMethod("getTileRecord", String.class);
            Class<?> tileInterface = Class.forName("com.android.systemui.plugins.qs.QSTile", false, loader);
            Method listening = tileInterface.getMethod("setListening", Object.class, boolean.class);
            Method refresh = tileInterface.getMethod("refreshState");
            Field spec = field(tileClass, "spec");
            for (View view : new ArrayList<>(tiles)) if (NativePreviewRenderer.contains(panel, view)) {
                Object nativeRecord = record.invoke(launcher, spec.get(view));
                if (nativeRecord == null) continue;
                Object tile = nativeRecord.getClass().getField("tile").get(nativeRecord);
                if (!previewListeningTiles.add(tile)) continue;
                listening.invoke(tile, previewListeningOwner, true);
                refresh.invoke(tile);
            }
        } catch (ReflectiveOperationException e) { issue("Обновление состояний для предпросмотра", e); }
    }

    private static void finishPreviewTileStates() {
        for (Object tile : new ArrayList<>(previewListeningTiles)) {
            try {
                Class<?> api = Class.forName("com.android.systemui.plugins.qs.QSTile", false, tile.getClass().getClassLoader());
                api.getMethod("setListening", Object.class, boolean.class).invoke(tile, previewListeningOwner, false);
            } catch (ReflectiveOperationException e) { issue("Завершение обновления предпросмотра", e); }
        }
        previewListeningTiles.clear();
    }

    private static void setIndicatorPage(View indicator, int page) throws ReflectiveOperationException {
        Class<?> cls = indicator.getClass();
        field(cls, "mActivePage").setInt(indicator, page);
        field(cls, "mCurrentPosition").setFloat(indicator, page);
        field(cls, "mFinalPosition").setFloat(indicator, page);
    }

    private static final class NativeFields {
        final Object target;
        final ArrayList<Field> fields = new ArrayList<>();
        final ArrayList<Object> values = new ArrayList<>();
        NativeFields(Object target) throws IllegalAccessException {
            this.target = target;
            for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
                String name = cls.getName();
                if (!name.equals(TILE) && !name.equals(BLUETOOTH)
                        && !name.equals("com.android.launcher3.pageindicators.PageIndicatorDots")) continue;
                for (Field f : cls.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    if (!type.isPrimitive() && type != String.class && type != Integer.class
                            && type != Boolean.class && type != Float.class) continue;
                    f.setAccessible(true); fields.add(f); values.add(f.get(target));
                }
            }
        }
        void restore() {
            for (int i = 0; i < fields.size(); i++) {
                try { fields.get(i).set(target, values.get(i)); }
                catch (IllegalAccessException e) { throw new IllegalStateException("Native QS restore", e); }
            }
        }
    }

    private static void loadConfig(Application app) {
        Thread worker = new Thread(() -> {
            Config loaded = Config.DEFAULT;
            boolean read = false;
            try {
                PackageInfo info = app.getPackageManager().getPackageInfo("com.android.systemui", 0);
                if (Build.VERSION.SDK_INT != 36 || info.getLongVersionCode() != 160000L) {
                    throw new IllegalStateException("Нужен SystemUI 16.0.0 / Android 16");
                }
                try (Cursor cursor = app.getContentResolver().query(SettingsProvider.URI,
                        SettingsProvider.COLUMNS, null, null, null)) {
                    if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("Нет настроек");
                    loaded = new Config(cursor.getInt(0) != 0, cursor.getInt(1), cursor.getInt(2),
                            cursor.getInt(3), cursor.getInt(4) != 0, cursor.getLong(5));
                    read = true;
                }
            } catch (Exception | LinkageError e) {
                issue("Модификации выключены: настройки не загружены", e);
            }
            Config snapshot = loaded;
            boolean success = read;
            new Handler(Looper.getMainLooper()).post(() -> {
                config = snapshot;
                settingsLoaded = success;
                if (snapshot.enabled) {
                    for (View tile : new ArrayList<>(tiles)) {
                        if (tile.getParent() instanceof View) ((View) tile.getParent()).requestLayout();
                        if (customCorners() && updateCorner != null) {
                            try { updateCorner.invoke(tile); }
                            catch (Exception | LinkageError e) { issue("Обновление скругления", e); }
                        }
                    }
                    for (LinearLayout panel : new ArrayList<>(panels)) addBatteryRow(panel);
                }
                // Report outside SystemUI's UI thread. No IPC during layout or tile updates.
                Thread reporter = new Thread(() -> report(app, snapshot), "EssentialTiling-report");
                reporter.setDaemon(true);
                reporter.start();
            });
        }, "EssentialTiling-config");
        worker.setDaemon(true);
        worker.start();
    }

    private static void report(Context context, Config c) {
        String summary = "Модуль " + BuildConfig.VERSION_NAME + "\n" + (c.enabled ? "Модификации включены" : "Модификации выключены")
                + "\nГеометрия: " + (geometryReady ? "доступна" : "недоступна")
                + " · Скругление: " + (cornersReady ? "доступно" : "недоступно")
                + " · Батарея: " + (batteryReady ? "доступна" : "недоступна")
                + "\nФормы: " + (NativeForms.supported ? "доступны" : "недоступны: " + NativeForms.failure)
                + "\nBluetooth: " + (bluetoothReady ? "доступен" : "штатный");
        synchronized (issues) {
            if (!issues.isEmpty()) summary += "\n" + String.join("\n", issues);
        }
        try {
            Bundle extras = new Bundle();
            extras.putString("summary", summary);
            extras.putLong("revision", settingsLoaded ? c.revision : -1);
            context.getContentResolver().call(SettingsProvider.URI, "report", null, extras);
        } catch (Exception | LinkageError e) { XposedBridge.log("EssentialTiling: отчёт недоступен: " + e); }
    }

    private static XC_MethodHook.Unhook hook(ClassLoader loader, String className, String name,
                                             XC_MethodHook callback, Class<?>... args) throws Exception {
        Method method = Class.forName(className, false, loader).getDeclaredMethod(name, args);
        method.setAccessible(true);
        return XposedBridge.hookMethod(method, callback);
    }

    private static Field field(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name); f.setAccessible(true); return f;
    }

    private static void issue(String message, Throwable e) {
        synchronized (issues) { if (issues.size() < 12) issues.add(message + ": " + e.getClass().getSimpleName()); }
        XposedBridge.log("EssentialTiling: " + message + ": " + e);
    }
}
