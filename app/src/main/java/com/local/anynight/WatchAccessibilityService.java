package com.local.anynight;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Parcel;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台识别 + 系统深浅色切换。
 *
 * 本服务承担三件事：
 * 1) 保活：HyperOS 会挂起普通后台进程，本服务由系统绑定，能把进程优先级顶住；
 *    再叠加一个常驻通知前台服务。
 * 2) 事件源：把任务栈事件、进程观察者（冷启动）和无障碍窗口事件转发给 TaskStackWatcher。
 * 3) 提供「打开任务切换页」的全局动作，供保活指引页使用。
 *
 * 切换逻辑（两个方向对称）：
 * 进入目标应用 → 记录当前系统深浅色，切到清单要求的状态；
 * 离开 → 还原成进入前的状态。所以清单里既可以是「浅色模式下深色化」，
 * 也可以是「深色模式下浅色化」。
 *
 * 本服务不读取屏幕内容。
 */
public class WatchAccessibilityService extends AccessibilityService {

    private static final String PREFS = "cfg";
    private static final String PREF_APPLIED = "night_applied";
    private static final String PREF_RESTORE = "night_restore";
    private static final String PREF_PKG = "night_applied_pkg";

    private static volatile WatchAccessibilityService instance;

    private TaskStackWatcher watcher;

    /** 当前是否处于「本服务改过系统开关」的状态。 */
    private boolean applied;
    /** 进入目标应用之前的系统深色状态，离开时以此还原。 */
    private boolean restoreNight;
    private String appliedPkg;

    private final ExecutorService switchExecutor = Executors.newSingleThreadExecutor();

    /**
     * 自愈看门狗：只在「系统被改过」期间运行，45 秒核对一次前台。
     *
     * 有些 ROM 会在息屏、内存紧张时丢掉任务栈事件，结果就是人已经回到桌面了，
     * 系统却还停在深色，而且再也没有事件把它叫醒。这段代码只在 applied=true 时
     * 才排队（不在目标应用里就完全不跑），代价可以忽略，但能保证最终还原。
     */
    private static final long GUARD_MS = 45_000L;
    private final android.os.Handler guardHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable guardTick = new Runnable() {
        @Override
        public void run() {
            if (!applied) return;
            if (watcher != null) watcher.requestCheck();
            guardHandler.postDelayed(this, GUARD_MS);
        }
    };

    private void startGuard() {
        guardHandler.removeCallbacks(guardTick);
        guardHandler.postDelayed(guardTick, GUARD_MS);
    }

    private void stopGuard() {
        guardHandler.removeCallbacks(guardTick);
    }

    /** 供指引页调用：打开系统任务切换页面。 */
    static boolean openRecents() {
        WatchAccessibilityService s = instance;
        if (s == null) return false;
        try {
            return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 首页「一键还原」：不管现在前台是谁，都把系统恢复到「本应用改动前」的模式。
     *
     * @return 有记录可还原时返回 true；从没改过则返回 false（不去动用户的设置）
     */
    static boolean forceRestore(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean wasApplied = sp.getBoolean(PREF_APPLIED, false);
        boolean back = sp.getBoolean(PREF_RESTORE, false);
        if (!wasApplied) return false;

        sp.edit().putBoolean(PREF_APPLIED, false).putString(PREF_PKG, null).apply();
        WatchAccessibilityService s = instance;
        if (s != null) {
            s.applied = false;
            s.appliedPkg = null;
            s.stopGuard();
        }
        Logx.i("一键还原 → " + (back ? "深色" : "浅色"));
        Thread t = new Thread(() -> {
            try {
                NightModeController.setNight(back);
            } catch (Throwable ignored) {
            }
        }, "force-restore");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /**
     * 界面关掉总开关时调用：如果此刻正处在「我们改过系统开关」的状态，
     * 立刻还原，不要让用户带着一个改过的系统状态留在这里。
     */
    static void restoreIfApplied() {
        WatchAccessibilityService s = instance;
        if (s == null || !s.applied) return;
        final boolean back = s.restoreNight;
        s.applied = false;
        s.appliedPkg = null;
        s.saveState();
        s.stopGuard();
        Logx.i("总开关关闭，还原为 " + (back ? "深色" : "浅色"));
        s.switchExecutor.execute(() -> {
            try {
                NightModeController.setNight(back);
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Logx.init(this);
        loadState();
        Logx.i("无障碍服务已连接，启动监听（pending=" + applied + "）");

        watcher = new TaskStackWatcher(this::onTopChanged);
        watcher.start();

        // 自动对账：进程被系统杀掉 / 重装 / Shizuku 重启，都可能留下
        // 「系统被本应用改过、但没人还原」的状态。服务一起来就按当前前台重判一次：
        // 前台不是目标应用 → 立刻还原成进入前的模式。这是最主要的一层回滚兜底。
        if (applied) {
            Logx.i("检测到上次未还原（还原目标=" + (restoreNight ? "深色" : "浅色") + "），开始对账");
            startGuard();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                TaskStackWatcher w = watcher;
                if (w != null) w.requestCheck();
            }, 800);
        }

        KeepAliveService.start(this);

        registerScreenReceiver();
        registerProcessObserver();
    }

    private void loadState() {
        try {
            applied = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(PREF_APPLIED, false);
            restoreNight = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(PREF_RESTORE, false);
            appliedPkg = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(PREF_PKG, null);
        } catch (Throwable ignored) {
        }
    }

    private void saveState() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_APPLIED, applied)
                    .putBoolean(PREF_RESTORE, restoreNight)
                    .putString(PREF_PKG, appliedPkg)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            Logx.e("注册亮屏广播失败", t);
        }
    }

    /**
     * 兜底事件源之一：系统进程观察者。
     * 该 ROM 上只有进程冷启动会回调 fg=true，热切换没有事件，
     * 但它能覆盖「任务栈监听被摘掉」的极端情况，且空闲零成本。
     */
    private void registerProcessObserver() {
        switchExecutor.execute(() -> {
            boolean ok = NightModeController.registerProcessObserver(processObserver);
            Logx.i("进程观察者注册=" + ok);
        });
    }

    private final IBinder processObserver = new android.os.Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code == 1) {
                    data.enforceInterface("android.app.IProcessObserver");
                    data.readInt();                 // pid
                    data.readInt();                 // uid
                    boolean fg = data.readInt() != 0;
                    if (fg && watcher != null) watcher.requestCheck();
                }
            } catch (Throwable ignored) {
            }
            return true;
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 亮屏/解锁后对账一次：息屏期间的切换可能被跳过
            if (watcher != null) watcher.requestCheck();
        }
    };

    private void onTopChanged(String pkg) {
        // 总开关关掉时，一切照旧，不做任何系统级改动
        int mode = Cfg.autoEnabled(this) ? TargetApps.modeOf(this, pkg) : -1;
        boolean systemDark = NightModeController.isNightNow(this);

        if (mode >= 0) {
            boolean want = mode == TargetApps.DARK;
            if (!applied) {
                // 第一次进入目标应用：记下原状态，离开时要还原成它
                applied = true;
                appliedPkg = pkg;
                restoreNight = systemDark;
                saveState();
                startGuard();
                Logx.i("进入 " + pkg + "：原状态=" + (systemDark ? "深色" : "浅色")
                        + "，目标=" + (want ? "深色" : "浅色"));
                if (systemDark != want) applyNight(want, "进入目标应用");
            } else if (!pkg.equals(appliedPkg)) {
                // 目标应用之间直接互跳，原状态不变，只改当前目标
                appliedPkg = pkg;
                saveState();
                Logx.i("切换到另一个目标 " + pkg + "，目标=" + (want ? "深色" : "浅色"));
                if (systemDark != want) applyNight(want, "切换目标应用");
            } else if (systemDark != want) {
                // 同一应用内被别的东西改了系统开关，纠正回来
                applyNight(want, "状态漂移纠正");
            }
        } else if (applied) {
            boolean back = restoreNight;
            applied = false;
            appliedPkg = null;
            saveState();
            stopGuard();
            Logx.i("离开目标应用（" + pkg + "），还原=" + (back ? "深色" : "浅色"));
            if (systemDark != back) applyNight(back, "离开目标应用还原");
        }
    }

    private void applyNight(boolean dark, String why) {
        final Context app = this;
        final boolean showHint = Cfg.hintEnabled(app);
        // 每次切换用独立线程：万一某个系统调用卡住，也只丢这一次，
        // 不会把后续所有切换都堵死（单线程池曾被一次卡死拖垮整条链路）。
        Thread t = new Thread(() -> {
            try {
                boolean ok = NightModeController.setNight(dark);
                Logx.i(why + " → " + (dark ? "深色" : "浅色") + " " + (ok ? "成功" : "失败"));
                if (ok && showHint) {
                    // 在对方应用之上说明「这次是我们触发的」，避免和它自带的深色开关混淆
                    OverlayHint.showSwitch(app, dark, null);
                }
            } catch (Throwable e) {
                Logx.e(why + " 失败", e);
            }
        }, "switch");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 任务栈回调在个别 ROM/应用上会漏（Flutter 应用尤其），窗口事件是另一路兜底。
        // 不做包名过滤：多一次去抖后的查询，换来不漏事件。
        if (watcher == null || event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        watcher.requestCheck();
    }

    @Override
    public void onInterrupt() {
        Logx.w("无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        instance = null;
        Logx.i("无障碍服务销毁（applied=" + applied + "）");
        try {
            unregisterReceiver(screenReceiver);
        } catch (Throwable ignored) {
        }
        stopGuard();
        if (watcher != null) watcher.stop();
        if (applied) {
            final boolean back = restoreNight;
            applied = false;
            appliedPkg = null;
            saveState();
            switchExecutor.execute(() -> {
                try {
                    NightModeController.setNight(back);
                } catch (Throwable ignored) {
                }
            });
        }
        switchExecutor.shutdown();
        super.onDestroy();
    }
}
