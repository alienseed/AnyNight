package com.local.anynight;

import android.content.Context;
import android.content.SharedPreferences;

/** 运行期开关，全部存在同一个 cfg 里，服务与界面共享。 */
final class Cfg {

    private static final String FILE = "cfg";
    private static final String KEY_AUTO = "auto_enabled";
    private static final String KEY_HINT = "hint_overlay";
    private static final String KEY_RECENTS_LOCK = "recents_lock_done";
    private static final String KEY_FIRST_RUN_DONE = "first_run_done";

    private Cfg() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 总开关：关掉后即使进了清单里的应用也不动系统开关。 */
    static boolean autoEnabled(Context c) {
        return sp(c).getBoolean(KEY_AUTO, true);
    }

    static void setAutoEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_AUTO, on).apply();
    }

    /** 切换时是否在对方应用上盖一条提示（悬浮窗）。 */
    static boolean hintEnabled(Context c) {
        return sp(c).getBoolean(KEY_HINT, true);
    }

    static void setHintEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_HINT, on).apply();
    }

    /** 任务切换页是否已经完成「上锁」。只有完成了才允许开启「任务切换页隐藏」。 */
    static boolean recentsLockDone(Context c) {
        return sp(c).getBoolean(KEY_RECENTS_LOCK, false);
    }

    static void setRecentsLockDone(Context c, boolean done) {
        sp(c).edit().putBoolean(KEY_RECENTS_LOCK, done).apply();
    }

    /** 首次安装后的第一次打开：自动进教程并弹一次提醒。 */
    static boolean firstRun(Context c) {
        return !sp(c).getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    static void setFirstRunDone(Context c) {
        sp(c).edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply();
    }
}
