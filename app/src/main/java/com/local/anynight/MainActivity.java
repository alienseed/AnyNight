package com.local.anynight;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * 单屏界面。
 *
 * 从上到下：当前系统状态（含总开关）→ 三项运行状态（每行可点，就是原来的「步骤」）
 * → 测试 → 设置 → 底部入口。全部用代码构建，深浅色跟随系统。
 */
public class MainActivity extends Activity {

    private static final int H_PADDING = 20;
    private static final int REQ_RECENTS_LOCK = 3001;

    /** 从开场叙事过来。首页据此把内容从米白里淡出来，而不是硬切。 */
    static final String EXTRA_FROM_INTRO = "from_intro";

    private LinearLayout root;

    private TextView pill;
    private final List<TextView> statusDots = new ArrayList<>();
    private final List<TextView> statusValues = new ArrayList<>();

    private TextView testButton;
    private TextView targetValue;

    private TextView nowModeValue;
    private TextView nowModeDesc;

    private ToggleView switchAuto;
    private ToggleView switchFgs;
    private ToggleView switchHint;
    private ToggleView switchRecents;
    private boolean bindingSwitches;

    private final Shizuku.OnBinderReceivedListener onBinderReceived = this::refresh;
    private final Shizuku.OnBinderDeadListener onBinderDead = this::refresh;
    private final Shizuku.OnRequestPermissionResultListener onPermissionResult =
            (code, result) -> refresh();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 第一次打开：先放开场叙事，播完它自己会再拉起本页
        if (!Cfg.introSeen(this)) {
            Cfg.setIntroSeen(this);
            Logx.init(this);
            Logx.i(tr("首次打开，播放开场"));
            startActivity(new Intent(this, IntroActivity.class));
            overridePendingTransition(0, 0);
            finish();
            return;
        }

        Logx.init(this);
        Logx.i(tr("首页打开 v") + BuildConfig.VERSION_NAME);
        buildUi();
        applySystemBars();
        applyRecentsHidden();
        settleFromIntro();

        TaskStackWatcher.setStateListener(this::refresh);
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived);
        Shizuku.addBinderDeadListener(onBinderDead);
        Shizuku.addRequestPermissionResultListener(onPermissionResult);

        // 首次安装后的第一次打开：直接进教程，并弹一次「必须开启全部权限」
        // 开场刚淡出到米白时，先让首页停一瞬——否则「名字化开、界面就在那儿」
        // 这个落点还没看清，整页就被教程顶走了
        if (Cfg.firstRun(this)) {
            Cfg.setFirstRunDone(this);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                Intent g = new Intent(this, GuideActivity.class);
                g.putExtra(GuideActivity.EXTRA_FIRST_RUN, true);
                g.putExtra(GuideActivity.EXTRA_HIGHLIGHT, 1);
                startActivity(g);
            }, 700);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 「长按标题重看开场」回来时复用这一个实例，所以淡入要在两处都接上
        setIntent(intent);
        settleFromIntro();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBars();
        // 前台服务必须在应用处于前台时拉起才不会被系统拒绝（Android 12+ 的后台启动限制），
        // 所以每次回到界面都补一次；它同时也是进程不被冻结的关键。
        if (KeepAliveService.isEnabled(this) && !KeepAliveService.isRunning()) {
            KeepAliveService.start(this);
        }
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RECENTS_LOCK && resultCode == RESULT_OK) {
            Cfg.setRecentsLockDone(this, true);
            setRecentsHidden(true);
            toast(tr("已上锁，任务切换页卡片已隐藏"));
            refresh();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统深浅色切换时重建界面，保证配色与图标同步
        buildUi();
        applySystemBars();
        refresh();
    }

    @Override
    protected void onDestroy() {
        TaskStackWatcher.setStateListener(null);
        Shizuku.removeBinderReceivedListener(onBinderReceived);
        Shizuku.removeBinderDeadListener(onBinderDead);
        Shizuku.removeRequestPermissionResultListener(onPermissionResult);
        super.onDestroy();
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

    private void ripple(View v, GradientDrawable content) {
        RippleDrawable rd = new RippleDrawable(
                ColorStateList.valueOf(color(R.color.ripple)), content, null);
        v.setBackground(rd);
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

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(10), dp(16), dp(10));
        c.setBackground(shapeStroked(color(R.color.card), 20, color(R.color.card_stroke)));
        return c;
    }

    // ---------------- 界面 ----------------

    /**
     * 从开场叙事过来时，首页不是「切」上来的，而是从开场最后那一帧里被揭开。
     *
     * 这里刻意用的是「遮盖」而不是「让开场透出下面的首页」：
     * 开场是独立的一页，首页在它之后才创建，顺序上不可能提前画好等着被透出；
     * 而真让开场窗口变透明，慢机器上会先露一下桌面或黑底。
     *
     * 遮盖色取的是开场末帧色（`IntroView.endBg`）：白天米白、夜间近黑，与主题一致。
     * 所以两种模式下都是「从同色雾里揭开」，既不会露窗口底，也不会出现异色交叉的脏灰。
     */
    private void settleFromIntro() {
        if (!getIntent().getBooleanExtra(EXTRA_FROM_INTRO, false)) return;

        final int lastFrame = IntroView.endBg(this);
        final android.view.ViewGroup decor =
                (android.view.ViewGroup) getWindow().getDecorView();
        if (decor.findViewWithTag(VEIL_TAG) != null) return;   // 已经在揭了

        // 状态栏那一条也归窗口底管，先换成末帧色，否则会先露一条异色边
        getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(lastFrame));

        View veil = new View(this);
        veil.setTag(VEIL_TAG);
        veil.setBackgroundColor(lastFrame);
        decor.addView(veil, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        veil.animate()
                .alpha(0f)
                .setDuration(620)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    decor.removeView(veil);
                    // 揭开完了，窗口底交还给主题：深色模式该黑还是黑
                    getWindow().setBackgroundDrawable(
                            new android.graphics.drawable.ColorDrawable(color(R.color.bg)));
                })
                .start();
    }

    private static final String VEIL_TAG = "intro_veil";

    private void buildUi() {
        statusDots.clear();
        statusValues.clear();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.bg));
        root.setPadding(dp(H_PADDING), dp(8), dp(H_PADDING), dp(8));

        // ---- 标题 ----
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("AnyNight", 26, color(R.color.text_primary), true);
        title.setLetterSpacing(-0.02f);
        // 长按标题：重看开场。不占版面，也不用清数据——想再感受一次的时候顺手
        title.setOnLongClickListener(v -> {
            startActivity(new Intent(this, IntroActivity.class));
            overridePendingTransition(0, 0);
            return true;
        });
        headerRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pill = text(tr("待配置"), 11.5f, color(R.color.warn), true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        headerRow.addView(pill, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headerCol = new LinearLayout(this);
        headerCol.setOrientation(LinearLayout.VERTICAL);
        headerCol.addView(headerRow, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView slogan = text(getString(R.string.app_slogan), 11.5f,
                color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams sloganLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sloganLp.topMargin = dp(3);
        headerCol.addView(slogan, sloganLp);
        root.addView(headerCol);

        // ---- 当前系统状态 + 总开关 ----
        LinearLayout overview = card();
        LinearLayout overviewRow = new LinearLayout(this);
        overviewRow.setOrientation(LinearLayout.HORIZONTAL);
        overviewRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout overviewCol = new LinearLayout(this);
        overviewCol.setOrientation(LinearLayout.VERTICAL);
        overviewCol.addView(text(tr("当前系统"), 10.5f, color(R.color.text_tertiary), false));
        nowModeValue = text(tr("浅色模式"), 18, color(R.color.text_primary), true);
        LinearLayout.LayoutParams nowLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nowLp.topMargin = dp(3);
        overviewCol.addView(nowModeValue, nowLp);
        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1;
        overviewRow.addView(overviewCol, colLp);

        LinearLayout switchCol = new LinearLayout(this);
        switchCol.setOrientation(LinearLayout.VERTICAL);
        switchCol.setGravity(Gravity.END);

        TextView switchLabel = text(tr("功能总开关"), 10, color(R.color.text_tertiary), false);
        switchCol.addView(switchLabel);

        switchAuto = new ToggleView(this);
        switchAuto.setChecked(Cfg.autoEnabled(this), false);
        switchAuto.setOnCheckedChange(checked -> {
            if (bindingSwitches) return;
            Cfg.setAutoEnabled(this, checked);
            if (!checked) WatchAccessibilityService.restoreIfApplied();
            refresh();
        });
        LinearLayout.LayoutParams switchLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        switchLp.topMargin = dp(4);
        switchCol.addView(switchAuto, switchLp);
        overviewRow.addView(switchCol, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        overview.addView(overviewRow);

        nowModeDesc = text("", 12, color(R.color.text_secondary), false);
        nowModeDesc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(9);
        overview.addView(nowModeDesc, descLp);

        // 兜底入口：任何原因导致系统外观卡在「被改过」的状态，都能一键回到进入前的模式。
        TextView restore = text(tr("系统外观卡住？一键还原"), 10.5f, color(R.color.text_tertiary), false);
        restore.setGravity(Gravity.END);
        restore.setPadding(0, dp(8), 0, 0);
        restore.setOnClickListener(v -> {
            boolean done = WatchAccessibilityService.forceRestore(this);
            toast(done ? tr("已还原为本应用改动前的模式") : tr("当前外观没有被本应用改过"));
            refresh();
        });
        overview.addView(restore, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams overviewLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        overviewLp.topMargin = dp(10);
        root.addView(overview, overviewLp);

        // ---- 运行状态（每行可以直接点进去处理）----
        root.addView(sectionLabel(tr("运行状态"), 10, 5));

        LinearLayout statusCard = card();
        statusCard.setPadding(dp(16), dp(4), dp(16), dp(4));

        String[] labels = {tr("Shizuku 服务"), tr("切换权限"), tr("无障碍服务")};
        View.OnClickListener[] actions = {
                v -> {
                    if (!NightModeController.isBinderAlive()) {
                        toast(tr("先按第 1 步启用 Shizuku"));
                        openGuideStep1();
                    } else {
                        toast(tr("Shizuku 服务正在运行"));
                    }
                },
                v -> {
                    if (!NightModeController.isBinderAlive()) {
                        toast(tr("先按第 1 步启用 Shizuku"));
                        openGuideStep1();
                        return;
                    }
                    NightModeController.requestPermission();
                },
                v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        toast(tr("在「已下载的应用」里找到「AnyNight」并打开"));
                    } catch (Throwable t) {
                        toast(tr("无法打开设置：") + t.getMessage());
                    }
                },
        };
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            row.setOnClickListener(actions[i]);

            TextView dot = text("●", 8.5f, color(R.color.text_tertiary), false);
            row.addView(dot);
            statusDots.add(dot);

            TextView label = text(labels[i], 13.5f, color(R.color.text_secondary), false);
            LinearLayout.LayoutParams labelLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.weight = 1;
            labelLp.leftMargin = dp(10);
            row.addView(label, labelLp);

            TextView value = text("—", 13.5f, color(R.color.text_primary), true);
            row.addView(value);
            statusValues.add(value);

            TextView chevron = text("›", 17, color(R.color.text_tertiary), false);
            LinearLayout.LayoutParams chevLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            chevLp.leftMargin = dp(6);
            row.addView(chevron, chevLp);

            statusCard.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        root.addView(statusCard);

        // ---- 主按钮 ----
        testButton = text(tr("立即测试 · 切换 5 秒"), 14.5f, color(R.color.accent_text), true);
        testButton.setGravity(Gravity.CENTER);
        testButton.setLetterSpacing(0.01f);
        ripple(testButton, shape(color(R.color.accent), 22));
        testButton.setOnClickListener(v -> runTest());
        LinearLayout.LayoutParams testLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        testLp.topMargin = dp(10);
        root.addView(testButton, testLp);

        TextView testTip = text(tr("会切一次系统外观，5 秒后自动还原；屏幕闪一下属正常。"),
                10.5f, color(R.color.text_tertiary), false);
        testTip.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams testTipLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        testTipLp.topMargin = dp(7);
        root.addView(testTip, testTipLp);

        // ---- 设置 ----
        root.addView(sectionLabel(tr("设置"), 10, 5));

        LinearLayout keep = card();
        keep.setPadding(dp(16), dp(2), dp(16), dp(2));

        targetValue = addPickerRow(keep, tr("指定应用"), tr("深色化 / 浅色化，改动即时生效"),
                v -> startActivity(new Intent(this, AppPickerActivity.class)));

        switchFgs = addToggle(keep, tr("常驻通知栏"), tr("低优先级通知，不响不亮"),
                KeepAliveService.isEnabled(this), checked -> {
                    if (bindingSwitches) return;
                    KeepAliveService.setEnabled(this, checked);
                    if (checked) {
                        requestNotificationPermissionIfNeeded();
                        KeepAliveService.start(this);
                    } else {
                        KeepAliveService.stop(this);
                    }
                    refresh();
                });

        switchHint = addToggle(keep, tr("切换提示"),
                OverlayHint.canOverlay(this)
                        ? tr("在对方应用上显示一条提示，说明是本应用触发的")
                        : tr("需要「显示在其他应用上层」权限后生效"),
                Cfg.hintEnabled(this), checked -> {
                    if (bindingSwitches) return;
                    if (checked && !OverlayHint.canOverlay(this)) {
                        toast(tr("请打开「显示在其他应用上层」"));
                        OverlayHint.requestOverlayPermission(this);
                    }
                    Cfg.setHintEnabled(this, checked);
                });

        switchRecents = addToggle(keep, tr("任务切换页隐藏"),
                tr("隐藏后卡片不再出现，所以必须先上锁"),
                isRecentsHidden(), checked -> {
                    if (bindingSwitches) return;
                    if (checked && !Cfg.recentsLockDone(this)) {
                        // 顺序不能反：先隐藏就再也找不到卡片上锁了。
                        // 先把开关退回未选中，进入上锁教程，完成后再回来打开。
                        bindingSwitches = true;
                        switchRecents.setChecked(false, true);
                        bindingSwitches = false;
                        startActivityForResult(
                                new Intent(this, LockGuideActivity.class), REQ_RECENTS_LOCK);
                        return;
                    }
                    setRecentsHidden(checked);
                });

        addLink(keep, getString(R.string.guide_title),
                DeviceProfile.isMiuiOrHyper() ? tr("Shizuku · 上锁 · 自启动 · 省电 · 权限")
                        : tr("Shizuku · 电池优化 · 后台限制 · 权限"),
                v -> startActivity(new Intent(this, GuideActivity.class)));

        addLink(keep, getString(R.string.check_title), tr("只读检测，不会改变屏幕颜色"),
                v -> startActivity(new Intent(this, CheckActivity.class)));

        root.addView(keep, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 底部 ----
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tipRow = new LinearLayout(this);
        tipRow.setOrientation(LinearLayout.HORIZONTAL);
        tipRow.setGravity(Gravity.CENTER);

        // 语言切换：中文界面显示 ENGLISH，英文界面显示「中文」
        TextView lang = text(I18n.isEn(this) ? "中文" : "ENGLISH", 12,
                color(R.color.text_secondary), true);
        lang.setGravity(Gravity.CENTER);
        lang.setPadding(dp(14), 0, dp(14), 0);
        lang.setBackground(shapeStroked(color(R.color.card), 17, color(R.color.card_stroke)));
        lang.setOnClickListener(v -> {
            I18n.setForced(this, I18n.isEn(this) ? I18n.ZH : I18n.EN);
            recreate();
        });
        tipRow.addView(lang, lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        View gap = new View(this);
        tipRow.addView(gap, lp(dp(9), dp(1)));

        TextView tip = text(tr("请我喝瓶大水"), 12, color(R.color.text_secondary), true);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(14), 0, dp(14), 0);
        tip.setBackground(shapeStroked(color(R.color.card), 17, color(R.color.card_stroke)));
        tip.setOnClickListener(v -> startActivity(new Intent(this, TipActivity.class)));
        tipRow.addView(tip, lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        LinearLayout.LayoutParams tipRowLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tipRowLp.topMargin = dp(10);
        tipRowLp.bottomMargin = dp(8);
        root.addView(tipRow, tipRowLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.bg));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(H_PADDING), dp(8) + bars.top,
                    dp(H_PADDING), dp(8) + bars.bottom);
            return insets;
        });

        setContentView(scroll);
    }

    private TextView sectionLabel(String s, float topMarginDp, float bottomMarginDp) {
        TextView t = text(s, 10.5f, color(R.color.text_tertiary), true);
        t.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topMarginDp);
        p.bottomMargin = dp(bottomMarginDp);
        t.setLayoutParams(p);
        return t;
    }

    private ToggleView addToggle(LinearLayout parent, String title, String desc, boolean checked,
                                 ToggleView.OnCheckedChange listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14, color(R.color.text_primary), false));

        TextView d = text(desc, 11, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams dLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(2);
        col.addView(d, dLp);

        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1;
        row.addView(col, colLp);

        ToggleView s = new ToggleView(this);
        s.setChecked(checked, false);
        s.setOnCheckedChange(listener);
        row.addView(s, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private void addLink(LinearLayout parent, String title, String desc,
                         View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14, color(R.color.text_primary), false));

        TextView d = text(desc, 11, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams dLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(2);
        col.addView(d, dLp);

        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1;
        row.addView(col, colLp);

        row.addView(text("›", 18, color(R.color.text_tertiary), false));
        row.setOnClickListener(listener);
        parent.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 与 addLink 类似，但右侧显示当前值：已选 N 个。 */
    private TextView addPickerRow(LinearLayout parent, String title, String desc,
                                  View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14, color(R.color.text_primary), false));

        TextView d = text(desc, 11, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams dLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(2);
        col.addView(d, dLp);

        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1;
        row.addView(col, colLp);

        TextView value = text("—", 12, color(R.color.text_secondary), true);
        row.addView(value);

        TextView chevron = text("›", 18, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams chevLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chevLp.leftMargin = dp(4);
        row.addView(chevron, chevLp);

        row.setOnClickListener(listener);
        parent.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
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
                // 浅色背景配深色图标，深色背景配浅色图标
                c.setSystemBarsAppearance(isNight() ? 0 : mask, mask);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 状态刷新 ----------------

    private boolean isA11yEnabled() {
        // 首选系统 API：Settings.Secure 里的值在部分 ROM 上对普通应用可能读不到
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager)
                            getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<android.accessibilityservice.AccessibilityServiceInfo> list =
                        am.getEnabledAccessibilityServiceList(
                                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                if (list != null) {
                    for (android.accessibilityservice.AccessibilityServiceInfo info : list) {
                        android.content.pm.ServiceInfo si = info.getResolveInfo() == null
                                ? null : info.getResolveInfo().serviceInfo;
                        if (si != null
                                && getPackageName().equals(si.packageName)
                                && si.name != null
                                && si.name.endsWith("WatchAccessibilityService")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabled)) return false;
            String shortName = getPackageName() + "/.WatchAccessibilityService";
            String full = getPackageName() + "/"
                    + WatchAccessibilityService.class.getName();
            return enabled.contains(shortName) || enabled.contains(full);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isShizukuInstalled() {
        try {
            getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isSystemDark() {
        try {
            UiModeManager um = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            return um != null && um.getNightMode() == UiModeManager.MODE_NIGHT_YES;
        } catch (Throwable t) {
            return isNight();
        }
    }

    // ---------------- 保活相关 ----------------

    private static final String PREF_HIDE_RECENTS = "hide_recents";

    private SharedPreferences cfg() {
        return getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    private boolean isRecentsHidden() {
        // 默认不隐藏：隐藏前必须先上锁，所以装完默认让卡片留在任务切换页。
        return cfg().getBoolean(PREF_HIDE_RECENTS, false);
    }

    private void setRecentsHidden(boolean hidden) {
        cfg().edit().putBoolean(PREF_HIDE_RECENTS, hidden).apply();
        applyRecentsHidden();
    }

    /**
     * 清单里的 excludeFromRecents 只对新建任务生效，已经存在的任务要单独打标记，
     * 所以进入界面时再对所有 AppTask 设一次，切换开关时同样即时生效。
     */
    private void applyRecentsHidden() {
        boolean hidden = isRecentsHidden();
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                task.setExcludeFromRecents(hidden);
            }
        } catch (Throwable ignored) {
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        } catch (Throwable ignored) {
        }
    }

    private void refresh() {
        if (root == null) return;

        boolean installed = isShizukuInstalled();
        boolean alive = NightModeController.isBinderAlive();
        boolean granted = NightModeController.hasPermission();
        boolean a11y = isA11yEnabled();
        boolean auto = Cfg.autoEnabled(this);

        boolean watching = granted && TaskStackWatcher.isRegistered();
        boolean degrading = granted && TaskStackWatcher.isPolling();
        // 刚授权时监听可能还没注册上，主动催一次，避免一直显示「轮询降级」
        if (granted && !TaskStackWatcher.isRegistered()) TaskStackWatcher.kickRegister();
        boolean ready = auto && granted && a11y && (watching || degrading)
                && TargetApps.count(this) > 0;
        boolean[] ok = {installed && alive, granted, a11y};
        String[] values = {
                !installed ? tr("未安装") : (alive ? tr("运行中") : tr("未运行")),
                granted ? tr("已授权") : tr("未授权"),
                a11y ? (watching ? tr("已开启") : degrading ? tr("开启 · 轮询降级") : tr("已开启"))
                        : tr("未开启")
        };

        for (int i = 0; i < ok.length && i < statusDots.size(); i++) {
            statusDots.get(i).setTextColor(ok[i]
                    ? color(R.color.success) : color(R.color.warn));
            statusValues.get(i).setText(values[i]);
            statusValues.get(i).setTextColor(ok[i]
                    ? color(R.color.text_primary) : color(R.color.warn));
        }

        // 开关状态（重装/重启后以实际为准）
        bindingSwitches = true;
        if (switchAuto != null) switchAuto.setChecked(auto, false);
        if (switchFgs != null) switchFgs.setChecked(KeepAliveService.isEnabled(this), false);
        if (switchHint != null) switchHint.setChecked(Cfg.hintEnabled(this), false);
        if (switchRecents != null) switchRecents.setChecked(isRecentsHidden(), false);
        bindingSwitches = false;
        updateHintDesc();

        // 当前系统模式 + 一句人话说明
        boolean dark = isSystemDark();
        if (nowModeValue != null) {
            nowModeValue.setText(dark ? tr("深色模式") : tr("浅色模式"));
        }
        int count = TargetApps.count(this);
        int darkCount = TargetApps.count(this, TargetApps.DARK);
        int lightCount = TargetApps.count(this, TargetApps.LIGHT);
        if (nowModeDesc != null) {
            String s;
            if (!auto) {
                s = tr("总开关已关闭，不会改动系统开关。打开后：进入指定应用自动切换，离开恢复。");
            } else if (count == 0) {
                s = tr("还没有选择应用。到下面「指定应用」里挑几个，它们进入时会自动变")
                        + (dark ? tr("浅色") : tr("深色")) + tr("。");
            } else if (dark) {
                s = tr("目前是深色模式：已选的 ") + count + tr(" 个应用里，")
                        + lightCount + tr(" 个进入时自动变浅色")
                        + (darkCount > 0 ? tr("（另 ") + darkCount + tr(" 个保持深色）") : "")
                        + tr("，离开后恢复。");
            } else {
                s = tr("目前是浅色模式：已选的 ") + count + tr(" 个应用里，")
                        + darkCount + tr(" 个进入时自动变深色")
                        + (lightCount > 0 ? tr("（另 ") + lightCount + tr(" 个保持浅色）") : "")
                        + tr("，离开后恢复。");
            }
            nowModeDesc.setText(s);
        }

        if (targetValue != null) {
            String value = count == 0
                    ? tr("未选择")
                    : tr("深色 ") + darkCount + tr(" · 浅色 ") + lightCount;
            targetValue.setText(value);
            targetValue.setTextColor(count == 0
                    ? color(R.color.warn) : color(R.color.text_secondary));
        }

        // 顶部状态胶囊
        pill.setText(ready ? tr("已生效") : tr("待配置"));
        pill.setTextColor(ready ? color(R.color.success) : color(R.color.warn));
        pill.setBackground(shape(ready ? color(R.color.success_bg) : color(R.color.warn_bg), 11));

        testButton.setEnabled(granted);
        testButton.setAlpha(granted ? 1f : 0.4f);
    }

    private boolean testing;

    private void runTest() {
        if (!NightModeController.hasPermission()) {
            toast(tr("先完成第 1 步：Shizuku 授权"));
            return;
        }
        if (testing) {
            toast(tr("测试正在进行，等它自己还原就好"));
            return;
        }
        final boolean targetDark = !isSystemDark();
        // 会真的切系统外观，所以先说清楚会发生什么、持续多久、怎么退出。
        new android.app.AlertDialog.Builder(this)
                .setTitle(tr("开始测试？"))
                .setMessage(tr("接下来会把系统外观切成") + (targetDark ? tr("深色") : tr("浅色"))
                        + tr("，保持 5 秒后自动还原。\n\n")
                        + tr("期间整个屏幕会闪一下、桌面图标会重排，这是系统界面跟着重建，")
                        + tr("不是出错；想中止直接按返回键离开本应用，本应用会在 5 秒后照常还原。"))
                .setNegativeButton(tr("取消"), null)
                .setPositiveButton(tr("开始"), (d, w) -> doTest(targetDark))
                .show();
    }

    private void doTest(boolean targetDark) {
        testing = true;
        toast(targetDark ? tr("正在切深色，5 秒后还原…") : tr("正在切浅色，5 秒后还原…"));
        new Thread(() -> {
            boolean ok = NightModeController.setNight(targetDark);
            runOnUiThread(() -> {
                toast(ok ? tr("已切换，5 秒后自动还原") : tr("切换失败"));
                if (ok) OverlayHint.showSwitch(this, targetDark, tr("测试，5 秒后还原"));
            });
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            NightModeController.setNight(!targetDark);
            runOnUiThread(() -> {
                testing = false;
                toast(tr("已还原"));
            });
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 「切换提示」下面那句说明要跟着权限状态走，授权后回到界面能立刻看到变化。 */
    private void updateHintDesc() {
        if (switchHint == null) return;
        try {
            View row = (View) switchHint.getParent();
            if (!(row instanceof LinearLayout)) return;
            View col = ((LinearLayout) row).getChildAt(0);
            if (!(col instanceof LinearLayout)) return;
            LinearLayout c = (LinearLayout) col;
            if (c.getChildCount() < 2) return;
            TextView d = (TextView) c.getChildAt(1);
            d.setText(OverlayHint.canOverlay(this)
                    ? tr("在对方应用上显示一条提示，说明是本应用触发的")
                    : tr("需要「显示在其他应用上层」权限后生效"));
        } catch (Throwable ignored) {
        }
    }

    private void openShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (i != null) startActivity(i);
        else toast(tr("未安装 Shizuku"));
    }

    /**
     * 跳教程并闪一下第 1 步。
     *
     * Shizuku 自己的引导对新用户不算友好（无线调试、配对码），直接把人丢进 Shizuku
     * 多半还是不知道点哪。教程里第 1 步结尾就有「打开 Shizuku」，从那里进去最顺。
     */
    private void openGuideStep1() {
        Intent i = new Intent(this, GuideActivity.class);
        i.putExtra(GuideActivity.EXTRA_HIGHLIGHT, 1);
        startActivity(i);
    }
}
