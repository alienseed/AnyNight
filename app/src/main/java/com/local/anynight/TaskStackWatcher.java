package com.local.anynight;

import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * 前台识别（v5）：事件驱动，无轮询。
 *
 * 事件源（三路，互为兜底，空闲成本都是 0）：
 * 1) ITaskStackListener（主）：每次前台切换必回调，实测 code=12 必达；
 * 2) IProcessObserver（辅）：只在进程冷启动时有 fg=true，能覆盖任务栈事件异常的情况；
 * 3) 无障碍窗口事件（辅）：非 Flutter 应用会发 TYPE_WINDOW_STATE_CHANGED，由服务转发进来。
 *
 * 唯一的周期任务是 10 分钟一次的心跳：检查监听是否还挂着、状态是否一致。
 * 监听被系统静默摘掉时，只有心跳能发现——这是「纯事件」必须付的保险。
 *
 * 回调里的 TaskInfo 布局被 MIUI 改过，按 AOSP 解析会抛 Unknown URI type，
 * 所以事件只当触发器，包名一律用一次 getTasks(1) 取。
 */
final class TaskStackWatcher {

    /** ITaskStackListener 的回调事务码（设备实测，硬编码以避免隐藏 API 反射）。 */
    private static final int CB_TASK_STACK_CHANGED = 1;
    private static final int CB_TASK_MOVED_TO_FRONT = 12;
    private static final int CB_TASK_FOCUS_CHANGED = 22;

    /** 事件到达后立刻查询；同一瞬间的多个回调合并成一次。 */
    private static final long DEBOUNCE_MS = 40L;
    /** 首次查询没看到变化（应用还在启动动画里）时的补查延迟。 */
    private static final long RETRY_MS = 220L;
    /** 心跳：只在「纯事件」方案里用于发现监听失效和状态漂移，10 分钟一次。 */
    private static final long WATCHDOG_MS = 10 * 60 * 1000L;
    /**
     * 降级轮询间隔。只有在任务栈监听挂不上（某些 ROM 改了事务码或直接拒绝）
     * 的时候才启用，属于「宁可多花一点电，也要能用」的兜底。
     */
    private static final long POLL_MS = 1200L;

    interface Callback {
        void onTopChanged(String pkg);
    }

    private final Callback callback;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;
    private String lastTop;

    /** 监听是否真的挂上了。界面靠它显示状态，避免注册失败还显示「已生效」。 */
    private static volatile boolean registered;

    /** 监听挂不上时的降级轮询是否在跑。 */
    private static volatile boolean polling;

    /** 注册状态变化时回调界面（同一进程，切主线程即可）。 */
    private static volatile Runnable stateListener;
    private static final Handler MAIN = new Handler(android.os.Looper.getMainLooper());

    /** 当前实例。用户刚授权 Shizuku 时界面会调 kickRegister() 立刻重试，不必等 10 分钟心跳。 */
    private static volatile TaskStackWatcher instance;
    private static volatile long lastKick;

    static boolean isRegistered() {
        return registered;
    }

    /** 授权状态刚变化时重试注册，节流 3 秒。 */
    static void kickRegister() {
        TaskStackWatcher w = instance;
        if (w == null || registered) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastKick < 3000) return;
        lastKick = now;
        Logx.i("界面请求重新注册任务栈监听");
        w.worker.execute(w::register);
    }

    static boolean isPolling() {
        return polling && !registered;
    }

    static void setStateListener(Runnable r) {
        stateListener = r;
    }

    private static void notifyState() {
        final Runnable r = stateListener;
        if (r != null) MAIN.post(r);
    }

    TaskStackWatcher(Callback callback) {
        this.callback = callback;
        instance = this;
    }

    /** Shizuku 重启后重新注册，避免一次 binder 挂掉就永久失去事件。 */
    private final Shizuku.OnBinderReceivedListener onBinderReceived = () -> {
        registered = false;
        notifyState();
        worker.execute(this::register);
    };

    private final IBinder listener = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                // 不区分回调类型：不同 ROM 上「切到前台」用的是不同的回调
                // （MIUI 上热启动一个已存在的任务就不发 onTaskMovedToFront），
                // 所以把任何一个回调都当触发器。代价是一次 getTasks 查询，
                // 有 40ms 去抖 + 包名比对，重复触发不会重复切换。
                requestCheck();
            } catch (Throwable ignored) {
            }
            return true;
        }
    };

    void start() {
        if (running) return;
        running = true;

        thread = new HandlerThread("stack-watch");
        thread.start();
        handler = new Handler(thread.getLooper());

        // 启动时补一次当前状态，之后由事件驱动
        handler.post(this::checkNow);
        handler.postDelayed(watchdog, WATCHDOG_MS);

        worker.execute(this::register);
        try {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived);
        } catch (Throwable t) {
            Logx.e("注册 Shizuku binder 监听失败", t);
        }
    }

    void stop() {
        running = false;
        registered = false;
        polling = false;
        if (instance == this) instance = null;
        notifyState();
        try {
            Shizuku.removeBinderReceivedListener(onBinderReceived);
        } catch (Throwable ignored) {
        }
        worker.execute(() -> NightModeController.unregisterTaskStackListener(listener));
        Handler h = handler;
        if (h != null) {
            h.removeCallbacksAndMessages(null);
        }
        if (thread != null) thread.quitSafely();
        worker.shutdown();
    }

    /** 供其它事件源（进程观察者、无障碍事件、亮屏）调用的入口。 */
    void requestCheck() {
        scheduleCheck();
    }

    /**
     * 强制重判：先清掉 lastTop 去抖缓存再查一次。
     *
     * 用于「先不动、稍后再判定」的场景（分享跳转后的稳定期、切换冷却）。
     * 普通 requestCheck() 在这里没用——lastTop 已经等于当前前台，
     * doCheck 会认为没变化而直接返回。
     */
    void requestCheckForced() {
        Handler h = handler;
        if (h == null || !running) return;
        h.post(() -> {
            lastTop = null;
            checkNow();
        });
    }

    /** 延迟 ms 后强制重判一次。 */
    void requestCheckDelayed(long ms) {
        Handler h = handler;
        if (h == null || !running) return;
        h.postDelayed(this::requestCheckForced, Math.max(0L, ms));
    }

    private void register() {
        if (!running || registered) return;
        boolean ok = NightModeController.registerTaskStackListener(listener);
        registered = ok;
        notifyState();
        Logx.i("任务栈监听注册=" + ok);
        Handler h = handler;
        if (ok) {
            polling = false;
            if (h != null) h.post(this::checkNow);
        } else if (h != null) {
            // 这个 ROM 上事件源挂不上：退化为低频轮询，界面会显示「轮询降级」
            Logx.w("任务栈监听不可用，启用降级轮询 " + POLL_MS + "ms");
            polling = true;
            notifyState();
            h.removeCallbacks(poll);
            h.postDelayed(poll, POLL_MS);
        }
    }

    /** 降级轮询：仅在事件源不可用时运行，恢复后自动停止。 */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (registered) {
                polling = false;
                notifyState();
                return;
            }
            polling = true;
            doCheck(true);
            Handler h = handler;
            if (h != null) h.postDelayed(this, POLL_MS);
        }
    };

    private void scheduleCheck() {
        Handler h = handler;
        if (h == null || !running) return;
        h.removeCallbacks(retry);
        h.removeCallbacks(check);
        h.postDelayed(check, DEBOUNCE_MS);
    }

    private final Runnable check = this::checkNow;

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            doCheck(true);
        }
    };

    /** 10 分钟一次：确认监听还在，顺带对账一次当前前台。 */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!registered) {
                Logx.w("心跳：监听已丢失，重新注册");
                worker.execute(TaskStackWatcher.this::register);
            }
            doCheck(true);
            Handler h = handler;
            if (h != null) h.postDelayed(this, WATCHDOG_MS);
        }
    };

    private void checkNow() {
        doCheck(false);
    }

    /**
     * @param isRetry true 表示补查/心跳，不再安排二次补查，避免连锁唤醒
     */
    private void doCheck(boolean isRetry) {
        if (!running) return;
        String top = NightModeController.topPackage();
        if (top == null) {
            if (!isRetry) {
                Handler h = handler;
                if (h != null) h.postDelayed(retry, RETRY_MS);
            }
            return;
        }
        if (top.equals(lastTop)) {
            if (!isRetry) {
                Handler h = handler;
                if (h != null) {
                    h.removeCallbacks(retry);
                    h.postDelayed(retry, RETRY_MS);
                }
            }
            return;
        }
        lastTop = top;
        Logx.d("前台=" + top + (isRetry ? " (心跳/补查)" : ""));
        callback.onTopChanged(top);
    }
}
