package com.local.anynight;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * 首次启动的开场叙事。只放一次。
 *
 * 播完不直接切走，而是用 900ms 把整屏淡到米白，再打开首页——
 * 首页底色也是米白，所以用户看到的是「名字化开，界面就在那儿」。
 */
public class IntroActivity extends Activity {

    /**
     * 收尾淡出：正常播完时画面最后一帧本来就停在米白上（19.35–20.45 已经把整屏淡到米白），
     * 所以不再多等，直接交给首页淡入——中间那 0.4 秒空白就是用户看到的「白屏」。
     * 只有跳过时画面可能停在深色中途，才需要淡一下兜底。
     */
    private static final long FADE_SKIP_MS = 240;

    private IntroView view;
    private View root;
    private boolean leaving;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        // 首帧永远是米白（第一幕是白天），所以窗口底固定米白 —— 夜间也不能换成深色，
        // 否则第一帧画上来之前会先闪一下黑
        w.setBackgroundDrawable(new ColorDrawable(IntroView.LIGHT_BG));
        // 开场是全屏的：系统栏收起来，画面才干净，也不会出现「深色页面配深色图标」
        w.setDecorFitsSystemWindows(false);
        keepSystemBarsQuiet();

        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setBackgroundColor(IntroView.LIGHT_BG);
        view = new IntroView(this, this::leave);
        box.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(box);
        root = box;

        // 4.0s 转入暗场 → 图标翻成浅色，两种主题都一样。
        // 15.6s：白天转回米白，图标翻回深色；夜间第四幕仍留在暗场，就不翻了。
        final boolean night = IntroView.isNight(this);
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> setLightIcons(false), 4000);
        if (!night) {
            h.postDelayed(() -> setLightIcons(true), 15600);
        }

        if (animatorScaleZero()) {
            goNext();          // 系统关掉了动画：这个页面没有存在的意义
            return;
        }
        view.start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();      // 有的 ROM 会把 hide 掉，聚焦后再收一次
    }

    private void hideBars() {
        try {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } catch (Throwable ignored) {
        }
    }

    private void setLightIcons(boolean light) {
        try {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(light ? mask : 0, mask);
        } catch (Throwable ignored) {
        }
    }

    /** 系统里把动画缩放关成 0 的人，直接跳过，别硬播。 */
    private boolean animatorScaleZero() {
        try {
            float v = Settings.Global.getFloat(getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return v == 0f;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 播完 / 被点掉：进首页。正常播完时不加收尾淡出，让首页从这片米白里直接浮出来。 */
    private void leave(boolean skipped) {
        if (leaving) return;
        leaving = true;
        if (view != null) view.stop();

        if (root == null || !skipped) {
            // 正常播完：最后一帧已是米白，切过去没有可感的中断，直接交给首页的淡入
            goNext();
            return;
        }
        // 跳过：画面可能停在深色的半途，先淡到米白再走
        root.animate()
                .alpha(0f)
                .setDuration(FADE_SKIP_MS)
                .withEndAction(this::goNext)
                .start();
        // 兜底：万一动画没回调（比如系统卡顿），稍后强制走
        new Handler(Looper.getMainLooper()).postDelayed(this::goNext, FADE_SKIP_MS + 700);
    }

    private void goNext() {
        if (isFinishing() || nextStarted) return;
        nextStarted = true;
        // 带个标记过去，首页据此把自己从米白里淡出来，接住开场的最后一帧。
        // CLEAR_TOP|SINGLE_TOP 是为了「长按标题重看」这条路：此时栈里那个首页还在，
        // 复用它（走 onNewIntent）比再堆一个新的干净，也不会有两个首页叠在返回栈里。
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_FROM_INTRO, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }

    private boolean nextStarted;

    @Override
    public void onBackPressed() {
        // 返回键等同跳过，不留在一个没有操作的页面上
        leave(true);
    }

    @Override
    protected void onDestroy() {
        if (view != null) view.stop();
        super.onDestroy();
    }

    private void keepSystemBarsQuiet() {
        Window w = getWindow();
        try {
            w.setStatusBarColor(IntroView.LIGHT_BG);
            w.setNavigationBarColor(IntroView.LIGHT_BG);
        } catch (Throwable ignored) {
        }
        try {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                // 首帧是米白（白天那一幕），深浅色主题下都一样配深图标
                c.setSystemBarsAppearance(mask, mask);
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Throwable ignored) {
        }
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
    }
}
