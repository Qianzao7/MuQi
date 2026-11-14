package com.qtanim.systemui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * 系统界面侧的上滑解锁运行参数。
 *
 * <p>SystemUI 进程读不到模块 App 的私有 SharedPreferences（跨包 SELinux），但模块 App 的
 * {@code ModuleStatusProvider} 是 exported 的 ContentProvider，任何进程都能
 * {@code call}。这里用 SystemUI 自己的 Context 走 {@code ContentResolver.call} 把参数
 * 一次性拉回来，落到 volatile 静态字段，供 {@link UnlockController} / 玻璃层 / 小白条读取。
 *
 * <p>读取失败（App 没装、进程没起来、非小米 ROM）一律退回默认值，绝不抛异常、绝不拖垮主流程。
 * 参数键名与设置页滑块一一对应，默认值就是「写死参数」时代的旧常量。
 */
final class SystemUIConfig {

    /** 上滑解锁总开关。 */
    static volatile boolean enabled = true;

    /** 解锁触发阈值：上滑进度到该值松手即解锁。范围 0.2..1.0，默认 0.5。 */
    static volatile float unlockThreshold = 0.5f;

    /** 小白条宽度（dp）。范围 40..300，默认 130。 */
    static volatile float handleWidthDp = 130f;

    /** 内容整体上移距离 = 屏幕高 × 该比例（1.0 = 完全划出屏幕顶部，实现"从底部划到顶部"）。范围 0.5..1.0，默认 1.0。 */
    static volatile float shiftRatio = 1.0f;

    /** 卡片底边左右两角的圆角半径（dp）：上滑时内容卡片底边的弧形圆角。范围 8..48，默认 28。 */
    static volatile float cornerRadiusDp = 28f;

    /** 上滑速度阈值（物理像素/秒）：松手瞬间上滑速度超过该值即触发解锁，即使进度没到阈值。范围 500..3000，默认 1200。 */
    static volatile float flingVelocity = 1200f;

    /** 自动上滑速度（1..20，越大越快）。松手触发解锁但还没到顶时，自动滑到顶的动画时长 = 3000 / 速度（毫秒）。默认 10。 */
    static volatile float autoCompleteSpeed = 10f;

    /** 背景（下层壁纸）明暗度：0 = 不压暗，1 = 全黑。范围 0..1，默认 0.30。随上滑进度线性加深。 */
    static volatile float bgDim = 0.30f;

    private static volatile boolean sLoaded;

    private SystemUIConfig() {
    }

    /**
     * 异步读取模块 App 的设置（只拉一次）。
     *
     * @param sysuiCtx SystemUI 进程的任意 Context（拿 ContentResolver 用）
     */
    static void loadAsync(final Context sysuiCtx) {
        if (sysuiCtx == null || sLoaded) {
            return;
        }
        sLoaded = true;
        final Context app = sysuiCtx.getApplicationContext() != null
                ? sysuiCtx.getApplicationContext() : sysuiCtx;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bundle b = app.getContentResolver().call(
                            Uri.parse("content://com.qtanim.module.status"),
                            "prefs", null, null);
                    if (b == null) {
                        return;
                    }
                    enabled = b.getBoolean("sysui_enable", true);
                    unlockThreshold = clamp(b.getInt("sysui_threshold", 50), 20, 100) / 100f;
                    handleWidthDp = clamp(b.getInt("sysui_handle_width_v2", 130), 40, 300);
                    shiftRatio = clamp(b.getInt("sysui_shift_ratio", 100), 50, 100) / 100f;
                    cornerRadiusDp = clamp(b.getInt("sysui_corner_radius_v2", 28), 8, 48);
                    flingVelocity = clamp(b.getInt("sysui_fling_velocity", 1200), 500, 3000);
                    autoCompleteSpeed = clamp(b.getInt("sysui_auto_speed", 10), 1, 20);
                    bgDim = clamp(b.getInt("sysui_bg_dim", 30), 0, 100) / 100f;
                    SysUiLog.log("config", "loaded enabled=" + enabled
                            + " threshold=" + unlockThreshold
                            + " handleWidth=" + handleWidthDp
                            + " shift=" + shiftRatio
                            + " corner=" + cornerRadiusDp
                            + " fling=" + flingVelocity
                            + " autoSpeed=" + autoCompleteSpeed
                            + " bgDim=" + bgDim);
                    // 把参数应用到已经搭好的控制器（setVisibility 等必须回主线程）
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            UnlockController.applyConfig();
                        }
                    });
                } catch (Throwable ignored) {
                    // 读不到就保持默认值
                }
            }
        }, "QTSysUI-config").start();
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
