# ============================================================
# 关键：禁用类名/成员名重命名（混淆）。
# Compose 运行时、Xposed 反射入口、JNI 绑定都依赖反射/序列化关系，
# 一旦重命名会启动闪退（T3.9 首版开混淆时复现）。R8 仍做死代码消除 +
# 资源收缩 + 内联优化（体积能大幅缩小），但类名/方法名保持不变。
-dontobfuscate
# ============================================================

-keep class com.qtanim.module.Main { *; }
-keep class com.qtanim.module.MainActivity { *; }
-keep class com.qtanim.module.ActiveReceiver { *; }

# Xposed 模块：manifest 里声明的组件不能被混淆/移除
-keep class com.qtanim.module.BridgeReceiver { *; }
-keep class com.qtanim.module.ModuleStatusProvider { *; }

# native 方法签名（JNI 绑定，不可改名）
-keepclasseswithmembers class com.qtanim.module.Main {
    native <methods>;
}

# libxposed API 102（io.github.libxposed:api:102.0.0）
# 官方 R8 规则：保留模块入口类 + 重写 java_init.list（混淆时重写类名）
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# miuix 系列：内部有对 MIUI 系统类/属性的反射，屏蔽警告即可
-dontwarn top.yukonga.miuix.**

# SystemUI 上滑解锁（融合功能）：反射入口 SystemUIUnlockModule 及其依赖类。
# 这些类运行在 com.android.systemui 进程，只依赖 Framework API，不引用 Compose。
# 防止 R8 死代码消除把依赖链（SystemUIHooks / UnlockController / MiBlurGlassView /
# MiBlurHelper / UnlockHandleView / CrashGuard）误删。
-keep class com.qtanim.systemui.** { *; }
