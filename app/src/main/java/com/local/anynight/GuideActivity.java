package com.local.anynight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
 * 二级页：使用教程。
 *
 * 顺序就是用户实际要做的顺序：先让 Shizuku 跑起来（不然点了没反应），
 * 再给任务卡片上锁（否则隐藏后卡片消失、再也锁不上），最后才是让系统别杀后台。
 * 每一步都尽量直接跳到系统对应页面，跳不过去时退到「应用信息」页。
 */
public class GuideActivity extends Activity {

    private static final int H_PADDING = 20;

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

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 26, color(R.color.text_primary), false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(6), 0, dp(10), dp(2));
        back.setOnClickListener(v -> finish());
        bar.addView(back, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(tr("使用教程"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        boolean miui = DeviceProfile.isMiuiOrHyper();
        TextView intro = text(tr("按顺序做完下面几步。")
                + tr("第 1 步是所有功能的前提（没有 Shizuku，点了也不会有反应），")
                + tr("第 2 步必须在开启「任务切换页隐藏」之前做。")
                + tr("首页已经显示「已生效」的话，可以只补没做过的项。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(14);
        page.addView(intro, introLp);

        addItem(page, "1", tr("启用 Shizuku"),
                tr("本应用借 Shizuku 拿到 shell 权限去拨系统的深浅色开关。")
                        + tr("没装、没启动、没授权，首页三行状态都会是红的。"),
                new String[]{tr("查看步骤")},
                new View.OnClickListener[]{
                        v -> startActivity(new Intent(this, ShizukuGuideActivity.class))
                });

        if (miui) {
            addItem(page, "2", tr("任务切换页上锁"),
                    tr("打开任务切换页，长按「AnyNight」卡片，点锁形图标。")
                            + tr("「任务切换页隐藏」会先把卡片藏起来，所以顺序不能反——")
                            + tr("先上锁，再隐藏。"),
                    new String[]{tr("打开任务切换页")},
                    new View.OnClickListener[]{v -> openRecents()});

            addItem(page, "3", tr("允许自启动"),
                    tr("应用信息 → 自启动。被系统清理后可以自动恢复。"),
                    new String[]{tr("去打开")},
                    new View.OnClickListener[]{v -> openAutostart()});

            addItem(page, "4", tr("省电策略设为无限制（必须）"),
                    tr("应用信息 → 省电策略 → 无限制。这一项没设的话，系统会把后台进程冻结，")
                            + tr("进入应用时根本不会变色。本应用空闲时是 0 CPU，放开不会更耗电。"),
                    new String[]{tr("去设置")},
                    new View.OnClickListener[]{v -> openAppInfo()});

            addItem(page, "5", tr("权限：通知 / 其它"),
                    tr("通知权限决定常驻通知能不能显示（保活效果），")
                            + tr("其它权限里放无障碍、悬浮窗等。两个在系统里是两个不同的页面。"),
                    new String[]{tr("通知权限"), tr("其它权限")},
                    new View.OnClickListener[]{
                            v -> openNotificationSettings(),
                            v -> openOtherPermissions()
                    });
        } else {
            addItem(page, "2", tr("电池优化设为「不受限制」"),
                    tr("系统设置 → 应用 → 本应用 → 电池 → 不受限制。")
                            + tr("这是 AOSP 原生就有的开关，绝大多数机器都有。"),
                    new String[]{tr("去设置")},
                    new View.OnClickListener[]{v -> openBatteryOptimization()});

            addItem(page, "3", tr("允许后台活动"),
                    tr("部分系统在应用信息里还有「允许后台活动 / 自启动」开关，有的话一并打开。"),
                    new String[]{tr("去应用信息")},
                    new View.OnClickListener[]{v -> openAppInfo()});

            addItem(page, "4", tr("权限：通知 / 其它"),
                    tr("通知权限决定常驻通知能不能显示；其它权限里放无障碍、悬浮窗等。"),
                    new String[]{tr("通知权限"), tr("其它权限")},
                    new View.OnClickListener[]{
                            v -> openNotificationSettings(),
                            v -> openOtherPermissions()
                    });
        }

        // Shizuku 一键放行
        LinearLayout boost = new LinearLayout(this);
        boost.setOrientation(LinearLayout.VERTICAL);
        boost.setPadding(dp(16), dp(14), dp(16), dp(14));
        boost.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        boost.addView(text(tr("用 Shizuku 一键放行"), 14.5f, color(R.color.text_primary), true));

        TextView boostDesc = text(tr("加入 Doze 白名单、待机桶设为 active、允许后台运行。")
                + tr("三项都由 shell 执行，不额外增加授权，也不产生常驻开销。"), 12,
                color(R.color.text_secondary), false);
        boostDesc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams boostDescLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        boostDescLp.topMargin = dp(8);
        boost.addView(boostDesc, boostDescLp);

        TextView boostBtn = text(tr("一键放行"), 12, color(R.color.accent_text), true);
        boostBtn.setGravity(Gravity.CENTER);
        boostBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        boostBtn.setBackground(shape(color(R.color.accent), 14));
        boostBtn.setOnClickListener(v -> applyBoost());
        LinearLayout.LayoutParams boostBtnLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        boostBtnLp.topMargin = dp(10);
        boost.addView(boostBtn, boostBtnLp);

        LinearLayout.LayoutParams boostLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        boostLp.bottomMargin = dp(10);
        page.addView(boost, boostLp);

        // 补充说明
        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(16), dp(14), dp(16), dp(14));
        note.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        TextView noteTitle = text(tr("开启无障碍时提示「受限制的设置」？"), 12.5f,
                color(R.color.text_primary), true);
        note.addView(noteTitle);

        TextView noteBody = text(tr("应用信息 → 高级设置 → 允许受限制的设置。")
                + tr("开启后再回到无障碍列表，就能打开本应用的服务。"), 12,
                color(R.color.text_secondary), false);
        noteBody.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteBodyLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteBodyLp.topMargin = dp(7);
        note.addView(noteBody, noteBodyLp);

        LinearLayout.LayoutParams noteLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(6);
        page.addView(note, noteLp);

        // 应用不进深色怎么办
        LinearLayout compat = new LinearLayout(this);
        compat.setOrientation(LinearLayout.VERTICAL);
        compat.setPadding(dp(16), dp(14), dp(16), dp(14));
        compat.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        compat.addView(text(tr("应用进入后没变深？"), 14.5f, color(R.color.text_primary), true));

        TextView compatDesc = text(tr("① 应用内要先选「跟随系统」：小红书是「我 → 设置 → 通用 → 深色模式」，"
                + "多数应用在「设置 → 显示 / 外观」里。不选它，应用永远用自己的主题。") + "\n"
                + tr("② 系统「深色模式设置 → 应用单独设置」里有它，就打开强制变深。") + "\n"
                + tr("③ 两边都没有，说明这个应用没有深色界面，系统也变不了。"), 12,
                color(R.color.text_secondary), false);
        compatDesc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams compatDescLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        compatDescLp.topMargin = dp(8);
        compat.addView(compatDesc, compatDescLp);

        TextView compatBtn = text(tr("打开系统深色设置"), 12, color(R.color.accent_text), true);
        compatBtn.setGravity(Gravity.CENTER);
        compatBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        compatBtn.setBackground(shape(color(R.color.accent), 14));
        compatBtn.setOnClickListener(v -> {
            if (!Compat.openSystemDarkSettings(this)) {
                toast(tr("没能打开系统设置，请手动到「设置 → 显示 → 深色模式」"));
            }
        });
        LinearLayout.LayoutParams compatBtnLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        compatBtnLp.topMargin = dp(10);
        compat.addView(compatBtn, compatBtnLp);

        LinearLayout.LayoutParams compatLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        compatLp.topMargin = dp(6);
        compatLp.bottomMargin = dp(10);
        page.addView(compat, compatLp);

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

    private void addItem(LinearLayout parent, String number, String title, String desc,
                         String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shapeStroked(color(R.color.card), 18, color(R.color.card_stroke)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(number, 11.5f, color(R.color.text_primary), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(color(R.color.chip_bg), 11));
        head.addView(badge, lp(dp(22), dp(22)));

        TextView t = text(title, 14.5f, color(R.color.text_primary), true);
        LinearLayout.LayoutParams tLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.weight = 1;
        tLp.leftMargin = dp(10);
        head.addView(t, tLp);

        card.addView(head);

        TextView d = text(desc, 12, color(R.color.text_secondary), false);
        d.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams dLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(8);
        card.addView(d, dLp);

        // 按钮行：一个或两个都走同一套布局，第二个用描边按钮做次要操作
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        btnRowLp.topMargin = dp(10);

        for (int i = 0; i < actions.length; i++) {
            boolean primary = i == 0;
            TextView btn = text(actions[i], 12,
                    primary ? color(R.color.accent_text) : color(R.color.text_primary), true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(14), dp(8), dp(14), dp(8));
            btn.setBackground(primary ? shape(color(R.color.accent), 14)
                    : shapeStroked(color(R.color.card), 14, color(R.color.card_stroke)));
            btn.setOnClickListener(listeners[i]);

            LinearLayout.LayoutParams blp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            if (i > 0) blp.leftMargin = dp(8);
            btnRow.addView(btn, blp);
        }
        card.addView(btnRow, btnRowLp);

        LinearLayout.LayoutParams cardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        parent.addView(card, cardLp);
    }

    /** 用 shell 身份做三项系统级放行，降低被冻结的概率。 */
    private void applyBoost() {
        if (!NightModeController.hasPermission()) {
            toast(tr("先回上一页完成 Shizuku 授权"));
            return;
        }
        toast(tr("正在放行…"));
        final String pkg = getPackageName();
        new Thread(() -> {
            boolean a = NightModeController.runShell("cmd deviceidle whitelist +" + pkg);
            boolean b = NightModeController.runShell("am set-standby-bucket " + pkg + " active");
            boolean c = NightModeController.runShell(
                    "appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
            final int ok = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0);
            runOnUiThread(() -> toast(ok == 3
                    ? tr("已全部放行（Doze / 待机桶 / 后台运行）")
                    : tr("已放行 ") + ok + tr("/3 项")));
        }).start();
    }

    // ---------------- 跳转 ----------------

    private void openRecents() {
        if (!WatchAccessibilityService.openRecents()) {
            toast(tr("无障碍服务没在运行，先回上一页开启"));
        }
    }

    private void openAutostart() {
        Intent i = new Intent("miui.intent.action.OP_AUTO_START");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("extra_pkgname", getPackageName());
        if (!tryStart(i)) openAppInfo();
    }

    /** 通知权限在系统里是单独一页（「通知管理」），和「其它权限」不是同一个入口。 */
    private void openNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            if (tryStart(i)) return;
        } catch (Throwable ignored) {
        }
        // MIUI 专门的「通知管理」页
        Intent miui = new Intent("miui.intent.action.APP_NOTIFICATION_SETTINGS");
        miui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        miui.putExtra("miui.intent.extra.APP_PACKAGE", getPackageName());
        miui.putExtra("app_package_name", getPackageName());
        if (!tryStart(miui)) openAppInfo();
    }

    /** 其它权限：无障碍、悬浮窗、后台弹出等，走 MIUI 的权限编辑页。 */
    private void openOtherPermissions() {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("extra_pkgname", getPackageName());
        if (tryStart(i)) return;

        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tryStart(fallback);
    }

    /** 非 MIUI 机器：跳到系统的「电池优化」列表，用户在本应用上选「不受限制」。 */
    private void openBatteryOptimization() {
        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(i)) openAppInfo();
    }

    /** 应用信息页：省电策略、自启动、权限、通知都在这一页里。 */
    private void openAppInfo() {
        ComponentName miui = new ComponentName("com.miui.securitycenter",
                "com.miui.appmanager.ApplicationsDetailsActivity");
        Intent i = new Intent();
        i.setComponent(miui);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("package_name", getPackageName());
        if (tryStart(i)) return;

        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tryStart(fallback);
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
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
