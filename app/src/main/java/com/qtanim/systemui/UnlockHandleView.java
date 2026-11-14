package com.qtanim.systemui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * 锁屏底部的小白条手势区（iOS 上滑解锁的触控入口）。
 *
 * <p>它是一个<b>只覆盖底部中央小白条那一小块</b>的视图，宽 = 小白条宽度 + 少量左右余量、
 * 高约 44dp。只有落在这一小块区域内的触摸才归本模块接管；屏幕其它位置（通知、左下/右下
 * 快捷应用、时钟卡片）完全不受影响。
 *
 * <p>小白条本体固定在视图底部（iOS 首页指示条风格），上滑时只做轻微淡出，不随手指乱跑，
 * 因此不会再出现「滑到哪卡在哪」的问题。进度换算与上抛速度检测都在这里完成，回调给
 * {@link Callback}，真正的联动逻辑在 {@link UnlockController}。
 */
final class UnlockHandleView extends View {

    interface Callback {
        /** 手势进度变化，progress 0 = 原位，1 = 拉满（可解锁）。 */
        void onDrag(float progress);

        /**
         * 松手：progress 为松手瞬间进度，velocityY 为松手瞬间竖直速度（物理像素/秒，负值=上滑），
         * shouldUnlock = 是否应解锁（进度达标，或上滑速度达到 fling 阈值）。
         */
        void onRelease(float progress, float velocityY, boolean shouldUnlock);
    }

    /** 小白条高度（dp），只影响观感。 */
    private static final float BAR_HEIGHT_DP = 5f;
    /** 小白条距视图底边的高度（dp）。 */
    private static final float BAR_BOTTOM_MARGIN_DP = 7.5f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Callback callback;

    private float downY;
    private float lastProgress;
    /** 拉满需要的上滑距离（物理像素），由控制器按屏幕高度设定。 */
    private float fullDragPx;
    /** 小白条宽度（dp），可配置。 */
    private float barWidthDp = 130f;
    /** 解锁触发阈值：progress 到该值松手即解锁。 */
    private float unlockThreshold = 0.5f;
    /** 上滑速度阈值（物理像素/秒）：松手瞬间上滑速度超过该值即解锁。 */
    private float flingVelocityThreshold = 1200f;
    /** fling 解锁的最小进度：避免纯点击/误触也触发解锁。 */
    private float minFlingProgress = 0.08f;
    /** 手势是否活跃（DOWN 之后、UP/CANCEL 之前）。 */
    private boolean active;
    /** 速度采样器。 */
    private VelocityTracker velocityTracker;

    UnlockHandleView(Context context) {
        super(context);
        barPaint.setColor(0xE6FFFFFF);
        setWillNotDraw(false);
    }

    void setCallback(Callback cb) {
        this.callback = cb;
    }

    void setFullDragPx(float px) {
        this.fullDragPx = Math.max(1f, px);
    }

    void setBarWidthDp(float dp) {
        this.barWidthDp = Math.max(40f, Math.min(300f, dp));
        SysUiLog.log("handle", "setBarWidthDp raw=" + dp + " -> barWidthDp=" + this.barWidthDp);
        invalidate();
    }

    void setUnlockThreshold(float t) {
        this.unlockThreshold = Math.max(0.2f, Math.min(1f, t));
    }

    void setFlingVelocityThreshold(float v) {
        this.flingVelocityThreshold = Math.max(0f, v);
    }

    /** 是否正处于一次手势中（供 dispatchTouchEvent 路由判断 MOVE/UP 是否继续接管）。 */
    boolean hasGesture() {
        return active;
    }

    /** 立即把小白条进度复位到 0（解锁/复位时用）。 */
    void resetProgress() {
        lastProgress = 0f;
        invalidate();
    }

    /** 直接设置小白条进度（动画驱动时用）。 */
    void setProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (p == lastProgress) {
            return;
        }
        lastProgress = p;
        invalidate();
    }

    /** 把小白条进度动画到目标值（回弹观感反馈）。 */
    void animateProgressTo(float target, long durationMs) {
        ValueAnimator anim = ValueAnimator.ofFloat(lastProgress, target);
        anim.setDuration(durationMs);
        anim.addUpdateListener(a -> setProgress((Float) a.getAnimatedValue()));
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }
        float barW = barWidthDp * d;
        float barH = BAR_HEIGHT_DP * d;
        float left = (w - barW) / 2f;
        // 小白条固定在视图底部（不跟手），上滑时仅轻微淡出
        float top = h - barH - BAR_BOTTOM_MARGIN_DP * d;
        float radius = barH / 2f;
        barPaint.setAlpha(Math.round(255f * (1f - 0.55f * lastProgress)));
        canvas.drawRoundRect(left, top, left + barW, top + barH, radius, radius, barPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Callback cb = callback;
        if (cb == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                active = true;
                downY = event.getRawY();
                lastProgress = 0f;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(event);
                cb.onDrag(0f);
                invalidate();
                // 返回 true：让系统把后续 MOVE/UP 都送到本视图（小视图，不影响其它区域）
                return true;
            case MotionEvent.ACTION_MOVE: {
                velocityTracker.addMovement(event);
                float dy = downY - event.getRawY();
                float progress = Math.max(0f, Math.min(1f, dy / fullDragPx));
                lastProgress = progress;
                cb.onDrag(progress);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                active = false;
                float dy = downY - event.getRawY();
                float progress = Math.max(0f, Math.min(1f, dy / fullDragPx));
                lastProgress = progress;

                float velocityY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityY = velocityTracker.getYVelocity();
                    velocityTracker.recycle();
                    velocityTracker = null;
                }

                // 进度达标，或上滑速度达到阈值（且至少滑了一小段，避免误触）都算解锁
                boolean flingUnlock = progress >= minFlingProgress
                        && velocityY <= -flingVelocityThreshold;
                boolean unlock = progress >= unlockThreshold || flingUnlock;
                SysUiLog.log("handle", "UP progress=" + progress + " vel=" + velocityY
                        + " flingUnlock=" + flingUnlock + " unlock=" + unlock
                        + " barWidthDp=" + barWidthDp);
                cb.onRelease(progress, velocityY, unlock);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                // 手势被系统打断（如状态栏抢占）：不触发解锁，直接回弹
                active = false;
                lastProgress = 0f;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                cb.onRelease(0f, 0f, false);
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
