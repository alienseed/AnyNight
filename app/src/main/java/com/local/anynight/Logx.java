package com.local.anynight;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 轻量日志：同时写 logcat 和 files/logs/app.log。
 *
 * 为什么自己写文件：HyperOS 上 logcat 随时会被清空，出问题时用户手里没有证据。
 * 写入是同步追加、单条不超过 200 字，频率是「每次切换一条」，开销可以忽略。
 * 单文件 256KB 上限，超出后轮转成 app.1.log（只留一份旧的）。
 */
final class Logx {

    static final String TAG = "AnyNight";

    private static final String DIR = "logs";
    private static final String CURRENT = "app.log";
    private static final String PREVIOUS = "app.1.log";
    private static final long MAX_BYTES = 256 * 1024;

    private static final Object LOCK = new Object();
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static File dir;
    private static boolean crashHooked;

    private Logx() {
    }

    static void init(Context c) {
        NightModeController.attach(c);
        synchronized (LOCK) {
            if (dir != null) return;
            File d = new File(c.getFilesDir(), DIR);
            if (!d.exists() && !d.mkdirs()) return;
            dir = d;
            if (!crashHooked) {
                crashHooked = true;
                installCrashHandler();
            }
        }
    }

    private static void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                write("E", "崩溃 " + t.getName() + ": " + stackOf(e));
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static void i(String msg) {
        Log.i(TAG, msg);
        write("I", msg);
    }

    static void d(String msg) {
        Log.d(TAG, msg);
        write("D", msg);
    }

    static void w(String msg) {
        Log.w(TAG, msg);
        write("W", msg);
    }

    static void e(String msg) {
        Log.e(TAG, msg);
        write("E", msg);
    }

    static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        write("E", msg + " :: " + stackOf(t));
    }

    private static String stackOf(Throwable t) {
        if (t == null) return "null";
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString().replace("\n", " | ").replace("\t", " ");
        return s.length() > 1200 ? s.substring(0, 1200) + "…" : s;
    }

    private static void write(String level, String msg) {
        File d;
        synchronized (LOCK) {
            d = dir;
        }
        if (d == null) return;
        String line = STAMP.format(new Date()) + " " + level + " " + msg + "\n";
        synchronized (LOCK) {
            try {
                File f = new File(d, CURRENT);
                if (f.length() > MAX_BYTES) {
                    File old = new File(d, PREVIOUS);
                    if (old.exists()) old.delete();
                    f.renameTo(old);
                    f = new File(d, CURRENT);
                }
                try (FileOutputStream out = new FileOutputStream(f, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- 导出 ----------------

    /**
     * 打包日志 + 设备信息，写入「下载/AnyNight」。
     *
     * MediaStore 自己插入的条目不需要任何存储权限（Android 10+），
     * 所以导出这一步不额外要权限。
     *
     * @return 成功时返回展示用的路径，失败抛异常
     */
    static String exportZip(Context c) throws Exception {
        File cache = new File(c.getCacheDir(), "export");
        if (!cache.exists() && !cache.mkdirs()) throw new IllegalStateException("无法创建临时目录");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String name = "anynight-log-" + stamp + ".zip";
        File zip = new File(cache, name);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            addFile(zos, new File(c.getFilesDir(), DIR + "/" + CURRENT), "app.log");
            addFile(zos, new File(c.getFilesDir(), DIR + "/" + PREVIOUS), "app.1.log");
            zos.putNextEntry(new ZipEntry("device.txt"));
            zos.write(deviceInfo(c).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        // 复制到 Download/AnyNight（走 MediaStore，无需权限）
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/AnyNight");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = c.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("无法写入下载目录");
            try (OutputStream out = c.getContentResolver().openOutputStream(uri);
                 FileInputStream in = new FileInputStream(zip)) {
                if (out == null) throw new IllegalStateException("无法打开下载目录");
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, v, null, null);
            zip.delete();
            i("日志已导出到 下载/AnyNight/" + name);
            return "下载/AnyNight/" + name;
        }
        throw new IllegalStateException("系统版本过低");
    }

    private static void addFile(ZipOutputStream zos, File f, String entry) throws Exception {
        if (!f.exists() || f.length() == 0) return;
        zos.putNextEntry(new ZipEntry(entry));
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    /** 出问题时最需要的上下文：版本、机型、当前是否深色、目标清单。 */
    private static String deviceInfo(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("应用版本: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" / SDK ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("机型: ").append(Build.BRAND).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("ROM: ").append(Build.DISPLAY).append("\n");
        sb.append("补丁: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        sb.append("导出时间: ").append(new Date().toString()).append("\n");
        try {
            sb.append(DeviceProfile.describe(c));
        } catch (Throwable ignored) {
        }
        sb.append("系统深色: ").append(NightModeController.isNightNow(c) ? "是" : "否").append("\n");
        try {
            sb.append("Shizuku 服务: ")
                    .append(NightModeController.isBinderAlive() ? "运行中" : "未运行").append("\n");
            sb.append("Shizuku 授权: ")
                    .append(NightModeController.hasPermission() ? "已授权" : "未授权").append("\n");
        } catch (Throwable ignored) {
        }
        try {
            sb.append("深色监听: ")
                    .append(TaskStackWatcher.isRegistered() ? "已注册" : "未注册").append("\n");
        } catch (Throwable ignored) {
        }
        sb.append("\n[目标应用]\n");
        try {
            sb.append(TargetApps.describe(c)).append("\n");
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }
}
