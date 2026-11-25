package dev.oscar.powermenuplus;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds "Soft reboot" and "Restart SystemUI" buttons under the native reboot button of the RedMagic power menu. */
public final class PowerMenuPlusModule extends XposedModule {
    private static final String TAG = "PowerMenuPlus";
    private static final String DIALOG = "com.zte.feature.globalaction.MfvGlobalActionsDialog$MFVActionsDialog";
    private static final Map<View, Float> NATIVE_ROW_Y = new WeakHashMap<>();
    private static final int COUNT = 2;
    private boolean installed;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam p) {
        android.util.Log.i(TAG, "onPackageReady " + p.getPackageName());
        if (installed || !"com.android.systemui".equals(p.getPackageName())) return;
        try {
            install(p.getClassLoader());
            installed = true;
            try { installAssist(p.getClassLoader()); } catch (Throwable t) { log(Log.ERROR, TAG, "assist hook failed", t); }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install failed", t); Log.e(TAG, "install failed", t);
        }
    }

    private static final long MENU_MS = 2000;

    /** Power-menu (very long press) delay lives in system_server's SingleKeyRule; shorten it for the power key. */
    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam p) {
        try {
            Class<?> rule = Class.forName("com.android.server.policy.SingleKeyGestureDetector$SingleKeyRule", false, p.getClassLoader());
            hook(rule.getDeclaredMethod("getVeryLongPressTimeoutMs")).setId("ppm.veryLong")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                    chain.getThisObject().getClass().getName().endsWith("PowerKeyRule") ? (Object) Long.valueOf(MENU_MS) : chain.proceed());
            Log.i(TAG, "system hook installed");
        } catch (Throwable t) { Log.e(TAG, "system hook failed", t); }
    }

    private static boolean zh(Context c) {
        return "zh".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private void install(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(DIALOG, false, cl);
        if (!Dialog.class.isAssignableFrom(cls)) throw new NoSuchMethodException("not an OEM dialog");
        Method initView = cls.getDeclaredMethod("initView");
        Field contentView = cls.getDeclaredField("mContentView"); contentView.setAccessible(true);
        Field resetContent = cls.getDeclaredField("mResetContent"); resetContent.setAccessible(true);
        Field globalAnim = cls.getDeclaredField("mGlobalAnim"); globalAnim.setAccessible(true);
        Method visibleAnim = globalAnim.getType().getDeclaredMethod("startVisibleAnim", View.class, boolean.class);

        hook(initView).setId("ppm.initView").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                Log.i(TAG, "initView ran");
                try {
                    Dialog d = (Dialog) chain.getThisObject();
                    ViewGroup content = (ViewGroup) contentView.get(d);
                    View reset = (View) resetContent.get(d);
                    if (content != null && reset != null) bind(d, content, reset);
                } catch (Throwable t) { log(Log.ERROR, TAG, "bind", t); Log.e(TAG, "bind", t); }
                return r;
            });

        String[] names = {"startSelectAnim", "startActionAnim", "startActionLoading", "startUnVisiableAnim",
            "startLockSelectAnim", "startUnSelectedAnim", "startVisiableAnim", "onStop"};
        for (String n : names) {
            final boolean show = n.equals("startUnSelectedAnim") || n.equals("startVisiableAnim");
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(n)) continue;
                hook(m).setId("ppm." + n).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();
                        try { sync(chain.getThisObject(), show, contentView, resetContent, globalAnim, visibleAnim); }
                        catch (Throwable t) { log(Log.ERROR, TAG, "sync", t); }
                        return r;
                    });
            }
        }
        log(Log.INFO, TAG, "hooks installed"); Log.i(TAG, "hooks installed");
    }

    private static final ThreadLocal<Boolean> POWER_ASSIST = ThreadLocal.withInitial(() -> false);

    /**
     * The ROM routes the power-key assistant through ZTE's AssistManagerAdapt, which only starts the ZTE
     * assistant. For the power-button invocation (type 6) skip it so AOSP's chain opens the default assistant.
     */
    private void installAssist(ClassLoader cl) throws Throwable {
        Class<?> am = Class.forName("com.android.systemui.assist.AssistManager", false, cl);
        Class<?> adapt = Class.forName("com.zte.adapt.mifavor.navbar.AssistManagerAdapt", false, cl);
        hook(am.getDeclaredMethod("startAssist", Bundle.class)).setId("ppm.startAssist")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Bundle b = (Bundle) chain.getArg(0);
                boolean prev = POWER_ASSIST.get();
                if (b != null && b.getInt("invocation_type", 0) == 6) POWER_ASSIST.set(true);
                try { return chain.proceed(); } finally { POWER_ASSIST.set(prev); }
            });
        hook(adapt.getDeclaredMethod("handleStartAssist", Bundle.class, Context.class)).setId("ppm.handleStartAssist")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                POWER_ASSIST.get() ? (Object) Boolean.FALSE : chain.proceed());
        hook(adapt.getDeclaredMethod("getAssistInfoForUser", android.content.ComponentName.class)).setId("ppm.getAssistInfo")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                POWER_ASSIST.get() ? chain.getArg(0) : chain.proceed());
    }

    private void sync(Object dialog, boolean show, Field contentView, Field resetContent, Field globalAnim, Method visibleAnim) throws Throwable {
        ViewGroup content = (ViewGroup) contentView.get(dialog);
        View box = content == null ? null : content.findViewWithTag(TAG);
        if (show && content != null && box == null && resetContent.get(dialog) instanceof View) {
            bind((Dialog) dialog, content, (View) resetContent.get(dialog));
            box = content.findViewWithTag(TAG);
        }
        if (!(box instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) box;
        Object anim = globalAnim.get(dialog);
        if (!show || anim == null) {
            g.setVisibility(View.INVISIBLE);
            for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).clearAnimation();
        } else {
            g.setVisibility(View.VISIBLE);
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                c.clearAnimation();
                visibleAnim.invoke(anim, c, true);
            }
        }
    }

    private void bind(Dialog dialog, ViewGroup content, View reset) {
        if (reset.getParent() instanceof View) {
            View row = (View) reset.getParent();
            Float y = NATIVE_ROW_Y.remove(row);
            if (y != null) row.setTranslationY(y);
        }
        View old = content.findViewWithTag(TAG);
        if (old != null) content.removeView(old);
        if (!(reset instanceof ViewGroup)) return;
        final Context ctx = content.getContext();
        final boolean zh = zh(ctx);
        String[] labels = {zh ? "\u8f6f\u91cd\u542f" : "Soft reboot", zh ? "\u91cd\u542f\u7cfb\u7edf\u754c\u9762" : "Restart SystemUI"};
        int[] icons = {ActionIcon.SOFT_REBOOT, ActionIcon.SYSTEMUI};
        final FrameLayout box = new FrameLayout(ctx);
        box.setTag(TAG);
        box.setVisibility(View.INVISIBLE);
        final int gap = Math.round(ctx.getResources().getDisplayMetrics().density * 16f);
        final ViewGroup[] btns = new ViewGroup[COUNT];
        for (int i = 0; i < COUNT; i++) {
            final int idx = i;
            btns[i] = NativeButtonFactory.create(ctx, (ViewGroup) reset, labels[i], icons[i]);
            box.addView(btns[i], new FrameLayout.LayoutParams(-2, -2));
            btns[i].setOnClickListener(v -> onClick(dialog, ctx, btns, idx, labels[idx]));
        }
        content.addView(box, new ViewGroup.LayoutParams(-1, -2));
        content.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int a, int b, int c, int d, int e, int f, int g, int h) {
                if (box.getParent() != content) content.removeOnLayoutChangeListener(this);
                else position(content, reset, box, btns, gap);
            }
        });
        content.post(() -> position(content, reset, box, btns, gap));
    }

    private void onClick(Dialog dialog, Context ctx, ViewGroup[] btns, int idx, String label) {
        if (!dialog.isShowing()) return;
        if (idx == 1) {                       // restart SystemUI: the persistent process is respawned by the system
            // close the menu first so system_server clears its "global actions showing" state; otherwise it
            // falls back to the AOSP LegacyGlobalActions dialog while SystemUI is respawning
            dialog.dismiss();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
            return;
        }
        for (ViewGroup b : btns) b.setEnabled(false);
        new Thread(() -> {
            String err = null;
            try {
                Bundle r = ctx.getContentResolver().call(Uri.parse("content://dev.oscar.powermenuplus.action"),
                    "soft_reboot", null, null);
                if (r == null) err = "Power Menu Plus provider unavailable";
                else if (!r.getBoolean("ok", false)) err = r.getString("message", "failed");
            } catch (Throwable t) { err = String.valueOf(t.getMessage()); }
            final String e = err;
            btns[0].post(() -> {
                if (e == null) {
                    btns[0].postDelayed(() -> { for (ViewGroup b : btns) b.setEnabled(true); }, 5000);
                } else {
                    for (ViewGroup b : btns) b.setEnabled(true);
                    Toast.makeText(ctx, label + ": " + e, Toast.LENGTH_LONG).show();
                }
            });
        }, "PPM-softreboot").start();
    }

    /** Same geometry as the native grid: one extra row of COUNT buttons below the reset button. */
    private static void position(ViewGroup content, View reset, View box, ViewGroup[] btns, int gap) {
        if (content.getWidth() <= 0 || reset.getWidth() <= 0 || reset.getHeight() <= 0) return;
        int[] cl = new int[2], rl = new int[2];
        content.getLocationOnScreen(cl);
        reset.getLocationOnScreen(rl);
        int w = reset.getWidth(), h = reset.getHeight();
        int bw = w * COUNT;
        if (box.getLayoutParams().width != bw || box.getLayoutParams().height != h) {
            box.getLayoutParams().width = bw;
            box.getLayoutParams().height = h;
            box.requestLayout();
        }
        for (int i = 0; i < COUNT; i++) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) btns[i].getLayoutParams();
            int left = i * w;
            if (lp.width != w || lp.height != h || lp.leftMargin != left || lp.topMargin != 0) {
                lp.width = w; lp.height = h; lp.leftMargin = left; lp.topMargin = 0;
                btns[i].setLayoutParams(lp);
            }
        }
        if (reset.getParent() instanceof View) {
            View row = (View) reset.getParent();
            NATIVE_ROW_Y.putIfAbsent(row, row.getTranslationY());
            int total = h * 2 + gap;
            float top = Math.max(gap, (content.getHeight() - total) / 2f);
            float delta = top - (rl[1] - cl[1]);
            if (Math.abs(delta) > 0.5f) row.setTranslationY(row.getTranslationY() + delta);
            box.setX((content.getWidth() - bw) / 2f);
            box.setY(top + h + gap);
        }
    }
}
