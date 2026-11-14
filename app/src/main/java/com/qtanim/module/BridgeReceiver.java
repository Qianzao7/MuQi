package com.qtanim.module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * 模块 App 侧的广播桥。
 *
 * 桌面进程（com.miui.home）里的 hook 与该 App 之间无法直接读写对方的私有
 * SharedPreferences（SELinux 会拦 untrusted_app 跨包访问，/data/local/tmp 也不可访问），
 * 所以统一走显式包名广播这条通道，不需要 root、不需要任何文件权限。
 *
 * 两个方向：
 *  1) hook → App：action = com.qtanim.module.BRIDGE
 *       op = "activate"       桌面进程启动过 → 记录激活时间戳，首页状态卡变绿
 *       op = "request_prefs"  请求 App 把当前设置广播回桌面进程
 *  2) App → hook：action = com.qtanim.module.PREFS
 *       携带全部设置项，由 hook 侧动态注册的接收器读取
 */
public class BridgeReceiver extends BroadcastReceiver {

    public static final String ACTION_BRIDGE = "com.qtanim.module.BRIDGE";
    public static final String ACTION_PREFS = "com.qtanim.module.PREFS";
    public static final String EXTRA_OP = "op";
    public static final String OP_ACTIVATE = "activate";
    public static final String OP_REQUEST_PREFS = "request_prefs";

    public static final String PREFS = "qtanim";
    public static final String KEY_LAST_ACTIVE = "last_active_ms";
    public static final String TARGET_PKG = "com.miui.home";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String op = intent.getStringExtra(EXTRA_OP);
        if (OP_ACTIVATE.equals(op)) {
            markActive(context);
        } else if (OP_REQUEST_PREFS.equals(op)) {
            pushPrefs(context);
        }
    }

    /** 记录 hook 在本机本次开机后确实运行过（首页据此显示「已激活」）。 */
    private void markActive(Context context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                    .commit();
        } catch (Throwable ignored) {
        }
    }

    /** 把 App 的全部设置广播回桌面进程，让 hook 侧静态参数立即刷新。 */
    private void pushPrefs(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Intent out = new Intent(ACTION_PREFS);
            out.setPackage(TARGET_PKG);
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    out.putExtra(e.getKey(), (Boolean) v);
                } else if (v instanceof Integer) {
                    out.putExtra(e.getKey(), (Integer) v);
                } else if (v instanceof Long) {
                    out.putExtra(e.getKey(), (Long) v);
                } else if (v instanceof Float) {
                    out.putExtra(e.getKey(), (Float) v);
                } else if (v instanceof String) {
                    out.putExtra(e.getKey(), (String) v);
                }
            }
            context.sendBroadcast(out);
        } catch (Throwable ignored) {
        }
    }
}
