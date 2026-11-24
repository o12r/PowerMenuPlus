package dev.oscar.powermenuplus;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/** Clones the OEM reboot button (same look as the native ones) from SystemUI's own layouts. */
final class NativeButtonFactory {
    private static final String[] LAYOUTS = {
        "global_dialog_simplified_two_buttons", "global_dialog_simplified_three_buttons",
        "global_dialog_simplified_two_buttons_abroad", "global_dialog_simplified_three_buttons_abroad"};

    static ViewGroup create(Context ctx, ViewGroup nativeButton, String label, int iconKind) {
        int id = nativeButton.getId();
        for (String name : LAYOUTS) {
            int res = ctx.getResources().getIdentifier(name, "layout", "com.android.systemui");
            if (res == 0) continue;
            View v = LayoutInflater.from(ctx).inflate(res, null, false).findViewById(id);
            if (!(v instanceof ViewGroup) || !(v.getParent() instanceof ViewGroup)) continue;
            ViewGroup b = (ViewGroup) v;
            ((ViewGroup) b.getParent()).removeView(b);
            ImageView iv = find(b, ImageView.class), nIv = find(nativeButton, ImageView.class);
            TextView tv = find(b, TextView.class), nTv = find(nativeButton, TextView.class);
            if (iv == null || tv == null || nIv == null || nTv == null) continue;
            iv.getLayoutParams().width = nIv.getLayoutParams().width;
            iv.getLayoutParams().height = nIv.getLayoutParams().height;
            iv.setImageDrawable(new ActionIcon(iconKind));
            if (nIv.getBackground() != null && nIv.getBackground().getConstantState() != null)
                iv.setBackground(nIv.getBackground().getConstantState().newDrawable(ctx.getResources()).mutate());
            iv.setImageTintList(nIv.getImageTintList());
            iv.setScaleType(nIv.getScaleType());
            iv.setPadding(nIv.getPaddingLeft(), nIv.getPaddingTop(), nIv.getPaddingRight(), nIv.getPaddingBottom());
            tv.setText(label);
            tv.setTextSize(0, nTv.getTextSize());
            tv.setTextColor(nTv.getTextColors());
            tv.setTypeface(nTv.getTypeface());
            tv.setIncludeFontPadding(nTv.getIncludeFontPadding());
            tv.setLetterSpacing(nTv.getLetterSpacing());
            tv.getLayoutParams().width = nTv.getLayoutParams().width;
            b.setContentDescription(label);
            iv.setContentDescription(label);
            SparseIntArray ids = new SparseIntArray();
            renewIds(b, ids);
            renewRules(b, ids);
            b.setVisibility(View.VISIBLE);
            b.setAlpha(1f);
            b.setClickable(true);
            b.setFocusable(true);
            return b;
        }
        throw new IllegalStateException("native reboot button layout unavailable");
    }

    static <T extends View> T find(View v, Class<T> cls) {
        if (cls.isInstance(v)) return cls.cast(v);
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            T t = find(g.getChildAt(i), cls);
            if (t != null) return t;
        }
        return null;
    }

    private static void renewIds(View v, SparseIntArray map) {
        if (v.getId() != View.NO_ID) {
            int n = View.generateViewId();
            map.put(v.getId(), n);
            v.setId(n);
        }
        v.setTag(null);
        v.setOnClickListener(null);
        v.setClickable(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) renewIds(g.getChildAt(i), map);
        }
    }

    private static void renewRules(View v, SparseIntArray map) {
        if (v.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
            int[] rules = lp.getRules();
            for (int i = 0; i < rules.length; i++) {
                if (rules[i] > 0) {
                    int n = map.get(rules[i]);
                    if (n != 0) lp.addRule(i, n); else lp.removeRule(i);
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) renewRules(g.getChildAt(i), map);
        }
    }
}
