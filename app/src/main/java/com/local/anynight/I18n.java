package com.local.anynight;

import android.content.Context;
import android.content.res.Configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 极简双语文案层。
 *
 * 界面全部由代码构建，文案就以中文原文为 key：命中字典就返回英文，没命中就原样返回。
 * 这样做的好处是「漏译」只会退回中文，不会崩、不会显示 key，也不需要在每个调用点
 * 维护一份 R.string 索引；代价是文案改动时字典要跟着改，所以字典集中在下面一张表里。
 *
 * 语言优先级：用户手动选的（cfg.lang）> 系统语言 > 中文。
 */
final class I18n {

    static final String PREF = "lang";
    static final String ZH = "zh";
    static final String EN = "en";

    private static final Map<String, String> DICT = new HashMap<>();

    private I18n() {
    }

    // ---------------- 语言状态 ----------------

    static String forced(Context c) {
        try {
            if (c == null) return "";
            String v = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString(PREF, "");
            return v == null ? "" : v;
        } catch (Throwable t) {
            // 构造期间 base context 还没挂上，这时按系统语言判断即可
            return "";
        }
    }

    static void setForced(Context c, String lang) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .edit().putString(PREF, lang == null ? "" : lang).apply();
    }

    static boolean isEn(Context c) {
        String f = forced(c);
        if (c == null) return false;
        if (EN.equals(f)) return true;
        if (ZH.equals(f)) return false;
        Locale l;
        try {
            l = c.getResources().getConfiguration().getLocales().get(0);
        } catch (Throwable t) {
            l = Locale.getDefault();
        }
        return l != null && "en".equals(l.getLanguage());
    }

    /** 手动切换语言后，让新起的界面按选定的语言解析资源。 */
    static Context wrap(Context base) {
        String f = forced(base);
        if (f.isEmpty()) return base;
        try {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.setLocale(new Locale(f));
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }

    /** 取当前语言的文案。 */
    static String t(Context c, String zh) {
        if (zh == null || zh.isEmpty()) return zh;
        if (!isEn(c)) return zh;
        String en = DICT.get(zh);
        return en == null ? zh : en;
    }

    // ---------------- 字典 ----------------

    private static void add(String zh, String en) {
        DICT.put(zh, en);
    }

    static {
        // ---- 首页 ----
        add("AnyNight", "AnyNight");
        add("待配置", "Setup");
        add("已生效", "Active");
        add("当前系统", "System");
        add("浅色模式", "Light");
        add("深色模式", "Dark");
        add("功能总开关", "Master switch");
        add("系统外观卡住？一键还原", "Appearance stuck? Reset");
        add("已还原为本应用改动前的模式", "Restored the appearance from before this app changed it");
        add("当前外观没有被本应用改过", "This app has not changed the appearance");
        add("运行状态", "STATUS");
        add("设置", "SETTINGS");
        add("Shizuku 服务", "Shizuku service");
        add("切换权限", "Toggle access");
        add("无障碍服务", "Accessibility");
        add("Shizuku 服务没在运行，先去 Shizuku 应用里启动",
                "Shizuku isn't running — start it in the Shizuku app first");
        add("Shizuku 服务正在运行", "Shizuku is running");
        add("在「已下载的应用」里找到「AnyNight」并打开",
                "Find \"AnyNight\" under \"Downloaded apps\" and turn it on");
        add("无法打开设置：", "Can't open settings: ");
        add("立即测试 · 切换 5 秒", "Test now · toggle 5s");
        add("会切一次系统外观，5 秒后自动还原；屏幕闪一下属正常。",
                "Toggles the appearance once, reverts after 5s. A brief flicker is normal.");
        add("指定应用", "Target apps");
        add("深色化 / 浅色化，改动即时生效", "Darken / lighten — applies immediately");
        add("常驻通知栏", "Ongoing notification");
        add("低优先级通知，不响不亮", "Low priority, silent, no lights");
        add("切换提示", "Switch hint");
        add("在对方应用上显示一条提示，说明是本应用触发的",
                "Shows a hint over the other app so you know it was us");
        add("需要「显示在其他应用上层」权限后生效",
                "Needs the \"Display over other apps\" permission");
        add("请打开「显示在其他应用上层」", "Allow \"Display over other apps\" first");
        add("任务切换页隐藏", "Hide from Recents");
        add("隐藏后卡片不再出现，所以必须先上锁", "The card disappears, so lock it first");
        add("使用教程", "Guide");
        add("兼容性信息", "Compatibility");
        add("只读检测，不会改变屏幕颜色", "Read-only; changes nothing");
        add("请我喝瓶大水", "Buy me a drink");
        add("联系作者", "Contact");
        add("已上锁，任务切换页卡片已隐藏", "Locked — card hidden from Recents");
        add("Shizuku · 上锁 · 自启动 · 省电 · 权限",
                "Shizuku · Lock · Autostart · Battery · Perms");
        add("Shizuku · 电池优化 · 后台限制 · 权限",
                "Shizuku · Battery · Background · Perms");
        add("总开关已关闭，不会改动系统开关。打开后：进入指定应用自动切换，离开恢复。",
                "The master switch is off, so the system setting is left alone. "
                        + "Turn it on to switch on entry and restore on exit.");
        add("还没有选择应用。到下面「指定应用」里挑几个，它们进入时会自动变",
                "No apps selected yet. Pick a few under \"Target apps\" below and they will turn ");
        add("目前是浅色模式：已选的 ", "System is light: of your ");
        add("目前是深色模式：已选的 ", "System is dark: of your ");
        add(" 个应用里，", " selected apps, ");
        add(" 个进入时自动变深色", " turn dark when opened");
        add(" 个进入时自动变浅色", " turn light when opened");
        add("（另 ", " (the other ");
        add(" 个保持深色）", " stay dark)");
        add(" 个保持浅色）", " stay light)");
        add("，离开后恢复。", ", and revert when you leave.");
        add("。", ".");
        add("未选择", "None");
        add("深色 ", "Dark ");
        add(" · 浅色 ", " · Light ");
        add("未安装", "Not installed");
        add("运行中", "Running");
        add("未运行", "Stopped");
        add("已授权", "Granted");
        add("未授权", "Not granted");
        add("已开启", "On");
        add("未开启", "Off");
        add("开启 · 轮询降级", "On · polling fallback");
        add("先完成第 1 步：Shizuku 授权", "Finish step 1 first: grant Shizuku access");
        add("测试正在进行，等它自己还原就好", "A test is already running — it reverts on its own");
        add("开始测试？", "Start the test?");
        add("取消", "Cancel");
        add("开始", "Start");
        add("接下来会把系统外观切成", "The system appearance will switch to ");
        add("，保持 5 秒后自动还原。\n\n", " for 5 seconds, then reverts.\n\n");
        add("期间整个屏幕会闪一下、桌面图标会重排，这是系统界面跟着重建，",
                "The whole screen will flicker and home icons will rearrange while the system UI rebuilds.");
        add("不是出错；想中止直接按返回键离开本应用，本应用会在 5 秒后照常还原。",
                " That is not an error. To abort, press Back to leave the app — "
                        + "it still reverts after 5 seconds.");
        add("正在切深色，5 秒后还原…", "Switching to dark, reverting in 5s…");
        add("正在切浅色，5 秒后还原…", "Switching to light, reverting in 5s…");
        add("已切换，5 秒后自动还原", "Switched — reverts in 5s");
        add("切换失败", "Switch failed");
        add("已还原", "Reverted");
        add("测试，5 秒后还原", "Test — reverts in 5s");
        add("未安装 Shizuku", "Shizuku is not installed");

        // ---- 选择应用 ----
        add("选择应用", "Choose apps");
        add("点一下应用就加入，再点一下移除。", "Tap an app to add it, tap again to remove. ");
        add("系统浅色时给这些应用上深色、系统深色时给它们上浅色，进出自动切换，离开立刻还原。",
                "When the system is light these apps go dark, and vice versa. "
                        + "Automatic on entry, restored on exit.");
        add("深色化", "Darken");
        add("浅色化", "Lighten");
        add("深色", "Dark");
        add("浅色", "Light");
        add("搜索应用", "Search apps");
        add("正在读取应用列表…", "Loading apps…");
        add("没有匹配的应用", "No matching apps");
        add("已选 0 个", "None selected");
        add("已选 ", "Selected ");
        add(" 个（深 ", " apps (dark ");
        add(" · 浅 ", " · light ");
        add("）", ")");
        add("未选择时不会切换", "Nothing switches until you pick an app");
        add("改动即时生效", "Applies immediately");
        add("改动即时生效 · 记得在应用内选「跟随系统」",
                "Applies now · enable \"Follow system\" in that app");
        add("应用内还要开启「跟随系统」", "Also enable \"Follow system\" in the app");
        add("① 应用内要先选「跟随系统」：小红书是「我 → 设置 → 通用 → 深色模式」，",
                "1) The app itself must be set to \"Follow system\" — in Xiaohongshu that is "
                        + "Me → Settings → General → Dark mode, ");
        add("多数应用在「设置 → 显示 / 外观」里。不选它，应用永远用自己的主题。",
                "and most apps put it under Settings → Display / Appearance. "
                        + "Without it the app always keeps its own theme.");
        add("② 系统「深色模式设置 → 应用单独设置」里有它，就打开强制变深。",
                "2) If it is listed under Settings → Dark mode → Per-app settings, force it on.");
        add("③ 两边都没有，说明这个应用没有深色界面，系统也变不了。",
                "3) If neither exists, the app simply has no dark UI and nothing can change that.");
        add("完成", "Done");
        add("兼容性检测 ", "Compat check ");
        add("有深色资源", "has dark resources");
        add("未发现深色资源", "no dark resources");
        add("未知", "unknown");
        add("「", "\"");
        add("」可能没有适配深色", "\" may not support dark mode");
        add("没有检测到它的深色资源，进入后可能不会变色。\n\n",
                "No dark resources were found, so it may not change colour.\n\n");
        add("可以到系统「深色模式设置 → 应用单独设置」里给它打开强制深色；",
                "You can force dark for it in Settings → Dark mode → Per-app settings;");
        add("如果那个列表里也没有它，说明系统也没有办法，建议移出清单。",
                "If it is missing there too, the system cannot help either — "
                        + "consider removing it from the list.");
        add("知道了", "Got it");
        add("去系统设置", "System settings");
        add("为什么要在应用内开启「跟随系统」", "Why enable \"Follow system\" inside the app");
        add("AnyNight 改的是系统的深浅色开关。应用要跟着变，得让它在应用内选择「跟随系统」，",
                "AnyNight flips the system dark-mode switch. For an app to follow it, that app "
                        + "must be set to \"Follow system\" internally, ");
        add("否则它会一直用自己的浅色或深色主题。\n\n",
                "otherwise it keeps using its own light or dark theme.\n\n");
        add("以小红书为例：我 → 设置 → 通用 → 深色模式 → 跟随系统。\n\n",
                "In Xiaohongshu, for example: Me → Settings → General → Dark mode → "
                        + "Follow system.\n\n");
        add("如果某个应用只有「浅色 / 深色」两个选项、没有「跟随系统」，",
                "If an app only offers \"Light / Dark\" and no \"Follow system\", ");
        add("那它不会跟着系统变；可以到系统「深色模式设置 → 应用单独设置」里强制开启。",
                "it will not follow the system; you can force it in "
                        + "Settings → Dark mode → Per-app settings.");
        add("通知权限决定常驻通知能不能显示；其它权限里放无障碍、悬浮窗等。",
                "Notification access controls the ongoing notification; other permissions cover "
                        + "accessibility and overlay.");
        add("已关闭", "Off");

        // ---- 兼容性信息 ----
        add("本页只读取信息，不会切换屏幕颜色，也不会闪屏。",
                "This page only reads information. It never switches the screen colour and never flickers.");
        add("想确认「本机到底能不能切」，回首页点「立即测试 · 切 5 秒」：",
                "To confirm this device can switch at all, tap \"Test now · 5s\" on the home screen:");
        add("它会切一次、5 秒后自动还原，期间有提示。",
                "It switches once, shows a hint, and reverts after 5 seconds.");
        add("系统", "System");
        add("Android 版本", "Android");
        add("系统当前深浅色", "Current system mode");
        add("夜间模式锁定", "Night mode locked");
        add("系统自带「按应用深色」", "System per-app dark mode");
        add("前台识别方式", "Foreground detection");
        add("读取中…", "Reading…");
        add("未安装（必需）", "Not installed (required)");
        add("有", "Yes");
        add("未检测到", "Not detected");
        add("事件驱动（零轮询）", "Event-driven (zero polling)");
        add("轮询降级", "Polling fallback");
        add("未注册", "Not registered");
        add("读不到", "Unreadable");
        add("是（只能经 Shizuku 切换）", "Yes (only via Shizuku)");
        add("否", "No");
        add("结论：还没拿到 Shizuku 授权。本应用切换系统开关靠的就是它，",
                "Verdict: Shizuku access has not been granted. It is the only way this app can flip "
                        + "the switch,");
        add("授权前什么都不会发生。授权后回首页点「立即测试」验证。",
                " so nothing happens until you grant it. Afterwards, tap \"Test now\" on the home screen.");
        add("另外系统自带「按应用深色」，个别应用不跟着变时优先用它。",
                " The system also has per-app dark mode; prefer it for apps that ignore the switch.");
        add("结论：Shizuku 就绪，但前台识别没挂上（无障碍服务多半没开）。",
                "Verdict: Shizuku is ready but foreground detection is not attached "
                        + "(accessibility is probably off).");
        add("回首页看「无障碍服务」这一行，开启后再回来刷新。",
                " Check the \"Accessibility\" row on the home screen, turn it on, then refresh.");
        add("结论：环境齐了。可用性请用首页「立即测试 · 切 5 秒」验证：",
                "Verdict: everything is in place. Verify with \"Test now · 5s\" on the home screen:");
        add("看到系统变色就说明本机完全可用，5 秒后会自动还原。",
                " if the screen changes colour, this device is fully supported; it reverts after "
                        + "5 seconds.");
        add("本机夜间模式被系统锁定，第三方应用只能经 Shizuku 的 shell 权限切换——",
                " Night mode is locked on this device; third-party apps can only switch it with "
                        + "Shizuku shell access —");
        add("本应用正是这么做的，所以「锁定」不等于不能用。",
                " which is exactly what AnyNight does, so \"locked\" does not mean unusable.");
        add("个别应用不跟着变时，可改用系统自带的「按应用深色」。",
                " If an app still ignores it, use the system's own per-app dark mode instead.");
        add("刷新信息", "Refresh");
        add("导出日志", "Export log");
        add("报告写到「下载/AnyNight」，里面是设备信息、运行日志和这次读取到的状态。",
                "The report goes to Download/AnyNight with device info, logs and what this page "
                        + "just read.");
        add("用着不对的时候把它发给作者，比描述现象有用得多。",
                " Send it to the author when something misbehaves — far more useful than a description.");
        add("已导出到 ", "Exported to ");
        add("导出失败：", "Export failed: ");
        add("导出日志失败", "Log export failed");

        // ---- 使用教程 ----
        add("按顺序做完下面几步。", "Work through these steps in order. ");
        add("第 1 步是所有功能的前提（没有 Shizuku，点了也不会有反应），",
                "Step 1 is required for everything (without Shizuku nothing responds),");
        add("第 2 步必须在开启「任务切换页隐藏」之前做。",
                " and step 2 must be done before turning on \"Hide from Recents\".");
        add("首页已经显示「已生效」的话，可以只补没做过的项。",
                " If the home screen already says \"Active\", just fill in whatever you skipped.");
        add("启用 Shizuku", "Set up Shizuku");
        add("本应用借 Shizuku 拿到 shell 权限去拨系统的深浅色开关。",
                "AnyNight borrows shell access through Shizuku to flip the system dark-mode switch.");
        add("没装、没启动、没授权，首页三行状态都会是红的。",
                " Not installed, not running or not granted — all three home-screen rows stay red.");
        add("查看步骤", "View steps");
        add("任务切换页上锁", "Lock the Recents card");
        add("打开任务切换页，长按「AnyNight」卡片，点锁形图标。",
                "Open Recents, long-press the AnyNight card, then tap the lock icon.");
        add("「任务切换页隐藏」会先把卡片藏起来，所以顺序不能反——",
                " \"Hide from Recents\" removes the card first, so the order matters —");
        add("先上锁，再隐藏。", "lock first, hide second.");
        add("打开任务切换页", "Open Recents");
        add("允许自启动", "Allow autostart");
        add("应用信息 → 自启动。被系统清理后可以自动恢复。",
                "App info → Autostart. Lets the app come back after being cleaned up.");
        add("去打开", "Open");
        add("省电策略设为无限制（必须）", "Battery: no restrictions (required)");
        add("应用信息 → 省电策略 → 无限制。这一项没设的话，系统会把后台进程冻结，",
                "App info → Battery saver → No restrictions. Without it the system freezes the "
                        + "background process,");
        add("进入应用时根本不会变色。本应用空闲时是 0 CPU，放开不会更耗电。",
                " and apps will never change colour. AnyNight uses 0 CPU when idle, so this costs "
                        + "no battery.");
        add("去设置", "Settings");
        add("权限：通知 / 其它", "Permissions: notification / other");
        add("通知权限决定常驻通知能不能显示（保活效果），",
                "Notification access decides whether the ongoing notification can show "
                        + "(which helps it survive),");
        add("其它权限里放无障碍、悬浮窗等。两个在系统里是两个不同的页面。",
                " while other permissions cover accessibility and overlay. They are two different "
                        + "pages on this system.");
        add("通知权限", "Notification");
        add("其它权限", "Other permissions");
        add("电池优化设为「不受限制」", "Battery optimization: unrestricted");
        add("系统设置 → 应用 → 本应用 → 电池 → 不受限制。",
                "Settings → Apps → AnyNight → Battery → Unrestricted.");
        add("这是 AOSP 原生就有的开关，绝大多数机器都有。",
                " This is a stock AOSP switch; nearly every device has it.");
        add("允许后台活动", "Allow background activity");
        add("部分系统在应用信息里还有「允许后台活动 / 自启动」开关，有的话一并打开。",
                "Some systems also expose \"Allow background activity / Autostart\" in app info — "
                        + "turn those on too.");
        add("用户触发导出日志", "User-triggered log export");
        add("去应用信息", "App info");
        add("用 Shizuku 一键放行", "Allow all via Shizuku");
        add("加入 Doze 白名单、待机桶设为 active、允许后台运行。",
                "Adds a Doze whitelist entry, sets the standby bucket to active, and allows "
                        + "background runs.");
        add("三项都由 shell 执行，不额外增加授权，也不产生常驻开销。",
                " All three run as shell commands — no extra permission, no background cost.");
        add("一键放行", "Allow all");
        add("正在放行…", "Allowing…");
        add("已全部放行（Doze / 待机桶 / 后台运行）",
                "All allowed (Doze / standby bucket / background)");
        add("已放行 ", "Allowed ");
        add("/3 项", "/3 done");
        add("开启无障碍时提示「受限制的设置」？", "Blocked by \"restricted settings\"?");
        add("应用信息 → 高级设置 → 允许受限制的设置。",
                "App info → Advanced → Allow restricted settings.");
        add("开启后再回到无障碍列表，就能打开本应用的服务。",
                " After enabling it, return to the accessibility list and turn on the AnyNight service.");
        add("应用进入后没变深？", "App did not go dark?");
        add("本应用改的是系统的深浅色开关，对方变不变色取决于它自己有没有做深色适配。",
                "AnyNight only flips the system switch; whether an app changes colour depends on "
                        + "its own dark-mode support.");
        add("到系统「深色模式设置 → 应用单独设置」里给它打开开关可以强制变深；",
                " You can force dark for it in Settings → Dark mode → Per-app settings;");
        add("那个列表里没有它，说明系统也没有办法。",
                " if it is missing from that list, the system cannot help either.");
        add("打开系统深色设置", "Open dark mode settings");
        add("没能打开系统设置，请手动到「设置 → 显示 → 深色模式」",
                "Could not open settings — go to Settings → Display → Dark mode manually");
        add("无障碍服务没在运行，先回上一页开启",
                "Accessibility is off — turn it on first");
        add("先回上一页完成 Shizuku 授权", "Go back and finish the Shizuku authorization first");

        // ---- 启用 Shizuku ----
        add("Shizuku 是开源工具，用一次 ADB 授权把 shell 权限借给普通应用。",
                "Shizuku is an open-source tool that lends shell access to normal apps after a "
                        + "one-time ADB authorization. ");
        add("AnyNight 只在进入 / 离开你指定的应用时用它拨一下系统的深浅色开关，",
                "AnyNight uses it only to flip the system dark-mode switch when you enter or leave "
                        + "the apps you picked, ");
        add("不读屏幕、不收集数据。", "and it never reads the screen or collects data.");
        add("安装包从哪来，很关键", "Where the APK comes from matters");
        add("请尽量避免使用通过「云恢复、一键换机、系统自带应用商店」",
                "Avoid Shizuku builds delivered by cloud backup, phone-to-phone migration or the "
                        + "vendor app store: ");
        add("等处提供的 Shizuku 安装包：版本可能偏旧或被替换，",
                "they may be outdated or repackaged, ");
        add("常见表现是能装能开、但配对一直失败。下面两个入口是官方仓库与其加速镜像。",
                "and typically install fine but fail to pair. The entries below are the official "
                        + "repo and two mirrors.");
        add("下载安装包", "Download");
        add("GitHub 官方 Releases", "GitHub Releases");
        add("官方 · 最新版", "Official · latest");
        add("加速镜像 1（ghfast.top）", "Mirror 1 (ghfast.top)");
        add("国内一般可直连", "Usually reachable in mainland China");
        add("加速镜像 2（gh-proxy.com）", "Mirror 2 (gh-proxy.com)");
        add("上面那个不通时用", "Use when the one above fails");
        add("复制全部链接", "Copy all links");
        add("链接已复制，可发到电脑或浏览器打开", "Links copied — open them in a browser");
        add("启用步骤（以小米 / 澎湃OS 为例）", "Steps (Xiaomi / HyperOS shown)");
        add("系统设置里搜「通知样式设置」", "Search Settings for \"Notification style\"");
        add("把通知样式设为「原生样式」", "Set the notification style to \"Native\"");
        add("系统设置里搜「全部参数与信息」", "Search Settings for \"All specs\"");
        add("快速点击 5 下「OS 版本」，开启开发者模式", "Tap \"OS version\" 5 times to enable Developer options");
        add("打开 Shizuku 软件，点击「配对」", "Open Shizuku and tap \"Pair\"");
        add("按指引操作完成后，返回 Shizuku 点击「启用」",
                "Follow the on-screen steps, then return to Shizuku and tap \"Start\"");
        add("启用成功后在 Shizuku 的「应用管理」里，给 AnyNight 打开开关",
                "Once started, open \"Authorized apps\" in Shizuku and enable AnyNight");
        add("提示：重启手机后 Shizuku 会失效，需要重新点一次「启用」",
                "Note: Shizuku stops after a reboot — tap \"Start\" again ");
        add("（不用重新配对，无线调试会记住）。",
                "(no need to re-pair; wireless debugging remembers it).");
        add("打开 Shizuku", "Open Shizuku");
        add("还没安装 Shizuku，先用上面的链接下载",
                "Shizuku is not installed — use the links above first");
        add("打不开 Shizuku：", "Could not open Shizuku: ");
        add("我已启用，返回", "Done, go back");
        add("回首页看「Shizuku 服务」和「切换权限」两行是否变绿",
                "Check whether the \"Shizuku service\" and \"Toggle access\" rows turn green");
        add("打开 ›", "Open ›");
        add("没有浏览器，链接已复制", "No browser — link copied");
        add("链接已复制", "Link copied");
        add("\n镜像1: ", "\nMirror 1: ");
        add("\n镜像2: ", "\nMirror 2: ");

        // ---- 上锁教程 ----
        add("先给任务卡片上锁", "Lock the Recents card first");
        add("「任务切换页隐藏」打开后，本应用不会出现在任务切换页里。",
                "Once \"Hide from Recents\" is on, AnyNight no longer shows up in Recents.");
        add("也就是说：先隐藏、后想上锁，就没有卡片可长了。",
                " In other words: hide first, and there is no card left to long-press.");
        add("所以这一步必须在打开隐藏开关之前做完。",
                " So this step must happen before you turn hiding on.");
        add("操作步骤", "Steps");
        add("打开任务切换页（屏幕底部上滑并停住，或点下面的按钮）",
                "Open Recents (swipe up and hold, or tap the button below)");
        add("找到「AnyNight」的卡片，长按它", "Find the AnyNight card and long-press it");
        add("点卡片上的锁形图标，让锁闭合", "Tap the lock icon on the card so it closes");
        add("回到本页，点最下方的「我已上锁，开启隐藏」",
                "Come back here and tap \"Locked — hide it\" at the bottom");
        add("无障碍服务没在运行，请手动上滑打开任务切换页",
                "Accessibility is off — swipe up to open Recents manually");
        add("为什么非要上锁", "Why lock it at all");
        add("上锁之后，系统清理后台时会保留这张卡片，本应用更容易一直活着；",
                "Once locked, the card survives background cleanup, so the app stays alive more "
                        + "reliably;");
        add("没上锁又已经隐藏，被清理后只能靠「允许自启动」慢慢恢复，效果会明显打折。",
                " if it is hidden without a lock, recovery after cleanup relies only on autostart "
                        + "and works far less well.");
        add("系统不对外暴露卡片是否已上锁，本应用没法替你检查，只能由你确认。",
                " The system does not expose whether a card is locked, so only you can confirm it.");
        add("我已上锁，开启隐藏", "Locked — hide it");
        add("先不隐藏", "Not now");
        add("确认已上锁？", "Is it locked?");
        add("锁闭合后，任务切换页里这张卡片右上角会显示锁形图标。",
                "When the lock closes, the card in Recents shows a lock icon in its corner.");
        add("确认之后本应用会从任务切换页隐藏，届时无法再长按上锁。",
                " After confirming, AnyNight disappears from Recents and you can no longer "
                        + "long-press to lock it.");
        add("还没上锁", "Not yet");
        add("已上锁", "Locked");

        // ---- 联系作者 ----
        add("作者邮箱", "Author email");
        add("点按或长按即可复制", "Tap or long-press to copy");
        add("已复制 ", "Copied ");
        add("复制失败：", "Copy failed: ");
        add("关闭", "Close");
        add("关于捐献与回复", "About donations and replies");
        add("本软件免费使用全部功能，是否捐献不影响其性质，",
                "Every feature is free; donating or not does not change that,");
        add("作者也无法承诺是否或多久回复你的联系。",
                " and the author cannot promise whether or how soon you will get a reply.");
        add("导出日志（zip）", "Export log (zip)");
        add("日志记录切换时间、目标应用、开关结果和崩溃堆栈，",
                "The log records switch times, target apps, results and crash stacks,");
        add("外加系统版本与机型，不含照片和任何账户信息。",
                " plus the OS version and device model — no photos, no account data.");
        add("导出为 zip 存到「下载/AnyNight」，发邮件时把它作为附件即可。",
                " It is saved as a zip under Download/AnyNight — attach it to your email.");
        add("正在打包日志…", "Packaging the log…");
        add("已保存到 ", "Saved to ");
        add("导出日志失败", "Log export failed");
        add("用邮件联系最稳妥。带上日志，能省掉很多来回问。",
                "Email is the most reliable. Attach the log and save a lot of back-and-forth.");
        add("先导出日志", "Export the log first");
        add("未知错误", "Unknown error");

        // ---- 请喝大水 ----
        add("微信扫码，请我喝瓶大水", "Scan with WeChat to buy me a drink");
        add("长按图片可保存到相册", "Long-press the image to save it");
        add("已保存到相册 Pictures/AnyNight", "Saved to Pictures/AnyNight");
        add("保存失败：", "Save failed: ");
        add("收款码加载失败，请重新安装应用。", "Could not load the QR code. Please reinstall the app.");
        add("「请喝大水」为捐献性质，金额随意，任何理由均不支持退费。",
                "\"Buy me a drink\" is a donation — any amount, no refunds for any reason.");

        // ---- 悬浮提示与通知 ----
        add("AnyNight · 已切为深色", "AnyNight · switched to dark");
        add("AnyNight · 已切为浅色", "AnyNight · switched to light");
        add("AnyNight · 运行中", "AnyNight · running");
        add("保活通知", "Keep-alive");
        add("保持深浅色切换服务在后台存活，不发出提示音",
                "Keeps the switch service alive in the background, silently");
        add("进出所选应用自动切换，空闲不唤醒",
                "Switches on entry and exit; never wakes while idle");

        // ---- 首页打开日志（仅调试用） ----
        add("首页打开 v", "Home opened v");

        // ---- v1.9.4：捐献页 / 联系页 / 更新 ----
        add("微信扫码，长按图片可保存到相册",
                "Scan with WeChat. Long-press the image to save it.");
        add("此为捐献性质，金额随意，无法退费。捐献不构成软件盈利，软件仍为免费使用全部功能。",
                "This is a voluntary donation of any amount and is non-refundable. It is not revenue: "
                        + "every feature stays free.");
        add("复制", "Copy");
        add("长按邮箱可选中，或点右侧「复制」",
                "Long-press the address to select it, or tap Copy.");
        add("感谢你的反馈，但作者无法承诺是否回复或多久回复。",
                "Thanks for the feedback — there is no promise of a reply, or of when.");
        add("导出为 zip，存在系统「下载」根目录（文件名以 AnyNight 开头），发邮件时作为附件即可。",
                "It is saved as a zip in the system Download folder (file name starts with AnyNight); "
                        + "attach it to the email.");
        add("报告写到系统「下载」根目录（AnyNight 开头），里面是设备信息、运行日志和这次读取到的状态。",
                "The report goes to the system Download folder (name starts with AnyNight): device "
                        + "info, logs and what this page just read.");
        add("已复制这一步", "Step copied");
        add("检查更新", "Check for updates");
        add("正在检查…", "Checking…");
        add("检查失败，网络不通或代理不可用", "Check failed: no network, or the mirror is unreachable");
        add("已是最新版本 v", "You are on the latest version, v");
        add("发现新版本 v", "New version v");
        add("当前 v", "You are on v");
        add("。下载后可直接覆盖安装，设置和已选应用都会保留。",
                ". It installs over the current build; settings and your app list are kept.");
        add("以后再说", "Not now");
        add("下载", "Download");
        add("正在下载 v", "Downloading v");
        add("下载失败：", "Download failed: ");
        add("网络错误", "network error");
        add("请先允许本应用安装应用", "Allow AnyNight to install apps first");
        add("无法启动安装：", "Could not start the installer: ");

        // ---- v1.9.5：捐献文案 / 一键复制 / 首次引导 ----
        add("此为捐献性质，金额随意，无法退费，非常感谢您的捐献！",
                "It is a voluntary donation of any amount, non-refundable — thank you very much!");
        add("本软件为作者失业在家开发，捐献不构成软件盈利，软件仍为免费使用全部功能。",
                " Built at home between jobs: donations are not revenue, and every feature stays free.");
        add("一键复制全部步骤", "Copy all steps");
        add("全部步骤已复制，可直接粘贴", "All steps copied — paste them anywhere");
        add("复制全部链接", "Copy all links");
        add("先按第 1 步启用 Shizuku", "Start with step 1: set up Shizuku");
        add("先开启全部权限", "Turn everything on first");
        add("AnyNight 靠 Shizuku、无障碍和后台权限工作，缺一项就会「点了没反应」。",
                "AnyNight needs Shizuku, accessibility and background permission; miss one and nothing "
                        + "seems to happen.");
        add("下面几步按顺序做完，再回首页点「立即测试」确认。",
                " Work through the steps below in order, then go back and tap \"Test now\".");
        add("从第 1 步开始", "Start with step 1");
        add("打开", "Open");

        // ---- v1.9.6：教程加无障碍步骤 / 按钮挪到右上角 ----
        add("开启无障碍服务", "Turn on accessibility");
        add("AnyNight 靠无障碍判断「现在前台是哪个应用」，没开就等于不知道你进了哪个应用。",
                "AnyNight uses accessibility to know which app is in front; without it the app has no idea.");
        add("设置 → 无障碍 → 已下载的应用 → 找到「AnyNight」并打开。",
                " Settings → Accessibility → Downloaded apps → AnyNight → turn it on.");
        add("去开启", "Turn on");
        add("「任务切换页隐藏」必须在上锁那步之后才开。",
                " \"Hide from Recents\" must come after the locking step.");
        add("无法打开无障碍设置，请手动到 设置 → 无障碍",
                "Could not open accessibility settings — go to Settings → Accessibility manually.");
        add("复制全部", "Copy all");
    }
}
