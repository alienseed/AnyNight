package com.local.anynight;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 开场叙事。全部用 Canvas 画，零图片资源。
 *
 * 设计稿坐标系固定 390 × 844（和 docs/intro.html 预览页一致），绘制时整体缩放到实际宽度，
 * 所以预览里量到的每个数字都能原样搬过来。
 *
 * 时间轴 t 单位秒，0 → 20.55：
 *   0.55–3.70   白天 沐浴光里 / 要的就是效率        ＋ 阅读小窗（浅底）
 *   3.74–4.62  底色转深
 *   4.74–8.20   而映像 要在暗处 / 方显作者本意      ＋ 相册小窗（深底）
 *   9.35–15.45  让每个 APP / 各归各的深浅（12.00 起第二行缓缓放大）
 *               ＋ 相机窗 9.35 / 聊天窗 9.80
 *   15.50–16.60 底色转回米白
 *   16.72–20.55 只留 AnyNight（17.75 起放大 7%，19.35 起淡到米白）
 *
 * 文字整句浮现；四个小窗跟着整幕一起柔和渐显（0.92s），渐显走完窗内动画才开始。
 */
class IntroView extends View {

    interface OnDone {
        /** skipped=true 表示用户点了跳过 */
        void onDone(boolean skipped);
    }

    /** 四扇小窗的绘制入口：本地坐标 (0,0)-(w,h)，tw 是窗口自己的时间 */
    private interface Panel {
        void draw(Canvas c, float w, float h, float tw);
    }

    // ---------- 设计稿 ----------
    static final float DW = 390f, DH = 844f;
    static final float DUR = 20.55f;

    // ---------- 只有两套色 ----------
    static final int LIGHT_BG = 0xFFF4F3EF;
    private static final int LIGHT_INK = 0xFF23232A, LIGHT_SUB = 0xFF7A7A72;
    private static final int DARK_BG = 0xFF121214, DARK_INK = 0xFFEDEAE4, DARK_SUB = 0xFF8D8B85;

    /**
     * 系统深色模式。只影响第四幕——前三幕是「白天 / 暗处」的对照，反相就词不达意了；
     * 但最后只剩名字这一幕要跟深色首页接得上，所以夜里它留在暗场，不再转回米白。
     */
    private final boolean night;

    /** 片子的末帧色：白天米白，夜间近黑。首页用它来接。 */
    private final int endBg;

    // ---------- 线宽与字号（全片统一） ----------
    private static final float LW = 2f;        // 结构线
    private static final float BW = 1.5f;      // 窗口外壳描边
    private static final float FS = 24f;       // 所有正文行
    private static final float LETTER = 1f;    // 字间距

    // ---------- 窗口位置：统一挪到屏幕下方的空白区，并在其中居中 ----------
    private static final float WIN_W = 152f, WIN_H = 250f;
    private static final float LOWER_TOP = 336f;    // 幕一/幕二：文字下方即空白区起点
    private static final float WIN_X = (DW - WIN_W) / 2f;                       // 单窗居中
    private static final float WIN1_Y = LOWER_TOP + (DH - LOWER_TOP - WIN_H) / 2f;
    private static final float A3_GAP = 18f;        // 幕三两窗横距
    private static final float A3_X = (DW - (WIN_W * 2f + A3_GAP)) / 2f;         // 幕三整体居中
    private static final float A3_STAGGER = 40f;    // 幕三两窗竖向错落
    private static final float A3_TEXT_BOT = 314f;
    private static final float A3_Y = A3_TEXT_BOT
            + (DH - A3_TEXT_BOT - (WIN_H + A3_STAGGER)) / 2f;
    private static final float A3_GROW_TO = 1.14f;  // 第三幕第二句放大的终点倍率

    // ---------- 时间轴 ----------
    private static final float A1A = 0.55f, A1B = 1.15f, A1OUT = 3.70f, A1WIN = 0.55f;
    private static final float BGD0 = 3.74f, BGD1 = 4.62f;
    private static final float A2A = 4.74f, A2B = 5.34f, A2OUT = 8.20f, A2WIN = 4.74f;
    private static final float A3A = 9.35f, A3B = 9.95f, A3OUT = 15.45f;
    private static final float A3W1 = 9.35f, A3W2 = 9.80f, A3GROW = 12.00f;
    private static final float BGL0 = 15.50f, BGL1 = 16.60f;
    private static final float A4 = 16.72f, A4GROW = 17.75f;
    private static final float OUT0 = 19.35f, OUT1 = 20.45f;

    // ---------- 开窗 ----------
    private static final float WIN_IN = 0.92f;  // 与整幕同步的柔渐显时长

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    private final OnDone done;
    private ValueAnimator animator;
    private boolean fired;

    /** 当前时间（秒） */
    private float t;

    /** 片子首帧与末帧是同一个颜色：白天米白，夜间近黑。首页要用它来接。 */
    static int endBg(Context c) {
        return isNight(c) ? DARK_BG : LIGHT_BG;
    }

    static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    IntroView(Context c, OnDone done) {
        super(c);
        this.done = done;
        setLayerType(LAYER_TYPE_HARDWARE, null);

        night = isNight(c);
        endBg = night ? DARK_BG : LIGHT_BG;
    }

    void start() {
        animator = ValueAnimator.ofFloat(0f, DUR);
        animator.setDuration((long) (DUR * 1000));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (!fired) finish(false);
            }
        });
        animator.start();
    }

    void stop() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }

    private void finish(boolean skipped) {
        if (fired) return;
        fired = true;
        stop();
        if (done != null) done.onDone(skipped);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            finish(true);
        }
        return true;
    }

    // ================= 绘制 =================

    /**
     * 屏幕适配：一律按 390:844 的构图等比缩放（contain），
     * 取宽、高两个方向里更小的那个比例 → 任何比例都不会裁掉文字或窗口。
     * 多出来的留白由「铺满全窗口的底色」兜住，因为全片底色本来就是纯色，看不出边界。
     *
     * 这样 16:9 直屏、20:9 长屏、21:9 带鱼屏、平板、折叠屏展开/合上都能直接跑，
     * 换形态时（configChanges 已接管）下一帧按新尺寸重算，不需要重建页面。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float s = Math.min(w / DW, h / DH);
        float ox = (w - DW * s) / 2f;
        float oy = (h - DH * s) / 2f;

        float bgP = bgProgress(t);
        int bgColor = mix(LIGHT_BG, DARK_BG, bgP);

        // 1) 全窗口底色（含等比缩放后的留白区，避免出现黑边）
        fillPaint(bgColor, 1f);
        canvas.drawRect(0, 0, w, h, p);

        // 2) 设计稿内容
        int save = canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(s, s);
        drawContent(canvas, t, bgP);
        canvas.restoreToCount(save);

        // 3) 收尾：整屏淡到米白，同样铺满窗口，边缘不能露底
        if (t > OUT0) {
            fillPaint(endBg, easeIO(seg(t, OUT0, OUT1)));
            canvas.drawRect(0, 0, w, h, p);
        }
    }

    /**
     * 浅 → 深 → 浅 的底色进度，0 米白 / 1 近黑。
     *
     * 夜间只有最后一处不同：15.50–16.60 那次「转回米白」不发生，
     * 于是第四幕留在暗场，深底浅字，末帧也能直接接上深色首页。
     * 前三幕（白天 → 暗处）两边完全一样——那是片子的意思所在。
     */
    private float bgProgress(float t) {
        if (t >= BGD0 && t < BGD1) return easeIO(seg(t, BGD0, BGD1));
        // 夜间：转入暗场之后就一直暗着（含第四幕），不再回米白
        if (night) return t >= BGD1 ? 1f : 0f;
        if (t >= BGD1 && t < BGL0) return 1f;
        if (t >= BGL0 && t < BGL1) return 1f - easeIO(seg(t, BGL0, BGL1));
        return 0f;
    }

    private void drawContent(Canvas c, float t, float bgP) {
        int ink = mix(LIGHT_INK, DARK_INK, bgP);
        int sub = mix(LIGHT_SUB, DARK_SUB, bgP);

        // ===== 第一幕：光 + 阅读窗 =====
        if (t < A1OUT + 0.5f) {
            float a = 1f - easeIn(seg(t, A1OUT, A1OUT + 0.5f));
            // 第一句：与整幕（窗口）同步渐显 —— 同起点、同时长、同曲线
            drawLine(c, "白天 沐浴光里", DW / 2, 268, FS, ink, t, A1A, a, true, WIN_IN);
            // 第二句：0.75s 渐显
            drawLine(c, "要的就是效率", DW / 2, 320, FS, sub, t, A1B, a, false, 0.75f);
        }
        if (t >= A1WIN && t < A1OUT + 0.5f) {
            float alpha = winA(t, A1WIN) * (1f - easeIn(seg(t, A1OUT + 0.05f, A1OUT + 0.45f)));
            if (alpha > 0.003f) {
                drawCard(c, WIN_X, WIN1_Y, WIN_W, WIN_H, alpha, winS(t, A1WIN), -0.045f,
                        LIGHT_BG, 0xFFE1DFD6, winTw(t, A1WIN), 0f, this::panelReading);
            }
        }

        // ===== 第二幕：暗 + 相册窗 =====
        if (t > A2A - 0.3f && t < A2OUT + 0.5f) {
            float a = t < A2OUT ? 1f : 1f - easeIn(seg(t, A2OUT, A2OUT + 0.5f));
            drawLine(c, "而映像 要在暗处", DW / 2, 268, FS, ink, t, A2A, a, true, WIN_IN);
            drawLine(c, "方显作者本意", DW / 2, 320, FS, sub, t, A2B, a, false, 0.75f);
        }
        if (t >= A2WIN && t < A2OUT + 0.5f) {
            float alpha = winA(t, A2WIN) * (1f - easeIn(seg(t, A2OUT + 0.05f, A2OUT + 0.45f)));
            if (alpha > 0.003f) {
                drawCard(c, WIN_X, WIN1_Y, WIN_W, WIN_H, alpha, winS(t, A2WIN), 0.045f,
                        0xFF101014, 0xFF2E2E38, winTw(t, A2WIN), 1.2f, this::panelAlbum);
            }
        }

        // ===== 第三幕：各归各位（第二行缓缓放大）+ 相机 / 聊天 =====
        if (t > A3A - 0.3f && t < A3OUT + 0.5f) {
            float a = t < A3OUT ? 1f : 1f - easeIn(seg(t, A3OUT, A3OUT + 0.5f));
            drawLine(c, "让每个 APP", DW / 2, 252, FS, ink, t, A3A, a, true, WIN_IN);

            // 第二句：像第四幕一样，静静放大（速率不变，终点更大）
            float g3 = clamp01((t - A3GROW) / (A3OUT - A3GROW));
            float s3 = lerp(1f, A3_GROW_TO, easeIO(g3));
            int save = c.save();
            c.translate(DW / 2, 304);
            c.scale(s3, s3);
            c.translate(-DW / 2, -304);
            drawLine(c, "各归各的深浅", DW / 2, 304, FS, ink, t, A3B, a, true, 0.75f);
            c.restoreToCount(save);
        }
        if (t >= A3W1 && t < A3OUT + 0.5f) {
            float alpha = winA(t, A3W1) * (1f - easeIn(seg(t, A3OUT + 0.05f, A3OUT + 0.45f)));
            if (alpha > 0.003f) {
                drawCard(c, A3_X, A3_Y, WIN_W, WIN_H, alpha, winS(t, A3W1), -0.045f,
                        0xFF101014, 0xFF2E2E38, winTw(t, A3W1), 2.4f, this::panelCamera);
            }
        }
        if (t >= A3W2 && t < A3OUT + 0.5f) {
            float alpha = winA(t, A3W2) * (1f - easeIn(seg(t, A3OUT + 0.05f, A3OUT + 0.45f)));
            if (alpha > 0.003f) {
                drawCard(c, A3_X + WIN_W + A3_GAP, A3_Y + A3_STAGGER, WIN_W, WIN_H,
                        alpha, winS(t, A3W2), 0.045f,
                        LIGHT_BG, 0xFFE1DFD6, winTw(t, A3W2), 3.6f, this::panelChat);
            }
        }

        // ===== 第四幕：只留名字（出现后缓缓放大，再淡出） =====
        if (t > A4 - 0.3f) {
            float app = clamp01((t - A4) / 0.9f);
            float grow = clamp01((t - A4GROW) / 1.5f);
            float alpha = easeOut(app) * (1f - seg(t, OUT0, OUT0 + 0.5f));
            if (alpha > 0.003f) {
                float s = lerp(0.94f, 1f, easeOut(app)) * lerp(1f, 1.07f, easeIO(grow));
                int save = c.save();
                c.translate(DW / 2, 424);
                c.scale(s, s);
                c.translate(-DW / 2, -424);
                tp.setTypeface(medium);
                tp.setTextSize(40f);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setColor(ink);
                tp.setAlpha((int) (clamp01(alpha) * 255));
                c.drawText("AnyNight", DW / 2, 424, tp);
                tp.setAlpha(255);
                c.restoreToCount(save);
            }
        }
    }

    // ================= 小窗外壳 =================

    private void drawCard(Canvas c, float x, float y, float w, float h,
                          float alpha, float sc, float tilt, int bg, int border,
                          float tw, float phase, Panel panel) {
        float bob = (float) Math.sin(tw * 0.8f + phase) * 2.0f;            // 轻微浮动
        float tiltJ = (float) Math.sin(tw * 0.55f + phase * 1.7f) * 0.007f; // 轻微摆动

        int base = c.save();
        if (alpha < 0.999f) c.saveLayerAlpha(null, (int) (clamp01(alpha) * 255));

        c.translate(x + w / 2f, y + h / 2f + bob);
        c.rotate((float) Math.toDegrees(tilt + tiltJ));
        c.scale(sc, sc);
        c.translate(-w / 2f, -h / 2f);

        // 投影：三层外扩的低透明度色块，模拟高斯，比真阴影便宜
        for (int i = 3; i >= 1; i--) {
            float e = i * 1.6f;
            rrect(c, -e, 4f + e, w + e, h + 4f + e, 24f, 0xFF000000, 0.045f);
        }

        // 窗口本体
        rrect(c, 0, 0, w, h, 24f, bg, 1f);

        // 内容（裁到圆角内）
        int inner = c.save();
        c.clipPath(roundPath(w, h, 24f));
        panel.draw(c, w, h, tw);
        c.restoreToCount(inner);

        // 极淡外壳描边（内贴，不外溢）
        stroke(c, BW / 2f, BW / 2f, w - BW / 2f, h - BW / 2f,
                24f - BW / 2f, border, BW, 1f);
        c.restoreToCount(base);
    }

    private Path roundPath(float w, float h, float rad) {
        path.reset();
        rf.set(0, 0, w, h);
        path.addRoundRect(rf, rad, rad, Path.Direction.CW);
        return path;
    }

    /** 窗口入场：跟着整幕一起柔和渐显，不做弹出回弹 */
    private static float winA(float t, float t0) {
        return easeIO(clamp01((t - t0) / WIN_IN));
    }

    private static float winS(float t, float t0) {
        return lerp(0.968f, 1f, easeOut(clamp01((t - t0) / WIN_IN)));
    }

    /** 窗内动画的起始时间：渐显走完之后，窗里才开始动 */
    private static float winTw(float t, float t0) {
        return Math.max(0f, t - t0 - WIN_IN);
    }

    // ================= 窗② 阅读 =================

    private void panelReading(Canvas c, float w, float h, float tw) {
        fill(c, 0, 0, w, h, LIGHT_BG, 1f);
        float pad = 11f;
        bar(c, pad, pad, 54f, 6f, 0xFF4A4A51, 1f);

        // 一条流：文字 → 表格 → 文字，整列 26px/s 一起上滑
        int[] shades = {0xFFC7C5BC, 0xFFB3B1A8, 0xFFD4D2C9, 0xFF9E9C94};
        Rnd rnd = new Rnd(4242);
        float[] w1 = new float[4], w2 = new float[5];
        for (int i = 0; i < 4; i++) w1[i] = 0.40f + rnd.next() * 0.48f;
        for (int i = 0; i < 5; i++) w2[i] = 0.34f + rnd.next() * 0.52f;

        final float GAP = 15f, LH = 6f, TABLE_H = 58f;
        final int N = 10;
        float[] yAt = new float[N], hAt = new float[N];
        float total = 0f;
        for (int i = 0; i < N; i++) {
            boolean isTable = (i == 4);
            float hh = isTable ? TABLE_H : LH;
            yAt[i] = total;
            hAt[i] = hh;
            total += hh + (isTable ? GAP * 2f : GAP);
        }

        float top = pad + 22f;
        float scroll = (tw * 26f) % total;
        int save = c.save();
        c.clipRect(0, top, w, h);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < N; i++) {
                float y = top - scroll + yAt[i] + pass * total;
                if (y > h || y + hAt[i] < top) continue;
                if (i < 4) {
                    bar(c, pad, y, (w - pad * 2f) * w1[i], LH, shades[i % 4], 0.92f);
                } else if (i == 4) {
                    drawTable(c, pad, y, w - pad * 2f, TABLE_H);
                } else {
                    int k = i - 5;
                    bar(c, pad, y, (w - pad * 2f) * w2[k], LH, shades[(k + 2) % 4], 0.92f);
                }
            }
        }
        c.restoreToCount(save);
    }

    /** 2×2 的浅色表格，格内只放很短的灰条，留白充足 */
    private void drawTable(Canvas c, float x, float y, float w, float h) {
        final int rows = 2, cols = 2;
        float rh = h / rows, cw = (w - LW * 2f) / cols;

        rrect(c, x, y, x + w, y + h, 8f, 0xFFEDEBE3, 1f);
        stroke(c, x + LW / 2f, y + LW / 2f, x + w - LW / 2f, y + h - LW / 2f,
                7f, 0xFFD9D7CE, LW, 1f);

        strokePaint(0xFFD9D7CE, LW, 1f);
        for (int r = 1; r < rows; r++) {
            float yy = y + r * rh;
            c.drawLine(x + LW / 2f, yy, x + w - LW / 2f, yy, p);
        }
        for (int col = 1; col < cols; col++) {
            float xx = x + LW / 2f + col * cw;
            c.drawLine(xx, y + rh, xx, y + h - LW / 2f, p);
        }

        Rnd rnd = new Rnd(99);
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                bar(c, x + LW / 2f + col * cw + 8f, y + r * rh + rh / 2f - 2f,
                        cw * (0.22f + rnd.next() * 0.20f), 4f,
                        r == 0 ? 0xFFB6B4AB : 0xFFCAC8BF, 1f);
            }
        }
    }

    // ================= 窗① 相册 =================

    private void panelAlbum(Canvas c, float w, float h, float tw) {
        fill(c, 0, 0, w, h, 0xFF101014, 1f);
        float pad = 11f;
        // 顶栏：一个圆点 + 一行文字（只有一行）
        fillPaint(0xFF4A4A56, 1f);
        c.drawCircle(pad + 7f, pad + 9f, 4f, p);
        bar(c, pad + 18f, pad + 6f, 26f, 5f, 0xFF2E2E38, 1f);

        float top = pad + 22f;
        final float gapX = 12f, gapY = 14f;
        final float colW = (w - pad * 2f - gapX) / 2f;
        final float boxH = colW * 1.22f;
        final int n = 6;
        // 每列一个固定偏移：列内严格对齐，只让两列之间轻微错开
        float[] dx = {0f, -4f}, dy = {0f, 11f};
        float total = n * (boxH + gapY);

        for (int col = 0; col < 2; col++) {
            float x = pad + col * (colW + gapX) + dx[col];
            float off = (tw * 24f) % total;
            int save = c.save();
            c.clipRect(0, top - 4f, w, h + 2f);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < n; i++) {
                    float y = top - off + i * (boxH + gapY) + pass * total + dy[col];
                    if (y > h || y + boxH < top - 4f) continue;
                    rrect(c, x, y, x + colW, y + boxH, 9f, 0xFF1A1A21, 1f);
                    glyphScene(c, x + colW / 2f, y + boxH * 0.48f,
                            Math.min(colW, boxH) * 0.62f,
                            0xFF3B3B47, 0xFF2D2D37, 0xFF494957);
                }
            }
            c.restoreToCount(save);
        }
    }

    // ================= 窗③ 相机 =================

    private void panelCamera(Canvas c, float w, float h, float tw) {
        fill(c, 0, 0, w, h, 0xFF101014, 1f);
        final float cycle = 3.4f;
        float ph = (tw % cycle) / cycle;
        final float pad = 12f;
        final float vx = pad, vy = pad, vw = w - pad * 2f, vh = h * 0.58f;

        // 取景窗：拍之前就是一块空底
        rrect(c, vx, vy, vx + vw, vy + vh, 16f, 0xFF17171D, 1f);
        stroke(c, vx + LW / 2f, vy + LW / 2f, vx + vw - LW / 2f, vy + vh - LW / 2f,
                15f, 0xFF33333E, LW, 1f);

        // 快门：只留一个圆色块，按下时收缩变淡
        float press = clamp01((ph - 0.30f) / 0.10f) * clamp01(1f - (ph - 0.40f) / 0.12f);
        float r = w * 0.115f, scx = w / 2f, scy = vy + vh + w * 0.30f;
        fillPaint(0xFFEDEAE4, 1f - press * 0.55f);
        c.drawCircle(scx, scy, r * (1f - press * 0.24f), p);

        // 照片浮起：拍完之后景色才出现
        float pk = clamp01((ph - 0.42f) / 0.42f);
        if (pk > 0.001f) {
            float pw = w * 0.58f, phh = pw * 1.16f;
            float px = w / 2f - pw / 2f;
            float py = vy + vh / 2f - phh / 2f + (1f - easeOut(pk)) * 60f;

            int save = c.save();
            if (pk < 0.999f) c.saveLayerAlpha(null, (int) (clamp01(pk) * 255));
            // 照片的投影，让它明确浮在取景窗之上
            for (int i = 3; i >= 1; i--) {
                float e = i * 1.6f;
                rrect(c, px - e, py + 3f + e, px + pw + e, py + phh + 3f + e, 12f, 0xFF000000, 0.055f);
            }
            rrect(c, px, py, px + pw, py + phh, 12f, 0xFF20202A, 1f);
            glyphScene(c, px + pw / 2f, py + phh / 2f, Math.min(pw, phh) * 0.62f,
                    0xFF56566A, 0xFF45455A, 0xFF6C6C80);
            c.restoreToCount(save);
        }

        // 闪光：只闪白，不加星芒
        float flash = clamp01((ph - 0.31f) / 0.05f) * clamp01(1f - (ph - 0.42f) / 0.22f);
        if (flash > 0.002f) {
            // 中心纯白向外透明；整体强度只用 paint alpha 控制，避免二次相乘变暗
            RadialGradient g = new RadialGradient(w / 2f, vy + vh / 2f, w * 0.86f,
                    new int[]{0xFFFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            p.setShader(g);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha((int) (clamp01(flash) * 235));   // 中心约 92% 白
            c.drawRect(0, 0, w, h, p);
            p.setAlpha(255);
            p.setShader(null);
        }
    }

    // ================= 窗④ 聊天 =================

    private void panelChat(Canvas c, float w, float h, float tw) {
        fill(c, 0, 0, w, h, LIGHT_BG, 1f);
        final float cycle = 5.0f;
        float ph = (tw % cycle) / cycle;
        final float pad = 12f;

        // 三个气泡：左、右、左，依次出现，字条逐条"打"出来
        int[] sides = {0, 1, 0};
        float[] at = {0.04f, 0.30f, 0.56f};
        float[] bwR = {0.70f, 0.58f, 0.64f};
        int[] lines = {2, 1, 1};
        float[][] lineW = {{0.60f, 0.36f}, {0.52f, 0f}, {0.46f, 0f}};

        float y = pad + 2f;
        for (int i = 0; i < sides.length; i++) {
            float k = easeOut(clamp01((ph - at[i]) / 0.16f));
            float bh = 16f + lines[i] * 15f;
            if (k <= 0.001f) { y += bh + 14f; continue; }

            float bw = (w - pad * 2f) * bwR[i];
            float x = sides[i] == 0 ? pad : w - pad - bw;
            int fillColor = sides[i] == 0 ? 0xFFFFFFFF : 0xFFDEDCD3;

            int save = c.save();
            if (k < 0.999f) c.saveLayerAlpha(null, (int) (k * 255));
            c.translate(0, (1f - k) * 10f);

            rrect(c, x, y, x + bw, y + bh, 14f, fillColor, 1f);

            // 小尾巴
            path.reset();
            if (sides[i] == 0) {
                path.moveTo(x + 8f, y + bh - 1f);
                path.lineTo(x + 1f, y + bh + 10f);
                path.lineTo(x + 22f, y + bh - 1f);
            } else {
                path.moveTo(x + bw - 8f, y + bh - 1f);
                path.lineTo(x + bw - 1f, y + bh + 10f);
                path.lineTo(x + bw - 22f, y + bh - 1f);
            }
            path.close();
            fillPaint(fillColor, 1f);
            c.drawPath(path, p);

            // 气泡里的字
            for (int j = 0; j < lines[i]; j++) {
                float grow = easeOut(clamp01((ph - at[i] - 0.12f - j * 0.08f) / 0.24f));
                bar(c, x + 12f, y + 9f + j * 15f, (bw - 24f) * lineW[i][j] * grow, 6f,
                        0xFF3A3A42, 0.85f);
            }
            c.restoreToCount(save);
            y += bh + 14f;
        }

        // 键盘：不要底板，九宫格 = 9 个圆角矩形键，逐个点亮
        final float keyH = 13f, gap = 8f;
        float keyW = (w - pad * 2f - gap * 2f) / 3f;
        float ky = h - (keyH * 3f + gap * 2f) - 10f;
        int keyIdx = (int) (ph * 9f) % 9;
        for (int r2 = 0; r2 < 3; r2++) {
            for (int col = 0; col < 3; col++) {
                int idx = r2 * 3 + col;
                float kx = pad + col * (keyW + gap);
                float kyy = ky + r2 * (keyH + gap);
                rrect(c, kx, kyy, kx + keyW, kyy + keyH, 5f,
                        idx == keyIdx ? 0xFF8E8C84 : 0xFFE4E2DA, 1f);
            }
        }
    }

    // ================= 风景母题 =================

    /**
     * 圆角山峦：两侧直坡 + 顶部小圆角，峰顶绝不是尖角。
     * 和预览页 hillPath() 同一个算法（张角 0.8 弧度）。
     */
    private void hillPath(float x0, float x1, float yBase, float yTop, float r) {
        float px = (x0 + x1) / 2f;
        float cy = yTop + r;
        final float th = 0.8f;
        float sx = px - r * (float) Math.sin(th), sy = cy - r * (float) Math.cos(th);
        path.reset();
        path.moveTo(x0, yBase);
        path.lineTo(sx, sy);
        rf.set(px - r, cy - r, px + r, cy + r);
        path.arcTo(rf, (float) Math.toDegrees(1.5 * Math.PI - th),
                (float) Math.toDegrees(2 * th), false);
        path.lineTo(x1, yBase);
        path.close();
    }

    /** 抽象风景：两道圆角山峦 + 一轮太阳（纯色块，无描边） */
    private void glyphScene(Canvas c, float cx, float cy, float s,
                            int cFront, int cBack, int cSun) {
        fillPaint(cSun, 1f);
        c.drawCircle(cx + s * 0.26f, cy - s * 0.31f, s * 0.095f, p);

        hillPath(cx - s * 0.52f, cx - s * 0.02f, cy + s * 0.30f, cy - s * 0.26f, s * 0.17f);
        fillPaint(cBack, 1f);
        c.drawPath(path, p);

        hillPath(cx - s * 0.22f, cx + s * 0.50f, cy + s * 0.30f, cy - s * 0.12f, s * 0.15f);
        fillPaint(cFront, 1f);
        c.drawPath(path, p);
    }

    // ================= 文字 =================

    /**
     * 整句淡入 + 轻微上浮：同一句共用一个 alpha，句子是一次性浮现的。
     * dur = 渐显时长；第一句传 WIN_IN（与整幕同步），第二句传 0.75。
     */
    private void drawLine(Canvas c, String str, float cx, float cy, float size,
                          int color, float t, float t0, float alphaOut, boolean mediumWeight,
                          float dur) {
        float k = clamp01((t - t0) / dur);
        if (k <= 0.0005f || alphaOut <= 0.001f) return;
        float e = easeIO(k);

        tp.setTypeface(mediumWeight ? medium : regular);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(color);

        int n = str.length();
        float[] wIds = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            wIds[i] = tp.measureText(str, i, i + 1);
            total += wIds[i] + LETTER;
        }
        total -= LETTER;

        float x = cx - total / 2f;
        float y = cy + (1f - e) * 12f;
        tp.setAlpha((int) (e * clamp01(alphaOut) * 255));
        for (int i = 0; i < n; i++) {
            c.drawText(str, i, i + 1, x, y, tp);
            x += wIds[i] + LETTER;
        }
        tp.setAlpha(255);
    }

    // ================= 画笔与基元 =================

    private void fillPaint(int color, float alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(color);
        p.setAlpha((int) (clamp01(alpha) * 255));
    }

    private void strokePaint(int color, float width, float alpha) {
        p.setStyle(Paint.Style.STROKE);
        p.setShader(null);
        p.setStrokeWidth(width);
        p.setColor(color);
        p.setAlpha((int) (clamp01(alpha) * 255));
    }

    private void fill(Canvas c, float l, float top, float r, float b, int color, float alpha) {
        fillPaint(color, alpha);
        c.drawRect(l, top, r, b, p);
    }

    private void rrect(Canvas c, float l, float top, float r, float b, float rad,
                       int color, float alpha) {
        fillPaint(color, alpha);
        rf.set(l, top, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    private void stroke(Canvas c, float l, float top, float r, float b, float rad,
                        int color, float width, float alpha) {
        strokePaint(color, width, alpha);
        rf.set(l, top, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    /** 内容条：圆角小色块（取代文字） */
    private void bar(Canvas c, float x, float y, float w, float h, int color, float alpha) {
        if (w <= 0.6f || h <= 0.6f) return;
        rrect(c, x, y, x + w, y + h, Math.min(h / 2f, 5f), color, alpha);
    }

    /** 和预览页同一个随机数发生器，保证相册/表格的排布逐帧稳定 */
    private static final class Rnd {
        private int s;

        Rnd(int seed) {
            s = seed;
        }

        float next() {
            int t = s += 0x6D2B79F5;
            t = (t ^ (t >>> 15)) * (1 | t);
            t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
            long u = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
            return u / 4294967296f;
        }
    }

    // ================= 缓动 =================

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float lerp(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static float easeOut(float x) {
        float i = 1 - x;
        return 1 - i * i * i;
    }

    private static float easeIn(float x) {
        return x * x * x;
    }

    private static float easeIO(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float seg(float t, float a, float b) {
        return clamp01((t - a) / (b - a));
    }

    private static int mix(int c1, int c2, float p) {
        int a1 = (c1 >>> 24), r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24), r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * p);
        int r = (int) (r1 + (r2 - r1) * p);
        int g = (int) (g1 + (g2 - g1) * p);
        int b = (int) (b1 + (b2 - b1) * p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
