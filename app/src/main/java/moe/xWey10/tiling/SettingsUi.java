package moe.xWey10.tiling;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.google.android.material.button.MaterialButton;

/** Reusable settings rows and typography. No QS drawing or module logic lives here. */
final class SettingsUi {
    private final Context context;private final ThemePalette palette;
    SettingsUi(Context context) { this.context=context;palette=new ThemePalette(context); }
    static Context themed(Context context) { android.util.TypedValue value=new android.util.TypedValue();return context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface,value,true)&&value.data!=0?context:new androidx.appcompat.view.ContextThemeWrapper(context,R.style.Theme_EssentialTiling); }
    int dp(float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v; }
    TextView text(String value,int appearance) { TextView v=new TextView(context);v.setText(value);v.setTextAppearance(appearance);v.setTextColor(palette.text);return v; }
    TextView section(String title) { TextView v=text(title,com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);v.setTextColor(palette.accent);v.setPadding(dp(8),dp(24),dp(8),dp(12));v.setAccessibilityHeading(true);return v; }
    TextView heading(String title) { TextView v=text(title,com.google.android.material.R.style.TextAppearance_Material3_HeadlineLarge);v.setPadding(dp(8),dp(20),dp(8),dp(24));v.setAccessibilityHeading(true);return v; }
    LinearLayout group() { LinearLayout v=column();v.setBackground(surface(palette.surface,24));v.setClipToOutline(true);return v; }
    GradientDrawable surface(int color,int radius) { GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg; }
    View row(String title,String summary,int icon,Runnable action) {
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(80));row.setPadding(dp(20),dp(16),dp(16),dp(16));
        ImageView glyph=new ImageView(context);glyph.setImageResource(icon);glyph.setImageTintList(ColorStateList.valueOf(palette.secondary));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(24),dp(24));ip.setMarginEnd(dp(20));row.addView(glyph,ip);
        LinearLayout labels=column();TextView name=text(title,com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);labels.addView(name);
        if(!summary.isEmpty()) { TextView detail=text(summary,com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);detail.setTextColor(palette.secondary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(4);labels.addView(detail,p); }
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=new ImageView(context);arrow.setImageResource(R.drawable.ic_chevron);arrow.setImageTintList(ColorStateList.valueOf(palette.secondary));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(24)));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.outline),null,surface(0xffffffff,0)));row.setFocusable(true);row.setOnClickListener(v->action.run());return row;
    }
    MaterialButton button(String title,boolean primary) { MaterialButton v=new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonStyle);v.setText(title);palette.button(v,primary);return v; }
}
