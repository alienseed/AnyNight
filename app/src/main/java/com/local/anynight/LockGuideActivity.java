package com.local.anynight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 二级页：开启「任务切换页隐藏」之前的强制教程。
 *
 * 顺序问题是真实存在的：卡片一旦隐藏，用户在任务切换页里就再也找不到它，
 * 也就无法长按上锁。所以这里做成强制前置——完成上锁回来，才允许打开隐藏开关。
 *
 * 系统不对外暴露「某张卡片是否已上锁」，本应用无法自动校验，
 * 只能把步骤写清楚、让用户自己确认，并把后果说明白。
 */
public class LockGuideActivity extends Activity {

    private static final int H_PADDING = 20;

    private static final String[] STEPS = {
            "打开任务切换页（屏幕底部上滑并停住，或点下面的按钮）",
            "找到「AnyNight」的卡片，长按它",
            "点卡片上的锁形图标，让锁闭合",
            "回到本页，点最下方的「我已上锁，开启隐藏」",
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        applySystemBars();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        applySystemBars();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int color(int res) {
        return getColor(res);
    }

    private static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private GradientDrawable shape(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable shapeStroked(int fill, float radiusDp, int stroke) {
        GradientDrawable d = shape(fill, radiusDp);
        d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private String tr(String s) {
        return I18n.t(this, s);
    }

    private TextView text(String s, float sizeSp, int col, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(col);
        if (bold) t.setTypeface(medium());
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(color(R.color.bg));
        page.setPadding(dp(H_PADDING), dp(10), dp(H_PADDING), dp(20));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 26, color(R.color.text_primary), false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(6), 0, dp(10), dp(2));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = text(tr("先给任务卡片上锁"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        TextView intro = text(tr("「任务切换页隐藏」打开后，本应用不会出现在任务切换页里。")
                + tr("也就是说：先隐藏、后想上锁，就没有卡片可长了。")
                + tr("所以这一步必须在打开隐藏开关之前做完。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(12);
        page.addView(intro, introLp);

        // 步骤卡
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(dp(16), dp(14), dp(16), dp(14));
        steps.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        steps.addView(text(tr("操作步骤"), 14.5f, color(R.color.text_primary), true));
        for (int i = 0; i < STEPS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            TextView num = text(String.valueOf(i + 1), 10.5f, color(R.color.text_secondary), true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(shape(color(R.color.chip_bg), 9));
            LinearLayout.LayoutParams numLp = lp(dp(18), dp(18));
            numLp.topMargin = dp(1);
            row.addView(num, numLp);

            TextView body = text(tr(STEPS[i]), 12.5f, color(R.color.text_secondary), false);
            body.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams bodyLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyLp.weight = 1;
            bodyLp.leftMargin = dp(9);
            row.addView(body, bodyLp);

            LinearLayout.LayoutParams rowLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(9);
            steps.addView(row, rowLp);
        }

        TextView openRecents = text(tr("打开任务切换页"), 12, color(R.color.accent_text), true);
        openRecents.setGravity(Gravity.CENTER);
        openRecents.setPadding(dp(14), dp(8), dp(14), dp(8));
        openRecents.setBackground(shape(color(R.color.accent), 14));
        openRecents.setOnClickListener(v -> {
            if (!WatchAccessibilityService.openRecents()) {
                toast(tr("无障碍服务没在运行，请手动上滑打开任务切换页"));
            }
        });
        LinearLayout.LayoutParams openLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        openLp.topMargin = dp(12);
        steps.addView(openRecents, openLp);

        LinearLayout.LayoutParams stepsLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsLp.bottomMargin = dp(10);
        page.addView(steps, stepsLp);

        // 说明卡
        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(16), dp(13), dp(16), dp(13));
        note.setBackground(shapeStroked(color(R.color.warn_bg), 16, color(R.color.warn_bg)));

        note.addView(text(tr("为什么非要上锁"), 13, color(R.color.warn), true));

        TextView noteBody = text(tr("上锁之后，系统清理后台时会保留这张卡片，本应用更容易一直活着；")
                + tr("没上锁又已经隐藏，被清理后只能靠「允许自启动」慢慢恢复，效果会明显打折。")
                + tr("系统不对外暴露卡片是否已上锁，本应用没法替你检查，只能由你确认。"),
                11.5f, color(R.color.text_secondary), false);
        noteBody.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteBodyLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteBodyLp.topMargin = dp(6);
        note.addView(noteBody, noteBodyLp);

        page.addView(note, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 底部按钮
        TextView done = text(tr("我已上锁，开启隐藏"), 14, color(R.color.accent_text), true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(shape(color(R.color.accent), 20));
        done.setOnClickListener(v -> confirm());
        LinearLayout.LayoutParams doneLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        doneLp.topMargin = dp(14);
        page.addView(done, doneLp);

        TextView later = text(tr("先不隐藏"), 12.5f, color(R.color.text_secondary), false);
        later.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams laterLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        laterLp.topMargin = dp(4);
        later.setOnClickListener(v -> finish());
        page.addView(later, laterLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        setContentView(scroll);
    }

    /** 无法自动校验，所以用一次明确确认把责任说清楚。 */
    private void confirm() {
        new AlertDialog.Builder(this)
                .setTitle(tr("确认已上锁？"))
                .setMessage(tr("锁闭合后，任务切换页里这张卡片右上角会显示锁形图标。")
                        + tr("确认之后本应用会从任务切换页隐藏，届时无法再长按上锁。"))
                .setNegativeButton(tr("还没上锁"), null)
                .setPositiveButton(tr("已上锁"), (d, w) -> {
                    Cfg.setRecentsLockDone(this, true);
                    setResult(RESULT_OK, new Intent());
                    finish();
                })
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars() {
        Window w = getWindow();
        int bg = color(R.color.bg);
        try {
            w.setStatusBarColor(bg);
            w.setNavigationBarColor(bg);
        } catch (Throwable ignored) {
        }
        try {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(isNight() ? 0 : mask, mask);
            }
        } catch (Throwable ignored) {
        }
    }
}
