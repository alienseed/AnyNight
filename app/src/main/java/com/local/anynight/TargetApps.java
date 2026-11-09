package com.local.anynight;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目标应用清单：包名 + 想要的效果（深色 / 浅色）。
 *
 * 系统只有一个全局深浅色开关（HyperOS 没有 per-app 开关），所以「指定应用」的实现是：
 * 当前台属于清单时把系统切到相反的颜色，离开就还原成进来前的状态。
 * 因此清单里可以放任意包名，前提是那个应用自身响应系统深浅色。
 *
 * 存储格式（StringSet）："pkg|d" 深色化、"pkg|l" 浅色化。
 * 旧版本只存裸包名，读到时按深色化处理。
 */
final class TargetApps {

    static final int DARK = 0;
    static final int LIGHT = 1;

    private static final String PREFS = "cfg";
    private static final String KEY = "targets";
    private static final String KEY_INIT = "targets_init";

    /** 首次安装默认只对小米相册生效。 */
    static final String DEFAULT_PACKAGE = "com.miui.gallery";

    private static Map<String, Integer> cache;

    private TargetApps() {
    }

    static synchronized Map<String, Integer> get(Context c) {
        if (cache != null) return cache;
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(KEY, null);
        boolean initialized = sp.getBoolean(KEY_INIT, false);
        Map<String, Integer> next = new LinkedHashMap<>();
        if (!initialized && (saved == null || saved.isEmpty())) {
            next.put(DEFAULT_PACKAGE, DARK);
        } else if (saved != null) {
            for (String raw : saved) {
                if (raw == null || raw.isEmpty()) continue;
                int sep = raw.indexOf('|');
                if (sep < 0) {
                    next.put(raw, DARK);
                } else {
                    String pkg = raw.substring(0, sep);
                    boolean light = "l".equals(raw.substring(sep + 1));
                    next.put(pkg, light ? LIGHT : DARK);
                }
            }
        }
        cache = next;
        return cache;
    }

    private static void persist(Context c, Map<String, Integer> map) {
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            out.add(e.getKey() + (e.getValue() == LIGHT ? "|l" : "|d"));
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY, out)
                .putBoolean(KEY_INIT, true)
                .apply();
        cache = map;
    }

    static synchronized void setMode(Context c, String pkg, int mode) {
        if (pkg == null) return;
        Map<String, Integer> map = new LinkedHashMap<>(get(c));
        if (mode < 0) {
            map.remove(pkg);
        } else {
            map.put(pkg, mode == LIGHT ? LIGHT : DARK);
        }
        persist(c, map);
    }

    static synchronized void clear(Context c) {
        persist(c, new LinkedHashMap<>());
    }

    static synchronized void invalidate() {
        cache = null;
    }

    /** @return DARK / LIGHT，不在清单里返回 -1 */
    static int modeOf(Context c, String pkg) {
        if (pkg == null) return -1;
        Integer m = get(c).get(pkg);
        return m == null ? -1 : m;
    }

    static boolean isTarget(Context c, String pkg) {
        return modeOf(c, pkg) >= 0;
    }

    static int count(Context c) {
        return get(c).size();
    }

    static int count(Context c, int mode) {
        int n = 0;
        for (int v : get(c).values()) {
            if (v == mode) n++;
        }
        return n;
    }

    static List<String> packages(Context c, int mode) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : get(c).entrySet()) {
            if (e.getValue() == mode) out.add(e.getKey());
        }
        return out;
    }

    /** 写进日志用：一眼看清清单内容。 */
    static String describe(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : get(c).entrySet()) {
            sb.append(e.getKey())
                    .append(" -> ")
                    .append(e.getValue() == LIGHT ? "浅色化" : "深色化")
                    .append('\n');
        }
        if (sb.length() == 0) sb.append("(空)\n");
        return sb.toString();
    }
}
