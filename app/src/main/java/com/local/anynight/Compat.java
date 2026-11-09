package com.local.anynight;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 尽力而为的「这个应用有没有做深色适配」检测。
 *
 * 做法：把目标应用的资源分别按「浅色配置」和「深色配置」解析一遍它的主题，
 * 比较 windowBackground / colorBackground / textColorPrimary / colorAccent 的取值。
 * 有 -night 资源（DayNight 主题、values-night 等）时两者必然不同；
 * 完全没做深色的应用两者完全一致。
 *
 * 这是启发式判断，不是系统结论：
 * - 个别应用用自定义属性画界面，主题属性不变但界面会变 → 可能误判为「未适配」；
 * - 因此结果只用于提示，不做硬性拦截。
 */
final class Compat {

    static final int UNKNOWN = 0;
    static final int SUPPORTED = 1;
    static final int NOT_SUPPORTED = 2;

    private static final Map<String, Integer> CACHE = new HashMap<>();

    private Compat() {
    }

    static synchronized int cached(String pkg) {
        Integer v = CACHE.get(pkg);
        return v == null ? UNKNOWN : v;
    }

    static int probe(Context c, String pkg) {
        synchronized (CACHE) {
            Integer v = CACHE.get(pkg);
            if (v != null) return v;
        }
        int result;
        try {
            result = detect(c, pkg);
        } catch (Throwable t) {
            result = UNKNOWN;
        }
        synchronized (CACHE) {
            CACHE.put(pkg, result);
        }
        return result;
    }

    private static int detect(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkg, 0);
        } catch (Throwable t) {
            return UNKNOWN;
        }

        int themeRes = ai.theme;
        if (themeRes == 0) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage(pkg);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                if (list != null && !list.isEmpty() && list.get(0).activityInfo != null) {
                    themeRes = list.get(0).activityInfo.theme;
                }
            } catch (Throwable ignored) {
            }
        }

        Resources base;
        try {
            base = pm.getResourcesForApplication(ai);
        } catch (Throwable t) {
            return UNKNOWN;
        }
        Configuration day = new Configuration(base.getConfiguration());
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_NO;
        Configuration night = new Configuration(day);
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_YES;

        DisplayMetrics dm = base.getDisplayMetrics();
        int dayHash = sample(new Resources(base.getAssets(), dm, day), themeRes);
        int nightHash = sample(new Resources(base.getAssets(), dm, night), themeRes);

        if (dayHash == 0 || nightHash == 0) return UNKNOWN;
        return dayHash == nightHash ? NOT_SUPPORTED : SUPPORTED;
    }

    /** 解析四个最常用的主题属性，做成一个指纹。取不到时返回 0。 */
    private static int sample(Resources r, int themeRes) {
        Resources.Theme theme = r.newTheme();
        if (themeRes != 0) {
            try {
                theme.applyStyle(themeRes, true);
            } catch (Throwable ignored) {
            }
        }
        int[] attrs = {
                android.R.attr.windowBackground,
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.colorAccent,
        };
        TypedValue tv = new TypedValue();
        int hash = 17;
        boolean any = false;
        for (int a : attrs) {
            try {
                if (theme.resolveAttribute(a, tv, true)) {
                    any = true;
                    hash = hash * 31 + tv.type * 131 + tv.data;
                } else {
                    hash = hash * 31;
                }
            } catch (Throwable ignored) {
                hash = hash * 31;
            }
        }
        return any ? hash : 0;
    }

    /** 系统「深色模式设置 → 应用单独设置」页面（HyperOS 就是这一页）。 */
    static boolean openSystemDarkSettings(Context c) {
        String[][] targets = {
                {"com.android.settings", "com.android.settings.display.DarkModeActivity"},
                {"com.android.settings", "com.android.settings.display.NightVisionEyeCareAppListActivity"},
        };
        for (String[] t : targets) {
            try {
                Intent i = new Intent();
                i.setClassName(t[0], t[1]);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Throwable ignored) {
            }
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
