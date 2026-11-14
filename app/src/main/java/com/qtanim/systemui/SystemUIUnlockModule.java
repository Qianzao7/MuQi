package com.qtanim.systemui;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * LSPosed 模块入口，作用域 {@code com.android.systemui}。
 *
 * <p>由 {@code META-INF/xposed/java_init.list} 发现并加载；本类及其依赖的类运行在
 * SystemUI 进程内，只允许使用 Android Framework API，不引用任何 Compose / AppCompat。
 */
public class SystemUIUnlockModule extends XposedModule {

    private static final String TAG = "QTSysUI";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        Log.i(TAG, "SystemUI loaded, installing hooks");
        try {
            SystemUIHooks.install(this, param.getDefaultClassLoader(), dataDirOf(param));
            Log.i(TAG, "hooks installed OK");
        } catch (Throwable t) {
            Log.e(TAG, "install hooks failed", t);
        }
    }

    /**
     * 宿主的 {@code dataDir}：崩溃守护状态文件的落点。
     *
     * <p>拿不到一律返回 {@code null}，由调用方退化成"不启用崩溃守护"。
     */
    private static String dataDirOf(XposedModuleInterface.PackageLoadedParam param) {
        try {
            android.content.pm.ApplicationInfo ai = param.getApplicationInfo();
            return ai != null ? ai.dataDir : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
