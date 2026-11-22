package moe.xWey10.tiling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native image layers, snapped to the same cells as Nothing's Workspace. */
public final class PreviewView extends View {
    interface Listener {
        void edited(QsLayout layout);
        void selected(QsLayout.Item item);
        void message(String text);
        default void history(boolean undo,boolean redo) { }
        default void focus(String id) { }
        default void remove(String id) { }
        default void empty(long page, int x, int y) { }
        default void inspect(QsLayout.Item item) { }
    }
    private record Sprite(String id, Rect source, RectF bounds) { }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Sprite> sprites = new ArrayList<>();
    private final HashMap<String, TileImage> images = new HashMap<>();
    private final LinkedHashMap<String, TileVisual> visuals = new LinkedHashMap<>();
    private final Rect baseSource = new Rect();
    private final RectF resizeStart = new RectF(), resizeBounds = new RectF();
    private final RectF destination = new RectF(), before = new RectF(), after = new RectF(), selection = new RectF(), hitArea = new RectF();
    private final ThemePalette palette;
    private Bitmap frame;
    private int baseHeight, page, edges;
    private float gridX, gridY, cellWidth, cellHeight, downX, downY, dragX, dragY;
    private boolean rtl, busy, dragging, moved, invalid;
    private boolean pressedRemove;
    private boolean formsSupported, cellFormsSupported;
    private String formsError="";
    private TileExtrusion.Handle branchHandle;
    private float branchCount;
    private int diagonalEnd;
    private final Matrix resizeInverse=new Matrix();
    private Path resizePath;
    private RectF resizeBody;
    private Matrix resizeContent;
    private boolean rotating;
    private float rotationStart;
    private boolean notchGesture, innerNotch;
    private int shapeCorner;
    private float visualCutW, visualCutH;
    private String pendingRemoval;
    private String selected, pressed;
    private QsLayout layout, rendered, working;
    private boolean localDraft;
    private float catalogueProgress;
    private final RectF insertionBounds=new RectF();
    private long insertionPage=-1;private int insertionX=-1,insertionY=-1;
    void setInsertionCell(long page,int x,int y) { insertionPage=page;insertionX=x;insertionY=y;invalidate(); }
    void setCatalogueProgress(float value) { catalogueProgress=Math.max(0,Math.min(1,value));invalidate(); }
    private boolean resetVariants;
    boolean hasVariants(String id) { return images.containsKey(key(id,1,1)+"#flat")&&images.containsKey(key(id,2,1)+"#flat"); }
    void clearLocalDraft() { localDraft=false;history.clear();notifyHistory(); }
    private QsLayout.Item anchor, proposed;
    private final LayoutHistory history=new LayoutHistory();
    private Listener listener;
    private Config config = Config.DEFAULT;
    private final EditorAccessibility accessibility = new EditorAccessibility();
    private final Runnable longPress = () -> {
        if (pressed == null || layout == null || busy) return;
        select(pressed); anchor = layout.item(pressed); dragging = true; edges = 0;
        working = layout; proposed = anchor; invalid = false;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        intercept(true); syncVisuals(120); invalidate();
    };
    public PreviewView(Context context) {
        super(context); palette = new ThemePalette(context);
        setContentDescription("Предпросмотр текущей шторки SystemUI");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); setFocusable(true);
        androidx.core.view.ViewCompat.setAccessibilityDelegate(this,accessibility);
    }
    void setListener(Listener value) { listener = value; }
    void setBusy(boolean value) { busy = value; if (value) cancelGesture(); accessibility.invalidateRoot();invalidate(); }
    public void setConfig(Config value) {
        if(!config.sameValues(value)) resetVariants=true;
        config=value;
    }
    Config getConfig() { return config; }
    public void setFrame(Bitmap value) { setFrame(value, Bundle.EMPTY); }
    void setFrame(Bitmap value, Bundle metadata) {
        try {
            int nextHeight = value == null ? 0 : metadata.getInt("baseHeight", value.getHeight());
            int nextPage = metadata.getInt("page");
            QsLayout next = null;
            ArrayList<Sprite> nextSprites = new ArrayList<>();
            HashMap<String, TileImage> nextImages = new HashMap<>();
            String json = metadata.getString("layout", "");
            if (!json.isEmpty()) {
                next = QsLayout.parse(json);
                if (value == null || nextHeight < 1 || nextHeight > value.getHeight() || metadata.getFloat("cellWidth") <= 0
                        || metadata.getFloat("cellHeight") <= 0 || nextPage < 0 || nextPage >= next.pages.size()) throw new IllegalArgumentException();
                JSONArray array = new JSONArray(metadata.getString("regions", "[]"));
                for (int n = 0; n < array.length(); n++) {
                    JSONObject r = array.getJSONObject(n); int x = r.getInt("sx"), y = r.getInt("sy"), w = r.getInt("sw"), h = r.getInt("sh");
                    Rect source = checked(value, nextHeight, x, y, w, h); String id = r.getString("id");
                    QsLayout.Item item = next.item(id); if (item == null) throw new IllegalArgumentException();
                    RectF bounds = new RectF((float) r.getDouble("left"), (float) r.getDouble("top"),
                            (float) r.getDouble("right"), (float) r.getDouble("bottom"));
                    nextSprites.add(new Sprite(id, source, bounds));
                    TileImage nativeImage=new TileImage(value, source, null, item.w(), item.h(), bounds.width(), bounds.height());
                    nativeImage.form=item.form(); nextImages.put(key(item),nativeImage);
                }
                JSONArray variants = new JSONArray(metadata.getString("variants", "[]"));
                if (variants.length() > 3) throw new IllegalArgumentException();
                for (int n = 0; n < variants.length(); n++) {
                    JSONObject r = variants.getJSONObject(n); String id = r.getString("id");
                    QsLayout.Item item = next.item(id); int w = r.getInt("w"), h = r.getInt("h");
                    if (item == null || !item.editable() || !item.permits(w, h)) throw new IllegalArgumentException();
                    Rect bg=region(value,nextHeight,r.getJSONArray("background"));
                    TileImage flat=new TileImage(value,region(value,nextHeight,r.getJSONArray("image")),bg,w,h); nextImages.put(key(id,w,h)+"#flat",flat);
                    TileImage original=nextImages.get(key(id,w,h));
                    TileImage variant=new TileImage(value,original==null?region(value,nextHeight,r.getJSONArray("image")):original.source,bg,w,h,
                            original==null?r.getJSONArray("image").getInt(2):original.width,original==null?r.getJSONArray("image").getInt(3):original.height);
                    if(original!=null) variant.form=original.form;
                    nextImages.put(key(id,w,h),variant);
                }
            }
            boolean compatible = frame != null && value != null && layout != null && next != null
                    && layout.base.equals(next.base) && page == nextPage && frame.getWidth() == value.getWidth()
                    && baseHeight == nextHeight && cellWidth == metadata.getFloat("cellWidth") && cellHeight == metadata.getFloat("cellHeight")
                    && gridX == metadata.getFloat("gridX") && gridY == metadata.getFloat("gridY") && rtl == metadata.getBoolean("rtl");
            // A warm response may arrive while the finger is moving. It only supplies new images.
            if (compatible && dragging && next.differsFrom(layout)) return;
            if (!compatible) { endGesture(); visuals.clear(); }
            boolean retain=localDraft&&layout!=null&&next!=null&&layout.base.equals(next.base)&&layout.pages.equals(next.pages)&&layout.portrait==next.portrait;
            if(!retain) localDraft=false;
            if(!dragging&&!retain) layout=next;
            formsSupported=metadata.getBoolean("formsSupported")&&moduleMatches(metadata);
            cellFormsSupported=formsSupported&&metadata.getBoolean("cellFormsSupported");
            formsError=capabilityMessage(metadata);
            rendered = next; frame = value; baseHeight = nextHeight; page = nextPage;
            gridX = metadata.getFloat("gridX"); gridY = metadata.getFloat("gridY");
            cellWidth = metadata.getFloat("cellWidth"); cellHeight = metadata.getFloat("cellHeight"); rtl = metadata.getBoolean("rtl");
            if(compatible&&!resetVariants) {
                java.util.IdentityHashMap<Bitmap,Boolean> atlases=new java.util.IdentityHashMap<>();atlases.put(value,true);long bytes=value.getAllocationByteCount();int kept=0;
                for(var entry:images.entrySet()) if(entry.getKey().endsWith("#flat")&&kept<24) {
                    Bitmap atlas=entry.getValue().atlas;if(!atlases.containsKey(atlas)) { if(bytes+atlas.getAllocationByteCount()>48L*1024*1024) continue;atlases.put(atlas,true);bytes+=atlas.getAllocationByteCount(); }
                    kept++;nextImages.putIfAbsent(entry.getKey(),entry.getValue());String plain=entry.getKey().substring(0,entry.getKey().length()-5);nextImages.putIfAbsent(plain,entry.getValue());
                }
            }
            resetVariants=false;
            sprites.clear(); sprites.addAll(nextSprites); images.clear(); images.putAll(nextImages);
            pendingRemoval = null;
            for (Sprite sprite : sprites) if (!visuals.containsKey(sprite.id)) {
                visuals.put(sprite.id, new TileVisual(sprite.bounds, images.get(key(rendered.item(sprite.id)))));
            }
            syncVisuals(compatible ? 180 : 0);
        } catch (org.json.JSONException | RuntimeException e) {
            endGesture(); frame = null; baseHeight = 0; sprites.clear(); images.clear(); visuals.clear(); layout = rendered = null;
            if (listener != null) listener.message("Не удалось прочитать сетку предпросмотра.");
        }
        if (layout == null || layout.item(selected) == null || layout.item(selected).page() != layout.pages.get(page).id()) selected = null;
        history.accept(layout);notifyHistory();notifySelection(); requestLayout(); invalidate();
    }
    private static boolean moduleMatches(Bundle metadata) {
        String version=metadata.getString("moduleVersion",""); return version.isEmpty()||version.equals(BuildConfig.VERSION_NAME);
    }
    static String capabilityMessage(Bundle metadata) {
        if(!moduleMatches(metadata)) return "Загружен модуль "+metadata.getString("moduleVersion")+". Перезапустите SystemUI для "+BuildConfig.VERSION_NAME+".";
        if(metadata.getBoolean("formsSupported")&&metadata.getBoolean("cellFormsSupported")) return "";
        String reason=metadata.getString("formsError","");
        if(!reason.isBlank()) return "Формы недоступны: "+reason;
        String version=metadata.getString("moduleVersion","");
        return version.isEmpty()?"Модуль SystemUI старее приложения. Перезапустите SystemUI.":"Модуль "+version+": формы недоступны. Перезапустите SystemUI.";
    }
    String capabilityMessage() { return formsError; }
    private static Rect checked(Bitmap value, int baseHeight, int x, int y, int w, int h) {
        if (x < 0 || y < baseHeight || w < 1 || h < 1 || x + w > value.getWidth() || y + h > value.getHeight()) throw new IllegalArgumentException();
        return new Rect(x, y, x + w, y + h);
    }
    private static Rect region(Bitmap value, int baseHeight, JSONArray a) throws org.json.JSONException {
        return checked(value, baseHeight, a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3));
    }
    private static String key(QsLayout.Item i) { return key(i.id(), i.w(), i.h()); }
    private static String key(String id, int w, int h) { return id + "/" + w + "x" + h; }
    QsLayout getLayoutDraft() { return layout; }
    private float imageScale() { return frame == null ? 1 : (float) getWidth() / frame.getWidth(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(Math.round(dp(360)), widthSpec);
        int height = frame == null ? Math.round(dp(72)) : Math.round((float) width * baseHeight / frame.getWidth());
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }
    private RectF cells(QsLayout.Item i, RectF out) {
        QsLayout.Page p = layout.page(i.page()); int col = rtl ? p.columns() - i.x() - i.w() : i.x();
        out.set(gridX + col * cellWidth, gridY + i.y() * cellHeight,
                gridX + (col + i.w()) * cellWidth, gridY + (i.y() + i.h()) * cellHeight); return out;
    }
    private RectF tileBounds(Sprite sprite, QsLayout.Item target) {
        QsLayout.Item old = rendered.item(sprite.id);
        if (old == null || target == null || old.samePlace(target)) { destination.set(sprite.bounds); return destination; }
        cells(old, before); cells(target, after); float factor = config.enabled ? config.scalePercent / 100f : 1f;
        TileImage image = images.get(key(target));
        float w = image == null ? Math.max(1, sprite.bounds.width() + (target.w() - old.w()) * cellWidth * factor) : image.width;
        float h = image == null ? Math.max(1, sprite.bounds.height() + (target.h() - old.h()) * cellHeight * factor) : image.height;
        float cx = after.centerX() + sprite.bounds.centerX() - before.centerX();
        float cy = after.centerY() + sprite.bounds.centerY() - before.centerY();
        destination.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2); return destination;
    }
    private RectF bodyBounds(QsLayout.Item item,RectF bounds,float x,float y) {
        RectF body=item.form().content(bounds,item.w(),item.h(),x,y);
        if(rtl) body.set(bounds.left+bounds.right-body.right,body.top,bounds.left+bounds.right-body.left,body.bottom);
        return body;
    }
    private void syncVisuals(long duration) { syncVisuals(duration,false); }
    private void syncVisuals(long duration,boolean fixedRotation) {
        if (layout == null) return;
        long now = SystemClock.uptimeMillis(); boolean animate = ValueAnimator.areAnimatorsEnabled();
        QsLayout shown = dragging && working != null ? working : layout;
        for (Sprite sprite : sprites) {
            TileVisual visual = visuals.get(sprite.id); if (visual == null) continue;
            boolean active = dragging && sprite.id.equals(selected);
            QsLayout.Item target = active && proposed != null ? proposed : shown.item(sprite.id);
            TileImage image = images.get(key(active&&branchHandle!=null?anchor:target));
            if(target.form().cut()&&image!=null&&(!image.form.equals(target.form())||target.form().transformed())) {
                TileContentSize body=TileContentSize.of(target);
                TileImage bodyImage=images.get(key(target.id(),body.width(),body.height())+"#flat");
                if(bodyImage!=null) image=bodyImage;
            }
            visual.largeContent(TileContentSize.of(target).mode()==2);
            if (image != null) visual.image(image, now, animate && duration > 0);
            tileBounds(sprite, target);
            RectF endpoint=new RectF(destination);
            if (active && edges == 0) {
                tileBounds(sprite, anchor); destination.offset(dragX, dragY);
            } else if (active) {
                destination.set(resizeBounds);
            }
            TileImage empty=images.get(key(target.id(),1,1));
            TileForm form=target.form();
            visual.form(target.w(),target.h(),active&&notchGesture?shapeCorner:form.corner(),
                    active&&notchGesture?visualCutW:form.cutW(),active&&notchGesture?visualCutH:form.cutH(),
                    NativeForms.custom(target)||target.form().cut()||NativeForms.custom(rendered.item(sprite.id))||rendered.item(sprite.id).form().cut()||target.w()!=rendered.item(sprite.id).w()||target.h()!=rendered.item(sprite.id).h(),empty);
            visual.shape(form,rtl); visual.grid(pitch(true),pitch(false),endpoint);
            if(active&&branchHandle!=null) visual.branch(TileExtrusion.rectangles(anchor,branchHandle,branchCount,resizeStart,rtl,resizeStep(true),resizeStep(false)),bodyBounds(anchor,resizeStart,resizeStep(true),resizeStep(false)),editMatrix(anchor,resizeStart));
            else if(active&&resizePath!=null) visual.pose(resizePath,resizeBody,resizeContent);
            visual.motion.move(destination, now, fixedRotation&&sprite.id.equals(selected)||active&&edges!=0 ? 0 : animate ? (active ? 60 : duration) : 0);
            visual.lift(active && edges == 0, now, animate);
        }
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (frame == null) return;
        float scale = imageScale(); canvas.save(); canvas.scale(scale, scale);
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL);
        baseSource.set(0, 0, frame.getWidth(), baseHeight); destination.set(0, 0, frame.getWidth(), baseHeight);
        canvas.drawBitmap(frame, baseSource, destination, paint);
        if (layout != null) {
            QsLayout.Page p = layout.pages.get(page);
            paint.setColor(palette.accent); paint.setAlpha(dragging?100:Math.round(58+18*catalogueProgress)); paint.setStrokeWidth(1 / scale);
            paint.setAntiAlias(false);
            float left = pixelCenter(gridX, scale, getWidth()), top = pixelCenter(gridY, scale, getHeight());
            float right = pixelCenter(gridX + p.columns() * cellWidth, scale, getWidth()), bottom = pixelCenter(gridY + p.rows() * cellHeight, scale, getHeight());
            for (int x = 0; x <= p.columns(); x++) {
                float line = pixelCenter(gridX + x * cellWidth, scale, getWidth()); canvas.drawLine(line, top, line, bottom, paint);
            }
            for (int y = 0; y <= p.rows(); y++) {
                float line = pixelCenter(gridY + y * cellHeight, scale, getHeight()); canvas.drawLine(left, line, right, line, paint);
            }
            paint.setAntiAlias(true);
            if(catalogueProgress>0&&p.id()==insertionPage&&insertionX>=0&&insertionX<p.columns()&&insertionY>=0&&insertionY<p.rows()) {
                int column=rtl?p.columns()-insertionX-1:insertionX;
                RectF cell=insertionBounds;cell.set(gridX+column*cellWidth,gridY+insertionY*cellHeight,gridX+(column+1)*cellWidth,gridY+(insertionY+1)*cellHeight);cell.inset(dp(3)/scale,dp(3)/scale);
                paint.setColor(palette.accent);paint.setAlpha(Math.round(38*catalogueProgress));canvas.drawRoundRect(cell,dp(8)/scale,dp(8)/scale,paint);
                paint.setAlpha(Math.round(220*catalogueProgress));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f)/scale);canvas.drawRoundRect(cell,dp(8)/scale,dp(8)/scale,paint);
                paint.setStrokeWidth(dp(2)/scale);float arm=dp(6)/scale;canvas.drawLine(cell.centerX()-arm,cell.centerY(),cell.centerX()+arm,cell.centerY(),paint);canvas.drawLine(cell.centerX(),cell.centerY()-arm,cell.centerX(),cell.centerY()+arm,paint);paint.setStyle(Paint.Style.FILL);
            }
            long now = SystemClock.uptimeMillis(); boolean moving = false;
            TileVisual raised = null;
            paint.setAlpha(255);
            for (Sprite sprite : sprites) {
                if (sprite.id.equals(pendingRemoval)) continue;
                TileVisual visual = visuals.get(sprite.id); moving |= visual.advance(now);
                if (dragging && sprite.id.equals(selected)) raised = visual;
                else drawLayer(canvas,visual,sprite.id);
            }
            if (raised != null) drawLayer(canvas,raised,selected);
            QsLayout.Item chosen = dragging ? proposed : layout.item(selected);
            if (chosen != null&&catalogueProgress<.999f) {
                if(catalogueProgress>0) canvas.saveLayerAlpha(null,Math.round(255*(1-catalogueProgress)));else canvas.save();
                TileVisual visual = visuals.get(chosen.id());
                RectF r = selection; if (visual != null) r.set(visual.motion.bounds); else cells(chosen, r);
                r.inset(-dp(2) / scale, -dp(2) / scale);
                paint.setAlpha(255); paint.setColor(invalid ? palette.error : palette.accent);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2) / scale);
                if(visual!=null&&(chosen.form().cut()||branchHandle!=null&&dragging)) canvas.drawPath(visibleLayer(chosen.id()),paint);
                else canvas.drawRoundRect(r, dp(10) / scale, dp(10) / scale, paint);
                if (chosen.editable() && layout.portrait) {
                    paint.setStyle(Paint.Style.FILL); float knob = dp(4) / scale;
                    if(!chosen.form().cellular()&&!chosen.form().diagonal()) {
                        RectF base=chosen.form().intrinsic(visual.motion.bounds,chosen.w(),chosen.h(),pitch(true),pitch(false)); base.inset(-dp(2)/scale,-dp(2)/scale);
                        drawEdgeKnob(canvas,chosen,visual.motion.bounds,base.left,base.centerY(),knob);drawEdgeKnob(canvas,chosen,visual.motion.bounds,base.right,base.centerY(),knob);
                        drawEdgeKnob(canvas,chosen,visual.motion.bounds,base.centerX(),base.top,knob);drawEdgeKnob(canvas,chosen,visual.motion.bounds,base.centerX(),base.bottom,knob);drawEdgeKnob(canvas,chosen,visual.motion.bounds,base.right,base.bottom,knob);
                    }
                    if(!dragging&&!chosen.form().diagonal()) for(TileExtrusion.Handle handle:TileExtrusion.handles(chosen)) {
                        if(!localHandle(chosen,handle)) continue;
                        RectF point=TileExtrusion.point(chosen,handle,visual.motion.bounds,rtl,pitch(true),pitch(false));
                        paint.setColor(palette.background); canvas.drawCircle(point.left,point.top,knob*1.8f,paint);
                        paint.setColor(invalid?palette.error:palette.accent); canvas.drawCircle(point.left,point.top,knob*1.3f,paint);
                    }
                    if(!dragging&&chosen.form().diagonal()) {
                        for(int end=1;end<=2;end++) { RectF point=diagonalPoint(chosen,visual.motion.bounds,end);
                            paint.setColor(palette.background); canvas.drawCircle(point.left,point.top,knob*1.8f,paint);
                            paint.setColor(palette.accent); canvas.drawCircle(point.left,point.top,knob*1.3f,paint);
                        }
                    }
                    if(chosen.form().rotation()==0&&chosen.form().corner()>=1&&chosen.form().corner()<=4&&!chosen.form().cellular()&&!chosen.form().nubs()) {
                        RectF cut=chosen.form().notch(visual.motion.bounds,chosen.w(),chosen.h());
                        canvas.drawCircle(chosen.form().left()?cut.right:cut.left,chosen.form().top()?cut.bottom:cut.top,knob*1.5f,paint);
                    }
                    if(!dragging) {
                        RectF knobPoint=rotationPoint(chosen,visual.motion.bounds); paint.setColor(palette.accent);
                        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)/scale);
                        float vx=knobPoint.left-visual.motion.bounds.centerX(),vy=knobPoint.top-visual.motion.bounds.centerY(),length=Math.max(1,(float)Math.hypot(vx,vy));
                        canvas.drawLine(knobPoint.left-vx/length*dp(18)/scale,knobPoint.top-vy/length*dp(18)/scale,knobPoint.left,knobPoint.top,paint);
                        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.background); canvas.drawCircle(knobPoint.left,knobPoint.top,dp(10)/scale,paint);
                        paint.setColor(palette.accent); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)/scale); canvas.drawCircle(knobPoint.left,knobPoint.top,dp(7)/scale,paint); paint.setStyle(Paint.Style.FILL);
                        for(TileSeams.Joint joint:TileSeams.list(chosen.form().seams())) {
                            RectF point=jointPoint(chosen,joint,visual.motion.bounds); float jx=point.left,jy=point.top;
                            paint.setColor(palette.background); canvas.drawCircle(jx,jy,dp(9)/scale,paint); paint.setColor(palette.accent); paint.setStrokeWidth(dp(2)/scale);
                            canvas.drawLine(jx-dp(4)/scale,jy,jx+dp(4)/scale,jy,paint); canvas.drawLine(jx,jy-dp(4)/scale,jx,jy+dp(4)/scale,paint);
                        }
                    }
                    if (!dragging) {
                        float cx = Math.max(dp(8) / scale, visual.motion.bounds.left + dp(2) / scale);
                        float cy = Math.max(dp(8) / scale, visual.motion.bounds.top + dp(2) / scale);
                        paint.setColor(palette.text); canvas.drawCircle(cx, cy, dp(8) / scale, paint);
                        paint.setColor(palette.background); paint.setStrokeWidth(dp(2) / scale);
                        canvas.drawLine(cx - dp(4) / scale, cy, cx + dp(4) / scale, cy, paint);
                    }
                }
                canvas.restore();
            }
            if(catalogueProgress>0&&!dragging) {
                paint.setStyle(Paint.Style.FILL);
                for(Sprite sprite:sprites) { QsLayout.Item item=layout.item(sprite.id);if(item==null||!item.editable()||sprite.id.equals(pendingRemoval)||catalogueProgress<.999f&&sprite.id.equals(selected)) continue;
                    TileVisual visual=visuals.get(sprite.id);float cx=Math.max(dp(8)/scale,visual.motion.bounds.left+dp(2)/scale),cy=Math.max(dp(8)/scale,visual.motion.bounds.top+dp(2)/scale);
                    paint.setColor(palette.text);paint.setAlpha(Math.round(255*catalogueProgress));canvas.drawCircle(cx,cy,dp(8)/scale,paint);paint.setColor(palette.background);paint.setAlpha(Math.round(255*catalogueProgress));paint.setStrokeWidth(dp(2)/scale);canvas.drawLine(cx-dp(4)/scale,cy,cx+dp(4)/scale,cy,paint);
                }
            }
            if (moving) postInvalidateOnAnimation();
        }
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(255); canvas.restore();
    }
    private void drawEdgeKnob(Canvas canvas,QsLayout.Item item,RectF bounds,float x,float y,float radius) { RectF point=TileRotation.point(editMatrix(item,bounds),x,y);canvas.drawCircle(point.left,point.top,radius,paint); }
    private Path visibleLayer(String id) {
        QsLayout shown=dragging&&working!=null?working:layout; QsLayout.Item item=shown.item(id); TileVisual lower=visuals.get(id);
        Path result=lower.outline(lower.motion.bounds,lower.image().radius);
        for(Sprite sprite:sprites) { if(sprite.id.equals(id)||sprite.id.equals(pendingRemoval)) continue;
            QsLayout.Item upper=shown.item(sprite.id); if(upper==null||!TileLayers.stackable(item,upper)||!TileLayers.above(upper,item)) continue;
            TileVisual top=visuals.get(sprite.id); TileLayers.subtract(result,top.outline(top.motion.bounds,top.image().radius),Math.min(pitch(true),pitch(false))*.06f);
        } return result;
    }
    private void drawLayer(Canvas canvas,TileVisual visual,String id) {
        canvas.save();
        QsLayout shown=dragging&&working!=null?working:layout; QsLayout.Item item=shown.item(id);
        for(Sprite sprite:sprites) { QsLayout.Item upper=shown.item(sprite.id); if(sprite.id.equals(id)||sprite.id.equals(pendingRemoval)||upper==null||!TileLayers.stackable(item,upper)||!TileLayers.above(upper,item)) continue;
            TileVisual top=visuals.get(sprite.id); canvas.clipOutPath(TileRotation.expand(top.outline(top.motion.bounds,top.image().radius),Math.min(pitch(true),pitch(false))*.06f));
        }
        visual.draw(canvas,paint); canvas.restore();
    }
    private static float pixelCenter(float value, float scale, int extent) {
        return Math.max(.5f, Math.min(extent - .5f, (float) Math.floor(value * scale) + .5f)) / scale;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (layout == null || busy || !layout.portrait || !isEnabled()) return false;
        float x = event.getX() / imageScale(), y = event.getY() / imageScale();
        if (event.getPointerCount() != 1) { cancelGesture(); return true; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y; dragX = dragY = 0; moved = false; invalid = false; edges = 0;
                branchHandle=null; branchCount=0; diagonalEnd=0; rotating=false; resizePath=null;
                notchGesture=false; innerNotch=false; shapeCorner=0; visualCutW=visualCutH=0;
                if(catalogueProgress>=.999f) for(Sprite sprite:sprites) { QsLayout.Item item=layout.item(sprite.id);if(item!=null&&item.editable()&&removeHit(sprite.id,x,y)) { select(sprite.id);pressedRemove=true;intercept(true);return true; } }
                QsLayout.Item chosen = layout.item(selected);
                if (chosen != null && chosen.editable() && removeHit(x, y)) {
                    pressedRemove = true; intercept(true); return true;
                }
                if (chosen != null && chosen.editable()&&catalogueProgress<.999f) {
                    TileVisual visual = visuals.get(chosen.id());
                    if (visual != null) visual.advance(SystemClock.uptimeMillis());
                    RectF bounds=visual == null ? cells(chosen, hitArea) : visual.motion.bounds;
                    RectF rotate=rotationPoint(chosen,bounds);
                    if(Math.hypot(x-rotate.left,y-rotate.top)<dp(14)/imageScale()) {
                        if(!cellFormsSupported) { if(listener!=null) listener.message(formsError); return true; }
                        rotating=true; edges=128; rotationStart=(float)Math.atan2(y-bounds.centerY(),x-bounds.centerX());
                    }
                    if(!rotating) for(TileSeams.Joint joint:TileSeams.list(chosen.form().seams())) {
                        RectF point=jointPoint(chosen,joint,bounds);
                        if(Math.hypot(x-point.left,y-point.top)<dp(12)/imageScale()) { mergeJoint(chosen,joint); moved=true; pressed=null; return true; }
                    }
                    if(!rotating&&chosen.form().diagonal()) for(int end:new int[]{1,2}) { RectF point=diagonalPoint(chosen,bounds,end);
                        if(Math.hypot(x-point.left,y-point.top)<dp(15)/imageScale()) { diagonalEnd=end; edges=64; break; }
                    }
                    TileExtrusion.Handle local=rotating||chosen.form().diagonal()?null:hitBranch(chosen,bounds,x,y);
                    if((local!=null||diagonalEnd!=0)&&!cellFormsSupported) {
                        moved=true; pressed=null;
                        if(listener!=null) listener.message(formsError);
                        return true;
                    }
                    branchHandle=local;
                    if(branchHandle!=null) edges=32;
                    if(edges==0&&chosen.form().rotation()==0&&chosen.form().corner()>=1&&chosen.form().corner()<=4&&!chosen.form().cellular()&&!chosen.form().nubs()) {
                        RectF cut=chosen.form().notch(bounds,chosen.w(),chosen.h());
                        if(Math.hypot(x-(chosen.form().left()?cut.right:cut.left),y-(chosen.form().top()?cut.bottom:cut.top)) < dp(14)/imageScale()) {
                            edges=16; innerNotch=true; notchGesture=true; shapeCorner=chosen.form().corner();
                        }
                    }
                    if(edges==0&&!chosen.form().cellular()&&!chosen.form().diagonal()) {
                        Matrix inverse=new Matrix();editMatrix(chosen,bounds).invert(inverse);RectF point=TileRotation.point(inverse,x,y);
                        edges=hitEdges(chosen.form().intrinsic(bounds,chosen.w(),chosen.h(),pitch(true),pitch(false)),point.left,point.top);
                    }
                }
                if(edges!=0&&(chosen.form().transformed()||chosen.form().baseW()>0)&&!cellFormsSupported) { if(listener!=null) listener.message(formsError);edges=0;moved=true;pressed=null;return true; }
                if (edges != 0) {
                    anchor = proposed = chosen; working = layout; dragging = true; intercept(true);
                    TileVisual visual = visuals.get(chosen.id());
                    resizeStart.set(visual == null ? cells(chosen, hitArea) : visual.motion.bounds);
                    resizeBounds.set(resizeStart); editMatrix(chosen,resizeStart).invert(resizeInverse);
                    if(branchHandle==null&&!innerNotch && (edges&5)!=0 && (edges&10)!=0) shapeCorner=(edges&2)!=0?((edges&1)!=0?1:2):((edges&4)!=0?3:4);
                    visualCutW=anchor.form().cutW(); visualCutH=anchor.form().cutH();
                    if (listener != null) listener.focus(chosen.id());
                    performHapticFeedback(HapticFeedbackConstants.GESTURE_START); return true;
                }
                pressed = hit(x, y);
                if (pressed != null && layout.item(pressed).editable()) {
                    if (listener != null) listener.focus(pressed);
                    postDelayed(longPress, Math.min(300, ViewConfiguration.getLongPressTimeout()));
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressedRemove) return true;
                if (dragging) updateGesture(x, y);
                else if (Math.hypot(x - downX, y - downY) * imageScale() > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    removeCallbacks(longPress); moved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (pressedRemove) {
                    boolean remove = removeHit(x, y); pressedRemove = false; intercept(false);
                    if (remove && listener != null) listener.remove(selected);
                    performClick(); return true;
                }
                if (dragging) {
                    updateGesture(x, y);
                    QsLayout result = invalid ? null : working;
                    if (invalid && listener != null) listener.message(rotating?"Для этого угла не хватает места вокруг центра. Переместите плитку дальше от края.":"Недостаточно места в сетке.");
                    if(result!=null&&branchHandle!=null&&result.differsFrom(layout)) {
                        for(Sprite sprite:sprites) if(sprite.id.equals(selected)) {
                            QsLayout.Item end=result.item(selected); RectF endpoint=new RectF(tileBounds(sprite,end));
                            TileVisual visual=visuals.get(selected);
                            visual.settle(anchor,branchHandle,branchCount,Math.round(branchCount),resizeStart,
                                    bodyBounds(anchor,resizeStart,resizeStep(true),resizeStep(false)),bodyBounds(end,endpoint,pitch(true),pitch(false)),
                                    resizeStep(true),resizeStep(false),rtl,editMatrix(anchor,resizeStart),contentMatrix(end,endpoint),SystemClock.uptimeMillis(),ValueAnimator.areAnimatorsEnabled()?220:0);
                        }
                    }
                    if(result!=null&&resizePath!=null&&result.differsFrom(layout)) {
                        QsLayout.Item end=result.item(selected); for(Sprite sprite:sprites) if(sprite.id.equals(selected)) {
                            RectF endpoint=new RectF(tileBounds(sprite,end)),endBody=bodyBounds(end,endpoint,pitch(true),pitch(false));
                            RectF from=TileRotation.point(resizeContent,resizeBody.centerX(),resizeBody.centerY()),to=TileRotation.point(contentMatrix(end,endpoint),endBody.centerX(),endBody.centerY());
                            visuals.get(selected).settlePose(to.left-from.left,to.top-from.top,SystemClock.uptimeMillis(),ValueAnimator.areAnimatorsEnabled()?220:0);
                        }
                    }
                    boolean wasRotating=rotating;
                    endGesture();
                    if (result != null && result.differsFrom(layout)) commit(result,wasRotating); else syncVisuals(220);
                } else if (!moved) {
                    select(pressed); performClick();
                    if (catalogueProgress>=.999f && pressed!=null && listener!=null) {
                        QsLayout.Item item=layout.item(pressed);
                        if(item!=null && item.editable())listener.inspect(item);
                    }
                    if (pressed == null && listener != null) {
                        QsLayout.Page p = layout.pages.get(page);
                        int col = (int) Math.floor((x - gridX) / cellWidth), row = (int) Math.floor((y - gridY) / cellHeight);
                        if (col >= 0 && row >= 0 && col < p.columns() && row < p.rows() && hit(x, y) == null)
                            listener.empty(p.id(), rtl ? p.columns() - col - 1 : col, row);
                    }
                }
                pressed = null; return true;
            case MotionEvent.ACTION_CANCEL: cancelGesture(); return true;
            default: return true;
        }
    }
    void pendingRemoval(String id) { pendingRemoval = id; if (id != null) select(null); invalidate(); }
    private boolean removeHit(float x,float y) { return removeHit(selected,x,y); }
    private boolean removeHit(String id,float x,float y) {
        TileVisual visual = visuals.get(id); if (visual == null) return false;
        visual.advance(SystemClock.uptimeMillis());
        float scale = imageScale(), cx = Math.max(dp(8) / scale, visual.motion.bounds.left + dp(2) / scale);
        float cy = Math.max(dp(8) / scale, visual.motion.bounds.top + dp(2) / scale);
        return Math.hypot(x - cx, y - cy) <= dp(12) / scale;
    }
    private boolean localHandle(QsLayout.Item item,TileExtrusion.Handle h) {
        return item.form().geometry() || ((h.edge()&10)!=0?item.form().columns(item.w())>1:item.form().rows(item.h())>1);
    }
    private TileExtrusion.Handle hitBranch(QsLayout.Item item,RectF bounds,float x,float y) {
        TileExtrusion.Handle best=null; float distance=dp(13)/imageScale();
        for(TileExtrusion.Handle h:TileExtrusion.handles(item)) {
            if(!localHandle(item,h)) continue;
            RectF pt=TileExtrusion.point(item,h,bounds,rtl,pitch(true),pitch(false)); float d=(float)Math.hypot(x-pt.left,y-pt.top);
            if(d<distance) { best=h; distance=d; }
        }
        return best;
    }
    private int hitEdges(RectF r, float x, float y) {
        float t = dp(12) / imageScale(); if (x < r.left - t || x > r.right + t || y < r.top - t || y > r.bottom + t) return 0;
        int result = 0;
        if (Math.abs(x - r.left) < t) result |= 1; else if (Math.abs(x - r.right) < t) result |= 4;
        if (Math.abs(y - r.top) < t) result |= 2; else if (Math.abs(y - r.bottom) < t) result |= 8;
        return result;
    }
    private String hit(float x,float y) {
        QsLayout.Page p=layout.pages.get(page);
        int col=(int)Math.floor((x-gridX)/cellWidth),row=(int)Math.floor((y-gridY)/cellHeight);
        if(col<0||row<0||col>=p.columns()||row>=p.rows()) return null;
        if(rtl) col=p.columns()-col-1;
        QsLayout.Item best=null;
        for(QsLayout.Item i:layout.items) if(i.page()==p.id()&&i.occupies(col,row)) {
            boolean masked=i.form().transformed()||i.form().layer()!=0;
            for(QsLayout.Item upper:layout.items) if(upper.page()==i.page()&&TileLayers.stackable(i,upper)&&TileLayers.above(upper,i)) { masked=true; break; }
            if(masked&&!TileLayers.contains(visibleLayer(i.id()),x,y)) continue;
            if(best==null||TileLayers.above(i,best)) best=i;
        }
        if(best!=null) return best.id();
        return null;
    }
    private void updateGesture(float x, float y) {
        if(edges!=0&&!rotating) { float[] delta={x-downX,y-downY};resizeInverse.mapVectors(delta);x=downX+delta[0];y=downY+delta[1]; }
        float dx = (x - downX) / cellWidth * (rtl ? -1 : 1), dy = (y - downY) / cellHeight;
        int col = anchor.x(), row = anchor.y(), w = anchor.w(), h = anchor.h(); QsLayout.Page p = layout.page(anchor.page());
        if (edges == 0) {
            col = Math.max(0, Math.min(p.columns() - w, col + Math.round(dx)));
            row = Math.max(0, Math.min(p.rows() - h, row + Math.round(dy)));
            float logicalX = Math.max(-anchor.x(), Math.min(p.columns() - anchor.x() - w, dx));
            float logicalY = Math.max(-anchor.y(), Math.min(p.rows() - anchor.y() - h, dy));
            dragX = logicalX * cellWidth * (rtl ? -1 : 1); dragY = logicalY * cellHeight;
        } else {
            if(rotating) {
                float angle=(float)Math.atan2(y-resizeStart.centerY(),x-resizeStart.centerX());
                float delta=(float)Math.toDegrees(angle-rotationStart)*(rtl?-1:1);
                TileForm f=anchor.form().rotated(TileRotation.snap(anchor.form().rotation()+delta));
                proposed=TileFrame.rotate(anchor,f.rotation(),p,resizeStart,pitch(true),pitch(false),cellWidth,cellHeight,visuals.get(selected).image().radius,rtl); working=proposed==null?null:layout.edit(anchor.id(),proposed.x(),proposed.y(),proposed.w(),proposed.h(),proposed.form());invalid=working==null;
                if(proposed==null) proposed=anchor;
                setPose(f,resizeStart,anchor.w(),anchor.h());syncVisuals(0);return;
            }
            if(diagonalEnd!=0) {
                updateDiagonal(x,y,p); syncVisuals(200); return;
            }
            if(branchHandle!=null) {
                float amount=switch(branchHandle.edge()) {
                    case 1->(downX-x)*(rtl?-1:1)/resizeStep(true);
                    case 4->(x-downX)*(rtl?-1:1)/resizeStep(true);
                    case 2->(downY-y)/resizeStep(false);
                    default->(y-downY)/resizeStep(false);
                };
                QsLayout.Item base=TileFrame.intrinsic(anchor);int maximum=0;
                for(int n=1;n<=24;n++) { QsLayout.Item grown=TileExtrusion.grow(base,branchHandle,n);if(grown==null||reframe(grown,false)==null) break;maximum=n; }
                branchCount=Math.max(-TileExtrusion.minimum(anchor,branchHandle),Math.min(maximum,amount));
                QsLayout.Item changed=TileExtrusion.grow(base,branchHandle,Math.round(branchCount));proposed=changed==null?null:reframe(changed,false);
                working=proposed==null?null:layout.edit(anchor.id(),proposed.x(),proposed.y(),proposed.w(),proposed.h(),proposed.form());invalid=working==null;if(proposed==null) proposed=anchor;
                Path path=TileOutline.path(TileExtrusion.rectangles(anchor,branchHandle,branchCount,resizeStart,rtl,resizeStep(true),resizeStep(false)),visuals.get(selected).image().radius);path.transform(editMatrix(anchor,resizeStart));resizeBounds.set(TileFrame.bounds(path));
                syncVisuals(200);return;
            }
            if(anchor.form().rotation()!=0||anchor.form().baseW()>0) { updateRotatedBox(x,y);syncVisuals(200);return; }
            boolean left = (edges & (rtl ? 4 : 1)) != 0, right = (edges & (rtl ? 1 : 4)) != 0;
            float stepX = resizeStep(true), stepY = resizeStep(false);
            boolean cutLeft=shapeCorner==1||shapeCorner==4, cutTop=shapeCorner==1||shapeCorner==2;
            float inX=(x-downX)*(cutLeft?1:-1),inY=(y-downY)*(cutTop?1:-1);
            if(!notchGesture && shapeCorner>0 && formsSupported && anchor.w()>=2 && anchor.h()>=2 && inX>stepX*.18f && inY>stepY*.18f) notchGesture=true;
            if(notchGesture) {
                float shapeStepX=resizeStart.width()/anchor.w(),shapeStepY=resizeStart.height()/anchor.h();
                visualCutW=Math.max(0,Math.min(anchor.w()-1,(innerNotch?anchor.form().cutW():0)+inX/shapeStepX));
                visualCutH=Math.max(0,Math.min(anchor.h()-1,(innerNotch?anchor.form().cutH():0)+inY/shapeStepY));
                int cw=Math.round(visualCutW),ch=Math.round(visualCutH);
                TileForm form=cw==0||ch==0?TileForm.RECT:new TileForm(shapeCorner,cw,ch);
                proposed=anchor.shaped(form); working=layout.edit(anchor.id(),col,row,w,h,form); invalid=working==null;
                resizeBounds.set(resizeStart); syncVisuals(200); return;
            }
            float pixelX = (x - downX) * (rtl ? -1 : 1);
            int maxW=left?anchor.x()+anchor.w():p.columns()-anchor.x(),maxH=(edges&2)!=0?anchor.y()+anchor.h():p.rows()-anchor.y();
            if(!formsSupported) { maxW=Math.min(2,maxW); maxH=Math.min(anchor.tall()?2:1,maxH); }
            float wantedW = Math.max(1, Math.min(maxW, anchor.w() + (left ? -pixelX : right ? pixelX : 0) / stepX));
            float wantedH = Math.max(1, Math.min(maxH, anchor.h() + ((edges & 2) != 0 ? downY - y : (edges & 8) != 0 ? y - downY : 0) / stepY));
            resizeBounds.set(resizeStart);
            float widthChange = (wantedW - anchor.w()) * stepX, heightChange = (wantedH - anchor.h()) * stepY;
            if ((edges & 1) != 0) resizeBounds.left -= widthChange;
            else if ((edges & 4) != 0) resizeBounds.right += widthChange;
            if ((edges & 2) != 0) resizeBounds.top -= heightChange;
            else if ((edges & 8) != 0) resizeBounds.bottom += heightChange;
            w = Math.max(1, Math.min(maxW, Math.round(wantedW)));
            h = Math.max(1, Math.min(maxH, Math.round(wantedH)));
            if(!formsSupported&&h==2&&w==1) w=2;
            if (left) col = anchor.x() + anchor.w() - w;
            if ((edges & 2) != 0) row = anchor.y() + anchor.h() - h;
        }
        proposed = anchor.at(col, row, w, h); working = layout.edit(anchor.id(), col, row, w, h); invalid = working == null;
        syncVisuals(200);
    }
    private Matrix editMatrix(QsLayout.Item item,RectF bounds) { return TileRotation.extra(item.form(),bounds,item.w(),item.h(),pitch(true),pitch(false),rtl); }
    private Matrix contentMatrix(QsLayout.Item item,RectF bounds) { return TileRotation.content(item.form(),bounds,item.w(),item.h(),pitch(true),pitch(false),rtl); }
    private RectF jointPoint(QsLayout.Item item,TileSeams.Joint joint,RectF bounds) {
        RectF raw=item.form().intrinsic(bounds,item.w(),item.h(),pitch(true),pitch(false));RectF gap=TileSeams.gap(item.form(),joint,raw,item.form().columns(item.w()),item.form().rows(item.h()),pitch(true),pitch(false));float x=rtl?bounds.left+bounds.right-gap.centerX():gap.centerX();return TileRotation.point(editMatrix(item,bounds),x,gap.centerY());
    }
    private QsLayout.Item reframe(QsLayout.Item base,boolean clamp) { return TileFrame.reframe(anchor,base,layout.page(anchor.page()),resizeStart,pitch(true),pitch(false),cellWidth,cellHeight,visuals.get(selected).image().radius,rtl,clamp); }
    private void setPose(TileForm form,RectF frame,int w,int h) {
        resizePath=form.basePath(frame,w,h,pitch(true),pitch(false),visuals.get(selected).image().radius);
        if(rtl) { Matrix mirror=new Matrix();mirror.setScale(-1,1,frame.centerX(),frame.centerY());resizePath.transform(mirror); }
        resizePath.transform(TileRotation.extra(form,frame,w,h,pitch(true),pitch(false),rtl));resizeBody=form.content(frame,w,h,pitch(true),pitch(false));if(rtl) resizeBody.set(2*frame.centerX()-resizeBody.right,resizeBody.top,2*frame.centerX()-resizeBody.left,resizeBody.bottom);
        resizeContent=TileRotation.content(form,frame,w,h,pitch(true),pitch(false),rtl);resizeBounds.set(TileFrame.bounds(resizePath));
    }
    private void updateRotatedBox(float x,float y) {
        QsLayout.Item base=TileFrame.intrinsic(anchor);boolean left=(edges&(rtl?4:1))!=0,right=(edges&(rtl?1:4))!=0;
        float ww=Math.max(1,Math.min(24,base.w()+(left?-(x-downX):right?x-downX:0)/pitch(true)*(rtl?-1:1))),hh=Math.max(1,Math.min(24,base.h()+((edges&2)!=0?downY-y:(edges&8)!=0?y-downY:0)/pitch(false)));
        int w=Math.round(ww),h=Math.round(hh);QsLayout.Item changed=base.at(left?base.w()-w:0,(edges&2)!=0?base.h()-h:0,w,h);proposed=reframe(changed,false);working=proposed==null?null:layout.edit(anchor.id(),proposed.x(),proposed.y(),proposed.w(),proposed.h(),proposed.form());invalid=working==null;if(proposed==null) proposed=anchor;
        RectF raw=anchor.form().intrinsic(resizeStart,anchor.w(),anchor.h(),pitch(true),pitch(false));if((edges&1)!=0) raw.left-=(ww-base.w())*pitch(true);else if((edges&4)!=0) raw.right+=(ww-base.w())*pitch(true);if((edges&2)!=0) raw.top-=(hh-base.h())*pitch(false);else if((edges&8)!=0) raw.bottom+=(hh-base.h())*pitch(false);
        TileForm f=base.form();resizePath=f.rawPath(raw,w,h,pitch(true),pitch(false),visuals.get(selected).image().radius);resizeBody=new RectF(raw);resizeContent=editMatrix(anchor,resizeStart);resizePath.transform(resizeContent);resizeBounds.set(TileFrame.bounds(resizePath));
    }
    private RectF rotationPoint(QsLayout.Item item,RectF bounds) {
        float angle=TileRotation.baseAngle(item.form(),bounds,item.w(),item.h(),pitch(true),pitch(false))+item.form().rotation();
        RectF intrinsic=item.form().intrinsic(bounds,item.w(),item.h(),pitch(true),pitch(false));
        if(rtl) angle=-angle; double radians=Math.toRadians(angle-90); float distance=Math.min(intrinsic.width(),intrinsic.height())/2+dp(20)/imageScale();
        float x=bounds.centerX()+(float)Math.cos(radians)*distance,y=bounds.centerY()+(float)Math.sin(radians)*distance,margin=dp(10)/imageScale();
        return new RectF(Math.max(margin,Math.min(frame.getWidth()-margin,x)),Math.max(margin,Math.min(baseHeight-margin,y)),0,0);
    }
    private void mergeJoint(QsLayout.Item item,TileSeams.Joint joint) {
        TileForm next=item.form().joined(joint); QsLayout result=layout.edit(item.id(),item.x(),item.y(),item.w(),item.h(),next); if(result==null) return;
        TileVisual visual=visuals.get(item.id()); if(visual!=null&&ValueAnimator.areAnimatorsEnabled()) visual.join(item.form(),next,SystemClock.uptimeMillis());
        commit(result); performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
    }
    void repairRotationFrames() {
        if(layout==null||busy||!cellFormsSupported||dragging) return; QsLayout repaired=layout;
        for(Sprite sprite:sprites) { QsLayout.Item item=repaired.item(sprite.id);if(item==null||item.form().rotation()==0||item.form().baseW()>0||!item.editable()) continue;
            QsLayout.Item next=TileFrame.reframe(item,TileFrame.intrinsic(item),layout.page(item.page()),sprite.bounds,pitch(true),pitch(false),cellWidth,cellHeight,visuals.get(item.id()).image().radius,rtl,true);
            if(next!=null) { QsLayout edit=repaired.edit(item.id(),next.x(),next.y(),next.w(),next.h(),next.form());if(edit!=null) repaired=edit; }
        }
        if(repaired.differsFrom(layout)) commit(repaired);
    }
    private RectF stableBounds(QsLayout.Item item) {
        for(Sprite sprite:sprites)if(sprite.id.equals(item.id()))return new RectF(tileBounds(sprite,item));
        return cells(item,new RectF());
    }
    void resetRotation() { rotateSelected(0); }
    void rotateSelected(float angle) {
        QsLayout.Item item=layout==null?null:layout.item(selected);if(item==null||busy)return;
        if(!cellFormsSupported){if(listener!=null)listener.message(formsError);return;}
        TileVisual visual=visuals.get(selected);
        QsLayout.Item changed=TileFrame.rotate(item,angle,layout.page(item.page()),stableBounds(item),pitch(true),pitch(false),cellWidth,cellHeight,visual.image().radius,rtl);
        QsLayout next=changed==null?null:layout.edit(item.id(),changed.x(),changed.y(),changed.w(),changed.h(),changed.form());
        if(next==null) { if(listener!=null)listener.message("Для этого угла не хватает места вокруг центра. Переместите плитку дальше от края."); }
        else if(next.differsFrom(layout))commit(next,true);
    }
    String historySnapshot(){return history.snapshot();}void restoreHistorySnapshot(String value){history.restore(value);notifyHistory();}
    void beginEdit(){history.begin();}void endEdit(){history.end();notifyHistory();}
    boolean canUndo(){return history.canUndo();}boolean canRedo(){return history.canRedo();}
    void undo(){if(!busy)restoreHistory(history.undo());}void redo(){if(!busy)restoreHistory(history.redo());}
    private void restoreHistory(QsLayout next){if(next==null)return;endGesture();localDraft=true;layout=next;syncVisuals(180);notifySelection();notifyHistory();if(listener!=null)listener.edited(next);}
    void useDraft(QsLayout next){if(busy||layout==null)throw new IllegalArgumentException("Дождитесь загрузки шторки.");next.validateAgainst(layout);if(!cellFormsSupported)for(QsLayout.Item item:next.items)if(item.form().cut())throw new IllegalArgumentException(formsError);commit(next);}
    private void notifyHistory(){if(listener!=null)listener.history(history.canUndo(),history.canRedo());}
    void layerSelected(boolean front) {
        QsLayout.Item i=layout==null?null:layout.item(selected); if(i==null||busy||!cellFormsSupported) return;
        int layer=0; for(QsLayout.Item other:layout.items) if(other.page()==i.page()) layer=front?Math.max(layer,other.form().layer()):Math.min(layer,other.form().layer());
        if(Math.abs(layer)>=9999) { if(listener!=null) listener.message("Достигнут предел слоёв."); return; }
        editSelected(layout.edit(i.id(),i.x(),i.y(),i.w(),i.h(),i.form().layered(layer+(front?1:-1))));
    }
    private RectF diagonalPoint(QsLayout.Item item,RectF bounds,int end) {
        DiagonalTile.Geometry g=DiagonalTile.geometry(item.form().intrinsic(bounds,item.w(),item.h(),pitch(true),pitch(false)),item.form().columns(item.w()),item.form().rows(item.h()),pitch(true),pitch(false),item.form().corner()==6);
        float x=end==1?g.ax():g.bx(),y=end==1?g.ay():g.by();if(rtl) x=bounds.left+bounds.right-x;
        return TileRotation.point(editMatrix(item,bounds),x,y);
    }
    private void updateDiagonal(float x,float y,QsLayout.Page page) {
        QsLayout.Item base=TileFrame.intrinsic(anchor);boolean rising=base.form().corner()==6;
        int ax=0,bx=base.w()-1,ay=rising?base.h()-1:0,by=rising?0:base.h()-1;
        float dx=(x-downX)/pitch(true)*(rtl?-1:1),dy=(y-downY)/pitch(false);int ox=diagonalEnd==1?ax:bx,oy=diagonalEnd==1?ay:by;
        float nx=Math.max(-24,Math.min(24,ox+dx)),ny=Math.max(-24,Math.min(24,oy+dy));
        if(diagonalEnd==1) { ax=Math.round(nx);ay=Math.round(ny); } else { bx=Math.round(nx);by=Math.round(ny); }
        int col=Math.min(ax,bx),row=Math.min(ay,by),w=Math.abs(bx-ax)+1,h=Math.abs(by-ay)+1;
        TileForm f=(w*h==1?TileForm.RECT:TileForm.diagonal((bx-ax)*(by-ay)<0,base.form().nubs())).attributes(base.form());
        QsLayout.Item changed=new QsLayout.Item(base.id(),base.title(),base.page(),col,row,w,h,true,base.tall(),f);proposed=reframe(changed,false);
        working=proposed==null?null:layout.edit(anchor.id(),proposed.x(),proposed.y(),proposed.w(),proposed.h(),proposed.form());invalid=working==null;if(proposed==null) proposed=anchor;
        RectF raw=anchor.form().intrinsic(resizeStart,anchor.w(),anchor.h(),pitch(true),pitch(false));
        float thickness=Math.min(raw.width()-(base.w()-1)*pitch(true),raw.height()-(base.h()-1)*pitch(false));
        float fx=raw.left+(diagonalEnd==1?base.w()-1:0)*pitch(true)+thickness/2,fy=raw.top+(diagonalEnd==1?(rising?0:base.h()-1):(rising?base.h()-1:0))*pitch(false)+thickness/2;
        float mx=raw.left+nx*pitch(true)+thickness/2,my=raw.top+ny*pitch(false)+thickness/2;
        RectF live=new RectF(Math.min(mx,fx)-thickness/2,Math.min(my,fy)-thickness/2,Math.max(mx,fx)+thickness/2,Math.max(my,fy)+thickness/2);
        float angle=(float)Math.toDegrees(Math.atan2(my-fy,mx-fx));if(angle>90) angle-=180;if(angle< -90) angle+=180;
        float length=(float)Math.hypot(mx-fx,my-fy)+thickness;RectF body=new RectF(live.centerX()-length/2,live.centerY()-thickness/2,live.centerX()+length/2,live.centerY()+thickness/2);
        Path path=DiagonalTile.path(new DiagonalTile.Geometry(fx,fy,mx,my,thickness,length,angle,body),f.nubs());Matrix content=new Matrix();content.setRotate(angle,live.centerX(),live.centerY());
        if(rtl) { Matrix mirror=new Matrix();mirror.setScale(-1,1,resizeStart.centerX(),resizeStart.centerY());path.transform(mirror);RectF reflected=new RectF(2*resizeStart.centerX()-body.right,body.top,2*resizeStart.centerX()-body.left,body.bottom);body=reflected;content.preConcat(mirror);content.postConcat(mirror); }
        Matrix extra=editMatrix(anchor,resizeStart);path.transform(extra);content.postConcat(extra);resizePath=path;resizeBody=body;resizeContent=content;resizeBounds.set(TileFrame.bounds(path));
    }
    void diagonalSelected(boolean rising) {
        QsLayout.Item item=layout==null?null:layout.item(selected); if(item==null||busy) return;
        if(!cellFormsSupported) { if(listener!=null) listener.message(formsError); return; }
        editSelected(preset(item,Math.max(2,item.form().columns(item.w())),Math.max(2,item.form().rows(item.h())),TileForm.diagonal(rising,item.form().nubs()).attributes(item.form())));
    }
    void nubsSelected() {
        QsLayout.Item item=layout==null?null:layout.item(selected); if(item==null||busy) return;
        if(!cellFormsSupported) { if(listener!=null) listener.message(formsError); return; }
        editSelected(layout.edit(item.id(),item.x(),item.y(),item.w(),item.h(),item.form().nubbed(!item.form().nubs())));
    }
    private float pitch(boolean horizontal) { return (horizontal?cellWidth:cellHeight)*(config.enabled?config.scalePercent/100f:1); }
    private float resizeStep(boolean horizontal) {
        TileImage small = images.get(key(anchor.id(), horizontal ? 1 : 2, 1));
        TileImage large = images.get(key(anchor.id(), 2, horizontal ? 1 : 2));
        if (small != null && large != null) {
            float step = horizontal ? large.width - small.width : large.height - small.height;
            if (step > 0) return step;
        }
        return Math.max(1, (horizontal ? cellWidth : cellHeight) * (config.enabled ? config.scalePercent / 100f : 1));
    }
    void formSelected(boolean cut) {
        QsLayout.Item i=layout==null?null:layout.item(selected); if(i==null||busy) return;
        if(cut&&!formsSupported) { if(listener!=null) listener.message(formsError.isEmpty()?"Формы недоступны. Перезапустите SystemUI.":formsError); return; }
        if(!cut) { editSelected(preset(i,i.form().columns(i.w()),i.form().rows(i.h()),TileForm.RECT.attributes(i.form()))); return; }
        int w=Math.max(2,i.form().columns(i.w())),h=Math.max(2,i.form().rows(i.h())); QsLayout.Page p=layout.page(i.page());
        if(w>p.columns()||h>p.rows()) { editSelected(null); return; }
        boolean[][] rows=new boolean[h][w];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) rows[y][x]=y==h-1||x==w-1;
        int x=Math.min(i.x(),p.columns()-w),y=Math.max(0,Math.min(i.y()+i.h()-h,p.rows()-h));
        TileForm form=TileForm.of(rows,0,h-1,w,1).attributes(i.form());
        editSelected(i.form().rotation()!=0||i.form().baseW()>0?preset(i,w,h,form):layout.edit(i.id(),x,y,w,h,form));
    }
    void resizeSelected(int w, int h) {
        QsLayout.Item i = layout == null ? null : layout.item(selected); if (i == null || busy) return;
        if(!formsSupported&&NativeForms.custom(i.at(i.x(),i.y(),w,h))) { if(listener!=null) listener.message(formsError.isEmpty()?"Формы недоступны. Перезапустите SystemUI.":formsError); return; }
        editSelected(preset(i,w,h,null));
    }
    private QsLayout preset(QsLayout.Item item,int w,int h,TileForm form) {
        QsLayout.Page page=layout.page(item.page());
        if(item.form().rotation()!=0||item.form().baseW()>0) {
            if(!cellFormsSupported) return null;
            QsLayout.Item base=TileFrame.intrinsic(item).at(0,0,w,h);if(form!=null) base=base.shaped(form.base(0,0));
            TileVisual visual=visuals.get(item.id());QsLayout.Item framed=TileFrame.reframe(item,base,page,visual.motion.bounds,pitch(true),pitch(false),cellWidth,cellHeight,visual.image().radius,rtl,true);
            return framed==null?null:layout.edit(item.id(),framed.x(),framed.y(),framed.w(),framed.h(),framed.form());
        }
        if(page==null||w>page.columns()||h>page.rows()) return null;
        // At the bottom/right edge a larger preset must grow up/left, rather than silently fail.
        int x=Math.min(item.x(),page.columns()-w),y=Math.min(item.y(),page.rows()-h);
        return layout.edit(item.id(),x,y,w,h,form);
    }
    void moveSelected(int dx, int dy) {
        QsLayout.Item i = layout == null ? null : layout.item(selected); if (i == null || busy) return;
        editSelected(layout.edit(i.id(), i.x() + (rtl ? -dx : dx), i.y() + dy, i.w(), i.h()));
    }
    private void editSelected(QsLayout next) {
        if (next == null) { if (listener != null) listener.message("Размер недоступен или в сетке нет места."); }
        else if (next.differsFrom(layout)) commit(next);
    }
    private void commit(QsLayout next) { commit(next,false); }
    private void commit(QsLayout next,boolean fixedRotation) { history.accept(next);notifyHistory();localDraft=true;layout = next;if(fixedRotation) { TileVisual visual=visuals.get(selected);if(visual!=null)visual.finishShapeAnimations(); } syncVisuals(220,fixedRotation); notifySelection(); if (listener != null) listener.edited(next); }
    private void select(String id) { selected = id; notifySelection(); invalidate(); }
    private void notifySelection() { accessibility.invalidateRoot();if (listener != null) listener.selected(layout == null ? null : layout.item(selected)); }
    private void intercept(boolean value) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(value); }
    private void cancelGesture() {
        endGesture(); syncVisuals(220);
    }
    private void endGesture() {
        removeCallbacks(longPress); branchHandle=null; branchCount=0; diagonalEnd=0; rotating=false;resizePath=null; dragging = false; working = null; proposed = null; pressed = null; pressedRemove = false; invalid = false; intercept(false); invalidate();
    }
    @Override protected void onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibility.dispatchHoverEvent(event)||super.dispatchHoverEvent(event);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return accessibility.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);
    }
    @Override protected void onFocusChanged(boolean gain,int direction,Rect previous) {
        super.onFocusChanged(gain,direction,previous);accessibility.onFocusChanged(gain,direction,previous);
    }
    private final class EditorAccessibility extends androidx.customview.widget.ExploreByTouchHelper {
        private static final int EMPTY=10_000;
        EditorAccessibility(){super(PreviewView.this);}
        private boolean available(){return layout!=null && page>=0 && page<layout.pages.size();}
        private QsLayout.Item item(int id){return available()&&id>0&&id<=layout.items.size()?layout.items.get(id-1):null;}
        private boolean editable(){return available()&&layout.portrait&&!busy&&isEnabled();}
        private int emptyAt(float x,float y){
            if(!available())return INVALID_ID;
            QsLayout.Page p=layout.pages.get(page);
            int col=(int)Math.floor((x-gridX)/cellWidth),row=(int)Math.floor((y-gridY)/cellHeight);
            if(rtl)col=p.columns()-col-1;
            if(col<0||row<0||col>=p.columns()||row>=p.rows())return INVALID_ID;
            for(QsLayout.Item i:layout.items)if(i.page()==p.id()&&i.occupies(col,row))return INVALID_ID;
            return EMPTY+row*p.columns()+col;
        }
        @Override protected int getVirtualViewAt(float x,float y){
            if(!available())return INVALID_ID;
            String found=hit(x/imageScale(),y/imageScale());
            if(found!=null){for(int n=0;n<layout.items.size();n++)if(layout.items.get(n).id().equals(found))return n+1;}
            return emptyAt(x/imageScale(),y/imageScale());
        }
        @Override protected void getVisibleVirtualViews(java.util.List<Integer> ids){
            if(!available())return;
            long current=layout.pages.get(page).id();
            for(int n=0;n<layout.items.size();n++)if(layout.items.get(n).page()==current&&!layout.items.get(n).id().equals(pendingRemoval))ids.add(n+1);
            QsLayout.Page p=layout.pages.get(page);
            for(int y=0;y<p.rows();y++)for(int x=0;x<p.columns();x++){
                int visual=rtl?p.columns()-x-1:x;
                int id=emptyAt(gridX+(visual+.5f)*cellWidth,gridY+(y+.5f)*cellHeight);
                if(id!=INVALID_ID)ids.add(id);
            }
        }
        @Override protected void onPopulateNodeForHost(androidx.core.view.accessibility.AccessibilityNodeInfoCompat node){
            super.onPopulateNodeForHost(node);node.setClassName("android.widget.GridView");
            if(available()){QsLayout.Page p=layout.pages.get(page);node.setCollectionInfo(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(p.rows(),p.columns(),false));}
        }
        @Override protected void onPopulateNodeForVirtualView(int id,androidx.core.view.accessibility.AccessibilityNodeInfoCompat node){
            QsLayout.Item i=item(id);RectF b=new RectF();QsLayout.Page p=available()?layout.pages.get(page):null;
            node.setClassName("android.widget.Button");node.setFocusable(true);node.setEnabled(editable());
            if(i!=null){
                cells(i,b);node.setContentDescription(i.title().isBlank()?i.id().replaceFirst("^t:",""):i.title());
                node.setStateDescription(i.form().columns(i.w())+" на "+i.form().rows(i.h())+", поворот "+Math.round(i.form().rotation())+"°");
                node.setSelected(i.id().equals(selected));
                if(editable()&&i.editable()){
                    node.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK,"Изменить плитку"));
                    action(node,R.id.access_move_left,"Переместить влево");action(node,R.id.access_move_right,"Переместить вправо");
                    action(node,R.id.access_move_up,"Переместить вверх");action(node,R.id.access_move_down,"Переместить вниз");
                    action(node,R.id.access_rotate,"Повернуть на 90 градусов");action(node,R.id.access_remove,"Удалить плитку");
                }
            }else if(p!=null&&id>=EMPTY&&id<EMPTY+p.rows()*p.columns()){
                int cell=id-EMPTY,x=cell%p.columns(),y=cell/p.columns(),visual=rtl?p.columns()-x-1:x;
                b.set(gridX+visual*cellWidth,gridY+y*cellHeight,gridX+(visual+1)*cellWidth,gridY+(y+1)*cellHeight);
                node.setContentDescription("Свободная клетка, строка "+(y+1)+", столбец "+(x+1));
                if(editable())node.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK,"Добавить плитку"));
            }else node.setContentDescription("Плитка недоступна");
            float scale=imageScale();Rect bounds=new Rect((int)(b.left*scale),(int)(b.top*scale),(int)Math.ceil(b.right*scale),(int)Math.ceil(b.bottom*scale));
            node.setBoundsInParent(bounds);
        }
        private void action(androidx.core.view.accessibility.AccessibilityNodeInfoCompat node,int id,String title){node.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(id,title));}
        @Override protected boolean onPerformActionForVirtualView(int id,int action,Bundle data){
            if(!editable())return false;
            QsLayout.Item i=item(id);
            if(i==null){
                QsLayout.Page p=layout.pages.get(page);int cell=id-EMPTY;
                if(action!=androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK||cell<0||cell>=p.columns()*p.rows()||listener==null)return false;
                int x=cell%p.columns(),y=cell/p.columns(),visual=rtl?p.columns()-x-1:x;
                if(emptyAt(gridX+(visual+.5f)*cellWidth,gridY+(y+.5f)*cellHeight)!=id)return false;
                listener.empty(p.id(),x,y);return true;
            }
            if(i.page()!=layout.pages.get(page).id()||!i.editable()||i.id().equals(pendingRemoval))return false;
            select(i.id());
            if(action==androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK){if(listener!=null)listener.inspect(i);return true;}
            if(action==R.id.access_move_left)moveSelected(-1,0);else if(action==R.id.access_move_right)moveSelected(1,0);
            else if(action==R.id.access_move_up)moveSelected(0,-1);else if(action==R.id.access_move_down)moveSelected(0,1);
            else if(action==R.id.access_rotate)rotateSelected(i.form().rotation()+90);
            else if(action==R.id.access_remove){if(listener!=null)listener.remove(i.id());}
            else return false;
            return true;
        }
    }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
}
