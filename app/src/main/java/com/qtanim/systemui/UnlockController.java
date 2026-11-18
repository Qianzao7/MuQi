package com.qtanim.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * 锁屏 iOS 上滑解锁联动控制器。
 *
 * <p>职责：
 * <ul>
 *   <li>在锁屏窗口上叠加玻璃条 + 背景压暗条 + 小白条；</li>
 *   <li>上滑时把锁屏内容（时钟 + 通知）作为一个整体上移，同时驱动底部交接处的柔光玻璃逐渐显现；</li>
 *   <li>拉到顶 / 上抛松手时拉起系统解锁界面；</li>
 *   <li>松手未到顶但触发解锁时，先自动滑到顶再解锁（速度可调）。</li>
 * </ul>
 *
 * <p>锁屏内容由两个<b>兄弟节点</b>组成：{@code keyguard_root_view}（HyperOSKeyguardRootView，
 * 时钟等）与 {@code shared_notification_container}（SharedNotificationContainer，通知）。
 * 两者都要一起上移，否则通知会留在原地。
 *
 * <p><b>状态判定</b>：锁屏是否显示、密码盘是否显示都实时读取 SystemUI 的真实状态，而不是
 * 只依赖缓存的静态布尔量。这样解锁后打开控制中心不会再误触发这套手势。
 *
 * <p><b>手势区</b>：小白条是一个只占底部中央的小视图，只有从那一小块上滑才接管手势，
 * 其它区域（通知滑动、左下/右下快捷应用、普通点击）完全走系统原生逻辑。
 */
final class UnlockController implements UnlockHandleView.Callback {

    /** 小白条触控区高度（dp）。 */
    private static final float HANDLE_TOUCH_HEIGHT_DP = 44f;

    /** 小白条触控区向左右额外扩展的宽度（dp），保证手指不必精确压中 5dp 的条。 */
    private static final float HANDLE_TOUCH_PAD_DP = 8f;

    private static volatile UnlockController sInstance;

    /** 锁屏是否正在显示（由 notifyKeyguardState 同步的缓存值，仅在实时状态读不到时兜底）。 */
    static volatile boolean keyguardShowing;

    /** 密码盘（bouncer）是否正在显示（由 show/hideBouncer hook 维护）。 */
    static volatile boolean bouncerShowing;

    /** 已捕获的 KeyguardStateControllerImpl（实时读 mShowing/mOccluded 用）。 */
    static volatile Object keyguardStateController;

    /** 已捕获的 StatusBarKeyguardViewManager（由 hook 注入）。 */
    static volatile Object keyguardViewManager;

    private final XposedModule module;
    private final ViewGroup window;
    private View keyguardRoot;
    private View notificationContainer;
    private View notificationPanel;
    private View bottomArea;
    private DimOverlayView dimView;
    private UnlockHandleView handle;
    /** 圆角半径缓存（px），读不到才回退，避免每帧重复反射。 */
    private float cornerRadiusPx = -1f;

    private float maxShiftPx;
    private float currentProgress;
    private boolean unlocking;
    private boolean setupDone;

    /**
     * 反向锚定层：景深主体 / 壁纸等长在 keyguard_root_view 子树里的层，会随内容根一起上移。
     * 这里把它们单独收集起来，上滑时写反向 translationY 抵消父容器位移，让它们钉在屏幕原位。
     */
    private final java.util.List<android.view.View> anchorTargets = new java.util.ArrayList<>();

    private UnlockController(XposedModule module, ViewGroup window) {
        this.module = module;
        this.window = window;
    }

    static void onWindowAttached(View windowView, XposedModule module) {
        try {
            if (!(windowView instanceof ViewGroup)) {
                return;
            }
            UnlockController c = sInstance;
            if (c != null && c.window == windowView) {
                c.trySetup();
                return;
            }
            c = new UnlockController(module, (ViewGroup) windowView);
            sInstance = c;
            c.trySetup();
        } catch (Throwable t) {
            module.log(5, "QTSysUI", "onWindowAttached failed", t);
        }
    }

    /** 窗口还没 layout 时（高度为 0）延迟到 layout 完成再装 overlay。 */
    private void trySetup() {
        if (setupDone) {
            return;
        }
        if (window.getHeight() > 0) {
            setupDone = true;
            setup();
            return;
        }
        window.post(new Runnable() {
            @Override
            public void run() {
                trySetup();
            }
        });
    }

    static void onKeyguardState(boolean showing, boolean occluded) {
        keyguardShowing = showing && !occluded;
        UnlockController c = sInstance;
        if (c == null) {
            return;
        }
        if (showing && !occluded) {
            // 锁屏重新显示（重新锁屏）：内容归位。玻璃卡片是 keyguardRoot 的子 View，随内容一起归位。
            c.resetIfIdle();
        } else {
            // 解锁完成 / 被遮挡：停手势、收 overlay，但不强行把内容拽回原位（避免闪一帧锁屏）
            c.onKeyguardGone();
        }
        c.updateOverlayVisibility();
    }

    static void onBouncerShow() {
        bouncerShowing = true;
        UnlockController c = sInstance;
        if (c != null) {
            // 密码盘要显示：只淡出内容层 + 收掉上滑 overlay，绝不把内容层拽回原位，
            // 否则会在密码盘出现前闪一帧「还没解锁」的锁屏。
            c.fadeOutContent();
            c.hideOverlays();
            c.updateOverlayVisibility();
        }
    }

    static void onBouncerHidden() {
        bouncerShowing = false;
        UnlockController c = sInstance;
        if (c != null) {
            c.updateOverlayVisibility();
            // 密码盘收起后，只有锁屏还在显示时才把内容层恢复原位；正在解锁时保持淡出。
            if (isKeyguardShowing()) {
                c.reset();
            }
        }
    }

    /** 实时读取锁屏状态：mShowing 为真且 mOccluded 为假才算“锁屏显示中”。 */
    static boolean isKeyguardShowing() {
        Object sc = keyguardStateController;
        if (sc != null) {
            try {
                Object showing = field(sc, "mShowing");
                Object occluded = field(sc, "mOccluded");
                if (showing instanceof Boolean) {
                    return (Boolean) showing
                            && !(occluded instanceof Boolean && (Boolean) occluded);
                }
            } catch (Throwable ignored) {
                // 读不到退回缓存值
            }
        }
        return keyguardShowing;
    }

    /** 密码盘是否显示中：优先用 hook 维护的标志，读不到时反射问管理器。 */
    static boolean isBouncerShowing() {
        if (bouncerShowing) {
            return true;
        }
        Object m = keyguardViewManager;
        if (m != null) {
            try {
                Method m2 = m.getClass().getMethod("isBouncerShowing");
                Object r = m2.invoke(m);
                return Boolean.TRUE.equals(r);
            } catch (Throwable t) {
                Object interactor = field(m, "mPrimaryBouncerInteractor");
                if (interactor != null) {
                    try {
                        Method isFully = interactor.getClass().getMethod("isFullyShowing");
                        Object r2 = isFully.invoke(interactor);
                        return Boolean.TRUE.equals(r2);
                    } catch (Throwable ignored) {
                        // 再失败就当没显示
                    }
                }
            }
        }
        return false;
    }

    /** 是否应当由本模块接管这次触摸（总开关 + 锁屏显示中 + 密码盘未显示 + 落在小白条触控区）。 */
    private static boolean shouldIntercept(MotionEvent ev) {
        if (!SystemUIConfig.enabled) {
            return false;
        }
        if (!isKeyguardShowing()) {
            return false;
        }
        if (isBouncerShowing()) {
            return false;
        }
        return inHandleZone(ev);
    }

    /** 供 onInterceptTouchEvent hook 调用：只有我们要接管时才返回 true（让系统别拦截）。 */
    static boolean isInHandleZone(Object windowView, MotionEvent ev) {
        return shouldIntercept(ev);
    }

    /** 触摸落点是否在小白条触控区（底部中央那一小块）。 */
    private static boolean inHandleZone(MotionEvent ev) {
        UnlockController c = sInstance;
        if (c == null || c.handle == null) {
            return false;
        }
        try {
            int[] loc = new int[2];
            c.handle.getLocationOnScreen(loc);
            float left = loc[0];
            float top = loc[1];
            float right = left + c.handle.getWidth();
            float bottom = top + c.handle.getHeight();
            float x = ev.getRawX();
            float y = ev.getRawY();
            return x >= left && x <= right && y >= top && y <= bottom;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * {@code dispatchTouchEvent} 前置路由：落点在手势区时，把这次事件直接喂给小白条并吞掉。
     * 只在锁屏显示且密码盘未显示时接管，解锁后拉控制中心再上滑不会再误触发。
     */
    static boolean dispatchToHandle(MotionEvent ev) {
        UnlockController c = sInstance;
        if (c == null || c.handle == null || !c.handle.isAttachedToWindow() || c.unlocking) {
            return false;
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean in = shouldIntercept(ev);
            if (!in) {
                return false;
            }
            SysUiLog.log("touch", "DOWN raw=" + ev.getRawX() + "," + ev.getRawY()
                    + " handleW=" + c.handle.getWidth() + " handleH=" + c.handle.getHeight());
            c.handle.onTouchEvent(ev);
            return true;
        }
        // MOVE/UP/CANCEL：跟着 handle 的手势状态走，保证一次手势不中途丢失
        if (c.handle.hasGesture()) {
            c.handle.onTouchEvent(ev);
            return true;
        }
        return false;
    }

    private void setup() {
        Context ctx = window.getContext();
        resolve();

        SystemUIConfig.loadAsync(ctx);

        // 清空日志广播：模块 App 点「清空日志」时发 com.qtanim.module.CLEAR_LOG 到这里，
        // SystemUI 进程（platform_app）能直接删 sdcard 上的日志文件，无需 root。
        try {
            IntentFilter filter = new IntentFilter("com.qtanim.module.CLEAR_LOG");
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    clearLogFile();
                }
            }, filter);
        } catch (Throwable t) {
            module.log(5, "QTSysUI", "register clear-log receiver failed", t);
        }

        float density = ctx.getResources().getDisplayMetrics().density;
        float screenH = window.getHeight();

        // 背景压暗条：覆盖卡片底边以下露出的壁纸。顶边不是直线，而是跟着卡片圆角底边走——
        // 左右两角「往上切」（卡片底角被削掉，压暗层在角上往上补），中间保持分界线。
        float cornerPx = resolveCornerRadius();
        dimView = new DimOverlayView(ctx);
        dimView.setCornerRadius(cornerPx);
        dimView.setAlpha(0f);
        FrameLayout.LayoutParams dimLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) screenH,
                Gravity.TOP);
        // 顶边抬到 screenH - r：压暗层多盖住上方 r 高度，正好补上卡片圆角被切掉的左右两角。
        dimLp.topMargin = (int) (screenH - cornerPx);

        window.addView(dimView, dimLp);

        // 圆角：上层（时钟/通知）保持透明、壁纸透出。卡片底边左右两角的弧形圆角由压暗层
        // 顶边（DimOverlayView 自绘的「角上往上补」形状）体现，上滑时随内容一起上移。

        // 小白条手势层：只占底部中央一小块，绝不挡其它区域的触摸。
        handle = new UnlockHandleView(ctx);
        handle.setCallback(this);
        FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(
                (int) ((SystemUIConfig.handleWidthDp + HANDLE_TOUCH_PAD_DP * 2f) * density),
                (int) (HANDLE_TOUCH_HEIGHT_DP * density),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.addView(handle, handleLp);
        applyHandleConfig();

        updateOverlayVisibility();

        module.log(4, "QTSysUI", "overlay installed, keyguardRoot="
                + (keyguardRoot != null) + " notification=" + (notificationContainer != null)
                + " screenH=" + (int) screenH + " maxShiftPx=" + (int) maxShiftPx);
        SysUiLog.log("setup", "overlay keyguardRoot=" + (keyguardRoot != null)
                + " notification=" + (notificationContainer != null)
                + " screenW=" + window.getWidth()
                + " screenH=" + (int) screenH
                + " cornerRadiusDp=" + SystemUIConfig.cornerRadiusDp
                + " handleWidthDp=" + SystemUIConfig.handleWidthDp
                + " bottomCornerRounded=" + (keyguardRoot != null));
    }

    private void applyHandleConfig() {
        if (handle == null) {
            return;
        }
        try {
            float density = window.getResources().getDisplayMetrics().density;
            float w = (SystemUIConfig.handleWidthDp + HANDLE_TOUCH_PAD_DP * 2f) * density;
            float h = HANDLE_TOUCH_HEIGHT_DP * density;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) handle.getLayoutParams();
            if (lp != null) {
                lp.width = (int) w;
                lp.height = (int) h;
                lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                handle.setLayoutParams(lp);
            }
            handle.setFullDragPx(Math.max(1f, window.getHeight() * 0.9f));
            handle.setBarWidthDp(SystemUIConfig.handleWidthDp);
            handle.setUnlockThreshold(SystemUIConfig.unlockThreshold);
            handle.setFlingVelocityThreshold(SystemUIConfig.flingVelocity);
            SysUiLog.log("handle", "applyHandleConfig widthDp=" + SystemUIConfig.handleWidthDp
                    + " lp.w=" + (int) w + " lp.h=" + (int) h);
        } catch (Throwable t) {
            module.log(5, "QTSysUI", "applyHandleConfig failed", t);
        }
    }

    /** 模块设置读到后，把参数应用到已搭好的控制器。 */
    static void applyConfig() {
        UnlockController c = sInstance;
        if (c == null) {
            return;
        }
        c.maxShiftPx = c.window.getHeight() * SystemUIConfig.shiftRatio;
        c.applyHandleConfig();
        c.cornerRadiusPx = -1f; // 强制重读圆角半径
        if (c.dimView != null) {
            // 圆角半径变了就重绘压暗层顶边（卡片底边圆角跟随设置）。
            c.dimView.setCornerRadius(c.resolveCornerRadius());
        }
        c.updateOverlayVisibility();
    }

    /** 删除 SystemUI 侧与桌面 native 共用的诊断日志文件（platform_app 权限，无需 root）。 */
    private static void clearLogFile() {
        String[] paths = {
                "/data/media/0/qtanim_hb.log",
                "/storage/emulated/0/qtanim_hb.log",
                "/data/user/0/com.android.systemui/files/qtanim_hb.log",
        };
        for (String p : paths) {
            try {
                File f = new File(p);
                if (f.exists()) {
                    f.delete();
                }
            } catch (Throwable ignored) {
                // 继续删下一个路径
            }
        }
        SysUiLog.log("clearlog", "cleared by SystemUI broadcast");
    }

    /** 定位锁屏内容层：时钟在 keyguard_root_view，通知在 shared_notification_container。 */
    private void resolve() {
        View rootView = findFirst(window, "keyguard_root_view");
        View bgLayer = findFirst(window, "keyguard_background_layer");
        if (rootView != null) {
            keyguardRoot = rootView;
        } else if (bgLayer != null) {
            keyguardRoot = bgLayer;
        } else {
            keyguardRoot = window;
        }
        // 通知容器：shared_notification_container（真正的通知堆叠）优先，别让
        // notification_container_parent（包通知列表的"壳"）抢在前面——壳的 translationY 被系统
        // 动画持续覆盖，写它通知看起来"不动"。
        notificationContainer = findFirst(window, "shared_notification_container",
                "notification_container_parent", "notification_stack_scroller");
        notificationPanel = findFirst(window, "notification_panel");
        // 底部灰色横带（手电筒/相机快捷方式所在），独立于 keyguard_root_view 时也要一起上移 + 套圆角。
        bottomArea = findFirst(window, "keyguard_bottom_area", "bottom_area");
        maxShiftPx = window.getHeight() * SystemUIConfig.shiftRatio;
        // 收集景深/壁纸等需要反向锚定的层（首次尝试；手势开始时还会兜底重试）
        collectAnchorTargets(keyguardRoot);
        SysUiLog.log("resolve", "keyguardRoot=" + (rootView != null ? "root_view" : (bgLayer != null ? "background_layer" : "window"))
                + " notification=" + (notificationContainer != null)
                + " panel=" + (notificationPanel != null)
                + " bottomArea=" + (bottomArea != null)
                + " anchors=" + anchorTargets.size()
                + " maxShiftPx=" + (int) maxShiftPx);
    }

    /**
     * 递归收集需要「反向锚定」的层：景深主体（deducted_image_view）、壁纸、深度/人像/裁切等层。
     * 这些层长在 keyguard_root_view 子树里，上滑时会跟着内容根一起上移；把它们单独记下来，
     * 上滑时写反向 translationY，让它们在屏幕上钉住不动。
     */
    private void collectAnchorTargets(View root) {
        anchorTargets.clear();
        if (root == null) {
            return;
        }
        collectAnchorTargetsRecursive(root);
    }

    private void collectAnchorTargetsRecursive(View view) {
        if (view == null) {
            return;
        }
        String idName = null;
        try {
            int id = view.getId();
            if (id != android.view.View.NO_ID) {
                idName = view.getResources().getResourceEntryName(id);
            }
        } catch (Throwable ignored) {
            // id 读不到就只按类名匹配
        }
        String cls = view.getClass().getName();
        String candidate = (idName == null ? "" : idName) + "|" + cls;

        // 命中这些关键词的层才需要锚定；blur_blend_container（模糊混合容器）要跟着动，显式排除。
        boolean hit = false;
        String[] keys = {"depth", "subject", "crop", "wallpaper", "portrait", "blur", "mask", "deducted"};
        for (String k : keys) {
            if (candidate.toLowerCase().contains(k)) {
                hit = true;
                break;
            }
        }
        if (idName != null && idName.toLowerCase().contains("blur_blend_container")) {
            hit = false;
        }
        if (hit) {
            anchorTargets.add(view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectAnchorTargetsRecursive(g.getChildAt(i));
            }
        }
    }

    /** 给所有锚定层写反向 translationY，抵消父容器（内容根）的位移，让它们钉在屏幕原位。 */
    private void applyAnchor(float shift) {
        if (anchorTargets.isEmpty()) {
            return;
        }
        // shift 是内容根的 translationY（负数=上移），锚定层写 -shift 即可抵消。
        float target = -shift;
        for (android.view.View v : anchorTargets) {
            if (v == null || !v.isAttachedToWindow()) {
                continue;
            }
            if (Math.abs(v.getTranslationY() - target) > 0.5f) {
                v.setTranslationY(target);
            }
        }
    }

    /** 把锚定层 translationY 还原为 0（解锁完成 / 重新锁屏时调用）。 */
    private void restoreAnchors() {
        for (android.view.View v : anchorTargets) {
            if (v != null && v.isAttachedToWindow()) {
                v.setTranslationY(0f);
            }
        }
    }


    /**
     * 读取圆角半径（px）：卡片底边圆角，取值来自设置页「卡片底边圆角」（dp，默认 28）。
     *
     * <p>之前读 {@code WindowInsets.getRoundedCorner()} 拿到的是屏幕四角的物理圆角（本机约
     * 147px），那是屏幕玻璃本身的圆角、不是「上滑内容卡片」底边两角的弧形，方向就错了；
     * 现改为读用户可调的 {@link SystemUIConfig#cornerRadiusDp}。
     */
    private float resolveCornerRadius() {
        if (cornerRadiusPx > 0f) {
            return cornerRadiusPx;
        }
        float dp = SystemUIConfig.cornerRadiusDp;
        if (dp <= 0f) {
            dp = 28f;
        }
        cornerRadiusPx = dp * window.getResources().getDisplayMetrics().density;
        SysUiLog.log("corner", "圆角半径=" + cornerRadiusPx + "px (dp=" + dp + ")");
        return cornerRadiusPx;
    }


    private static View findFirst(View root, String... ids) {
        if (root == null) {
            return null;
        }
        for (String name : ids) {
            int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
            if (id != 0) {
                View v = root.findViewById(id);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    /** 跳过未变化写入：HyperOS 的根视图 setTranslationY 每次都会触发 onStateChanged。 */
    private static void setTy(View v, float y) {
        if (v != null && v.getTranslationY() != y) {
            v.setTranslationY(y);
        }
    }

    @Override
    public void onDrag(float progress) {
        if (unlocking) {
            return;
        }
        if ((keyguardRoot == null || !keyguardRoot.isAttachedToWindow())) {
            resolve();
        }
        // 景深层可能晚于内容根完成构建：手势开始时若还没收集到，兜底重试一次。
        if (anchorTargets.isEmpty() && keyguardRoot != null) {
            collectAnchorTargets(keyguardRoot);
        }
        applyProgress(progress);
    }

    @Override
    public void onRelease(float progress, float velocityY, boolean shouldUnlock) {
        if (unlocking) {
            return;
        }
        if (shouldUnlock) {
            if (progress >= 1f) {
                doUnlock();
            } else {
                animateToTopThenUnlock(progress);
            }
            return;
        }
        animateBack();
    }

    /** 把所有上滑进度统一应用到内容层、背景压暗条、玻璃条与小白条。 */
    private void applyProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        currentProgress = p;
        float shift = -p * maxShiftPx;
        // 内容层（时钟根 + 通知堆叠 + 通知面板 + 底部横带）一起上移，
        // 跳过未变化写入，避免触发系统状态回调。
        setTy(keyguardRoot, shift);
        setTy(notificationContainer, shift);
        setTy(notificationPanel, shift);
        setTy(bottomArea, shift);
        // 景深/壁纸等锚定层反向抵消，钉在屏幕原位，不跟随内容上移。
        applyAnchor(shift);
        if (dimView != null) {
            dimView.setTranslationY(shift);
            // 固定暗度：一旦开始上滑即到设定值，不随进度递增；回位到 0 时彻底收掉。
            dimView.setAlpha(p <= 0.001f ? 0f : SystemUIConfig.bgDim);
        }
        if (handle != null) {
            // 小白条跟手：随内容一起上移（与内容层同速划走），仅轻微淡出。
            handle.setProgress(p);
            handle.setTranslationY(shift);
        }
    }

    /** 松手触发解锁但没到顶：自动上滑到顶，动画结束再真正解锁。 */
    private void animateToTopThenUnlock(final float fromProgress) {
        unlocking = true;
        long durationMs = (long) ((1f - fromProgress) * 3000f
                / Math.max(1f, SystemUIConfig.autoCompleteSpeed));
        ValueAnimator anim = ValueAnimator.ofFloat(fromProgress, 1f);
        anim.setDuration(durationMs);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> applyProgress((Float) a.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                doUnlock();
            }
        });
        anim.start();
    }

    /** 真正发起解锁，并把舞台交还给系统解锁动画。 */
    private void doUnlock() {
        if (requestUnlock()) {
            unlocking = true;
            settleAfterUnlock();
        } else {
            unlocking = false;
            currentProgress = 0f;
            animateBack();
        }
    }

    /**
     * 解锁已发起：把内容层淡出（系统解锁动画会复位视图，先淡出可避免闪一帧完整锁屏），
     * 并收掉玻璃/压暗/小白条。内容层 translationY 不回位，交给系统解锁动画接管，
     * 系统 dismiss 后由 {@link #reset()} 归位。
     */
    private void settleAfterUnlock() {
        fadeOutContent();
        hideOverlays();
        updateOverlayVisibility();
    }

    /** 把锁屏内容层（时钟 + 通知）快速淡出，防止解锁/密码盘出现前闪一帧完整锁屏。 */
    private void fadeOutContent() {
        if (keyguardRoot != null && keyguardRoot.isAttachedToWindow()) {
            keyguardRoot.animate().alpha(0f).setDuration(120L).start();
        }
        if (notificationContainer != null && notificationContainer.isAttachedToWindow()) {
            notificationContainer.animate().alpha(0f).setDuration(120L).start();
        }
        if (notificationPanel != null && notificationPanel.isAttachedToWindow()) {
            notificationPanel.animate().alpha(0f).setDuration(120L).start();
        }
        if (bottomArea != null && bottomArea.isAttachedToWindow()) {
            bottomArea.animate().alpha(0f).setDuration(120L).start();
        }
    }

    /** 收掉上滑临时 overlay（压暗、小白条），不碰内容层与真玻璃。 */
    private void hideOverlays() {
        currentProgress = 0f;
        if (dimView != null) {
            dimView.setAlpha(0f);
            dimView.setTranslationY(0f);
        }
        if (handle != null) {
            handle.resetProgress();
            handle.setTranslationY(0f);
        }
    }

    private void animateBack() {
        final float from = currentProgress;
        if (from <= 0f) {
            applyProgress(0f);
            return;
        }
        // 用一个 ValueAnimator 把「内容层、玻璃、压暗、小白条」整体平滑带回，
        // 取代此前「内容 animate 回位 + 玻璃/压暗瞬间 reset」的割裂：松手时线条不再突然消失。
        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(220L);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> applyProgress((Float) a.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                applyProgress(0f);
            }
        });
        anim.start();
    }

    private void reset() {
        unlocking = false;
        currentProgress = 0f;
        if (keyguardRoot != null && keyguardRoot.isAttachedToWindow()) {
            keyguardRoot.setTranslationY(0f);
            keyguardRoot.setAlpha(1f);
        }
        if (notificationContainer != null && notificationContainer.isAttachedToWindow()) {
            notificationContainer.setTranslationY(0f);
            notificationContainer.setAlpha(1f);
        }
        if (notificationPanel != null && notificationPanel.isAttachedToWindow()) {
            notificationPanel.setTranslationY(0f);
            notificationPanel.setAlpha(1f);
        }
        if (bottomArea != null && bottomArea.isAttachedToWindow()) {
            bottomArea.setTranslationY(0f);
            bottomArea.setAlpha(1f);
        }
        if (dimView != null) {
            dimView.setAlpha(0f);
            dimView.setTranslationY(0f);
        }
        if (handle != null) {
            handle.resetProgress();
            handle.setTranslationY(0f);
        }
        // 锚定层归位
        restoreAnchors();
    }

    /** 解锁完成 / 被遮挡时调用：收 overlay，但内容层不回位（下次锁屏显示时再归位）。 */
    private void onKeyguardGone() {
        unlocking = false;
        currentProgress = 0f;
        if (dimView != null) {
            dimView.setAlpha(0f);
            dimView.setTranslationY(0f);
        }
        if (handle != null) {
            handle.resetProgress();
            handle.setTranslationY(0f);
        }
        // 锚定层归位
        restoreAnchors();
        // 修复通知中心「有通知却显示空状态」：通知容器/面板与下拉通知中心共用同一组
        // 视图，解锁时 fadeOutContent() 把它们 alpha 置 0、translationY 上移，若解锁后
        // 不恢复，拉下通知中心就看不到通知。这里延迟到 dismiss 动画结束后再恢复，
        // 既不会在解锁瞬间闪一帧锁屏通知，又能保证通知中心正常显示。
        window.postDelayed(this::restoreNotificationLayers, 300L);
    }

    /** 恢复通知容器/面板的可见性与位置（下拉通知中心与锁屏共用这些视图）。 */
    private void restoreNotificationLayers() {
        try {
            if (notificationContainer != null && notificationContainer.isAttachedToWindow()) {
                notificationContainer.animate().cancel();
                notificationContainer.setAlpha(1f);
                notificationContainer.setTranslationY(0f);
            }
            if (notificationPanel != null && notificationPanel.isAttachedToWindow()) {
                notificationPanel.animate().cancel();
                notificationPanel.setAlpha(1f);
                notificationPanel.setTranslationY(0f);
            }
            SysUiLog.log("notif", "notification layers restored after unlock");
        } catch (Throwable t) {
            // 恢复失败不影响其它逻辑（下次锁屏 reset 仍会兜底）
        }
    }

    /** 空闲时才复位（锁屏重新显示时用），避免打断正在进行的手势。 */
    private void resetIfIdle() {
        if (unlocking || currentProgress > 0f) {
            return;
        }
        reset();
    }

    /** 根据"总开关 + 锁屏显示 + 密码盘未显示"决定 overlay 显隐。 */
    private void updateOverlayVisibility() {
        boolean show = SystemUIConfig.enabled && isKeyguardShowing() && !isBouncerShowing();
        int vis = show ? View.VISIBLE : View.GONE;
        if (handle != null) {
            handle.setVisibility(vis);
        }
        if (dimView != null) {
            dimView.setVisibility(vis);
        }
    }

    // ---------------- 解锁 ----------------

    private boolean requestUnlock() {
        Object m = keyguardViewManager;
        if (m == null) {
            module.log(5, "QTSysUI", "keyguardViewManager not captured", null);
            return false;
        }
        try {
            int userId = currentUserId(m);
            if (!isSecure(m, userId)) {
                Object callback = field(m, "mViewMediatorCallback");
                if (callback != null) {
                    Method done = callback.getClass().getMethod("keyguardDone", int.class);
                    done.setAccessible(true);
                    done.invoke(callback, userId);
                    module.log(4, "QTSysUI", "unlock=keyguardDone(user " + userId + ")");
                    return true;
                }
            }
            Object interactor = field(m, "mPrimaryBouncerInteractor");
            if (interactor != null) {
                Method show = interactor.getClass().getMethod("show", String.class);
                show.setAccessible(true);
                Object ok = show.invoke(interactor, "qtanim");
                if (Boolean.TRUE.equals(ok)) {
                    module.log(4, "QTSysUI", "unlock=bouncer(secure)");
                    return true;
                }
            }
            Method show = m.getClass().getMethod("showPrimaryBouncer", String.class);
            show.setAccessible(true);
            show.invoke(m, "qtanim");
            return true;
        } catch (Throwable t) {
            module.log(5, "QTSysUI", "requestUnlock failed", t);
            return false;
        }
    }

    private static boolean isSecure(Object m, int userId) {
        try {
            Object model = field(m, "mKeyguardSecurityModel");
            if (model == null) {
                return true;
            }
            Object mode = model.getClass().getMethod("getSecurityMode", int.class).invoke(model, userId);
            return mode == null || !"None".equals(String.valueOf(mode));
        } catch (Throwable t) {
            return true;
        }
    }

    private static int currentUserId(Object m) {
        try {
            Object interactor = field(m, "mPrimaryBouncerInteractor");
            Object selected = field(interactor, "selectedUserInteractor");
            if (selected == null) {
                return 0;
            }
            Method getter = selected.getClass().getMethod("getSelectedUserId");
            getter.setAccessible(true);
            return (Integer) getter.invoke(selected);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object field(Object o, String name) {
        if (o == null) {
            return null;
        }
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (Throwable ignored) {
                // 继续往父类找
            }
        }
        return null;
    }
}
