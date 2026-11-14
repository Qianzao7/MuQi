# 幕启 · MuQi

> 小米 HyperOS 桌面/锁屏美化 LSPosed 模块：iOS 26 风格桌面飞入动画 + iOS 式上滑解锁。

幕启是一款基于 **LSPosed / Xposed** 的小米 HyperOS 美化模块，包含两个核心能力：

1. **iOS 26 飞入动画**：解锁桌面时图标错峰飞入，带横向错峰、回弹等可调参数。
2. **iOS 式上滑解锁**：锁屏上滑时，上层内容（时钟 / 通知）跟手整体划走，小白条联动，卡片底边左右两角呈现 iOS 式弧形圆角。

---

## 功能

### iOS 26 飞入动画
- 图标错峰飞入（纵向 + 横向错峰强度可调）
- 回弹幅度、图标动画速度可调

### 上滑解锁
- 小白条跟手、宽度可调
- 上层内容随手指整体上移，比例可调
- 卡片底边左右两角弧形圆角（半径可调）
- 松手回弹 / 上抛解锁，触发阈值与速度阈值可调

---

## 环境要求

| 项 | 要求 |
|---|---|
| 系统 | 小米 HyperOS（Android 13+，minSdk 33） |
| 框架 | LSPosed / Xposed（libxposed） |
| 作用域 | `com.android.systemui`（上滑解锁）、`com.miui.home`（桌面动画） |
| Root | Magisk / KernelSU |

> 不同 HyperOS 版本的锁屏实现差异较大，仅针对原生小米 HyperOS4 适配，魔改 ROM / 国际版不保证可用。

---

## 安装

1. 安装 APK。
2. 在 LSPosed 中启用本模块，勾选作用域：**系统界面**（`com.android.systemui`）与**系统桌面**（`com.miui.home`）。
3. 重启系统界面 / 桌面（或重启手机）。
4. 打开幕启 App，在「动画」页开启并调参。

---

## 构建

要求 **JDK 17+**。

```bash
# 1. 配置 SDK 路径
cp local.properties.example local.properties
# 编辑 local.properties，把 sdk.dir 改成你的 Android SDK 路径

# 2. 编译 debug APK
./gradlew assembleDebug --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 构建脚本 `build.sh` 提供 Linux / macOS 一键编译。

---

## 开源说明

本项目采用**部分开源**策略：

| 部分 | 许可证 | 说明 |
|---|---|---|
| 上滑解锁 + 界面（Java / Kotlin） | **AGPL-3.0** | 源码开放 |
| 飞入动画核心（`libhyperbounce_hook.so`）|  | 闭源仅提供编译后二进制 |

> 飞入动画核心以预编译 native 库（`.so`）形式随仓库分发，其源码不包含在本仓库中，经未授权请勿提取或二次分发。

---

## 致谢

特别致谢以下项目与社区（排名不分先后）：

- [Miuix](https://github.com/YuKongA/miuix)（Apache-2.0）—— UI 组件库
- [libxposed](https://github.com/libxposed/api)（Apache-2.0）—— Hook 框架 API
- [Jetpack Compose / AndroidX](https://github.com/androidx/androidx)（Apache-2.0）
- [Kotlin](https://github.com/JetBrains/kotlin)（Apache-2.0）
- [Material Icons](https://github.com/google/material-design-icons)（Apache-2.0）

详情见 [CREDITS.md](./CREDITS.md)。

---

## License

- 上滑解锁与界面部分：**[AGPL-3.0](LICENSE)**。
- 飞入动画核心（native 库）：闭源，未授权使用。
