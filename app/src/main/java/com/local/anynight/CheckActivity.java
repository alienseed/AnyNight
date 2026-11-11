package com.local.anynight;

import android.app.Activity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 兼容性信息（只读）。
 *
 * 这里只「读」，绝不写：读 ROM 版本、Shizuku 状态、系统当前深浅色、夜间模式是否被锁定、
 * 系统自带不有「按应用深色」、前台识别走的是哪种方式。
 *
 * 为什么不再做写入实测：写入会真的切换系统深浅色，全屏界面（SystemUI、桌面）会跟着
 * 重启一遍。早期版本在自检页切了一次之后再读回、再还原，页面被系统重建又跑一遍自检，
 * 就成了停不下来的黑白闪烁。想验证能不能切，回首页点「立即测试」——
 * 那里只切一次、5 秒自动还原，而且会明确告诉你正在发生什么。
 *
 * 断电式的异常也有兜底：本应用把「改动前的模式」写进本地，进程被杀/重启后会对账还原；
 * 首页还有「一键还原」入口。
 */
public class CheckActivity extends Activity {

    private static final int H_PADDING = 20;

    private final List<TextView> values = new ArrayList<>();
    private TextView conclusion;
    private TextView updateBtn;
    private boolean checking;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        applySystemBars();
        runCheck();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        applySystemBars();
        runCheck();
    }

    // ---------------- 工具 ----------------

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
        values.clear();

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

        TextView title = text(tr("兼容性信息"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        TextView intro = text(tr("本页只读取信息，不会切换屏幕颜色，也不会闪屏。")
                + tr("想确认「本机到底能不能切」，回首页点「立即测试 · 切 5 秒」：")
                + tr("它会切一次、5 秒后自动还原，期间有提示。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(10);
        introLp.bottomMargin = dp(12);
        page.addView(intro, introLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        card.setBackground(shapeStroked(color(R.color.card), 20, color(R.color.card_stroke)));

        String[] labels = {
                tr("系统"), tr("Android 版本"), "Shizuku", tr("系统当前深浅色"),
                tr("夜间模式锁定"), tr("系统自带「按应用深色」"), tr("前台识别方式")
        };
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));

            TextView label = text(labels[i], 13, color(R.color.text_secondary), false);
            LinearLayout.LayoutParams labelLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.weight = 1;
            row.addView(label, labelLp);

            TextView value = text(tr("读取中…"), 12.5f, color(R.color.text_primary), true);
            value.setGravity(Gravity.END);
            value.setMaxWidth(dp(200));
            row.addView(value);
            values.add(value);

            card.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        page.addView(card);

        conclusion = text("", 12, color(R.color.text_secondary), false);
        conclusion.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams conLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        conLp.topMargin = dp(10);
        page.addView(conclusion, conLp);

        // 按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(14);

        TextView again = text(tr("刷新信息"), 13, color(R.color.text_primary), true);
        again.setGravity(Gravity.CENTER);
        again.setBackground(shapeStroked(color(R.color.card), 16, color(R.color.card_stroke)));
        again.setOnClickListener(v -> runCheck());
        LinearLayout.LayoutParams againLp = lp(0, dp(40));
        againLp.weight = 1;
        btnRow.addView(again, againLp);

        View gap = new View(this);
        btnRow.addView(gap, lp(dp(10), dp(1)));

        TextView export = text(tr("导出日志"), 13, color(R.color.accent_text), true);
        export.setGravity(Gravity.CENTER);
        export.setBackground(shape(color(R.color.accent), 16));
        export.setOnClickListener(v -> exportReport());
        LinearLayout.LayoutParams exportLp = lp(0, dp(40));
        exportLp.weight = 1;
        btnRow.addView(export, exportLp);

        page.addView(btnRow, btnRowLp);

        // 检查更新：放在这里，和「导出报告」同属「维护」动作
        updateBtn = text(tr("检查更新") + " · v" + Update.localVersion(), 13,
                color(R.color.text_primary), true);
        updateBtn.setGravity(Gravity.CENTER);
        updateBtn.setBackground(shapeStroked(color(R.color.card), 16, color(R.color.card_stroke)));
        updateBtn.setOnClickListener(v -> checkUpdate());
        LinearLayout.LayoutParams updateLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        updateLp.topMargin = dp(10);
        page.addView(updateBtn, updateLp);

        TextView note = text(tr("报告写到系统「下载」根目录（AnyNight 开头），里面是设备信息、运行日志和这次读取到的状态。")
                + tr("用着不对的时候把它发给作者，比描述现象有用得多。"),
                11, color(R.color.text_tertiary), false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(10);
        page.addView(note, noteLp);

        // 联系作者：放在这里，出问题的人本来就会先打开这个页面
        TextView contact = text(tr("联系作者"), 13, color(R.color.text_secondary), true);
        contact.setGravity(Gravity.CENTER);
        contact.setBackground(shapeStroked(color(R.color.card), 16, color(R.color.card_stroke)));
        contact.setOnClickListener(v -> startActivity(new Intent(this, ContactActivity.class)));
        LinearLayout.LayoutParams contactLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        contactLp.topMargin = dp(12);
        page.addView(contact, contactLp);

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

    /** @param good true 绿 / false 橙 / null 中性 */
    private void setRow(int i, String value, Boolean good) {
        if (i < 0 || i >= values.size()) return;
        TextView v = values.get(i);
        v.setText(value);
        v.setTextColor(good == null ? color(R.color.text_primary)
                : good ? color(R.color.success) : color(R.color.warn));
    }

    // ---------------- 只读检测 ----------------

    private void runCheck() {
        if (values.size() < 7) return;

        setRow(0, DeviceProfile.romShort(), true);
        setRow(1, android.os.Build.VERSION.RELEASE + " / SDK "
                + android.os.Build.VERSION.SDK_INT, true);

        boolean installed = true;
        try {
            getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
        } catch (Throwable t) {
            installed = false;
        }
        boolean alive = NightModeController.isBinderAlive();
        boolean granted = NightModeController.hasPermission();
        setRow(2, !installed ? tr("未安装（必需）") : !alive ? tr("未运行") : granted ? tr("已授权") : tr("未授权"),
                installed && alive && granted);

        setRow(3, NightModeController.isNightNow(this) ? tr("深色") : tr("浅色"), null);

        boolean hasList = DeviceProfile.hasPerAppDarkList(this);
        setRow(5, hasList ? tr("有") : tr("未检测到"), hasList);

        boolean watching = TaskStackWatcher.isRegistered();
        boolean polling = TaskStackWatcher.isPolling();
        setRow(6, watching ? tr("事件驱动（零轮询）") : polling ? tr("轮询降级") : tr("未注册"),
                watching || polling);

        // 锁定状态要读 dumpsys，放到线程里；只读，不写。
        setRow(4, tr("读取中…"), null);
        final boolean finalGranted = granted;
        new Thread(() -> {
            Boolean locked = DeviceProfile.isNightModeLocked();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                setRow(4, locked == null ? tr("读不到") : locked
                                ? tr("是（只能经 Shizuku 切换）") : tr("否"),
                        locked == null ? null : Boolean.TRUE);
                if (conclusion != null) {
                    conclusion.setText(conclusionText(finalGranted, hasList,
                            locked != null && locked));
                }
            });
        }, "readonly-check").start();
    }

    private String conclusionText(boolean granted, boolean hasList, boolean locked) {
        if (!granted) {
            return tr("结论：还没拿到 Shizuku 授权。本应用切换系统开关靠的就是它，")
                    + tr("授权前什么都不会发生。授权后回首页点「立即测试」验证。")
                    + (hasList ? tr("另外系统自带「按应用深色」，个别应用不跟着变时优先用它。") : "");
        }
        if (!TaskStackWatcher.isRegistered() && !TaskStackWatcher.isPolling()) {
            return tr("结论：Shizuku 就绪，但前台识别没挂上（无障碍服务多半没开）。")
                    + tr("回首页看「无障碍服务」这一行，开启后再回来刷新。");
        }
        return tr("结论：环境齐了。可用性请用首页「立即测试 · 切 5 秒」验证：")
                + tr("看到系统变色就说明本机完全可用，5 秒后会自动还原。")
                + (locked ? tr("本机夜间模式被系统锁定，第三方应用只能经 Shizuku 的 shell 权限切换——")
                + tr("本应用正是这么做的，所以「锁定」不等于不能用。") : "")
                + (hasList ? tr("个别应用不跟着变时，可改用系统自带的「按应用深色」。") : "");
    }

    private void exportReport() {
        new Thread(() -> {
            try {
                String path = Logx.exportZip(this);
                runOnUiThread(() -> toast(tr("已导出到 ") + path));
            } catch (Throwable t) {
                Logx.e(tr("导出日志失败"), t);
                runOnUiThread(() -> toast(tr("导出失败：") + t.getMessage()));
            }
        }).start();
    }

    // ---------------- 检查更新 ----------------

    private void checkUpdate() {
        if (checking || updateBtn == null) return;
        checking = true;
        updateBtn.setText(tr("正在检查…"));
        Update.check(this, (info, err) -> {
            checking = false;
            if (updateBtn != null) {
                updateBtn.setText(tr("检查更新") + " · v" + Update.localVersion());
            }
            if (info == null) {
                toast(tr("检查失败，网络不通或代理不可用"));
                Logx.w("检查更新失败: " + err);
                return;
            }
            if (!Update.isNewer(info.version, Update.localVersion())) {
                toast(tr("已是最新版本 v") + Update.localVersion());
                return;
            }
            askDownload(info);
        });
    }

    private void askDownload(Update.Info info) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(tr("发现新版本 v") + info.version)
                .setMessage(tr("当前 v") + Update.localVersion()
                        + tr("。下载后可直接覆盖安装，设置和已选应用都会保留。"))
                .setNegativeButton(tr("以后再说"), null)
                .setPositiveButton(tr("下载"), (d, w) -> startDownload(info))
                .show();
    }

    private void startDownload(Update.Info info) {
        android.widget.ProgressBar bar = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int pad = dp(22);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(tr("正在下载 v") + info.version)
                .setView(box)
                .setCancelable(false)
                .create();
        try {
            dialog.show();
        } catch (Throwable ignored) {
        }

        Update.download(this, info, new Update.DownloadCallback() {
            @Override
            public void progress(int percent) {
                bar.setProgress(percent);
            }

            @Override
            public void done(java.io.File apk, String error) {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
                if (apk == null) {
                    toast(tr("下载失败：") + (error == null ? tr("网络错误") : error));
                    return;
                }
                install(apk);
            }
        });
    }

    private void install(java.io.File apk) {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            toast(tr("请先允许本应用安装应用"));
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {
            }
            return;
        }
        try {
            android.net.Uri uri = ApkProvider.uriFor(this, apk);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast(tr("无法启动安装：") + t.getMessage());
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
