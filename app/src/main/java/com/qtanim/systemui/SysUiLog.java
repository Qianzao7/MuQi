package com.qtanim.systemui;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * SystemUI 侧的落盘调试日志。
 *
 * <p>SystemUI 进程（platform_app）里 {@code Log.*} 只能进 logcat，用户抓起来麻烦；
 * 这里把关键诊断信息<b>追加写进 hb.log</b>（优先写到 sdcard 根目录的
 * {@code /data/media/0/qtanim_hb.log}，与桌面 native 的 hb.log 同一个文件），用户用
 * 文件管理器或 {@code adb pull} 就能直接拿到，无需 root。
 *
 * <p>全部失败静默降级，绝不抛异常、绝不影响主流程。写入频率低（只在初始化、手势、
 * 配置变化时），不存在性能问题。
 */
final class SysUiLog {

    private static final String TAG = "QTSysUILog";

    private static volatile File sFile;
    private static volatile boolean sInited;

    private SysUiLog() {
    }

    private static synchronized void ensureInit() {
        if (sInited) {
            return;
        }
        sInited = true;
        String[] candidates = {
                "/data/media/0/qtanim_hb.log",
                "/storage/emulated/0/qtanim_hb.log",
                "/data/user/0/com.android.systemui/files/qtanim_hb.log",
        };
        for (String path : candidates) {
            try {
                File f = new File(path);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(f, true);
                String head = "=== [SYS] init " + System.currentTimeMillis()
                        + " sdk=" + Build.VERSION.SDK_INT
                        + " brand=" + Build.BRAND
                        + " model=" + Build.MODEL
                        + " path=" + path + " ===\n";
                fos.write(head.getBytes("UTF-8"));
                fos.close();
                sFile = f;
                Log.i(TAG, "log file ready: " + path);
                return;
            } catch (Throwable ignored) {
                // 换下一个路径
            }
        }
        Log.w(TAG, "no writable log path");
    }

    /** 写一行日志（自动加时间戳与 [SYS] 前缀），同时 echo 到 logcat 便于对照。 */
    static void log(String msg) {
        try {
            ensureInit();
            if (sFile == null) {
                return;
            }
            String line = System.currentTimeMillis() + " [SYS] " + msg + "\n";
            FileOutputStream fos = new FileOutputStream(sFile, true);
            fos.write(line.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
        Log.i(TAG, msg);
    }

    /** 带标签的一行日志。 */
    static void log(String tag, String msg) {
        log("[" + tag + "] " + msg);
    }
}
