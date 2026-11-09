package com.local.anynight;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
 * 二级页：联系作者。
 *
 * 三块内容：作者邮箱（点按/长按复制）、导出日志（zip 存到 下载）、
 * 以及「免费使用、捐献不影响性质、不承诺回复」的说明。
 */
public class ContactActivity extends Activity {

    static final String AUTHOR_EMAIL = "alienleung@foxmail.com";

    private static final int H_PADDING = 20;

    private boolean exporting;

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

    // ---------------- 基础工具 ----------------

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

    // ---------------- 界面 ----------------

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
        bar.addView(back, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(tr("联系作者"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        TextView intro = text(tr("用邮件联系最稳妥。带上日志，能省掉很多来回问。"), 12.5f,
                color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(14);
        page.addView(intro, introLp);

        page.addView(mailCard());
        page.addView(logCard());
        page.addView(noteCard());

        TextView close = text(tr("关闭"), 13, color(R.color.text_secondary), true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(22), 0, dp(22), 0);
        close.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        closeLp.gravity = Gravity.CENTER_HORIZONTAL;
        closeLp.topMargin = dp(4);
        page.addView(close, closeLp);

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

    private View mailCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        card.addView(text(tr("作者邮箱"), 12, color(R.color.text_tertiary), true));

        TextView mail = text(AUTHOR_EMAIL, 17, color(R.color.text_primary), true);
        mail.setPadding(dp(14), dp(12), dp(14), dp(12));
        mail.setBackground(shape(color(R.color.chip_bg), 14));
        mail.setGravity(Gravity.CENTER);
        mail.setOnClickListener(v -> copyMail());
        mail.setOnLongClickListener(v -> {
            copyMail();
            return true;
        });
        LinearLayout.LayoutParams mailLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mailLp.topMargin = dp(8);
        card.addView(mail, mailLp);

        TextView hint = text(tr("点按或长按即可复制"), 11, color(R.color.text_tertiary), false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        card.addView(hint, hintLp);

        LinearLayout.LayoutParams cardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        return card;
    }

    private View logCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        card.addView(text(tr("先导出日志"), 14.5f, color(R.color.text_primary), true));

        TextView desc = text(tr("日志记录切换时间、目标应用、开关结果和崩溃堆栈，")
                + tr("外加系统版本与机型，不含照片和任何账户信息。")
                + tr("导出为 zip 存到「下载/AnyNight」，发邮件时把它作为附件即可。"),
                12, color(R.color.text_secondary), false);
        desc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(8);
        card.addView(desc, descLp);

        TextView btn = text(tr("导出日志（zip）"), 12, color(R.color.accent_text), true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(8), dp(14), dp(8));
        btn.setBackground(shape(color(R.color.accent), 14));
        btn.setOnClickListener(v -> exportLog());
        LinearLayout.LayoutParams btnLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        btnLp.topMargin = dp(10);
        card.addView(btn, btnLp);

        LinearLayout.LayoutParams cardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        return card;
    }

    private View noteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shape(color(R.color.chip_bg), 18));

        card.addView(text(tr("关于捐献与回复"), 12.5f, color(R.color.text_primary), true));

        TextView body = text(tr("本软件免费使用全部功能，是否捐献不影响其性质，")
                + tr("作者也无法承诺是否或多久回复你的联系。"), 12,
                color(R.color.text_secondary), false);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(7);
        card.addView(body, bodyLp);

        LinearLayout.LayoutParams cardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);
        return card;
    }

    // ---------------- 行为 ----------------

    private void copyMail() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("email", AUTHOR_EMAIL));
                toast(tr("已复制 ") + AUTHOR_EMAIL);
            }
        } catch (Throwable t) {
            toast(tr("复制失败：") + t.getMessage());
        }
    }

    private void exportLog() {
        if (exporting) return;
        exporting = true;
        toast(tr("正在打包日志…"));
        Logx.i(tr("用户触发导出日志"));
        new Thread(() -> {
            String path = null;
            String err = null;
            try {
                path = Logx.exportZip(this);
            } catch (Throwable t) {
                err = t.getMessage();
                Logx.e(tr("导出日志失败"), t);
            }
            final String okPath = path;
            final String failMsg = err;
            runOnUiThread(() -> {
                exporting = false;
                toast(okPath != null ? tr("已保存到 ") + okPath
                        : tr("导出失败：") + (failMsg == null ? tr("未知错误") : failMsg));
            });
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 系统栏 ----------------

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
