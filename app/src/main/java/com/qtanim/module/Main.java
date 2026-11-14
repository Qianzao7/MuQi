package com.qtanim.module;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HyperOS 4（Android 17）适配。
 *
 * 动画本体由 native 库 libhyperbounce_hook.so 完成：LSPosed 通过
 * META-INF/xposed/native_init.list 在桌面进程里加载该 .so，.so 的 constructor
 * 会 inline hook com.miui.home 的 Flutter AOT 库 libapp.so 里的弹簧函数，
 * 实现「以中间图标为中心的错峰飞入 + 沿自身飞行轨迹过冲回弹」（iOS26 飞入）。
 *
 * 本 Java 类不再做任何逐帧动画（OS3 时代的 lambda$showUserPresentAnimation$0
 * 逐帧设置 View 的代码已全部移除），只保留两件事：
 *   1) 与模块 App 的设置桥（广播 + ContentProvider）：拉取「启用开关」与后续
 *      要接入的运行时可调参数；
 *   2) 上报激活状态，让模块首页显示「已激活」。
 *
 * 迁移说明（T3.8）：从 legacy Xposed API（de.robv.android.xposed:api:82）迁到
 * modern libxposed API 102（io.github.libxposed:api:102.0.0）。入口从
 * IXposedHookLoadPackage.handleLoadPackage 改为 XposedModule.onPackageReady，
 * Java hook 从 XposedHelpers.findAndHookMethod 改为 hook(Executable).intercept，
 * 日志从 XposedBridge.log 改为 XposedInterface.log。
 */
public class Main extends XposedModule {

    // 供 static 方法访问 XposedInterface（log/hook 等实例方法）。构造函数里只存
    // 引用，不做任何初始化工作（框架要求 onModuleLoaded 之前不得初始化）。
    private static Main sInstance;

    public Main() {
        super();
        sInstance = this;
    }

    private static final String PKG_HOME = "com.miui.home";
    private static final String PKG_MODULE = "com.qtanim.module";

    private static final String PREFS = "qtanim";
    // 与模块 App 之间的广播桥：桌面进程无法读写 App 私有目录（SELinux 拦 untrusted_app 跨包访问），
    // /data/local/tmp 也不可访问，所以设置项与激活状态全部走显式包名广播。
    private static final String ACTION_BRIDGE = "com.qtanim.module.BRIDGE";  // hook → App
    private static final String ACTION_PREFS = "com.qtanim.module.PREFS";    // App → hook
    private static final String EXTRA_OP = "op";
    private static final String OP_ACTIVATE = "activate";
    private static final String OP_REQUEST_PREFS = "request_prefs";
    // 模块 App 的 ContentProvider：跨应用读取设置最可靠的通道（不受广播限制影响）
    private static final String STATUS_URI = "content://com.qtanim.module.status";
    private static final String METHOD_PREFS = "prefs";
    private static final String METHOD_ACTIVATE = "activate";
    private static final String KEY_ENABLE = "enable";

    // 运行期开关
    private static volatile boolean sEnabled = true;

    // —— 动画运行时调参桥 ——
    // native 库 libhyperbounce_hook.so 由 LSPosed 通过 native_init 在桌面进程加载；
    // 这里延迟到 onPackageReady 再 System.loadLibrary 复用同一 .so（靠 DT_SONAME
    // 命中 bionic 的已加载缓存），把导出符号 Java_..._nativeSetParams 绑定到下面的
    // native 方法，滑块值才能传进 native。放在类初始化（static 块）里失败会拖垮
    // 整个模块加载，所以延迟 + 只尝试一次 + 失败也不影响激活上报。
    private static volatile boolean sNativeReady = false;
    private static volatile boolean sNativeTried = false;

    private static native void nativeSetParams(double stagger, double hStagger,
                                               double iconSpeed, double middleRow,
                                               double bounce, double dockLead);

    // 桌面进程的 Application Context（Application 创建后才拿得到）
    private static volatile Context sAppCtx = null;
    // 是否已收到过 App 推送的设置
    private static volatile boolean sPrefsLoaded = false;
    // Provider 调用是否偏慢（App 进程冷启动），慢则后续全部走异步刷新
    private static volatile boolean sProviderSlow = false;
    // prefs 请求节流
    private static volatile long sLastRequestMs = 0L;
    // 广播桥是否已初始化（防 callApplicationOnCreate hook 与轮询兜底重复触发）
    private static volatile boolean sBridgeInit = false;
    // 广播里做 IPC，一律不在主线程
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!PKG_HOME.equals(pkg)) {
            return;
        }
        // diag120：无条件写心跳文件，确认 Java hook 到底有没有被 LSPosed 加载执行。
        writeJavaHookLog("onPackageReady pkg=" + pkg
                + " t=" + System.currentTimeMillis());
        ClassLoader cl = param.getClassLoader();

        // 延迟绑定 native 调参桥（失败也不影响后面的激活上报）
        ensureNative();

        // muqi47：立即写一份默认参数文件，不依赖 Application/Context 的创建时序。
        // native 侧每次解锁读该文件，因此即使后续 Provider/广播/Context 全部失败，
        // 至少能保证默认参数落地（stagger=1.0 等），滑块桥的文件通道不再空转。
        writeParamsToNative(1.0, 1.0, 1.0, 0.0, 1.0, 1.0);

        // Application 创建后才有 Context，在那里注册接收器并上报激活。
        // 动画本身由 native 库接管，这里不再 hook 任何动画方法。
        hookAppCreated(cl);

        log("✓ native-only hook ready (OS4), waiting for module app prefs");
    }

    /** 只尝试一次：复用 LSPosed 已加载的 native 库，绑定 nativeSetParams。 */
    private static void ensureNative() {
        if (sNativeTried) return;
        sNativeTried = true;
        try {
            System.loadLibrary("hyperbounce_hook");
            sNativeReady = true;
            log("✓ native bridge ready (nativeSetParams bound)");
        } catch (Throwable t) {
            log("✗ loadLibrary failed: " + t);
        }
    }

    /** hook 桌面进程的 Application 创建，拿 Context 后建立与模块 App 的广播桥。 */
    private static void hookAppCreated(ClassLoader cl) {
        try {
            Class<?> instr = cl.loadClass("android.app.Instrumentation");
            java.lang.reflect.Method m = instr.getDeclaredMethod(
                    "callApplicationOnCreate", android.app.Application.class);
            // legacy 的 XC_MethodHook.afterHookedMethod → libxposed 拦截器链：
            // chain.proceed() 之前是 before、之后是 after；用 try/finally 保证
            // 即使原方法抛异常，after 逻辑（拿 Context 建桥）也照常执行。
            sInstance.hook(m).intercept(chain -> {
                try {
                    return chain.proceed();
                } finally {
                    Object arg0 = chain.getArg(0);
                    if (arg0 instanceof Context) {
                        final Context ctx = (Context) arg0;
                        sAppCtx = ctx;
                        scheduleBridge(ctx);
                    }
                }
            });
            log("hooked Instrumentation.callApplicationOnCreate");
        } catch (Throwable t) {
            log("hook app created failed: " + t);
        }
        // muqi47：兜底——如果上面这个 hook 因为「进程启动早于 LSPosed 注入、
        // Application 已经创建过了」而错过，就后台轮询反射拿 Application Context，
        // 保证广播桥/Provider 依然能建立（不依赖 hook 时序）。
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                pollForContext();
            }
        });
    }

    /** 单线程调度 initBridge，防重入。 */
    private static void scheduleBridge(final Context ctx) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                initBridge(ctx);
            }
        });
    }

    /** 轮询反射 ActivityThread.currentActivityThread().getApplication()，最多等约 15s。 */
    private static void pollForContext() {
        for (int i = 0; i < 30 && sAppCtx == null; i++) {
            try {
                Object at = Class.forName("android.app.ActivityThread")
                        .getMethod("currentActivityThread").invoke(null);
                if (at != null) {
                    Object app = at.getClass().getMethod("getApplication").invoke(at);
                    if (app instanceof Context) {
                        sAppCtx = (Context) app;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (sAppCtx != null) break;
            try { Thread.sleep(500); } catch (Throwable ignored) { }
        }
        if (sAppCtx != null) {
            initBridge(sAppCtx);
        } else {
            log("✗ context poll timeout — prefs bridge unavailable (默认参数文件已写)");
        }
    }

    /** 注册接收 App 设置广播的接收器，并同步拉取一次当前设置 + 上报激活。 */
    private static void initBridge(Context ctx) {
        if (sBridgeInit) return;
        sBridgeInit = true;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    applyPrefsFromIntent(intent);
                }
            };
            ctx.registerReceiver(receiver, new IntentFilter(ACTION_PREFS), Context.RECEIVER_EXPORTED);
            log("✓ prefs receiver registered");
        } catch (Throwable t) {
            log("register prefs receiver failed: " + t);
        }
        // diag110: 兜底——无论如何先写一份默认参数文件，保证 native 文件桥可用。
        // 即使 Provider/广播都失败，native 也能读到参数；用户拖滑块后广播会再更新。
        writeParamsToNative(1.0, 1.0, 1.0, 0.0, 1.0, 1.0);

        // 进程刚起来、还没有动画在跑，这里同步向 App 的 ContentProvider 要一次设置（最可靠）
        long t0 = System.nanoTime();
        boolean ok = applyPrefsFromBundle(callProvider(ctx, METHOD_PREFS));
        long costMs = (System.nanoTime() - t0) / 1000000L;
        if (costMs > 120L) {
            sProviderSlow = true;
            log("provider slow at startup (" + costMs + "ms) → 后续改异步刷新");
        }
        log(ok ? "✓ prefs loaded from provider at startup (" + costMs + "ms)"
                : "✗ provider returned no prefs, fallback to broadcast");
        // 上报激活：先走 Provider（同步、可靠），再发广播兜底
        callProvider(ctx, METHOD_ACTIVATE);
        sendToModule(ctx, OP_ACTIVATE);
        sendToModule(ctx, OP_REQUEST_PREFS);
    }

    /** 给模块 App 发显式包名广播（带上 INCLUDE_STOPPED，App 没启动也能拉开）。 */
    private static void sendToModule(Context ctx, String op) {
        try {
            Intent i = new Intent(ACTION_BRIDGE);
            i.setPackage(PKG_MODULE);
            i.putExtra(EXTRA_OP, op);
            i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            ctx.sendBroadcast(i);
            log("→ sent " + op + " to " + PKG_MODULE);
        } catch (Throwable t) {
            log("send " + op + " failed: " + t);
        }
    }

    /**
     * 调用模块 App 的 ContentProvider。
     * Provider 是 Android 官方的跨应用数据通道，不需要 root、不受广播限制影响。
     */
    private static android.os.Bundle callProvider(Context ctx, String method) {
        try {
            return ctx.getContentResolver().call(
                    android.net.Uri.parse(STATUS_URI), method, null, null);
        } catch (Throwable t) {
            log("provider " + method + " unavailable: " + t);
            return null;
        }
    }

    /** 统一的设置读取接口：广播 Intent 与 Provider Bundle 都转成它。 */
    private interface PrefReader {
        boolean getBool(String key, boolean def);

        int getInt(String key, int def);
    }

    /** 把 App 广播过来的设置写进静态字段（hook 回调直接读这些字段）。 */
    private static void applyPrefsFromIntent(Intent i) {
        if (i == null) return;
        applyPrefs(new PrefReader() {
            @Override
            public boolean getBool(String key, boolean def) {
                return i.getBooleanExtra(key, def);
            }

            @Override
            public int getInt(String key, int def) {
                return i.getIntExtra(key, def);
            }
        });
    }

    private static boolean applyPrefsFromBundle(android.os.Bundle b) {
        if (b == null || b.isEmpty()) return false;
        applyPrefs(new PrefReader() {
            @Override
            public boolean getBool(String key, boolean def) {
                return b.getBoolean(key, def);
            }

            @Override
            public int getInt(String key, int def) {
                return b.getInt(key, def);
            }
        });
        return true;
    }

    private static void applyPrefs(PrefReader r) {
        try {
            sEnabled = r.getBool(KEY_ENABLE, true);
            sPrefsLoaded = true;
            log("✓ prefs applied → enable=" + sEnabled);
            // 动画参数：滑块值 → native 因子（int 已是原生映射，这里只做安全钳制）
            double stagger   = clampInt(r.getInt("stagger", 100), 0, 200) / 100.0;
            double hStagger  = clampInt(r.getInt("h_stagger", 100), 0, 200) / 100.0;
            double iconSpeed = clampInt(r.getInt("icon_speed", 100), 50, 200) / 100.0;
            double middleRow = (double) clampInt(r.getInt("middle_row", 0), -600, 300);
            double bounce    = clampInt(r.getInt("bounce", 100), 0, 200) / 100.0;
            double dockLead  = clampInt(r.getInt("dock_lead", 100), 0, 200) / 100.0;
            // 文件桥：写到桌面进程自己的 files 目录，native 每次解锁时读取。
            // 不依赖 System.loadLibrary，避免 JNI 绑定失败导致滑块失效。
            writeParamsToNative(stagger, hStagger, iconSpeed, middleRow, bounce, dockLead);
            // JNI 桥作为备用（若 native 库能通过 loadLibrary 复用成功）
            if (sNativeReady) {
                nativeSetParams(stagger, hStagger, iconSpeed, middleRow, bounce, dockLead);
            }
        } catch (Throwable t) {
            log("apply prefs failed: " + t);
        }
    }

    /**
     * 文件桥：把 6 个动画参数序列化成 56 字节二进制（小端 magic + 6 个 double），
     * 写到 com.miui.home 进程自己的 files 目录。native 库与 Java hook 同进程，
     * 读写同一目录无需任何跨进程权限。
     *
     * muqi47：改用固定绝对路径（不依赖 Context.getFilesDir()），这样即使
     * callApplicationOnCreate 的 hook 因时序问题没拿到 Context，文件桥依然能写。
     * 桌面进程（com.miui.home）写自己的 /data/data/com.miui.home/files/ 目录
     * 是 owner 权限，SELinux 放行。
     */
    private static void writeParamsToNative(double stagger, double hStagger, double iconSpeed,
                                            double middleRow, double bounce, double dockLead) {
        try {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(56);
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            bb.putLong(0x5154414E494D0001L);  // "QTANIM\0\1"
            bb.putDouble(stagger);
            bb.putDouble(hStagger);
            bb.putDouble(iconSpeed);
            bb.putDouble(middleRow);
            bb.putDouble(bounce);
            bb.putDouble(dockLead);
            // 固定路径：与 native 侧 load_params() 尝试的第一条路径一致。
            java.io.File dir = new java.io.File("/data/data/com.miui.home/files");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "qtanim_params.bin");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(bb.array());
            fos.close();
            log("✓ params written to " + f.getAbsolutePath()
                    + " stagger=" + stagger + " h=" + hStagger + " speed=" + iconSpeed
                    + " mid=" + middleRow + " bounce=" + bounce + " dock=" + dockLead);
            writeJavaHookLog("params written OK to " + f.getAbsolutePath()
                    + " stagger=" + stagger + " speed=" + iconSpeed + " bounce=" + bounce);
        } catch (Throwable t) {
            log("write params failed: " + t);
            writeJavaHookLog("params write FAILED: " + t);
        }
    }

    /**
     * 刷新设置（拉取模式）。800ms 节流，一次解锁最多拉一次。
     */
    private static void ensurePrefsLoaded() {
        final Context ctx = sAppCtx;
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        if (now - sLastRequestMs < 800L) return;
        sLastRequestMs = now;

        if (sProviderSlow) {
            POOL.execute(new Runnable() {
                @Override
                public void run() {
                    if (!applyPrefsFromBundle(callProvider(ctx, METHOD_PREFS))) {
                        sendToModule(ctx, OP_REQUEST_PREFS);
                    }
                }
            });
            return;
        }

        long t0 = System.nanoTime();
        android.os.Bundle b = callProvider(ctx, METHOD_PREFS);
        long costMs = (System.nanoTime() - t0) / 1000000L;
        if (costMs > 120L) {
            sProviderSlow = true;
            log("provider slow (" + costMs + "ms) → 后续改为异步刷新");
        }
        if (!applyPrefsFromBundle(b)) {
            POOL.execute(new Runnable() {
                @Override
                public void run() {
                    sendToModule(ctx, OP_REQUEST_PREFS);
                }
            });
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** 统一日志：走 XposedInterface.log（libxposed 替代 XposedBridge.log）。 */
    private static void log(String s) {
        Main m = sInstance;
        if (m != null) {
            m.log(Log.INFO, "QTanimation", s);
        }
    }

    /**
     * diag120：把 Java hook 的执行轨迹写进桌面进程自己的 files 目录，方便用文件管理器
     * 直接确认 Java 侧是否被 LSPosed 加载、卡在哪一步。追加写，不覆盖历史。
     */
    private static void writeJavaHookLog(String s) {
        try {
            java.io.File dir = new java.io.File("/data/data/com.miui.home/files");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "java_hook.log");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
            fos.write((s + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }
}
