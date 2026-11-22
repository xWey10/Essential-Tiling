package moe.xWey10.tiling;

import android.content.Context;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import com.google.android.material.button.MaterialButtonToggleGroup;
import android.view.ViewTreeObserver;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/** The live editor stays above a separately scrolling catalogue of real QS glyphs. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic only: requires the existing live editor, never inflated from XML.
final class TilePickerView extends LinearLayout {
    record Cell(long page, int x, int y) { }
    interface Add { void choose(TileClient.Entry entry, Cell cell); }
    private final ThemePalette palette;
    private final PreviewView preview;
    private final IntSupplier page;
    private final TextView status;
    private final EditText search;
    private final GridView grid;
    private final MaterialButtonToggleGroup categories;private final int systemCategory,appCategory;
    private final RectF expandedViewport=new RectF();
    private final Button retry;
    private final Button restart;
    private ValueAnimator transition;
    private Runnable transitionEnd;
    private void clearTransitionEnd() { if(transitionEnd!=null)removeCallbacks(transitionEnd);transitionEnd=null; }
    private boolean transitionReady;
    private ViewTreeObserver.OnPreDrawListener pendingEnter;
    private float chromeProgress=1;
    private final FittedPreview fittedPreview;
    private final ArrayList<TileClient.Entry> all = new ArrayList<>(), shown = new ArrayList<>();
    private final CatalogueAdapter adapter = new CatalogueAdapter();
    private Cell target;
    private boolean loading = true, busy, awaitingFrame;
    private String loadError = "";

    TilePickerView(Context context, PreviewView preview, View navigation, IntSupplier page, Runnable close, Add add) {
        super(SettingsUi.themed(context));context=getContext(); this.preview = preview; this.page = page; palette = new ThemePalette(context);
        setClipChildren(false);setClipToPadding(false);
        setOrientation(VERTICAL); setBackgroundColor(palette.background); setPadding(dp(16), 0, dp(16), dp(8));
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Плитки", 18); header.addView(title, new LayoutParams(0, dp(48), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        Button done = new MaterialButton(context); done.setText("Готово"); palette.button(done, false);
        done.setOnClickListener(v -> close.run()); LayoutParams doneParams=new LayoutParams(dp(80),dp(48));doneParams.setMarginEnd(dp(8));header.addView(done,doneParams);
        restart=new MaterialButton(context);restart.setContentDescription("Перезапустить SystemUI");((MaterialButton)restart).setIconResource(R.drawable.ic_restart);palette.button(restart,false);header.addView(restart,new LayoutParams(dp(48),dp(48)));
        for(Button action:new Button[]{done,restart}) { MaterialButton compact=(MaterialButton)action;compact.setTextSize(14);compact.setInsetTop(dp(6));compact.setInsetBottom(dp(6));compact.setCornerRadius(dp(18));compact.setMinHeight(0);compact.setMinimumHeight(0);compact.setIconSize(dp(20)); }
        addView(header, new LayoutParams(-1, -2));
        addView(navigation, new LayoutParams(-1, -2));
        fittedPreview = new FittedPreview(context, preview);
        addView(fittedPreview, new LayoutParams(-1, -2));
        TextInputLayout searchField=new TextInputLayout(context);searchField.setHint("Поиск плиток");searchField.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);searchField.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);searchField.setEndIconContentDescription("Очистить поиск");searchField.setBoxCornerRadii(dp(28),dp(28),dp(28),dp(28));
        search=new TextInputEditText(searchField.getContext());search.setSingleLine(true);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setTextSize(16);search.setTextColor(palette.text);searchField.addView(search,new LayoutParams(-1,dp(56)));LayoutParams searchParams=new LayoutParams(-1,-2);searchParams.topMargin=dp(12);addView(searchField,searchParams);
        categories=new MaterialButtonToggleGroup(context);categories.setSingleSelection(true);categories.setSelectionRequired(true);
        MaterialButton system=new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle),apps=new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        systemCategory=View.generateViewId();appCategory=View.generateViewId();system.setId(systemCategory);apps.setId(appCategory);system.setText("Системные");apps.setText("Приложения");
        for(MaterialButton button:new MaterialButton[]{system,apps}) { button.setTextSize(13);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(12),0,dp(12),0);categories.addView(button,new LayoutParams(0,dp(44),1)); }
        categories.check(systemCategory);LayoutParams categoryParams=new LayoutParams(-1,dp(48));categoryParams.topMargin=dp(4);addView(categories,categoryParams);
        categories.addOnButtonCheckedListener((group,id,checked)-> { if(checked) { changeCategory(); } });
        status = label("Загрузка…", 13); status.setTextColor(palette.secondary); status.setPadding(dp(8), dp(4), dp(8), dp(8));
        addView(status, new LayoutParams(-1, -2));
        retry = new MaterialButton(context); retry.setText("Повторить"); palette.button(retry, false);
        addView(retry, new LayoutParams(-1, dp(44))); retry.setVisibility(GONE);
        View divider=new View(context);divider.setBackgroundColor(palette.outline);addView(divider,new LayoutParams(-1,dp(1)));
        grid = new CatalogueGrid(context); grid.setNumColumns(4); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(4)); grid.setVerticalSpacing(dp(12)); grid.setClipToPadding(true);grid.setClipChildren(true);
        grid.setPadding(0, dp(12), 0, dp(12)); grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter); addView(grid, new LayoutParams(-1, 0, 1));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Cell cell = target();
            if (!loading && !busy && !awaitingFrame && loadError.isEmpty() && cell != null) add.choose(shown.get(position), cell);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        setChrome(0);preview.setAlpha(0);
    }

    void restart(Runnable action) { restart.setOnClickListener(v->action.run()); }
    void animateEnter(RectF from) { animateEnter(from,from); }
    void animateEnter(RectF from,RectF viewport) {
        expandedViewport.set(viewport);
        transitionReady=false;preview.setEnabled(false);
        pendingEnter=new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() { if(preview.getWidth()==0) return true;getViewTreeObserver().removeOnPreDrawListener(this);pendingEnter=null;transitionReady=true;animate(from,true,null);return true; }
        };getViewTreeObserver().addOnPreDrawListener(pendingEnter);
    }
    void animateExit(RectF target,Runnable done) { if(!transitionReady) { cancelTransition();done.run();return; }animate(target,false,done); }
    private void animate(RectF other,boolean entering,Runnable done) {
        clearTransitionEnd();
        if(transition!=null) { transition.removeAllListeners();transition.cancel(); }
        int[] xy=new int[2];preview.getLocationOnScreen(xy);float oldTx=preview.getTranslationX(),oldTy=preview.getTranslationY();
        float left=xy[0]-oldTx,top=xy[1]-oldTy;
        float sx=other.width()/Math.max(1,preview.getWidth()),tx=other.left-left,ty=other.top-top;
        float fromScale=entering?sx:preview.getScaleX(),fromX=entering?tx:oldTx,fromY=entering?ty:oldTy;
        Rect clip=preview.getClipBounds();RectF full=new RectF(0,0,preview.getWidth(),preview.getHeight());
        RectF expanded=new RectF((expandedViewport.left-other.left)/sx,(expandedViewport.top-other.top)/sx,(expandedViewport.right-other.left)/sx,(expandedViewport.bottom-other.top)/sx);expanded.intersect(full);
        RectF fromClip=entering?expanded:clip==null?full:new RectF(clip),toClip=entering?full:expanded;
        float toScale=entering?1:sx,toX=entering?0:tx,toY=entering?0:ty;float fromChrome=entering?0:chromeProgress,toChrome=entering?1:0;
        preview.setPivotX(0);preview.setPivotY(0);preview.setScaleX(fromScale);preview.setScaleY(fromScale);preview.setTranslationX(fromX);preview.setTranslationY(fromY);setChrome(fromChrome);preview.setEnabled(false);preview.setAlpha(1);setClip(fromClip);
        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(ValueAnimator.areAnimatorsEnabled()?300:0);transition.setInterpolator(new FastOutSlowInInterpolator());
        transition.addUpdateListener(anim->{ float t=(Float)anim.getAnimatedValue();preview.setScaleX(fromScale+(toScale-fromScale)*t);preview.setScaleY(fromScale+(toScale-fromScale)*t);preview.setTranslationX(fromX+(toX-fromX)*t);preview.setTranslationY(fromY+(toY-fromY)*t);float chrome=fromChrome+(toChrome-fromChrome)*t;preview.setCatalogueProgress(chrome);setChrome(chrome);setClip(new RectF(fromClip.left+(toClip.left-fromClip.left)*t,fromClip.top+(toClip.top-fromClip.top)*t,fromClip.right+(toClip.right-fromClip.right)*t,fromClip.bottom+(toClip.bottom-fromClip.bottom)*t)); });
        transition.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator animator) { clearTransitionEnd();transition=null;if(entering) { preview.setEnabled(true);preview.setClipBounds(null); }else { cancelTransition();if(done!=null) done.run(); } } });
        ValueAnimator animation=transition;animation.start();
        // Commit the final state on time even if a vsync is late; cancellation removes this task.
        if(transition==animation) {
            animation.setCurrentPlayTime(0);
            if(transition==animation && animation.getDuration()>0) {
                transitionEnd=()-> { if(transition==animation)animation.end(); };
                postDelayed(transitionEnd,animation.getDuration());
            }
        }
    }
    private void setClip(RectF clip) { preview.setClipBounds(new Rect(Math.round(clip.left),Math.round(clip.top),Math.round(clip.right),Math.round(clip.bottom))); }
    private void setChrome(float fraction) { chromeProgress=fraction;for(int n=0;n<getChildCount();n++) { View child=getChildAt(n);if(child==fittedPreview) continue;child.setAlpha(fraction);child.setTranslationY(dp(16)*(1-fraction)); } }
    void cancelTransition() { clearTransitionEnd();if(pendingEnter!=null) { getViewTreeObserver().removeOnPreDrawListener(pendingEnter);pendingEnter=null; }if(transition!=null) { transition.removeAllListeners();transition.cancel();transition=null; }preview.setAlpha(1);preview.setClipBounds(null);preview.setScaleX(1);preview.setScaleY(1);preview.setTranslationX(0);preview.setTranslationY(0);preview.setCatalogueProgress(0);preview.setEnabled(true);setChrome(1); }

    private void changeCategory() { refresh();grid.setSelection(0); }
    void applications(boolean value) { categories.check(value?appCategory:systemCategory); }
    void target(long screen, int x, int y) { target = new Cell(screen, x, y); refresh(); }
    void entries(List<TileClient.Entry> entries) {
        all.clear(); all.addAll(entries);
        all.sort(java.util.Comparator.comparing(e -> e.toString().toLowerCase(Locale.getDefault())));
        loading = false; loadError = ""; refresh();
    }
    void retry(Runnable action) { retry.setOnClickListener(v -> action.run()); }
    void loading() { loading = true; loadError = ""; refresh(); }
    void catalogError(String error) { loading = false; loadError = error; refresh(); }
    boolean needsLoad() { return !loadError.isEmpty(); }
    void busy(boolean value) { busy=value;restart.setEnabled(!value);refresh(); }
    void waitingForFrame() { awaitingFrame = true; refresh(); }
    void frameReady() { awaitingFrame = false; refresh(); }
    void message(String value) { status.setText(value); status.setVisibility(value.isEmpty() ? GONE : VISIBLE); }
    void refresh() {
        if (grid == null) return;
        QsLayout current = preview.getLayoutDraft(); String query = search.getText().toString().toLowerCase(Locale.getDefault());
        ArrayList<TileClient.Entry> filtered=new ArrayList<>();
        for (TileClient.Entry item : all) if ((current == null || current.item("t:" + item.spec()) == null)
                && item.spec().startsWith("custom(")==(categories.getCheckedButtonId()==appCategory)
                && item.toString().toLowerCase(Locale.getDefault()).contains(query)) filtered.add(item);
        if(!shown.equals(filtered)) { shown.clear();shown.addAll(filtered);adapter.notifyDataSetChanged(); }
        Cell place=target();boolean room = place != null;preview.setInsertionCell(room?place.page():-1,room?place.x():-1,room?place.y():-1);
        grid.setEnabled(!loading && !busy && !awaitingFrame && loadError.isEmpty() && room);
        retry.setVisibility(!loading && !loadError.isEmpty() ? VISIBLE : GONE); retry.setEnabled(!busy);
        message(!loadError.isEmpty() ? loadError : loading ? "Загрузка…" : busy ? "Сохранение…" : awaitingFrame ? "Обновление…" : shown.isEmpty()
                ? query.isEmpty() ? "Нет доступных плиток" : "Ничего не найдено" : room ? "" : "Нет свободных ячеек");
    }
    private Cell target() {
        QsLayout model = preview.getLayoutDraft(); int index = page.getAsInt();
        if (model == null || !model.portrait || index < 0 || index >= model.pages.size()) return null;
        QsLayout.Page screen = model.pages.get(index);
        if (target != null && target.page == screen.id() && free(model, screen, target.x, target.y)) return target;
        for (int y = 0; y < screen.rows(); y++) for (int x = 0; x < screen.columns(); x++) if (free(model, screen, x, y)) return new Cell(screen.id(), x, y);
        return null;
    }
    private boolean free(QsLayout model, QsLayout.Page screen, int x, int y) {
        if (x < 0 || y < 0 || x >= screen.columns() || y >= screen.rows()) return false;
        for (QsLayout.Item item : model.items) if (item.page() == screen.id() && item.occupies(x,y)) return false;
        return true;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(getContext()); view.setText(text); view.setTextSize(size); view.setTextColor(palette.text); return view;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? getResources().getDisplayMetrics().heightPixels : MeasureSpec.getSize(heightSpec);
        fittedPreview.limit = Math.max(dp(48), (available - getPaddingTop() - getPaddingBottom()) / 2 - dp(48));
        grid.setNumColumns(MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight() < dp(300) ? 3 : 4);
        super.onMeasure(widthSpec, heightSpec);
        int fixed=getMeasuredHeight()-getPaddingTop()-getPaddingBottom()-fittedPreview.getMeasuredHeight()-grid.getMeasuredHeight();
        int limit=Math.max(dp(48),Math.min(fittedPreview.limit,available-getPaddingTop()-getPaddingBottom()-fixed-Math.min(dp(144),available/3)));
        if(limit!=fittedPreview.limit) { fittedPreview.limit=limit;super.onMeasure(widthSpec,heightSpec); }
    }

    private final class CatalogueAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public TileClient.Entry getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View reusable, ViewGroup parent) {
            TileCell cell = reusable instanceof TileCell ? (TileCell) reusable : new TileCell();
            TileClient.Entry entry = getItem(position); cell.title.setText(entry.label());
            cell.setContentDescription(entry.toString());
            if (entry.icon() != null) cell.icon.setImageBitmap(entry.icon());
            else cell.icon.setImageResource(android.R.drawable.ic_menu_help);
            return cell;
        }
    }
    private final class TileCell extends LinearLayout {
        final FrameLayout circle;
        final ImageView icon;
        final TextView title;
        TileCell() {
            super(TilePickerView.this.getContext()); setOrientation(VERTICAL); setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(4), dp(4), dp(4), dp(8));setClipChildren(true);setClipToPadding(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            circle = new FrameLayout(getContext());
            GradientDrawable surface = new GradientDrawable(); surface.setShape(GradientDrawable.OVAL); surface.setColor(palette.surface);
            GradientDrawable mask = new GradientDrawable(); mask.setShape(GradientDrawable.OVAL); mask.setColor(android.graphics.Color.WHITE);
            circle.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.outline), surface, mask));
            circle.setDuplicateParentStateEnabled(true);
            icon = new ImageView(getContext()); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageTintList(ColorStateList.valueOf(palette.text)); icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            circle.addView(icon, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            addView(circle, new LayoutParams(dp(64), dp(64)));
            title = label("", 13); title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL); title.setMinLines(3);title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END);
            title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams name = new LayoutParams(-1, -2); name.topMargin = dp(8); addView(title, name);
        }
        @Override protected void onMeasure(int w,int h) {
            int side=Math.max(dp(24),Math.min(dp(64),MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight()));circle.getLayoutParams().width=side;circle.getLayoutParams().height=side;super.onMeasure(w,h);
        }
    }

    /** Fade recycled rows into the separator instead of clipping circles and labels abruptly. */
    private final class CatalogueGrid extends GridView {
        private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient topFade,bottomFade;
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
            super.onSizeChanged(w,h,oldW,oldH);float fade=Math.min(dp(40),h/3f);
            topFade=new LinearGradient(0,0,0,Math.max(1,fade),0x00ffffff,0xffffffff,Shader.TileMode.CLAMP);
            bottomFade=new LinearGradient(0,h-fade,0,Math.max(h-fade+1,h),0xffffffff,0x00ffffff,Shader.TileMode.CLAMP);
        }
        CatalogueGrid(Context context) { super(context);mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN)); }
        @Override protected void dispatchDraw(Canvas canvas) {
            boolean top=canScrollVertically(-1),bottom=canScrollVertically(1);if(!top&&!bottom) { super.dispatchDraw(canvas);return; }
            int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);super.dispatchDraw(canvas);float fade=Math.min(dp(40),getHeight()/3f);
            if(top) { mask.setShader(topFade);canvas.drawRect(0,0,getWidth(),fade,mask); }
            if(bottom) { mask.setShader(bottomFade);canvas.drawRect(0,getHeight()-fade,getWidth(),getHeight(),mask); }
            mask.setShader(null);canvas.restoreToCount(layer);
        }
    }

    private final class FittedPreview extends FrameLayout {
        private final View child;
        int limit;
        FittedPreview(Context c, View child) {
            super(c);setClipChildren(false);setClipToPadding(false); this.child = child; addView(child, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int contentWidth=Math.max(1,Math.round(width*.94f));
            int availableHeight = limit;
            child.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (child.getMeasuredHeight() > availableHeight) {
                int fittedWidth = Math.max(1, Math.round((float) contentWidth * availableHeight / child.getMeasuredHeight()));
                child.measure(MeasureSpec.makeMeasureSpec(fittedWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
            setMeasuredDimension(width, child.getMeasuredHeight());
        }
    }
}
