package com.local.anynight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查更新 / 下载安装包。
 *
 * 为什么不用 GitHub API：api.github.com 在国内经常连不上，而且有速率限制。
 * 这里用一个更土但更稳的办法——请求 releases/latest，让它自己 302 到
 * /releases/tag/vX.Y.Z，从跳转后的地址里读版本号；读不到再从页面正文里找。
 * 三条线路依次尝试：直连、gh-proxy、ghproxy.net（都是反向代理，国内可直连）。
 *
 * 下载同样按这三条线路依次回退，装包走自带的 ApkProvider（见 manifest）。
 */
final class Update {

    static final String REPO = "alienseed/AnyNight";

    /** 前缀为空表示直连 GitHub；其余是国内反向代理。 */
    private static final String[] PREFIXES = {
            "",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://ghfast.top/",
    };

    private static final int CONNECT_MS = 7000;
    private static final int READ_MS = 9000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Info {
        String tag;       // v1.9.4
        String version;   // 1.9.4
        String apkName;   // app-v1.9.4.apk
        String pageUrl;
    }

    interface CheckCallback {
        void done(Info info, String error);
    }

    interface DownloadCallback {
        void progress(int percent);
        void done(File apk, String error);
    }

    private Update() {
    }

    static String localVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /** 远端版本是否比本地新。只比较前三段数字。 */
    static boolean isNewer(String remote, String local) {
        int[] r = parse(remote);
        int[] l = parse(local);
        if (r == null || l == null) return false;
        for (int i = 0; i < 3; i++) {
            if (r[i] != l[i]) return r[i] > l[i];
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null) return null;
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(v);
        if (!m.find()) return null;
        try {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int c = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            return new int[]{a, b, c};
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- 检查 ----------------

    static void check(Context c, CheckCallback cb) {
        new Thread(() -> {
            String lastError = null;
            for (String prefix : PREFIXES) {
                try {
                    Info info = fetch(prefix);
                    if (info != null) {
                        final Info f = info;
                        MAIN.post(() -> cb.done(f, null));
                        return;
                    }
                } catch (Throwable t) {
                    lastError = t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage());
                }
            }
            final String err = lastError;
            MAIN.post(() -> cb.done(null, err));
        }, "update-check").start();
    }

    private static Info fetch(String prefix) throws IOException {
        String base = prefix + "https://github.com/" + REPO + "/releases/latest";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestProperty("User-Agent", "AnyNight/" + localVersion());
            conn.setRequestProperty("Accept", "text/html");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) throw new IOException("HTTP " + code);

            // ① 先看跳转后的地址（GitHub 会跳到 /releases/tag/vX）
            String tag = findTag(conn.getURL().toString());

            // ② 再从正文里找，代理有时直接回 200 并内联内容
            if (tag == null) {
                String body = readSome(conn, 300 * 1024);
                tag = findTag(body);
            }
            if (tag == null) throw new IOException("没找到版本号");

            Info info = new Info();
            info.tag = tag;
            info.version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            info.apkName = "app-" + tag + ".apk";
            info.pageUrl = "https://github.com/" + REPO + "/releases/tag/" + tag;
            return info;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String findTag(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("/tag/(v?\\d+(?:\\.\\d+){1,3})").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String readSome(HttpURLConnection conn, int max) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
                total += n;
                if (total >= max) break;
            }
            return sb.toString();
        }
    }

    // ---------------- 下载 ----------------

    static void download(Context c, Info info, DownloadCallback cb) {
        new Thread(() -> {
            File dir = new File(c.getCacheDir(), "apk");
            if (!dir.exists() && !dir.mkdirs()) {
                MAIN.post(() -> cb.done(null, "无法创建缓存目录"));
                return;
            }
            File out = new File(dir, info.apkName);
            File part = new File(dir, info.apkName + ".part");
            String rel = "releases/download/" + info.tag + "/" + info.apkName;

            String lastError = null;
            for (String prefix : PREFIXES) {
                try {
                    downloadTo(prefix + "https://github.com/" + REPO + "/" + rel,
                            part, cb);
                    if (part.length() < 100 * 1024) throw new IOException("文件过小");
                    if (out.exists() && !out.delete()) throw new IOException("无法覆盖旧文件");
                    if (!part.renameTo(out)) throw new IOException("无法写入文件");
                    File done = out;
                    MAIN.post(() -> cb.done(done, null));
                    return;
                } catch (Throwable t) {
                    lastError = t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage());
                    // 换下一条线路重来
                    part.delete();
                }
            }
            final String err = lastError;
            MAIN.post(() -> cb.done(null, err));
        }, "update-download").start();
    }

    private static void downloadTo(String url, File part, DownloadCallback cb)
            throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestProperty("User-Agent", "AnyNight/" + localVersion());
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) throw new IOException("HTTP " + code);

            long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[32 * 1024];
                long got = 0;
                int lastPct = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (total > 0) {
                        int pct = (int) (got * 100 / total);
                        if (pct / 5 != lastPct / 5) {
                            lastPct = pct;
                            final int p = pct;
                            MAIN.post(() -> cb.progress(p));
                        }
                    }
                }
                out.flush();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
