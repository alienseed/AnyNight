package com.local.anynight;

import android.app.Activity;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * 「请我喝瓶大水」：展示内置的微信收款码。
 *
 * 收款码直接打包在资源里（drawable-nodpi/dasheng_qr.jpg），
 * 因此不再需要「照片和视频」权限，也不会跟着相册最新一张图漂移。
 * 换码时替换这个资源文件并重新编译即可。
 */
public class TipActivity extends Activity {

    private Bitmap bitmap;
    private LinearLayout card;
    private int screenWidth;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        loadQr();
        buildUi();
        applySystemBars();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        applySystemBars();
    }

    /** 解码内置收款码；失败时置空，界面会给出提示而不是空白。 */
    private void loadQr() {
        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dasheng_qr, opt);
        } catch (Throwable t) {
            bitmap = null;
        }
    }

    // ---------------- 基础工具 ----------------

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int color(int res) {
        return getColor(res);
    }

    private static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private GradientDrawable shape(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private String tr(String s) {
        return I18n.t(this, s);
    }

    private TextView text(String s, float sizeSp, int col, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(col);
        if (bold) t.setTypeface(medium());
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // ---------------- 界面 ----------------

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xD9040508);
        page.setPadding(dp(20), dp(24), dp(20), dp(24));
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(20), dp(20) + bars.top, dp(20), dp(20) + bars.bottom);
            return insets;
        });

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(14));
        card.setBackground(shape(color(R.color.card), 26));

        TextView title = text(tr("请我喝瓶大水"), 18, color(R.color.text_primary), true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.01f);
        card.addView(title, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = text(tr("微信扫码，长按图片可保存到相册"), 13,
                color(R.color.text_secondary), false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(7);
        card.addView(sub, subLp);

        if (bitmap == null) {
            TextView fail = text(tr("收款码加载失败，请重新安装应用。"), 12.5f,
                    color(R.color.warn), false);
            fail.setGravity(Gravity.CENTER);
            fail.setPadding(0, dp(34), 0, dp(34));
            card.addView(fail, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            addQrArea();
        }

        page.addView(card, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    private void addQrArea() {
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackground(shape(Color.WHITE, 16));
        image.setClipToOutline(true);
        // 收款码是纯白底，圆角裁一点白边没有信息损失，也避免直角生硬
        image.setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.58f));
        image.setOnLongClickListener(v -> {
            saveToGallery();
            return true;
        });

        LinearLayout.LayoutParams imgLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        imgLp.gravity = Gravity.CENTER_HORIZONTAL;
        imgLp.topMargin = dp(14);

        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);
        frame.addView(image, new LinearLayout.LayoutParams(
                Math.min(screenWidth - dp(76), dp(320)),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(frame, imgLp);

        // 顶部那句已经写了「长按图片可保存到相册」，这里不再重复
        TextView terms = text(tr("此为捐献性质，金额随意，无法退费，非常感谢您的捐献！"
                        + "本软件为作者失业在家开发，捐献不构成软件盈利，软件仍为免费使用全部功能。"),
                11.5f, color(R.color.text_tertiary), false);
        terms.setGravity(Gravity.CENTER);
        terms.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams termsLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        termsLp.topMargin = dp(6);
        card.addView(terms, termsLp);

        TextView close = text(tr("关闭"), 13, color(R.color.text_secondary), true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(20), dp(9), dp(20), dp(9));
        close.setBackground(shape(color(R.color.chip_bg), 16));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        closeLp.gravity = Gravity.CENTER_HORIZONTAL;
        closeLp.topMargin = dp(13);
        card.addView(close, closeLp);
    }

    /** 长按保存：写入 Pictures/AnyNight，MediaStore 自身写入不需要任何权限。 */
    private void saveToGallery() {
        if (bitmap == null) return;
        try {
            String name = "dasheng_" + System.currentTimeMillis() + ".png";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/AnyNight");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("insert failed");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("open failed");
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);

            Toast.makeText(this, tr("已保存到相册 Pictures/AnyNight"),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, tr("保存失败：") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- 系统栏 ----------------

    private void applySystemBars() {
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        try {
            w.setStatusBarColor(0xD9040508);
            w.setNavigationBarColor(0xD9040508);
        } catch (Throwable ignored) {
        }
        try {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                // 背景是深色遮罩，图标始终用浅色
                c.setSystemBarsAppearance(0, mask);
            }
        } catch (Throwable ignored) {
        }
    }
}
