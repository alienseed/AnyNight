package com.local.anynight;

import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Field;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * 通过 Shizuku 提供的 shell 身份调用 IUiModeManager.setNightMode()。
 *
 * 背景：HyperOS 把夜间模式锁住了（dumpsys uimode 里 mNightModeLocked=true），
 * 普通应用直接写 Settings.Secure.ui_night_mode 不会被采纳，
 * 只有具备 MODIFY_DAY_NIGHT_MODE 的进程（shell/adb）调用系统服务才能切换。
 */
public final class NightModeController {

    public static final int PERM_REQUEST_CODE = 1001;

    private static final String SERVICE_NAME = "uimode";
    private static final String STUB_CLASS = "android.app.IUiModeManager$Stub";
    private static final String DESCRIPTOR = "android.app.IUiModeManager";
    private static final int MODE_NIGHT_NO = 1;
    private static final int MODE_NIGHT_YES = 2;

    /**
     * IUiModeManager.Stub.TRANSACTION_setNightMode。
     * 实测得出（该机上依次调用 service call uimode <code> i32 2 探测，
     * code=7 生效，code=1/2/4/5 抛空指针，code=3/6 无副作用）。
     * 系统隐藏 API 限制（max-target-o）导致反射取不到该常量，只能硬编码。
     */
    private static final int TRANSACTION_SET_NIGHT_MODE = 7;

    /**
     * 其余实测事务码（同一台机器）：
     * getNightMode = 8，registerProcessObserver = 13，getTasks = 32。
     * 这些常量都在隐藏类里，反射被拦，只能硬编码，且随系统版本可能变化。
     */
    private static final int TRANSACTION_REGISTER_PROCESS_OBSERVER = 13;
    private static final int TRANSACTION_GET_TASKS = 32;

    /**
     * 任务栈监听（activity_task 服务）。事务码同样来自设备端探针实测：
     * registerTaskStackListener = 46，unregisterTaskStackListener = 47。
     * 回调侧是 ITaskStackListener，实测 code=12（onTaskMovedToFront）在每次
     * 前台切换时都会到达，是「零轮询」方案的事件源。
     */
    private static final String TASK_SERVICE = "activity_task";
    private static final String TASK_DESCRIPTOR = "android.app.IActivityTaskManager";
    private static final int TRANSACTION_REGISTER_TASK_STACK_LISTENER = 46;
    private static final int TRANSACTION_UNREGISTER_TASK_STACK_LISTENER = 47;

    private static Integer cachedCode;

    private NightModeController() {
    }

    public static boolean isBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!Shizuku.pingBinder()) return false;
            if (Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(PERM_REQUEST_CODE);
        } catch (Throwable ignored) {
        }
    }

    /** 当前系统是否已处于深色模式。 */
    public static boolean isNightNow(android.content.Context ctx) {
        try {
            android.app.UiModeManager um = (android.app.UiModeManager)
                    ctx.getSystemService(android.content.Context.UI_MODE_SERVICE);
            if (um != null) {
                return um.getNightMode() == android.app.UiModeManager.MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {
        }
        int v = android.provider.Settings.Secure.getInt(
                ctx.getContentResolver(), "ui_night_mode", 1);
        return v == MODE_NIGHT_YES;
    }

    /** 取 IUiModeManager.Stub.TRANSACTION_setNightMode。 */
    private static Integer transactionCode() {
        if (cachedCode != null) return cachedCode;

        // 先试反射（部分系统上可用），失败则回落到实测值
        try {
            Class<?> stub = Class.forName(STUB_CLASS);
            Field f = stub.getDeclaredField("TRANSACTION_setNightMode");
            f.setAccessible(true);
            int code = f.getInt(null);
            cachedCode = code;
            Logx.i("transactionCode from reflection: " + code);
            return code;
        } catch (Throwable t) {
            Logx.i("reflection unavailable (" + t.getClass().getSimpleName()
                    + "), use probed code " + TRANSACTION_SET_NIGHT_MODE);
        }

        cachedCode = TRANSACTION_SET_NIGHT_MODE;
        return cachedCode;
    }

    /**
     * 切换系统深色模式，会阻塞，必须在线程中调用。
     *
     * 优先走 shell 命令 `cmd uimode night yes|no`：
     * 这是 AOSP 自带的命令行入口，不依赖任何隐藏事务码，跨 ROM/跨版本都稳定，
     * 也是本应用能「理论上支持所有 Android」的关键——binder 事务码只在
     * 拿不到 shell 的极端情况下才用。
     */
    public static boolean setNight(boolean dark) {
        if (!hasPermission()) {
            Logx.w("setNight: no permission");
            return false;
        }
        Logx.d("setNight 开始 dark=" + dark);

        // 1) binder 事务：最快，也是本机验证过的方式
        if (binderMode != 2) {
            if (setNightViaBinder(dark)) {
                if (binderMode == 0) {
                    binderMode = 1;
                    Logx.i("深浅色切换：使用 binder 通道");
                }
                return true;
            }
            binderMode = 2;
            Logx.w("binder 通道不可用，改走 shell(cmd uimode)");
        }

        // 2) shell 命令：不依赖事务码，跨 ROM 更稳，但每条命令要起一个子进程
        if (shellMode != 2) {
            if (setNightViaShell(dark)) {
                if (shellMode == 0) {
                    shellMode = 1;
                    Logx.i("深浅色切换：使用 shell(cmd uimode) 通道");
                }
                return true;
            }
            shellMode = 2;
            Logx.w("shell 通道不可用");
        }
        return false;
    }

    /** 0=未探测 1=可用 2=不可用 */
    private static volatile int binderMode = 0;
    private static volatile int shellMode = 0;

    private static android.content.Context appCtx;

    /** 读回当前状态需要一个 Context，Logx.init 时一并带进来。 */
    static void attach(android.content.Context c) {
        if (c != null) appCtx = c.getApplicationContext();
    }


    /**
     * 用 shell 身份执行 `cmd uimode night`，并**读回**验证是否真的生效。
     * 读回这一步很重要：有的系统接受命令但不采纳（夜间模式被锁）。
     */
    private static boolean setNightViaShell(boolean dark) {
        String out = shell("cmd uimode night " + (dark ? "yes" : "no"));
        if (out == null) return false;
        String now = shell("cmd uimode night");
        if (now == null) return false;
        boolean isYes = now.toLowerCase(java.util.Locale.ROOT).contains("yes");
        boolean ok = isYes == dark;
        Logx.i("setNight(shell " + (dark ? "yes" : "no") + ") 读回="
                + now.trim() + " ok=" + ok);
        return ok;
    }

    private static boolean setNightViaBinder(boolean dark) {
        try {
            IBinder service = SystemServiceHelper.getSystemService(SERVICE_NAME);
            if (service == null) {
                Logx.w("setNight: service null");
                return false;
            }

            Integer code = transactionCode();
            if (code == null) {
                Logx.w("setNight: code null");
                return false;
            }

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(dark ? MODE_NIGHT_YES : MODE_NIGHT_NO);
                boolean ok = new ShizukuBinderWrapper(service).transact(code, data, reply, 0);
                reply.readException();
                Logx.i("setNight(" + dark + ") transact=" + ok);
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable t) {
            Logx.e("setNight failed", t);
            return false;
        }
    }

    /**
     * 读取当前最顶层任务所属包名，失败返回 null。
     *
     * 两条通道依次尝试，都是为了跨 ROM 兼容：
     * 1) IActivityManager.getTasks(1)（快，但事务码随 ROM 版本变）；
     * 2) `dumpsys activity activities` 里的 topResumedActivity（文本解析，慢但稳定）。
     * 都不需要「使用情况访问」权限。
     */
    public static String topPackage() {
        if (!hasPermission()) return null;
        String p = topPackageViaBinder();
        if (p != null && !p.isEmpty()) return p;
        return topPackageViaDumpsys();
    }

    /** 解析 dumpsys 输出里的 topResumedActivity / mResumedActivity。 */
    private static String topPackageViaDumpsys() {
        String out = shell("dumpsys activity activities | grep -m2 -E "
                + "'topResumedActivity|mResumedActivity'");
        if (out == null || out.isEmpty()) return null;
        // 形如：topResumedActivity=ActivityRecord{189652815 u0 com.pkg/.Main t2505}
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\bu\\d+\\s+([A-Za-z0-9_.]+)/")
                .matcher(out);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String topPackageViaBinder() {
        IBinder service = null;
        try {
            service = SystemServiceHelper.getSystemService("activity");
            if (service == null) return null;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.IActivityManager");
                data.writeInt(1);
                new ShizukuBinderWrapper(service).transact(TRANSACTION_GET_TASKS, data, reply, 0);
                reply.readException();

                java.util.ArrayList<android.app.ActivityManager.RunningTaskInfo> tasks =
                        new java.util.ArrayList<>();
                reply.readTypedList(tasks, android.app.ActivityManager.RunningTaskInfo.CREATOR);
                if (tasks.isEmpty()) return null;
                android.content.ComponentName top = tasks.get(0).topActivity;
                return top == null ? null : top.getPackageName();
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable t) {
            Logx.w("topPackage failed: " + t);
            return null;
        }
    }

    /**
     * 以 shell 身份注册进程观察者（IProcessObserver）。
     * 实测该 ROM 上只有冷启动会产生回调，热切换没有事件，因此只能当加速信号用。
     */
    public static boolean registerProcessObserver(IBinder observer) {
        if (!hasPermission()) return false;
        try {
            IBinder service = SystemServiceHelper.getSystemService("activity");
            if (service == null) return false;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.IActivityManager");
                data.writeStrongBinder(observer);
                boolean ok = new ShizukuBinderWrapper(service)
                        .transact(TRANSACTION_REGISTER_PROCESS_OBSERVER, data, reply, 0);
                reply.readException();
                Logx.i("registerProcessObserver ok=" + ok);
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable t) {
            Logx.w("registerProcessObserver failed: " + t);
            return false;
        }
    }

    /**
     * 以 shell 身份执行一条命令（Shizuku 起子进程，等价于 adb shell）。
     * 用于设备空闲白名单、待机桶这类只有 shell 能改的系统设置。
     */
    public static boolean runShell(String command) {
        String out = shell(command);
        Logx.i("shell[" + command + "] " + (out == null ? "失败" : "完成"));
        return out != null;
    }

    /**
     * 以 shell 身份执行命令并取回标准输出，失败返回 null。
     *
     * 走 Shizuku.newProcess（反射，因为它在 API 里是被隐藏的）。
     * 这里所有跨 ROM 的能力都建立在「能起一个 shell 子进程」上——
     * 只要 Shizuku 可用，命令就跟 adb shell 里敲的一样。
     *
     * 注意：不能用 waitFor(timeout) —— Shizuku 的 Process 实现没覆盖带超时的那个重载，
     * 会抛 "process hasn't exited"。先读到流结束（进程退出后流才 EOF），再 waitFor()。
     */
    public static String shell(String command) {
        if (!hasPermission()) return null;
        Process p = null;
        Thread watchdog = null;
        final boolean[] timedOut = {false};
        try {
            java.lang.reflect.Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            p = (Process) m.invoke(null, new String[]{"sh", "-c", command}, null, null);
            if (p == null) return null;

            final Process proc = p;
            watchdog = new Thread(() -> {
                try {
                    Thread.sleep(SHELL_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                timedOut[0] = true;
                try {
                    proc.destroy();
                } catch (Throwable ignored) {
                }
            }, "shell-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            // 读流放到独立线程上带超时等待：Shizuku 的远程 Process 偶尔会永远不返回 EOF，
            // 直接在本线程 read 会把整条切换链路卡死。
            final java.io.InputStream is = p.getInputStream();
            final String[] holder = new String[1];
            Thread reader = new Thread(() -> holder[0] = readAll(is), "shell-reader");
            reader.setDaemon(true);
            reader.start();
            try {
                reader.join(SHELL_TIMEOUT_MS);
            } catch (InterruptedException ignored) {
            }
            watchdog.interrupt();
            if (reader.isAlive()) {
                timedOut[0] = true;
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
                Logx.w("shell 超时: " + command);
                return null;
            }
            return holder[0];
        } catch (Throwable t) {
            Logx.w("shell failed: " + command + " " + t);
            return null;
        } finally {
            if (watchdog != null) watchdog.interrupt();
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static final long SHELL_TIMEOUT_MS = 3500L;

    private static String readAll(java.io.InputStream in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    /**
     * 以 shell 身份注册任务栈监听器，这是「零轮询」方案的事件源。
     *
     * 设备端探针已验证：同一台机器、以 shell 身份注册成功（返回 true），
     * 之后每次前台切换都会收到 ITaskStackListener 回调（实测 code=12 必达）。
     * 注意：不得在该事务里对隐藏类做反射，HyperOS 会直接杀掉进程。
     */
    public static boolean registerTaskStackListener(IBinder listener) {
        return taskListenerTransact(TRANSACTION_REGISTER_TASK_STACK_LISTENER, listener,
                "registerTaskStackListener");
    }

    /** 注销监听器；进程退出时系统也会自动清理，这一步只是让回收更及时。 */
    public static boolean unregisterTaskStackListener(IBinder listener) {
        return taskListenerTransact(TRANSACTION_UNREGISTER_TASK_STACK_LISTENER, listener,
                "unregisterTaskStackListener");
    }

    private static boolean taskListenerTransact(int code, IBinder listener, String tag) {
        if (!hasPermission()) return false;
        try {
            IBinder service = SystemServiceHelper.getSystemService(TASK_SERVICE);
            if (service == null) return false;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(TASK_DESCRIPTOR);
                data.writeStrongBinder(listener);
                boolean ok = new ShizukuBinderWrapper(service).transact(code, data, reply, 0);
                reply.readException();
                Logx.i(tag + " ok=" + ok);
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable t) {
            Logx.w(tag + " failed: " + t);
            return false;
        }
    }
}
