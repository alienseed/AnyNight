package com.local.anynight;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
 * 二级页：启用 Shizuku。
 *
 * 单独成页的原因：这是唯一一个「装错了就全盘不能用」的前置条件。
 * 网上流传的云备份恢复版、一键换机带过来的版本、系统商店里的旧版，
 * 常出现「能装能开、就是配对失败」，用户很难自己判断。
 * 所以这里直接给官方 GitHub 与可访问的加速镜像，并写明 7 步操作。
 */
public class ShizukuGuideActivity extends Activity {

    private static final int H_PADDING = 20;

    private static final String GITHUB =
            "https://github.com/RikkaApps/Shizuku/releases";
    private static final String MIRROR_1 =
            "https://ghfast.top/https://github.com/RikkaApps/Shizuku/releases";
    private static final String MIRROR_2 =
            "https://gh-proxy.com/https://github.com/RikkaApps/Shizuku/releases";
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    private static final String[] STEPS = {
            "系统设置里搜「通知样式设置」",
            "把通知样式设为「原生样式」",
            "系统设置里搜「全部参数与信息」",
            "快速点击 5 下「OS 版本」，开启开发者模式",
            "打开 Shizuku 软件，点击「配对」",
            "按指引操作完成后，返回 Shizuku 点击「启用」",
            "启用成功后在 Shizuku 的「应用管理」里，给 AnyNight 打开开关",
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

        TextView title = text(tr("启用 Shizuku"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        TextView intro = text(tr("Shizuku 是开源工具，用一次 ADB 授权把 shell 权限借给普通应用。")
                + tr("AnyNight 只在进入 / 离开你指定的应用时用它拨一下系统的深浅色开关，")
                + tr("不读屏幕、不收集数据。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(12);
        page.addView(intro, introLp);

        // 警告卡片
        LinearLayout warn = new LinearLayout(this);
        warn.setOrientation(LinearLayout.VERTICAL);
        warn.setPadding(dp(16), dp(13), dp(16), dp(13));
        warn.setBackground(shapeStroked(color(R.color.warn_bg), 16, color(R.color.warn_bg)));
        warn.addView(text(tr("安装包从哪来，很关键"), 13, color(R.color.warn), true));
        TextView warnBody = text(tr("请尽量避免使用通过「云恢复、一键换机、系统自带应用商店」")
                + tr("等处提供的 Shizuku 安装包：版本可能偏旧或被替换，")
                + tr("常见表现是能装能开、但配对一直失败。下面两个入口是官方仓库与其加速镜像。"),
                11.5f, color(R.color.text_secondary), false);
        warnBody.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams warnLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        warnLp.topMargin = dp(6);
        warn.addView(warnBody, warnLp);

        LinearLayout.LayoutParams warnCardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        warnCardLp.bottomMargin = dp(10);
        page.addView(warn, warnCardLp);

        // 下载入口
        LinearLayout dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.VERTICAL);
        dl.setPadding(dp(16), dp(6), dp(16), dp(6));
        dl.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        dl.addView(text(tr("下载安装包"), 12, color(R.color.text_tertiary), true));
        addLinkRow(dl, tr("GitHub 官方 Releases"), GITHUB, tr("官方 · 最新版"));
        addLinkRow(dl, tr("加速镜像 1（ghfast.top）"), MIRROR_1, tr("国内一般可直连"));
        addLinkRow(dl, tr("加速镜像 2（gh-proxy.com）"), MIRROR_2, tr("上面那个不通时用"));


        LinearLayout.LayoutParams dlLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlLp.bottomMargin = dp(10);
        page.addView(dl, dlLp);

        // 步骤
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(dp(16), dp(14), dp(16), dp(14));
        steps.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        LinearLayout stepsHead = new LinearLayout(this);
        stepsHead.setOrientation(LinearLayout.HORIZONTAL);
        stepsHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView stepsTitle = text(tr("启用步骤（以小米 / 澎湃OS 为例）"), 14,
                color(R.color.text_primary), true);
        LinearLayout.LayoutParams stepsTitleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsTitleLp.weight = 1;
        stepsHead.addView(stepsTitle, stepsTitleLp);

        TextView copyAll = text(tr("复制全部"), 10.5f, color(R.color.accent_text), true);
        copyAll.setGravity(Gravity.CENTER);
        copyAll.setPadding(dp(10), 0, dp(10), 0);
        copyAll.setBackground(shape(color(R.color.accent), 12));
        copyAll.setOnClickListener(v -> {
            copyToClipboard(stepsAsText());
            toast(tr("全部步骤已复制，可直接粘贴"));
        });
        LinearLayout.LayoutParams copyAllLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27));
        copyAllLp.leftMargin = dp(6);
        stepsHead.addView(copyAll, copyAllLp);

        steps.addView(stepsHead);

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

        TextView tip = text(tr("提示：重启手机后 Shizuku 会失效，需要重新点一次「启用」")
                + tr("（不用重新配对，无线调试会记住）。"), 11, color(R.color.text_tertiary), false);
        tip.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams tipLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = dp(11);
        steps.addView(tip, tipLp);

        LinearLayout.LayoutParams stepsLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsLp.bottomMargin = dp(10);
        page.addView(steps, stepsLp);

        // 底部按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView open = text(tr("打开 Shizuku"), 13, color(R.color.text_primary), true);
        open.setGravity(Gravity.CENTER);
        open.setBackground(shapeStroked(color(R.color.card), 16, color(R.color.card_stroke)));
        open.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
            if (i == null) {
                toast(tr("还没安装 Shizuku，先用上面的链接下载"));
                return;
            }
            try {
                startActivity(i);
            } catch (Throwable t) {
                toast(tr("打不开 Shizuku：") + t.getMessage());
            }
        });
        LinearLayout.LayoutParams openLp = lp(0, dp(42));
        openLp.weight = 1;
        btnRow.addView(open, openLp);

        View gap = new View(this);
        btnRow.addView(gap, lp(dp(10), dp(1)));

        TextView done = text(tr("我已启用，返回"), 13, color(R.color.accent_text), true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(shape(color(R.color.accent), 16));
        done.setOnClickListener(v -> {
            toast(tr("回首页看「Shizuku 服务」和「切换权限」两行是否变绿"));
            finish();
        });
        LinearLayout.LayoutParams doneLp = lp(0, dp(42));
        doneLp.weight = 1;
        btnRow.addView(done, doneLp);

        page.addView(btnRow);

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

    /** 一行：标题 + 说明 + 域名；点一下用浏览器打开，长按复制。 */
    private void addLinkRow(LinearLayout parent, String title, String url, String note) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = text(title, 13, color(R.color.text_primary), true);
        LinearLayout.LayoutParams tLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.weight = 1;
        top.addView(tv, tLp);

        // 右侧两个小按钮：复制这条链接 / 直接用浏览器打开
        TextView cp = text(tr("复制"), 10.5f, color(R.color.text_tertiary), true);
        cp.setGravity(Gravity.CENTER);
        cp.setPadding(dp(9), 0, dp(9), 0);
        cp.setBackground(shapeStroked(color(R.color.card), 11, color(R.color.card_stroke)));
        cp.setOnClickListener(v -> {
            copyToClipboard(url);
            toast(tr("链接已复制"));
        });
        top.addView(cp, lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));

        TextView op = text(tr("打开"), 10.5f, color(R.color.accent_text), true);
        op.setGravity(Gravity.CENTER);
        op.setPadding(dp(9), 0, dp(9), 0);
        op.setBackground(shape(color(R.color.accent), 11));
        op.setOnClickListener(v -> openUrl(url));
        LinearLayout.LayoutParams opLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
        opLp.leftMargin = dp(6);
        top.addView(op, opLp);

        row.addView(top);

        TextView n = text(note, 10.5f, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams nLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nLp.topMargin = dp(2);
        row.addView(n, nLp);

        row.setOnClickListener(v -> openUrl(url));
        row.setOnLongClickListener(v -> {
            copyToClipboard(url);
            toast(tr("链接已复制"));
            return true;
        });

        LinearLayout.LayoutParams rowLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(2);
        parent.addView(row, rowLp);
    }

    private void copyToClipboard(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("AnyNight", s));
        } catch (Throwable ignored) {
        }
    }

    /** 用浏览器打开；没有浏览器就把链接复制走。 */
    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            copyToClipboard(url);
            toast(tr("没有浏览器，链接已复制"));
        }
    }

    /** 把标题、七个步骤和提示拼成一段纯文本，方便贴到别处对照操作。 */
    private String stepsAsText() {
        StringBuilder sb = new StringBuilder();
        sb.append(tr("启用步骤（以小米 / 澎湃OS 为例）")).append("\n");
        for (int i = 0; i < STEPS.length; i++) {
            sb.append(i + 1).append(". ").append(tr(STEPS[i])).append("\n");
        }
        sb.append("\n").append(tr("提示：重启手机后 Shizuku 会失效，需要重新点一次「启用」"))
                .append(tr("（不用重新配对，无线调试会记住）。"));
        return sb.toString();
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
