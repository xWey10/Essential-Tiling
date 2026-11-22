package moe.xWey10.tiling;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.method.TransformationMethod;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Reversible measurement/layout transaction for an existing QS tree, on the UI thread only. */
final class ViewTreeState {
    private final List<State> states = new ArrayList<>();
    private final IdentityHashMap<GradientDrawable, float[]> corners = new IdentityHashMap<>();
    ViewTreeState(View root) { collect(root); }

    private void collect(View v) {
        states.add(new State(v));
        corners(v.getBackground(), 0);
        corners(v.getForeground(), 0);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }

    private void corners(Drawable d, int depth) {
        if (d == null || depth > 8) return;
        if (d instanceof GradientDrawable) {
            GradientDrawable g = (GradientDrawable) d;
            float[] radii = g.getCornerRadii();
            corners.put(g, radii == null ? new float[]{g.getCornerRadius()} : radii);
        } else if (d instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) d;
            for (int i = 0; i < layer.getNumberOfLayers(); i++) corners(layer.getDrawable(i), depth + 1);
        } else if (d instanceof StateListDrawable) {
            StateListDrawable list = (StateListDrawable) d;
            for (int i = 0; i < list.getStateCount(); i++) corners(list.getStateDrawable(i), depth + 1);
        } else if (d instanceof InsetDrawable) corners(((InsetDrawable) d).getDrawable(), depth + 1);
    }

    void restoreProperties() { for (State state : states) state.restore(false); }
    void restoreGeometry() {
        for (State state : states) state.view.forceLayout();
        for (State state : states) state.restore(true);
        corners.forEach((g, r) -> { if (r.length == 1) g.setCornerRadius(r[0]); else g.setCornerRadii(r); });
    }

    private static final class State {
        final View view;
        final int l, t, r, b, mw, mh, visibility, sx, sy, pl, pt, pr, pb;
        final float alpha, scaleX, scaleY, tx, ty, rotation, rotationX, rotationY, pivotX, pivotY;
        final boolean selected, pivotSet, relativePadding;
        final int paddingStart, paddingEnd;
        final Rect clip;
        final int orientation, gravity;
        final ViewGroup.LayoutParams params;
        final TextState text;
        final List<Field> fields = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        State(View v) {
            view = v; l = v.getLeft(); t = v.getTop(); r = v.getRight(); b = v.getBottom();
            mw = v.getMeasuredWidth(); mh = v.getMeasuredHeight(); visibility = v.getVisibility();
            sx = v.getScrollX(); sy = v.getScrollY();
            pl = v.getPaddingLeft(); pt = v.getPaddingTop(); pr = v.getPaddingRight(); pb = v.getPaddingBottom();
            alpha = v.getAlpha(); scaleX = v.getScaleX(); scaleY = v.getScaleY();
            tx = v.getTranslationX(); ty = v.getTranslationY();
            rotation = v.getRotation(); rotationX = v.getRotationX(); rotationY = v.getRotationY();
            pivotX = v.getPivotX(); pivotY = v.getPivotY();
            pivotSet = v.isPivotSet(); relativePadding = v.isPaddingRelative();
            paddingStart = v.getPaddingStart(); paddingEnd = v.getPaddingEnd();
            selected = v.isSelected(); clip = v.getClipBounds();
            orientation = v instanceof LinearLayout ? ((LinearLayout) v).getOrientation() : -1;
            gravity = v instanceof LinearLayout ? ((LinearLayout) v).getGravity() : 0;
            params = v.getLayoutParams();
            text = v instanceof TextView ? new TextState((TextView) v) : null;
            if (params != null) for (Field f : params.getClass().getFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || !f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); fields.add(f); values.add(f.get(params)); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Layout snapshot", e); }
            }
        }
        void restore(boolean measure) {
            if (params != null) {
                for (int i = 0; i < fields.size(); i++) {
                    try { fields.get(i).set(params, values.get(i)); }
                    catch (ReflectiveOperationException e) { throw new IllegalStateException("Layout restore", e); }
                }
                if (view.getLayoutParams() != params) view.setLayoutParams(params);
            }
            view.setVisibility(visibility); view.setAlpha(alpha);
            view.setScaleX(scaleX); view.setScaleY(scaleY); view.setTranslationX(tx); view.setTranslationY(ty);
            view.setRotation(rotation); view.setRotationX(rotationX); view.setRotationY(rotationY);
            if (pivotSet) { view.setPivotX(pivotX); view.setPivotY(pivotY); } else view.resetPivot();
            if (relativePadding) view.setPaddingRelative(paddingStart, pt, paddingEnd, pb);
            else view.setPadding(pl, pt, pr, pb);
            view.setSelected(selected); view.setClipBounds(clip);
            if (orientation >= 0) {
                ((LinearLayout) view).setOrientation(orientation); ((LinearLayout) view).setGravity(gravity);
            }
            if (text != null) text.restore((TextView) view);
            if (measure) {
                view.forceLayout();
                view.measure(View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(mh, View.MeasureSpec.EXACTLY));
                view.layout(l, t, r, b);
            }
            view.scrollTo(sx, sy);
        }
    }

    /** applySize also changes line limits and ellipsizing. Geometry alone cannot undo that. */
    private static final class TextState {
        final int minLines, maxLines, minHeight, maxHeight, gravity;
        final float size, spacing;
        final boolean singleLine, horizontal;
        final Typeface font;
        final TextUtils.TruncateAt ellipsize;
        final TransformationMethod transformation;
        TextState(TextView v) {
            minLines = v.getMinLines(); maxLines = v.getMaxLines(); minHeight = v.getMinHeight(); maxHeight = v.getMaxHeight();
            gravity = v.getGravity(); size = v.getTextSize(); spacing = v.getLetterSpacing(); font = v.getTypeface();
            singleLine = v.isSingleLine(); horizontal = v.isHorizontallyScrollable();
            ellipsize = v.getEllipsize(); transformation = v.getTransformationMethod();
        }
        void restore(TextView v) {
            v.setSingleLine(singleLine); v.setHorizontallyScrolling(horizontal);
            if (minLines >= 0) v.setMinLines(minLines); else v.setMinHeight(minHeight);
            if (maxLines >= 0) v.setMaxLines(maxLines); else v.setMaxHeight(maxHeight);
            v.setGravity(gravity); v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
            v.setTypeface(font); v.setLetterSpacing(spacing); v.setEllipsize(ellipsize); v.setTransformationMethod(transformation);
        }
    }
}
