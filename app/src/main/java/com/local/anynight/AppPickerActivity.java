package com.local.anynight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 二级页：挑选要「深色化 / 浅色化」的应用。
 *
 * 系统只有一个全局深浅色开关（HyperOS 没有 per-app 开关），所以这里选的是
 * 「进到哪些应用时把系统切到相反的颜色」，离开就还原。方向由顶部两个分段决定：
 * 浅色模式下深色化、深色模式下浅色化，两者是对称的。
 */
public class AppPickerActivity extends Activity {

    private static final int H_PADDING = 20;

    private static class Entry {
        String pkg;
        String label;
        Drawable icon;
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final Map<String, Integer> selected = new LinkedHashMap<>();

    private Adapter adapter;
    private TextView countView;
    private TextView hintView;
    private TextView emptyView;
    private TextView segDark;
    private TextView segLight;
    private String query = "";

    /** 当前要加入的方向：TargetApps.DARK / LIGHT。 */
    private int addMode = TargetApps.DARK;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        applySystemBars();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        selected.clear();
        selected.putAll(TargetApps.get(this));
        updateCount();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        applySystemBars();
        if (adapter != null) adapter.notifyDataSetChanged();
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
        page.setPadding(dp(H_PADDING), dp(10), dp(H_PADDING), dp(12));
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(H_PADDING), dp(10) + bars.top, dp(H_PADDING), dp(10) + bars.bottom);
            return insets;
        });

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 26, color(R.color.text_primary), false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(4), 0, dp(10), dp(2));
        back.setOnClickListener(v -> finish());
        bar.addView(back, lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(tr("选择应用"), 20, color(R.color.text_primary), true);
        LinearLayout.LayoutParams titleLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.weight = 1;
        bar.addView(title, titleLp);
        page.addView(bar);

        TextView intro = text(tr("点一下应用就加入，再点一下移除。")
                + tr("系统浅色时给这些应用上深色、系统深色时给它们上浅色，进出自动切换，离开立刻还原。"),
                12.5f, color(R.color.text_secondary), false);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.topMargin = dp(8);
        introLp.bottomMargin = dp(10);
        page.addView(intro, introLp);

        // 应用内还得选「跟随系统」——这是最常见的「选了却没变」的原因（例如小红书）。
        TextView followTip = text(tr("应用内还要开启「跟随系统」") + " ›", 11.5f,
                color(R.color.text_secondary), true);
        followTip.setGravity(Gravity.END);
        followTip.setPadding(0, 0, 0, dp(10));
        followTip.setOnClickListener(v -> showFollowSystemDialog());
        page.addView(followTip, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 方向分段
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(shapeStroked(color(R.color.card), 20, color(R.color.card_stroke)));

        segDark = text(tr("深色化"), 13, color(R.color.accent_text), true);
        segLight = text(tr("浅色化"), 13, color(R.color.text_secondary), true);
        for (TextView v : new TextView[]{segDark, segLight}) {
            v.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = lp(0, dp(34));
            p.weight = 1;
            seg.addView(v, p);
        }
        segDark.setOnClickListener(v -> setAddMode(TargetApps.DARK));
        segLight.setOnClickListener(v -> setAddMode(TargetApps.LIGHT));
        page.addView(seg, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        paintSegments();

        // 搜索框
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(tr("搜索应用"));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        search.setTextColor(color(R.color.text_primary));
        search.setHintTextColor(color(R.color.text_tertiary));
        search.setBackground(shapeStroked(color(R.color.card), 16, color(R.color.card_stroke)));
        search.setPadding(dp(16), dp(11), dp(16), dp(11));
        search.setIncludeFontPadding(false);
        search.setBackgroundTintList(null);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams searchLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        searchLp.topMargin = dp(10);
        page.addView(search, searchLp);

        // 列表
        FrameLayout listWrap = new FrameLayout(this);
        LinearLayout.LayoutParams listWrapLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        listWrapLp.weight = 1;
        listWrapLp.topMargin = dp(10);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onPick(shown.get(position)));
        listWrap.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = text(tr("正在读取应用列表…"), 13, color(R.color.text_tertiary), false);
        emptyView.setGravity(Gravity.CENTER);
        listWrap.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        page.addView(listWrap, listWrapLp);

        // 底部：已选数量 + 完成
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(8), 0, 0);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        countView = text(tr("已选 0 个"), 13.5f, color(R.color.text_primary), true);
        col.addView(countView);

        hintView = text(tr("改动即时生效"), 11, color(R.color.text_tertiary), false);
        LinearLayout.LayoutParams hintLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(3);
        col.addView(hintView, hintLp);

        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1;
        bottom.addView(col, colLp);

        TextView done = text(tr("完成"), 13.5f, color(R.color.accent_text), true);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(24), 0, dp(24), 0);
        done.setBackground(shape(color(R.color.accent), 18));
        done.setOnClickListener(v -> finish());
        bottom.addView(done, lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        page.addView(bottom, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(page);
        updateCount();
    }

    private void setAddMode(int mode) {
        addMode = mode;
        paintSegments();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void paintSegments() {
        if (segDark == null || segLight == null) return;
        TextView on = addMode == TargetApps.DARK ? segDark : segLight;
        TextView off = addMode == TargetApps.DARK ? segLight : segDark;
        on.setBackground(shape(color(R.color.accent), 16));
        on.setTextColor(color(R.color.accent_text));
        off.setBackgroundColor(0x00000000);
        off.setTextColor(color(R.color.text_secondary));
    }

    /** 点一下加入，再点移除，方向不同的点一下改成当前方向。 */
    private void onPick(Entry e) {
        Integer cur = selected.get(e.pkg);
        if (cur == null) {
            TargetApps.setMode(this, e.pkg, addMode);
            selected.put(e.pkg, addMode);
            checkCompatAsync(e.pkg, e.label);
        } else if (cur == addMode) {
            TargetApps.setMode(this, e.pkg, -1);
            selected.remove(e.pkg);
        } else {
            TargetApps.setMode(this, e.pkg, addMode);
            selected.put(e.pkg, addMode);
            checkCompatAsync(e.pkg, e.label);
        }
        updateCount();
        adapter.notifyDataSetChanged();
    }

    /**
     * 加入后顺手判断一下这个应用有没有深色资源。
     * 没有的话提示去系统「深色模式设置 → 应用单独设置」打开它的开关。
     */
    private void checkCompatAsync(String pkg, String label) {
        new Thread(() -> {
            final int r = Compat.probe(this, pkg);
            Logx.i(tr("兼容性检测 ") + pkg + " = " + (r == Compat.SUPPORTED ? tr("有深色资源")
                    : r == Compat.NOT_SUPPORTED ? tr("未发现深色资源") : tr("未知")));
            if (r != Compat.NOT_SUPPORTED) return;
            runOnUiThread(() -> showCompatDialog(pkg, label));
        }).start();
    }

    private void showCompatDialog(String pkg, String label) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(tr("「") + label + tr("」可能没有适配深色"))
                .setMessage(tr("没有检测到它的深色资源，进入后可能不会变色。\n\n")
                        + tr("可以到系统「深色模式设置 → 应用单独设置」里给它打开强制深色；")
                        + tr("如果那个列表里也没有它，说明系统也没有办法，建议移出清单。"))
                .setNegativeButton(tr("知道了"), null)
                .setPositiveButton(tr("去系统设置"), (d, w) -> Compat.openSystemDarkSettings(this))
                .show();
    }

    /** 说明「为什么还要在应用内开跟随系统」，顺带给出常见应用路径。 */
    private void showFollowSystemDialog() {
        new AlertDialog.Builder(this)
                .setTitle(tr("为什么要在应用内开启「跟随系统」"))
                .setMessage(tr("AnyNight 改的是系统的深浅色开关。应用要跟着变，得让它在应用内选择「跟随系统」，")
                        + tr("否则它会一直用自己的浅色或深色主题。\n\n")
                        + tr("以小红书为例：我 → 设置 → 通用 → 深色模式 → 跟随系统。\n\n")
                        + tr("如果某个应用只有「浅色 / 深色」两个选项、没有「跟随系统」，")
                        + tr("那它不会跟着系统变；可以到系统「深色模式设置 → 应用单独设置」里强制开启。"))
                .setPositiveButton(tr("知道了"), null)
                .show();
    }

    private void updateCount() {
        if (countView == null) return;
        int n = selected.size();
        int dark = 0;
        for (int v : selected.values()) if (v == TargetApps.DARK) dark++;
        int light = n - dark;
        countView.setText(n == 0 ? tr("已选 0 个")
                : tr("已选 ") + n + tr(" 个（深 ") + dark + tr(" · 浅 ") + light + tr("）"));
        countView.setTextColor(n == 0 ? color(R.color.warn) : color(R.color.text_primary));
        hintView.setText(n == 0 ? tr("未选择时不会切换")
                : tr("改动即时生效 · 记得在应用内选「跟随系统」"));
    }

    private void applyFilter() {
        shown.clear();
        for (Entry e : all) {
            if (query.isEmpty()
                    || e.label.toLowerCase(Locale.ROOT).contains(query)
                    || e.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(e);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (emptyView != null) {
            if (all.isEmpty()) {
                emptyView.setText(tr("正在读取应用列表…"));
                emptyView.setVisibility(View.VISIBLE);
            } else if (shown.isEmpty()) {
                emptyView.setText(tr("没有匹配的应用"));
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    /** 图标和名称都在后台读，避免首帧卡住。 */
    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos;
            try {
                infos = pm.queryIntentActivities(i, 0);
            } catch (Throwable t) {
                infos = new ArrayList<>();
            }
            List<Entry> loaded = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : infos) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (getPackageName().equals(pkg) || !seen.add(pkg)) continue;
                Entry e = new Entry();
                e.pkg = pkg;
                try {
                    e.label = String.valueOf(ri.activityInfo.loadLabel(pm));
                    e.icon = ri.activityInfo.loadIcon(pm);
                } catch (Throwable t) {
                    e.label = pkg;
                }
                loaded.add(e);
            }
            Collator collator = Collator.getInstance(Locale.CHINA);
            Collections.sort(loaded, (a, b) -> collator.compare(a.label, b.label));

            runOnUiThread(() -> {
                all.clear();
                all.addAll(loaded);
                applyFilter();
            });
        }).start();
    }

    /** 关闭返回时让前台服务下次读取新清单。 */
    @Override
    protected void onPause() {
        super.onPause();
        TargetApps.invalidate();
    }

    // ---------------- 列表 ----------------

    private class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout item;
            if (convertView instanceof LinearLayout && convertView.getId() == 200) {
                item = (LinearLayout) convertView;
            } else {
                // 整行透明 + 一条内缩的分隔线：早期版本给每一行都套了圆角卡片，
                // 连续排列时圆弧一个接一个，视觉上就成了波浪，所以改成平铺列表。
                item = new LinearLayout(AppPickerActivity.this);
                item.setId(200);
                item.setOrientation(LinearLayout.VERTICAL);

                LinearLayout row = new LinearLayout(AppPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackgroundColor(0x00000000);

                ImageView icon = new ImageView(AppPickerActivity.this);
                icon.setId(101);
                row.addView(icon, lp(dp(38), dp(38)));

                LinearLayout col = new LinearLayout(AppPickerActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);

                TextView label = text("", 13.5f, color(R.color.text_primary), false);
                label.setId(102);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setSingleLine(true);
                col.addView(label);

                TextView pkg = text("", 10.5f, color(R.color.text_tertiary), false);
                pkg.setId(103);
                pkg.setEllipsize(TextUtils.TruncateAt.END);
                pkg.setSingleLine(true);
                LinearLayout.LayoutParams pkgLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                pkgLp.topMargin = dp(2);
                col.addView(pkg, pkgLp);

                LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
                colLp.weight = 1;
                colLp.leftMargin = dp(12);
                row.addView(col, colLp);

                TextView badge = text("", 10, color(R.color.text_secondary), true);
                badge.setId(105);
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(7), dp(3), dp(7), dp(3));
                LinearLayout.LayoutParams badgeLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.rightMargin = dp(8);
                row.addView(badge, badgeLp);

                TextView check = text("", 12, color(R.color.accent_text), true);
                check.setId(104);
                check.setGravity(Gravity.CENTER);
                row.addView(check, lp(dp(22), dp(22)));

                item.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                View divider = new View(AppPickerActivity.this);
                divider.setBackgroundColor(color(R.color.card_stroke));
                LinearLayout.LayoutParams divLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                divLp.leftMargin = dp(62);
                item.addView(divider, divLp);
            }

            Entry e = shown.get(position);
            ImageView icon = item.findViewById(101);
            TextView label = item.findViewById(102);
            TextView pkg = item.findViewById(103);
            TextView check = item.findViewById(104);
            TextView badge = item.findViewById(105);

            icon.setImageDrawable(e.icon);
            label.setText(e.label);
            pkg.setText(e.pkg);

            Integer mode = selected.get(e.pkg);
            boolean on = mode != null;
            check.setText(on ? "✓" : "");
            check.setBackground(on
                    ? shape(color(R.color.accent), 11)
                    : shapeStroked(0x00000000, 11, color(R.color.check_stroke)));

            if (on) {
                boolean dark = mode == TargetApps.DARK;
                badge.setText(dark ? tr("深色") : tr("浅色"));
                badge.setTextColor(color(R.color.text_secondary));
                badge.setBackground(shape(color(R.color.chip_bg), 8));
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            return item;
        }
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
