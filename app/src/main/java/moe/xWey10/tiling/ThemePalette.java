package moe.xWey10.tiling;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.Button;
import com.google.android.material.button.MaterialButton;

/** Material 3 semantic roles; the theme is populated from the phone's dynamic palette. */
final class ThemePalette {
    final int background,surface,text,secondary,outline,accent,error,onAccent,container,onContainer;
    ThemePalette(Context c) {
        background=role(c,com.google.android.material.R.attr.colorSurface,R.color.settings_background);
        surface=role(c,com.google.android.material.R.attr.colorSurfaceContainer,R.color.settings_surface);
        text=role(c,com.google.android.material.R.attr.colorOnSurface,R.color.settings_text);
        secondary=role(c,com.google.android.material.R.attr.colorOnSurfaceVariant,R.color.settings_secondary);
        outline=role(c,com.google.android.material.R.attr.colorOutlineVariant,R.color.settings_outline);
        accent=role(c,com.google.android.material.R.attr.colorPrimary,R.color.settings_accent);
        error=role(c,com.google.android.material.R.attr.colorError,R.color.settings_text);
        onAccent=role(c,com.google.android.material.R.attr.colorOnPrimary,R.color.settings_on_accent);
        container=role(c,com.google.android.material.R.attr.colorPrimaryContainer,R.color.settings_accent_container);
        onContainer=role(c,com.google.android.material.R.attr.colorOnPrimaryContainer,R.color.settings_on_accent_container);
    }
    private static int role(Context c,int attr,int fallback) {
        TypedValue v=new TypedValue();if(c.getTheme().resolveAttribute(attr,v,true)) return v.resourceId!=0?c.getColor(v.resourceId):v.data;return c.getColor(fallback);
    }
    void button(Button button,boolean primary) {
        float d=button.getResources().getDisplayMetrics().density;
        if(button instanceof MaterialButton material) {
            material.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{alpha(text,.12f),primary?accent:surface}));
            material.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{alpha(text,.38f),primary?onAccent:text}));
            material.setIconTint(material.getTextColors());material.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            material.setCornerRadius(Math.round(24*d));material.setStrokeWidth(0);
        } else {
            GradientDrawable shape=new GradientDrawable();shape.setColor(primary?accent:surface);shape.setCornerRadius(24*d);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(accent,.14f)),shape,null));button.setBackgroundTintList(null);button.setTextColor(primary?onAccent:text);
        }
        button.setAllCaps(false);button.setMinHeight(Math.round(48*d));button.setPadding(Math.round(12*d),0,Math.round(12*d),0);
    }
    private static int alpha(int c,float a) { return Color.argb(Math.round(255*a),Color.red(c),Color.green(c),Color.blue(c)); }
}
