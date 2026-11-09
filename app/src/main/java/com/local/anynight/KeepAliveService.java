package com.local.anynight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 常驻通知（前台服务）：HyperOS 对后台进程的冻结非常激进，一个带常驻通知的前台服务
 * 能显著降低被挂起的概率。通知本身用 IMPORTANCE_MIN，不响不亮不打扰。
 *
 * Android 14+ 要求前台服务声明类型，这里用 specialUse（无更贴切的类型），
 * 清单里通过 PROPERTY_SPECIAL_USE_FGS_SUBTYPE 说明用途。
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "keepalive";
    private static final int NOTIF_ID = 1001;

    private static final String PREFS = "cfg";
    private static final String KEY_FGS = "fgs";

    private static volatile boolean running;

    static boolean isRunning() {
        return running;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 用户是否希望常驻通知（默认开）。 */
    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_FGS, true);
    }

    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_FGS, enabled).apply();
    }

    static void start(Context c) {
        if (!isEnabled(c)) return;
        try {
            c.startForegroundService(new Intent(c, KeepAliveService.class));
        } catch (Throwable t) {
            Logx.e("start keepalive failed", t);
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logx.init(this);
        createChannel();
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
            running = true;
            Logx.i("keepalive started");
        } catch (Throwable t) {
            Logx.e("startForeground failed", t);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        Logx.i("keepalive stopped");
        super.onDestroy();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        // 用 LOW 而不是 MIN：MIUI 会把 MIN 级通知直接藏起来，
        // 前台服务等于没挂，进程照样被冻结。
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, I18n.t(this, "保活通知"), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(I18n.t(this, "保持深浅色切换服务在后台存活，不发出提示音"));
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_moon)
                .setContentTitle(I18n.t(this, "AnyNight · 运行中"))
                .setContentText(I18n.t(this, "进出所选应用自动切换，空闲不唤醒"))
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }
}
