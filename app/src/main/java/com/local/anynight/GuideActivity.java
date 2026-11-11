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

    /** 从首页「Shizuku 服务」那一行点进来时，闪烁第 1 步。 */
    static final String EXTRA_HIGHLIGHT = "highlight_step";
    /** 首次安装后的第一次打开：进来先弹一次「必须开启全部权限」。 */
    static final String EXTRA_FIRST_RUN = "first_run";

    private static final int H_PADDING = 20;

    private LinearLayout scrollBody;
    private ScrollView scrollView;
    private View firstStepCard;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firstRun = getIntent() != null && getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);
        buildUi();
        applySystemBars();
        if (firstRun) showFirstRunDialog();
        if (getIntent() != null && getIntent().getIntExtra(EXTRA_HIGHLIGHT, 0) == 1) {
            flashFirstStep();
        }
    }

    private boolean firstRun;

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
                + tr("「任务切换页隐藏」必须在上锁那步之后才开。")
                + tr("首页已经显示「已生效」的话，可以只补没做过的项。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(14);
        page.addView(intro, introLp);

        firstStepCard = addItem(page, "1", tr("启用 Shizuku"),
                tr("本应用借 Shizuku 拿到 shell 权限去拨系统的深浅色开关。")
                        + tr("没装、没启动、没授权，首页三行状态都会是红的。"),
                new String[]{tr("查看步骤")},
                new View.OnClickListener[]{
                        v -> startActivity(new Intent(this, ShizukuGuideActivity.class))
                });

        addItem(page, "2", tr("开启无障碍服务"),
                tr("AnyNight 靠无障碍判断「现在前台是哪个应用」，没开就等于不知道你进了哪个应用。")
                        + tr("设置 → 无障碍 → 已下载的应用 → 找到「AnyNight」并打开。"),
                new String[]{tr("去开启")},
                new View.OnClickListener[]{v -> openAccessibilitySettings()});

        if (miui) {
            addItem(page, "3", tr("任务切换页上锁"),
                    tr("打开任务切换页，长按「AnyNight」卡片，点锁形图标。")
                            + tr("「任务切换页隐藏」会先把卡片藏起来，所以顺序不能反——")
                            + tr("先上锁，再隐藏。"),
                    new String[]{tr("打开任务切换页")},
                    new View.OnClickListener[]{v -> openRecents()});

            addItem(page, "4", tr("允许自启动"),
                    tr("应用信息 → 自启动。被系统清理后可以自动恢复。"),
                    new String[]{tr("去打开")},
                    new View.OnClickListener[]{v -> openAutostart()});

            addItem(page, "5", tr("省电策略设为无限制（必须）"),
                    tr("应用信息 → 省电策略 → 无限制。这一项没设的话，系统会把后台进程冻结，")
                            + tr("进入应用时根本不会变色。本应用空闲时是 0 CPU，放开不会更耗电。"),
                    new String[]{tr("去设置")},
                    new View.OnClickListener[]{v -> openAppInfo()});

            addItem(page, "6", tr("权限：通知 / 其它"),
                    tr("通知权限决定常驻通知能不能显示（保活效果），")
                            + tr("其它权限里放无障碍、悬浮窗等。两个在系统里是两个不同的页面。"),
                    new String[]{tr("通知权限"), tr("其它权限")},
                    new View.OnClickListener[]{
                            v -> openNotificationSettings(),
                            v -> openOtherPermissions()
                    });
        } else {
            addItem(page, "3", tr("电池优化设为「不受限制」"),
                    tr("系统设置 → 应用 → 本应用 → 电池 → 不受限制。")
                            + tr("这是 AOSP 原生就有的开关，绝大多数机器都有。"),
                    new String[]{tr("去设置")},
                    new View.OnClickListener[]{v -> openBatteryOptimization()});

            addItem(page, "4", tr("允许后台活动"),
                    tr("部分系统在应用信息里还有「允许后台活动 / 自启动」开关，有的话一并打开。"),
                    new String[]{tr("去应用信息")},
                    new View.OnClickListener[]{v -> openAppInfo()});

            addItem(page, "5", tr("权限：通知 / 其它"),
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

        LinearLayout boostHead = new LinearLayout(this);
        boostHead.setOrientation(LinearLayout.HORIZONTAL);
        boostHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView boostTitle = text(tr("用 Shizuku 一键放行"), 14,
                color(R.color.text_primary), true);
        LinearLayout.LayoutParams boostTitleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        boostTitleLp.weight = 1;
        boostHead.addView(boostTitle, boostTitleLp);

        TextView boostBtn = text(tr("一键放行"), 10.5f, color(R.color.accent_text), true);
        boostBtn.setGravity(Gravity.CENTER);
        boostBtn.setPadding(dp(10), 0, dp(10), 0);
        boostBtn.setBackground(shape(color(R.color.accent), 12));
        boostBtn.setOnClickListener(v -> applyBoost());
        boostHead.addView(boostBtn, lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)));

        boost.addView(boostHead);

        TextView boostDesc = text(tr("加入 Doze 白名单、待机桶设为 active、允许后台运行。")
                + tr("三项都由 shell 执行，不额外增加授权，也不产生常驻开销。"), 12,
                color(R.color.text_secondary), false);
        boostDesc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams boostDescLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        boostDescLp.topMargin = dp(8);
        boost.addView(boostDesc, boostDescLp);

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
        scrollView = scroll;

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        setContentView(scroll);
    }

    /** 首次打开时的提醒：缺一项权限就会「点了没反应」。 */
    private void showFirstRunDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(tr("先开启全部权限"))
                .setMessage(tr("AnyNight 靠 Shizuku、无障碍和后台权限工作，缺一项就会「点了没反应」。")
                        + tr("下面几步按顺序做完，再回首页点「立即测试」确认。"))
                .setPositiveButton(tr("从第 1 步开始"), null)
                .show();
    }

    /** 从首页点「Shizuku 服务」进来时，闪一下第 1 步，省得用户自己找。 */
    private void flashFirstStep() {
        final View card = firstStepCard;
        if (card == null) return;
        card.postDelayed(() -> {
            if (scrollView != null) {
                scrollView.smoothScrollTo(0, Math.max(0, card.getTop() - dp(10)));
            }
            // 白卡在浅色背景上做透明度闪烁几乎看不出来，改成「描边加粗并变深」的脉冲
            final android.graphics.drawable.GradientDrawable g =
                    card.getBackground() instanceof android.graphics.drawable.GradientDrawable
                            ? (android.graphics.drawable.GradientDrawable) card.getBackground()
                            : null;
            if (g == null) return;
            final int c0 = color(R.color.card_stroke);
            final int c1 = color(R.color.accent);
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(340);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            a.setRepeatCount(5);
            a.addUpdateListener(an -> {
                float t = (float) an.getAnimatedValue();
                g.setStroke(Math.max(1, dp(1 + 3.5f * t)), blend(c0, c1, t));
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    g.setStroke(Math.max(1, dp(1)), c0);
                }
            });
            a.start();
        }, 420);
    }

    /** 两个颜色按比例混合。 */
    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private View addItem(LinearLayout parent, String number, String title, String desc,
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

        TextView t = text(title, 14, color(R.color.text_primary), true);
        LinearLayout.LayoutParams tLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.weight = 1;
        tLp.leftMargin = dp(10);
        head.addView(t, tLp);

        // 操作按钮统一放在卡片右上角，和标题同一行
        for (int i = 0; i < actions.length; i++) {
            boolean primary = i == 0;
            TextView btn = text(actions[i], 10.5f,
                    primary ? color(R.color.accent_text) : color(R.color.text_secondary), true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(10), 0, dp(10), 0);
            btn.setBackground(primary ? shape(color(R.color.accent), 12)
                    : shapeStroked(color(R.color.card), 12, color(R.color.card_stroke)));
            btn.setOnClickListener(listeners[i]);
            LinearLayout.LayoutParams blp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27));
            blp.leftMargin = dp(6);
            head.addView(btn, blp);
        }

        card.addView(head);

        TextView d = text(desc, 12, color(R.color.text_secondary), false);
        d.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams dLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(8);
        card.addView(d, dLp);

        LinearLayout.LayoutParams cardLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        parent.addView(card, cardLp);
        return card;
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
    private void openAccessibilitySettings() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(i)) toast(tr("无法打开无障碍设置，请手动到 设置 → 无障碍"));
    }

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
