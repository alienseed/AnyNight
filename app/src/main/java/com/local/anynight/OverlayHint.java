package com.local.anynight;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 切换提示：在「对方应用」之上盖一条两秒的小字。
 *
 * 为什么需要它：有些应用自己就有「强制深色」开关，用户看到界面变深分不清是谁干的。
 * 本应用切完系统开关后，在同屏底部打一条提示，就能把「这是本应用触发的」说清楚。
 *
 * 实现用 TYPE_APPLICATION_OVERLAY（悬浮窗），因为 Toast 在 Android 12+ 里
 * 由后台进程发出会被系统丢掉，恰好本应用就是后台进程。
 * 没有悬浮窗权限时退化为 Toast，并且只在应用自己在前台时才看得到。
 */
final class OverlayHint {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View current;
    private static WindowManager wm;

    private OverlayHint() {
    }

    static boolean canOverlay(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 跳到系统「显示在其他应用上层」授权页。 */
    static boolean requestOverlayPermission(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            Logx.w("打开悬浮窗授权页失败: " + t);
            return false;
        }
    }

    /**
     * @param dark true 表示刚切成深色
     */
    static void showSwitch(Context c, boolean dark, String why) {
        String text = I18n.t(c, dark ? "AnyNight · 已切为深色" : "AnyNight · 已切为浅色");
        if (why != null && !why.isEmpty()) text = text + "（" + why + "）";
        show(c, text, dark ? 0xFF7BD88F : 0xFFFFC46B);
    }

    static void show(Context c, String text, int dotColor) {
        if (!canOverlay(c)) {
            Logx.i("无悬浮窗权限，提示降级为 Toast");
            toast(c, text);
            return;
        }
        try {
            final Context app = c.getApplicationContext();
            MAIN.post(() -> {
                try {
                    remove();
                    if (wm == null) {
                        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                    }
                    if (wm == null) return;

                    LinearLayout box = new LinearLayout(app);
                    box.setOrientation(LinearLayout.HORIZONTAL);
                    box.setGravity(Gravity.CENTER_VERTICAL);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xF01A1A1E);
                    bg.setCornerRadius(dp(app, 18));
                    box.setBackground(bg);
                    box.setPadding(dp(app, 14), dp(app, 9), dp(app, 16), dp(app, 9));

                    View dot = new View(app);
                    GradientDrawable dotBg = new GradientDrawable();
                    dotBg.setShape(GradientDrawable.OVAL);
                    dotBg.setColor(dotColor);
                    dot.setBackground(dotBg);
                    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                            dp(app, 7), dp(app, 7));
                    dotLp.rightMargin = dp(app, 8);
                    box.addView(dot, dotLp);

                    TextView tv = new TextView(app);
                    tv.setText(text);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    tv.setTextColor(Color.WHITE);
                    tv.setIncludeFontPadding(false);
                    tv.setMaxWidth(Math.min(dp(app, 280),
                            app.getResources().getDisplayMetrics().widthPixels
                                    - dp(app, 72)));
                    box.addView(tv);

                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            Build.VERSION.SDK_INT >= 26
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            android.graphics.PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    lp.y = dp(app, 96);

                    box.setAlpha(0f);
                    wm.addView(box, lp);
                    current = box;
                    Logx.i("悬浮提示已显示: " + text);
                    box.animate().alpha(1f).setDuration(140).start();

                    MAIN.postDelayed(OverlayHint::remove, 2600);
                } catch (Throwable t) {
                    Logx.w("悬浮提示失败: " + t);
                }
            });
        } catch (Throwable t) {
            Logx.w("悬浮提示失败: " + t);
        }
    }

    private static void remove() {
        View v = current;
        current = null;
        if (v == null || wm == null) return;
        try {
            v.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> {
                        try {
                            wm.removeView(v);
                        } catch (Throwable ignored) {
                        }
                    }).start();
        } catch (Throwable t) {
            try {
                wm.removeView(v);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void toast(Context c, String text) {
        try {
            android.widget.Toast.makeText(c.getApplicationContext(), text,
                    android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
