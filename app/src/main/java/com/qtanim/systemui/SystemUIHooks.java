package com.qtanim.systemui;

import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * SystemUI 侧的 hook 安装。
 *
 * <p>全部 hook 都挂在 SystemUI 自己的类上，用 PROTECTIVE 模式 + 单独 try/catch，
 * 单条失败不影响主功能。核心三条：
 * <ul>
 *   <li>{@code NotificationShadeWindowView#onAttachedToWindow} —— 装上 overlay（玻璃层 + 小白条）；</li>
 *   <li>{@code NotificationShadeWindowView#onInterceptTouchEvent} —— 落点在小白条触控区时不拦截，手势归我们；</li>
 *   <li>{@code KeyguardStateControllerImpl#notifyKeyguardState} —— 解锁/被遮挡时复位。</li>
 * </ul>
 * 另捕获 {@code StatusBarKeyguardViewManager}（构造器 + 方法兜底），供拉满解锁用。
 */
final class SystemUIHooks {

    private static final String TAG = "QTSysUI";

    private static final String[] WINDOW_CLASSES = {
            "com.android.systemui.shade.NotificationShadeWindowView",
            "com.android.systemui.statusbar.phone.NotificationShadeWindowView",
    };

    private SystemUIHooks() {
    }

    static void install(XposedModule module, ClassLoader cl, String dataDir) throws Throwable {
        // 诊断日志提前写：无论后面崩溃守护是否禁用，都能确认「模块被加载、走到 install」。
        // 下次导日志时据此区分：无任何 init 行 = 类加载阶段就崩；有 install start = 正常装 hook。
        SysUiLog.log("hooks", "module loaded, dataDir=" + dataDir);

        // ① 崩溃守护：必须在装任何功能 hook 之前判定。
        //    命中（SystemUI 短时间内连续崩溃）则本次不装任何 hook，模块主动闭嘴，
        //    让系统界面自己能起来。判据本身出错一律返回 false = 照常装。
        if (CrashGuard.evaluate(dataDir)) {
            SysUiLog.log("hooks", "crash guard triggered, disabled: " + CrashGuard.reason());
            module.log(5, TAG, "crash guard triggered, module disabled this boot: "
                    + CrashGuard.reason(), null);
            return;
        }
        CrashGuard.startHeartbeat();
        SysUiLog.log("hooks", "install start sdk=" + android.os.Build.VERSION.SDK_INT
                + " brand=" + android.os.Build.BRAND
                + " model=" + android.os.Build.MODEL
                + " dataDir=" + dataDir);

        Class<?> windowClass = firstClass(cl, WINDOW_CLASSES);
        SysUiLog.log("hooks", "windowClass=" + (windowClass != null ? windowClass.getName() : "NULL"));

        // ① 窗口挂载 → 装 overlay
        Method attach = declared(windowClass, "onAttachedToWindow");
        module.hook(attach)
                .setId("qtsysui-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        UnlockController.onWindowAttached((View) chain.getThisObject(), module);
                    } catch (Throwable t) {
                        module.log(5, TAG, "attach hook failed", t);
                    }
                    return result;
                });

        // ①.5 最外层事件入口：落点在手势区时直接把事件喂给小白条并吞掉，不让系统继续分发。
        //     这是上滑解锁能生效的关键 —— 只改 onInterceptTouchEvent 的返回值并不可靠
        //     （原生拦截链会在更早位置收走事件），在最外层 dispatchTouchEvent 前置接管才稳。
        try {
            Method dispatch = declared(windowClass, "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatch)
                    .setId("qtsysui-dispatch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            MotionEvent ev = (MotionEvent) chain.getArg(0);
                            if (UnlockController.dispatchToHandle(ev)) {
                                return Boolean.TRUE;
                            }
                        } catch (Throwable t) {
                            module.log(5, TAG, "dispatch hook failed", t);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable t) {
            module.log(5, TAG, "dispatchTouchEvent hook skipped", t);
        }

        // ② 小白条触控区不拦截
        try {
            Method intercept = declared(windowClass, "onInterceptTouchEvent", MotionEvent.class);
            module.hook(intercept)
                    .setId("qtsysui-intercept")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            MotionEvent ev = (MotionEvent) chain.getArg(0);
                            if (UnlockController.isInHandleZone(chain.getThisObject(), ev)) {
                                return Boolean.FALSE;
                            }
                        } catch (Throwable t) {
                            module.log(5, TAG, "intercept hook failed", t);
                        }
                        return result;
                    });
        } catch (Throwable t) {
            module.log(5, TAG, "onInterceptTouchEvent hook skipped", t);
        }

        // ③ 锁屏状态复位
        Class<?> stateClass = cl.loadClass(
                "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl");
        Method state = declared(stateClass, "notifyKeyguardState", boolean.class, boolean.class);
        module.hook(state)
                .setId("qtsysui-keyguard-state")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        // 顺手捕获 KeyguardStateControllerImpl 实例，供实时读 mShowing/mOccluded
                        UnlockController.keyguardStateController = chain.getThisObject();
                        UnlockController.onKeyguardState(
                                (Boolean) chain.getArg(0), (Boolean) chain.getArg(1));
                    } catch (Throwable t) {
                        module.log(5, TAG, "keyguard-state hook failed", t);
                    }
                    return result;
                });

        // 捕获 KeyguardStateControllerImpl 实例（构造器兜底，保证实时状态读得到）
        try {
            Class<?> ksc = cl.loadClass(
                    "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl");
            for (Constructor<?> ctor : ksc.getDeclaredConstructors()) {
                module.hook(ctor)
                        .setId("qtsysui-ksc-capture")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            UnlockController.keyguardStateController = result;
                            return result;
                        });
            }
        } catch (Throwable t) {
            module.log(5, TAG, "keyguard-state-ctor capture skipped", t);
        }

        // ④ 捕获 StatusBarKeyguardViewManager（解锁用）
        captureManager(module, cl);

        // ⑤ 密码盘显示/收起状态跟踪：密码盘显示时不拦截手势、隐藏 overlay，避免挡住密码输入
        try {
            Class<?> sbvm = cl.loadClass(
                    "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager");
            for (String name : new String[]{"showBouncer", "showPrimaryBouncer"}) {
                for (Method m : sbvm.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || m.getParameterTypes().length != 1) {
                        continue;
                    }
                    try {
                        module.hook(m)
                                .setId("qtsysui-bouncer-show-" + name)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object result = chain.proceed();
                                    try {
                                        UnlockController.onBouncerShow();
                                    } catch (Throwable t) {
                                        module.log(5, TAG, "bouncer-show hook failed", t);
                                    }
                                    return result;
                                });
                    } catch (Throwable ignored) {
                        // 重名方法继续试
                    }
                }
            }
            Method hide = declared(sbvm, "hideBouncer", boolean.class);
            module.hook(hide)
                    .setId("qtsysui-bouncer-hide")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            UnlockController.onBouncerHidden();
                        } catch (Throwable t) {
                            module.log(5, TAG, "bouncer-hide hook failed", t);
                        }
                        return result;
                    });
        } catch (Throwable t) {
            module.log(5, TAG, "bouncer hooks skipped", t);
        }

        module.log(4, TAG, "hooks installed");
    }

    /** hook 构造器 + 若干实例方法，把 StatusBarKeyguardViewManager 实例存下来。 */
    private static void captureManager(XposedModule module, ClassLoader cl) {
        try {
            Class<?> sbvm = cl.loadClass(
                    "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager");
            for (Constructor<?> ctor : sbvm.getDeclaredConstructors()) {
                module.hook(ctor)
                        .setId("qtsysui-capture-ctor")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            UnlockController.keyguardViewManager = result;
                            return result;
                        });
            }
            String[] methods = {"reset", "show", "setOccluded"};
            for (String name : methods) {
                for (Method m : sbvm.getDeclaredMethods()) {
                    if (!m.getName().equals(name)) {
                        continue;
                    }
                    try {
                        module.hook(m)
                                .setId("qtsysui-capture-" + name)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object result = chain.proceed();
                                    UnlockController.keyguardViewManager = chain.getThisObject();
                                    return result;
                                });
                    } catch (Throwable ignored) {
                        // 重名方法继续试
                    }
                }
            }
        } catch (Throwable t) {
            module.log(5, TAG, "captureManager skipped", t);
        }
    }

    private static Class<?> firstClass(ClassLoader cl, String[] names)
            throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Method declared(Class<?> clazz, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(name, params);
        if (m.getDeclaringClass() != clazz) {
            throw new NoSuchMethodException(clazz.getName() + "#" + name + " is inherited");
        }
        return m;
    }
}
