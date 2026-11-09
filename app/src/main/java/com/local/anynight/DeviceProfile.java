package com.local.anynight;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * 机型 / ROM 识别 + 能力实测。
 *
 * 这套方案只依赖 Android 公开的东西（UiModeManager 的全局深浅色开关 + 任务栈前台信息），
 * 所以不是「只对小米有效」，而是「对任何按 Android 规范实现深浅色的系统都可能有效」。
 * 但每个定制系统会在三个地方动手脚，只能实测：
 *
 * 1) 深浅色开关能不能被第三方（shell 身份）改：有的系统把它锁死；
 * 2) 系统有没有自带「按应用指定深色」：有的话交给系统比自己切更稳；
 * 3) 任务栈事件能不能挂上：挂不上就得降级成轮询。
 *
 * 因此这里不做「品牌白名单」，只做能力探测——探测结果直接决定界面上的结论，
 * 也一并写进导出的日志，用户把日志发回来就能远程判断「这台机器支不支持」。
 */
final class DeviceProfile {

    private static String cachedRom;

    private DeviceProfile() {
    }

    // ---------------- 读系统属性 ----------------

    /** 读 getprop。取不到返回空串，不抛异常。 */
    static String prop(String key) {
        Process p = null;
        try {
            p = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return line == null ? "" : line.trim();
            }
        } catch (Throwable t) {
            return "";
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static boolean isMiui() {
        String brand = (android.os.Build.BRAND + android.os.Build.MANUFACTURER)
                .toLowerCase(Locale.ROOT);
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
                || !prop("ro.miui.ui.version.name").isEmpty();
    }

    static boolean isMiuiOrHyper() {
        return isMiui() || !prop("ro.mi.os.version.name").isEmpty();
    }

    /** 华为/荣耀：EMUI 或 HarmonyOS（4.x 及以前仍是 Android 内核）。 */
    static boolean isHuawei() {
        String brand = (android.os.Build.BRAND + android.os.Build.MANUFACTURER)
                .toLowerCase(Locale.ROOT);
        return brand.contains("huawei") || brand.contains("honor")
                || !prop("ro.build.version.emui").isEmpty();
    }

    static boolean isColorOs() {
        String brand = (android.os.Build.BRAND + android.os.Build.MANUFACTURER)
                .toLowerCase(Locale.ROOT);
        return brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme");
    }

    static boolean isOriginOs() {
        String brand = (android.os.Build.BRAND + android.os.Build.MANUFACTURER)
                .toLowerCase(Locale.ROOT);
        return brand.contains("vivo") || brand.contains("iqoo");
    }

    /** 简短版，给界面用：HyperOS 4.0.7.0 / MIUI V816。完整版留给日志。 */
    static String romShort() {
        String hyper = prop("ro.mi.os.version.incremental");
        if (!hyper.isEmpty()) {
            String v = hyper.startsWith("OS") ? hyper.substring(2) : hyper;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(\\d+(?:\\.\\d+){1,3})").matcher(v);
            return "HyperOS " + (m.find() ? m.group(1) : v);
        }
        if (isMiui()) {
            String v = prop("ro.miui.ui.version.name");
            return "MIUI " + (v.isEmpty() ? "?" : v);
        }
        if (isHuawei()) {
            String emui = prop("ro.build.version.emui");
            return emui.isEmpty() ? "EMUI / HarmonyOS" : emui;
        }
        if (isColorOs()) return "ColorOS";
        if (isOriginOs()) return "OriginOS";
        String brand = android.os.Build.BRAND;
        return brand == null || brand.isEmpty() ? "Android" : capitalize(brand);
    }

    /** 形如「HyperOS 4.0.7.0 / Android 17」。 */
    static String romLabel() {
        if (cachedRom != null) return cachedRom;
        StringBuilder sb = new StringBuilder();
        String hyper = prop("ro.mi.os.version.incremental");
        if (!hyper.isEmpty()) {
            sb.append("HyperOS ").append(hyper);
        } else if (isMiui()) {
            String v = prop("ro.miui.ui.version.name");
            sb.append("MIUI ").append(v.isEmpty() ? "?" : v);
        } else if (isHuawei()) {
            String emui = prop("ro.build.version.emui");
            sb.append(emui.isEmpty() ? "EMUI / HarmonyOS" : emui);
        } else if (isColorOs()) {
            sb.append("ColorOS/realme UI");
        } else if (isOriginOs()) {
            sb.append("OriginOS");
        } else {
            String brand = android.os.Build.BRAND;
            sb.append(brand == null || brand.isEmpty() ? "Android" : capitalize(brand));
        }
        sb.append(" / Android ").append(android.os.Build.VERSION.RELEASE);
        cachedRom = sb.toString();
        return cachedRom;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------- 能力探测 ----------------

    /**
     * 只读：系统是否把夜间模式锁住了（dumpsys uimode 里的 mNightModeLocked）。
     *
     * 锁定意味着第三方应用直接写 Settings 不会被采纳，只能由具备
     * MODIFY_DAY_NIGHT_MODE 的进程（shell / adb）调系统服务——本应用正好借
     * Shizuku 拿到这个身份，所以「锁定」并不等于不能用，只是必须授权。
     *
     * 这里只读 dumpsys，绝不写，所以不会引起任何界面闪烁。读不到返回 null。
     */
    static Boolean isNightModeLocked() {
        String out = NightModeController.shell("dumpsys uimode");
        if (out == null || out.isEmpty()) return null;
        if (out.contains("mNightModeLocked=true")) return Boolean.TRUE;
        if (out.contains("mNightModeLocked=false")) return Boolean.FALSE;
        return null;
    }

    /**
     * 系统是否自带「深色模式 → 按应用单独设置」。
     *
     * 注意 MIUI/HyperOS 的 com.android.settings/.Settings 是个万能分发页，
     * 任何 Settings$Xxx 都能解析成功，所以把落到 .Settings 的结果当作「没找到」。
     */
    static boolean hasPerAppDarkList(Context c) {
        String[][] targets = {
                {"com.android.settings", "com.android.settings.display.DarkModeActivity"},
                {"com.android.settings", "com.android.settings.display.NightVisionEyeCareAppListActivity"},
                {"com.android.settings", "com.android.settings.Settings$DarkModeSettingsActivity"},
                {"com.android.settings", "com.android.settings.Settings$DarkThemeSettingsActivity"},
                {"com.coloros.settings", "com.coloros.settings.feature.darkmode.DarkModeActivity"},
                {"com.android.settings", "com.samsung.android.settings.display.DarkModeSettings"},
        };
        for (String[] t : targets) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                ComponentName found = i.resolveActivity(c.getPackageManager());
                if (found == null) continue;
                String cls = found.getClassName();
                if (cls == null || cls.endsWith(".Settings")) continue;
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** 写进日志的一段说明，用户回传日志时作者能直接看出环境。 */
    static String describe(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("ROM: ").append(romLabel()).append('\n');
        sb.append("品牌/机型: ").append(android.os.Build.BRAND).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        sb.append("系统自带单应用深色: ")
                .append(hasPerAppDarkList(c) ? "有" : "未检测到").append('\n');
        Boolean locked = isNightModeLocked();
        sb.append("夜间模式锁定: ")
                .append(locked == null ? "读不到" : locked ? "是" : "否").append('\n');
        sb.append("系统当前深浅色: ")
                .append(NightModeController.isNightNow(c) ? "深色" : "浅色").append('\n');
        return sb.toString();
    }
}
