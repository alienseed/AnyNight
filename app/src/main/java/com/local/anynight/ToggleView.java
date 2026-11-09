package com.local.anynight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 自绘开关。
 *
 * 不用系统 Switch 的原因：MIUI 会按自己的主题重绘开关，
 * 深色强调色下缩成两个黑圆点，完全看不出开还是关。
 * 这里自己画，两个主题下都能保证「开=绿轨白点、关=灰轨白点」的辨识度。
 */
class ToggleView extends View {

    interface OnCheckedChange {
        void onChanged(boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean checked;
    private float fraction;
    private ValueAnimator animator;

    private int trackOn;
    private int trackOff;

    private OnCheckedChange listener;

    ToggleView(Context c) {
        super(c);
        init();
    }

    ToggleView(Context c, AttributeSet attrs) {
        super(c, attrs);
        init();
    }

    private void init() {
        trackOn = getContext().getColor(R.color.success);
        trackOff = getContext().getColor(R.color.card_stroke);
        setClickable(true);
        setFocusable(true);
        fraction = checked ? 1f : 0f;
    }

    void setOnCheckedChange(OnCheckedChange l) {
        listener = l;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean value) {
        setChecked(value, true);
    }

    void setChecked(boolean value, boolean animate) {
        if (checked == value) return;
        checked = value;
        if (animate) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(fraction, value ? 1f : 0f);
            animator.setDuration(150);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                fraction = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            fraction = value ? 1f : 0f;
            invalidate();
        }
        setContentDescription(value ? "已开启" : "已关闭");
    }

    @Override
    public boolean performClick() {
        setChecked(!checked);
        if (listener != null) listener.onChanged(checked);
        return super.performClick();
    }

    private int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension(Math.round(46 * d), Math.round(27 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;
        float inset = dp(3);
        float knobRadius = radius - inset;

        paint.setColor(blend(trackOff, trackOn, fraction));
        canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);

        // 关态轨道偏浅，给滑块加一点阴影感的描边，避免糊在一起
        if (fraction < 0.5f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(blend(trackOff, 0xFF000000, 0.12f));
            canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f),
                    radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float minX = inset + knobRadius;
        float maxX = w - inset - knobRadius;
        float cx = minX + (maxX - minX) * fraction;

        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, h / 2f, knobRadius, paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
