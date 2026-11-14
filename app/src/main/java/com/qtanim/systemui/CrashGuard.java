package com.qtanim.systemui;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 崩溃守护：系统界面（SystemUI）短时间内连续崩溃时，本模块主动"闭嘴"。
 *
 * <p>Xposed 模块的典型故障形态是"拖着宿主一起死"。一旦 SystemUI 进入崩溃循环，
 * 用户面对的是解不了锁的手机；这时候最该做的是让模块先消失，而不是继续努力生效。
 * 宁可白装一次，也不能让系统界面起不来。
 *
 * <h2>判据：进程活得够不够久</h2>
 * 状态落在宿主自己的 {@code files} 目录（{@code <dataDir>/files/qtsysui_guard.properties}）。
 * hook 进程与宿主同 uid，读写这个文件不需要任何权限、不需要 Context、不需要跨进程，
 * {@code onPackageLoaded} 阶段就能拿到 {@code dataDir} 直接读 —— 因此它能排在"装任何 hook"之前。
 *
 * <p>每次启动记两个时刻：本次启动时间、最后一次主线程心跳时间。下次启动时算
 * {@code 上次存活 = lastAlive - bootTime}：
 * <ul>
 *   <li>活满 {@link #ALIVE_OK_MS}（60 秒）→ 上次是正常会话，连击计数重置；</li>
 *   <li>没活满 → 上次"刚起来就没了"，连击 +1；</li>
 *   <li>连击到 {@link #MAX_BOOTS}（4）次 → 判定为崩溃循环，本次禁用本模块。</li>
 * </ul>
 *
 * <p>心跳故意跑在主线程：真出事时（主线程卡死 / 类加载递归）心跳会断，{@code lastAlive}
 * 就停在启动后不久 —— 正好把"卡死"记成"短命"。心跳落盘走后台单线程，主线程只记时间戳。
 *
 * <h2>禁用后怎么做</h2>
 * 本次启动不安装任何 hook：不碰视图、不碰触摸、不碰解锁逻辑，模块表现得像没装过，
 * 系统界面自己就能起来。恢复方式：在 LSPosed 里取消勾选本模块重启，或删掉宿主的
 * {@code files/qtsysui_guard.properties} 后重启系统界面。
 *
 * <h2>失败即放行</h2>
 * 本类里任何一步出错（拿不到 dataDir、读坏、写失败）都一律返回"不触发"——
 * 绝不能因为"安全检查本身坏了"就把模块干掉。
 */
final class CrashGuard {

    private static final String TAG = "QTSysUI";
    private static final String FILE_NAME = "qtsysui_guard.properties";

    /**
     * 构建标识：每次交付新包都 bump 一次。判定时若与上次记录不一致，说明用户换了个新版本，
     * 之前的「禁用 / 连击」记录一律作废，给新包一次正常启动机会 —— 否则用户装了修复版，
     * 却因为旧版留下的 disabled=1 永远无法恢复。
     */
    private static final String BUILD_TAG = "T3.100";

    /** 上次进程活过这么久就算一次正常会话，连击清零。只把"1 分钟内就死"当短命。 */
    private static final long ALIVE_OK_MS = 60_000L;
    /** 连续这么多次"短命启动"就判定为崩溃循环。 */
    private static final int MAX_BOOTS = 4;
    /** 主线程心跳间隔。 */
    private static final long BEAT_MS = 30_000L;

    // ---- 运行状态（只在本进程内用，每次启动都会被 evaluate 覆盖） ----
    private static volatile boolean disabled;
    private static volatile String disabledReason = "";
    private static volatile boolean ready;

    /** 本次启动时刻；只算连击用。 */
    private static long bootTime;
    /** 最后一次主线程心跳时间。 */
    private static long lastAlive;
    /** 状态文件；dataDir 拿不到时保持 null。 */
    private static File stateFile;

    /** 心跳落盘的专用后台线程：主线程只记时间戳，不碰磁盘。 */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "QTSysUI-guard-io");
        t.setDaemon(true);
        return t;
    });

    private CrashGuard() {
    }

    /**
     * 启动时判定一次。<b>必须在装任何功能 hook 之前调用。</b>
     *
     * @param dataDir 宿主的 {@code ApplicationInfo.dataDir}，可为 null（拿不到就放行）
     * @return {@code true} = 本次应禁用模块，不要装 hook
     */
    static synchronized boolean evaluate(String dataDir) {
        try {
            if (dataDir == null || dataDir.isEmpty()) {
                return false;
            }
            File dir = new File(dataDir, "files");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            stateFile = new File(dir, FILE_NAME);
            Properties p = load(stateFile);

            long now = System.currentTimeMillis();
            long prevBoot = num(p, "bootTime", 0L);
            long prevAlive = num(p, "lastAlive", 0L);
            int boots = (int) num(p, "boots", 0L);
            boolean wasDisabled = "1".equals(p.getProperty("disabled"));

            // 新版本 → 作废旧版的禁用与连击记录，按首次安装重来。
            boolean newBuild = !BUILD_TAG.equals(value(p.getProperty("buildTag")));
            if (newBuild) {
                boots = 0;
                wasDisabled = false;
                disabledReason = "";
                prevBoot = 0L;
                prevAlive = 0L;
            }

            // 是否有历史：只有「文件不存在 / bootTime 没记上」才算首次安装。
            // 注意区分"没有历史"与"一个心跳都没打就死了"：后者 prevAlive == prevBoot，
            // 存活时长为 0，是最确凿的短命证据，绝不能当成"首次安装"把连击清零。
            boolean hasHistory = prevBoot > 0L;

            long lived;
            if (!hasHistory) {
                lived = -1L;                 // 首次安装，没有历史
            } else if (prevAlive >= prevBoot) {
                lived = prevAlive - prevBoot; // 0 也是短命，不是"没有历史"
            } else {
                lived = 0L;                  // 数据倒挂，按最坏算
            }

            if (wasDisabled) {
                disabledReason = value(p.getProperty("reason"));
            } else if (!hasHistory || lived >= ALIVE_OK_MS) {
                boots = 1;                    // 首次 / 上次活得够久 → 正常会话
            } else {
                boots = boots + 1;            // 上次刚起来就没了 → 连击
            }

            boolean tripped = wasDisabled || boots >= MAX_BOOTS;
            if (tripped && !wasDisabled) {
                disabledReason = "系统界面疑似崩溃循环：上次进程仅存活 "
                        + Math.max(0L, lived / 1000L) + " 秒，已连续第 " + boots + " 次";
            }
            disabled = tripped;

            bootTime = now;
            lastAlive = now;
            p.setProperty("bootTime", String.valueOf(now));
            p.setProperty("lastAlive", String.valueOf(now));
            p.setProperty("boots", String.valueOf(boots));
            p.setProperty("disabled", disabled ? "1" : "0");
            p.setProperty("reason", disabledReason);
            p.setProperty("buildTag", BUILD_TAG);
            // 同步写：进程可能马上就没了，异步会把这一次的记录丢掉，连击就永远累不起来
            save(stateFile, p);
            ready = true;
            return disabled;
        } catch (Throwable t) {
            // 判据本身出任何问题都不能影响模块
            return false;
        }
    }

    /** 本次是否应禁用模块。evaluate 没跑成时恒为 false（退化成"没有崩溃守护"）。 */
    static boolean isDisabled() {
        return disabled;
    }

    /** 给日志看的一句话原因。 */
    static String reason() {
        return disabledReason;
    }

    /**
     * 主线程心跳，只在非禁用状态下打。
     *
     * <p>禁用后模块已经闭嘴，进程死活不再反映本模块的行为，记了也没意义。
     */
    static void startHeartbeat() {
        if (!ready || disabled) {
            return;
        }
        try {
            final Handler h = new Handler(Looper.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    if (disabled) {
                        return; // 中途被禁用就停
                    }
                    lastAlive = System.currentTimeMillis();
                    persistAlive();
                    h.postDelayed(this, BEAT_MS);
                }
            });
        } catch (Throwable ignored) {
            // 心跳挂不上只是判据退化，不影响功能
        }
    }

    /** 把心跳时间戳落到状态文件；丢到后台线程，主线程不碰磁盘。 */
    private static void persistAlive() {
        final long alive = lastAlive;
        final File f = stateFile;
        if (f == null) {
            return;
        }
        IO.execute(() -> {
            synchronized (CrashGuard.class) {
                try {
                    Properties p = load(f);
                    p.setProperty("lastAlive", String.valueOf(alive));
                    save(f, p);
                } catch (Throwable ignored) {
                    // 心跳写失败无所谓：最多让下一次存活判定偏保守
                }
            }
        });
    }

    // ------------------------------------------------------------------ 文件

    /** 读不出来就当没有历史：从 0 开始记，绝不因为读坏而拒绝启动。 */
    private static Properties load(File f) {
        Properties p = new Properties();
        try {
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                }
            }
        } catch (Throwable ignored) {
            // 读坏 = 无历史
        }
        return p;
    }

    private static void save(File f, Properties p) throws Exception {
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "QTSysUI crash guard state");
        }
    }

    private static long num(Properties p, String key, long def) {
        try {
            String s = p.getProperty(key);
            return s == null ? def : Long.parseLong(s.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static String value(String s) {
        return s == null ? "" : s;
    }
}
