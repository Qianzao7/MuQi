package com.qtanim.systemui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 上滑解锁用的背景压暗层。
 *
 * <p>它覆盖「卡片底边以下」露出的壁纸，但顶边不是一条直线，而是跟着卡片的圆角底边走：
 * 左右两角<b>往上切</b>（卡片底角被削掉，压暗层在角上往上补到 B-r），中间保持 B。
 * 这样滑动时卡片底边左右两角就是 iOS 式的弧形圆角。
 *
 * <p>顶边形状（view 自身坐标，顶边 y=0 对应屏幕 B-r）：
 * <pre>
 *   (0,0) ──弧──▶ (r,r) ──直线──▶ (w-r,r) ──弧──▶ (w,0)
 *      ▲                                                  │
 *      └──────────── 向下填满到 h ─────────────────────────┘
 * </pre>
 * 即两角满高（y=0）、中间顶边沉到 y=r，整体是一个「角上凸起」的压暗区。
 */
final class DimOverlayView extends View {

    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cornerRadius;

    DimOverlayView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
    }

    void setCornerRadius(float radius) {
        if (cornerRadius != radius) {
            cornerRadius = radius;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float r = Math.min(cornerRadius, w / 2f);
        path.reset();
        if (r > 0.5f) {
            // 顶边 = 卡片圆角底边（左右两角往上切）。
            path.moveTo(0f, 0f);
            // 左上角弧：(0,0) → (r,r)，凸向左下（往角上补）。
            path.arcTo(new RectF(0f, -r, 2f * r, r), 180f, -90f);
            // 中间直线：(r,r) → (w-r,r)。
            path.lineTo(w - r, r);
            // 右上角弧：(w-r,r) → (w,0)，凸向右下。
            path.arcTo(new RectF(w - 2f * r, -r, w, r), 90f, -90f);
            // 右侧、底边、左侧闭合，向下填满。
            path.lineTo(w, h);
            path.lineTo(0f, h);
            path.close();
        } else {
            path.addRect(0f, 0f, w, h, Path.Direction.CW);
        }
        canvas.drawPath(path, paint);
    }
}
