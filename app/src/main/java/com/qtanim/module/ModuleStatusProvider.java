package com.qtanim.module;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;

/**
 * 供桌面进程（com.miui.home）读取设置 / 上报激活的 ContentProvider。
 *
 * 为什么用 Provider：
 *  - 桌面进程是另一个 App，无法读本 App 的私有 SharedPreferences（SELinux 拦跨包访问）；
 *  - /data/local/tmp 对普通 App 和 hook 进程都不可用（shell_data_file 被拒）；
 *  - 广播方向在某些 HyperOS 版本上会被拦截；
 *  - ContentProvider 是 Android 官方跨进程数据通道，exported 后任何应用都能 call，
 *    不依赖 root，也不受广播限制影响。
 */
public class ModuleStatusProvider extends ContentProvider {

    public static final String AUTHORITY = "com.qtanim.module.status";
    public static final String METHOD_PREFS = "prefs";
    public static final String METHOD_ACTIVATE = "activate";
    public static final String PREFS = "qtanim";
    public static final String KEY_LAST_ACTIVE = "last_active_ms";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        Context ctx = getContext();
        if (ctx == null) return out;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (METHOD_ACTIVATE.equals(method)) {
            // hook 侧在桌面进程启动时调用：证明模块真的被 LSPosed 加载了
            sp.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).commit();
            out.putBoolean("ok", true);
            return out;
        }

        if (METHOD_PREFS.equals(method)) {
            // 把全部设置项返回给 hook 侧（hook 进程据此判断开关与各参数）
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    out.putBoolean(e.getKey(), (Boolean) v);
                } else if (v instanceof Integer) {
                    out.putInt(e.getKey(), (Integer) v);
                } else if (v instanceof Long) {
                    out.putLong(e.getKey(), (Long) v);
                } else if (v instanceof Float) {
                    out.putFloat(e.getKey(), (Float) v);
                } else if (v instanceof String) {
                    out.putString(e.getKey(), (String) v);
                }
            }
            // diag110: 补默认 enable，避免 prefs 为空时 hook 侧 applyPrefs 直接短路
            if (!out.containsKey("enable")) {
                out.putBoolean("enable", true);
            }
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
