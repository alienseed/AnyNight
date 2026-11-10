package com.local.anynight;

/**
 * 临时界面（跳板）判定。
 *
 * 这些包会短暂占据前台，但它们不是用户"打开的另一个应用"：
 * - 分享/打开方式选择器（intentresolver）：从相册点"发送到微信"时出现；
 * - 权限、安装、系统弹窗（securitycenter / permissioncontroller）；
 * - 状态栏、系统窗口（systemui / android）。
 *
 * 以前把它们当成"离开了目标应用"，于是在选择器出现的那一瞬间就把系统
 * 拨回原状态——正好和被分享应用启动撞在一起：微信起不来，深浅色来回跳。
 * 现在一律穿透：保持当前状态，等真正的目标应用上来再判定。
 */
final class Transient {

    private static final String[] PKGS = {
            "android",
            "com.android.intentresolver",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.lbe.security.miui",
            "com.miui.securitycenter",
            "com.miui.securitycore",     // 应用双开 / XSpace 的选择框
            "com.miui.contentextension",
    };

    private Transient() {
    }

    static boolean is(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (String p : PKGS) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }
}
