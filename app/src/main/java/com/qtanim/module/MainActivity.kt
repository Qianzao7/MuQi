package com.qtanim.module

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.qtanim.module.ui.BarBackdropContent
import com.qtanim.module.ui.BarBlurHost
import com.qtanim.module.ui.ColorfulIcon
import com.qtanim.module.ui.DockBar
import com.qtanim.module.ui.GlassCard
import com.qtanim.module.ui.LiquidGlassNavigationItem
import com.qtanim.module.ui.LocalFlowingBackground
import com.qtanim.module.ui.LocalGlowMode
import com.qtanim.module.ui.ThemeSettingsPage
import com.qtanim.module.ui.pageBackground

private const val PREF_THEME_MODE = "pref_theme_mode"       // 0 跟随 / 1 浅 / 2 深
private const val PREF_FLOATING_BAR = "pref_floating_bar"   // bool
private const val PREF_LIQUID_GLASS = "pref_liquid_glass"   // bool
private const val PREF_BLUR = "pref_blur"                   // bool
private const val PREF_ABOUT_BG = "pref_about_bg"           // 0 小米流光 / 1 彩色流光
private const val PREF_GLOW_MODE = "pref_glow_mode"         // 0 全局 / 1 局部(横幅) / 2 仅关于页
private const val PREF_SUBPAGE_TRANSITION = "pref_subpage_transition" // 0 侧滑手势 / 1 卡片弹出

// —— 创作者信息（酷安）——
// 昵称：显示在「关于」里
private const val CREATOR_NAME = "千千千早"
// 酷安用户 ID（个人主页链接 coolapk.com/u/<UID> 里的那串数字）
private const val CREATOR_COOLAPK_UID = "38305973"

// —— 开源仓库 ——
// GitHub 项目仓库链接（关于页点击跳转）
private const val GITHUB_REPO_URL = "https://github.com/Qianzao7/MuQi"

// 激活 / 成功的绿色：对齐 HyperOS 设置里的深绿，比 iOS 的浅绿（0xFF34C759）更沉
private val ActivateGreen = Color(0xFF00A36C)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // 启动时把当前设置广播给桌面进程（若其进程在运行，hook 会立刻拿到最新值）
        pushPrefsToLauncher(this)
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台都补写一次参数（节流在 writeParamsViaRoot 内部），
        // 确保用户只要打开过 App，参数文件就落盘，不依赖冷启动时机。
        pushPrefsToLauncher(this)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE) }

    // ———— 所有可变的主题 / 底栏状态（写 pref 时同步更新 state，UI 自动重组） ————
    var themeMode by remember { mutableIntStateOf(sp.getInt(PREF_THEME_MODE, 0)) }
    var floatingBar by remember { mutableStateOf(sp.getBoolean(PREF_FLOATING_BAR, true)) }
    var liquidGlass by remember { mutableStateOf(sp.getBoolean(PREF_LIQUID_GLASS, true)) }
    var blurEnabled by remember { mutableStateOf(sp.getBoolean(PREF_BLUR, true)) }
    var aboutBgStyle by remember { mutableIntStateOf(sp.getInt(PREF_ABOUT_BG, 0)) }
    var glowMode by remember { mutableIntStateOf(sp.getInt(PREF_GLOW_MODE, 0)) }
    var subpageTransition by remember { mutableIntStateOf(sp.getInt(PREF_SUBPAGE_TRANSITION, 1)) }

    // 重新创建 ThemeController 响应 themeMode 变化
    val themeModeActual = when (themeMode) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val controller = remember(themeModeActual) { ThemeController(colorSchemeMode = themeModeActual) }

    MiuixTheme(controller = controller) {
        App(
            themeMode = themeMode,
            onThemeModeChange = { v ->
                themeMode = v
                sp.edit().putInt(PREF_THEME_MODE, v).commit(); pushPrefsToLauncher(ctx)
            },
            floatingBar = floatingBar,
            onFloatingBarChange = { v ->
                floatingBar = v
                sp.edit().putBoolean(PREF_FLOATING_BAR, v).commit(); pushPrefsToLauncher(ctx)
                if (!v) {
                    liquidGlass = false
                    sp.edit().putBoolean(PREF_LIQUID_GLASS, false).commit(); pushPrefsToLauncher(ctx)
                }
            },
            liquidGlass = liquidGlass,
            onLiquidGlassChange = { v ->
                liquidGlass = v
                sp.edit().putBoolean(PREF_LIQUID_GLASS, v).commit(); pushPrefsToLauncher(ctx)
            },
            blurEnabled = blurEnabled,
            onBlurChange = { v ->
                blurEnabled = v
                sp.edit().putBoolean(PREF_BLUR, v).commit(); pushPrefsToLauncher(ctx)
            },
            aboutBgStyle = aboutBgStyle,
            onAboutBgChange = { v ->
                aboutBgStyle = v
                sp.edit().putInt(PREF_ABOUT_BG, v).commit()
                // 彩色流光只支持「局部」显示，切过去时自动把流光效果收窄为局部
                if (v == 1 && glowMode != 1) {
                    glowMode = 1
                    sp.edit().putInt(PREF_GLOW_MODE, 1).commit()
                }
            },
            glowMode = glowMode,
            onGlowModeChange = { v ->
                // 全局 / 仅关于页是小米流光专属，彩色流光只能局部
                if (aboutBgStyle == 1 && v != 1) {
                    Toast.makeText(ctx, "彩色流光仅支持「局部」显示", Toast.LENGTH_SHORT).show()
                } else {
                    glowMode = v
                    sp.edit().putInt(PREF_GLOW_MODE, v).commit()
                }
            },
            subpageTransition = subpageTransition,
            onSubpageTransitionChange = { v ->
                subpageTransition = v
                sp.edit().putInt(PREF_SUBPAGE_TRANSITION, v).commit()
            },
        )
    }
}

/** 「关于」页下的子页面：主题设置 / 捐赠 / 日志诊断 / 开源声明。null 即停留在关于页主列表。 */
private enum class AboutSubPage { None, ThemeSettings, Donate, LogDiag, OpenSource }

/** 记录组件在窗口中的中心坐标（用于 icon-zoom 转场的缩放锚点）。 */
private fun Modifier.recordNavOrigin(holder: MutableState<Offset>): Modifier =
    this.onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        holder.value = Offset(
            pos.x + coords.size.width / 2f,
            pos.y + coords.size.height / 2f,
        )
    }

@Composable
private fun App(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    floatingBar: Boolean,
    onFloatingBarChange: (Boolean) -> Unit,
    liquidGlass: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    aboutBgStyle: Int,
    onAboutBgChange: (Int) -> Unit,
    glowMode: Int,
    onGlowModeChange: (Int) -> Unit,
    subpageTransition: Int,
    onSubpageTransitionChange: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showRestartSystemUiDialog by remember { mutableStateOf(false) }
    var aboutSub by remember { mutableStateOf(AboutSubPage.None) }
    // 二级界面入口卡片的屏幕中心坐标：icon-zoom 转场从「点击的那张卡片」位置生长出来，
    // 实现 ColorOS「从哪来回哪去」的手感。未点击时用屏幕中心兜底。
    var clickOrigin by remember { mutableStateOf(Offset.Zero) }

    // 屏幕像素尺寸：icon-zoom 转场把 clickOrigin（root 坐标）归一化为缩放锚点 fraction。
    val density = LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // 主页面用 HorizontalPager 承载，支持左右滑动切换；currentPage 即当前 tab。
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val pagerScope = rememberCoroutineScope()

    // 深色模式：状态栏 / 导航栏图标跟着反色，否则黑底上黑图标看不见
    val isDark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    // 当前底栏样式：液态玻璃 > 悬浮 > 标准
    val dockStyle = when {
        liquidGlass && floatingBar -> 2
        floatingBar -> 1
        else -> 0
    }

    val navItems = listOf(
        LiquidGlassNavigationItem(ImageVector.vectorResource(R.drawable.ic_nav_home), "主页"),
        LiquidGlassNavigationItem(ImageVector.vectorResource(R.drawable.ic_nav_anim), "动画"),
        LiquidGlassNavigationItem(ImageVector.vectorResource(R.drawable.ic_nav_about), "关于"),
    )

    // 主题设置 / 捐赠是「关于」页的子页面：返回键应回到关于页，而不是直接退出应用
    BackHandler(enabled = aboutSub != AboutSubPage.None) { aboutSub = AboutSubPage.None }

    CompositionLocalProvider(LocalGlowMode provides glowMode) {
        BarBlurHost(
            enabled = blurEnabled,
            captureForEffects = blurEnabled,
            liquidGlassEnabled = dockStyle == 2 && aboutSub == AboutSubPage.None,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 兜底背景：AnimatedContent 转场时页面缩放/位移露出的区域，
                    // 若无此背景会透到 Window 白色底，深色模式下就会「边缘闪白」。
                    .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F2)),
            ) {
                // 全局流光模式：整页背景铺小米「我的设备」风格极光（浅色/深色自动），
                // 各页面背景转为透明让流光透出；其余模式则走各页面自己的背景。
                if (glowMode == 0) {
                    MeshAuroraBackground(modifier = Modifier.fillMaxSize(), dark = isDark)
                }
                BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = aboutSub,
                        transitionSpec = {
                            if (subpageTransition == 1) {
                                // 卡片弹出（icon-zoom）：子页面从「点击的那张入口卡片」位置
                                // spring 缩放弹出（带轻微 overshoot 回弹），返回时反向收回。
                                // 缩放锚点 = 点击卡片的屏幕中心（clickOrigin）。
                                val springSpec = spring<Float>(
                                    dampingRatio = 0.72f,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                                val originX = (clickOrigin.x / screenWidthPx).coerceIn(0f, 1f)
                                val originY = (clickOrigin.y / screenHeightPx).coerceIn(0f, 1f)
                                val pivot = TransformOrigin(originX, originY)
                                if (targetState != AboutSubPage.None) {
                                    val enter: EnterTransition = scaleIn(
                                        animationSpec = springSpec,
                                        initialScale = 0.0f,
                                        transformOrigin = pivot,
                                    ) + fadeIn(animationSpec = springSpec)
                                    val exit: ExitTransition =
                                        fadeOut(animationSpec = springSpec)
                                    enter togetherWith exit
                                } else {
                                    val enter: EnterTransition = fadeIn(animationSpec = springSpec)
                                    val exit: ExitTransition = scaleOut(
                                        animationSpec = springSpec,
                                        targetScale = 0.0f,
                                        transformOrigin = pivot,
                                    ) + fadeOut(animationSpec = springSpec)
                                    enter togetherWith exit
                                }
                            } else {
                                // 侧滑手势：轻量 iOS 式 push/pop（slide + fade + 旧页轻微压暗）。
                                val dur = 280
                                if (targetState != AboutSubPage.None) {
                                    val enter: EnterTransition =
                                        slideInHorizontally(
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing),
                                            initialOffsetX = { fullWidth: Int -> fullWidth },
                                        ) + fadeIn(animationSpec = tween(dur, easing = FastOutSlowInEasing))
                                    val exit: ExitTransition =
                                        slideOutHorizontally(
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                                        ) + fadeOut(animationSpec = tween(dur, easing = FastOutSlowInEasing))
                                    enter togetherWith exit
                                } else {
                                    val enter: EnterTransition =
                                        slideInHorizontally(
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing),
                                            initialOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                                        ) + fadeIn(animationSpec = tween(dur, easing = FastOutSlowInEasing))
                                    val exit: ExitTransition =
                                        slideOutHorizontally(
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth: Int -> fullWidth },
                                        ) + fadeOut(animationSpec = tween(dur, easing = FastOutSlowInEasing))
                                    enter togetherWith exit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "aboutSub",
                    ) { sub ->
                        when (sub) {
                            AboutSubPage.ThemeSettings -> ThemeSettingsPage(
                                currentThemeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                                floating = floatingBar,
                                onFloatingChange = onFloatingBarChange,
                                liquidGlass = liquidGlass,
                                onLiquidGlassChange = onLiquidGlassChange,
                                blur = blurEnabled,
                                onBlurChange = onBlurChange,
                                aboutBgStyle = aboutBgStyle,
                                onAboutBgChange = onAboutBgChange,
                                glowMode = glowMode,
                                onGlowModeChange = onGlowModeChange,
                                subpageTransition = subpageTransition,
                                onSubpageTransitionChange = onSubpageTransitionChange,
                                onBack = { aboutSub = AboutSubPage.None },
                            )
                            AboutSubPage.Donate -> DonatePage(
                                onBack = { aboutSub = AboutSubPage.None },
                            )
                            AboutSubPage.LogDiag -> LogDiagPage(
                                onBack = { aboutSub = AboutSubPage.None },
                            )
                            AboutSubPage.OpenSource -> OpenSourcePage(
                                onBack = { aboutSub = AboutSubPage.None },
                            )
                            else -> HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                when (page) {
                                    0 -> HomePage(
                                        sp,
                                        onRestartClick = { showRestartDialog = true },
                                        onRestartSystemUiClick = { showRestartSystemUiDialog = true },
                                    )
                                    1 -> AnimPage(sp)
                                    else -> AboutPage(
                                        sp,
                                        aboutBgStyle = aboutBgStyle,
                                        onRestartClick = { showRestartDialog = true },
                                        onNavigate = { target, origin ->
                                            clickOrigin = origin
                                            aboutSub = target
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                // 子页面中隐藏底栏（自带返回），避免两层导航混在一起
                if (aboutSub == AboutSubPage.None) {
                    DockBar(
                        style = dockStyle,
                        selectedTabIndex = { pagerState.currentPage },
                        onTabSelected = { index ->
                            pagerScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        items = navItems,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (showRestartDialog) {
                    RestartLauncherDialog(ctx, sp, onDismiss = { showRestartDialog = false })
                }

                if (showRestartSystemUiDialog) {
                    RestartSystemUiDialog(onDismiss = { showRestartSystemUiDialog = false })
                }
            }
        }
    }
}

// ========================= 主页 =========================
@Composable
private fun HomePage(
    sp: android.content.SharedPreferences,
    onRestartClick: () -> Unit,
    onRestartSystemUiClick: () -> Unit,
) {
    val ctx = LocalContext.current
    // 激活状态：native 库注入桌面进程后会在 sdcard 写 qtanim_active 标记，
    // writeParamsViaRoot 用 su 读该标记后回写 lsp_active 布尔值。这里每 2 秒
    // 重新读一次 prefs；若仍为未激活则每 10 秒补触发一次检测，桌面进程重启后
    // 状态卡会自动变绿，无需退出重进。
    var isLsposedActive by remember { mutableStateOf(isModuleActive(sp)) }
    LaunchedEffect(Unit) {
        var lastCheck = 0L
        while (true) {
            isLsposedActive = isModuleActive(sp)
            if (!isLsposedActive) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCheck > 10000L) {
                    lastCheck = now
                    writeParamsViaRoot(ctx)
                }
            }
            kotlinx.coroutines.delay(2000)
        }
    }
    val appVersion = remember { getAppVersion(ctx) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground())
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MiuixText(
                text = "幕启",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        item { LsposedStatusCard(isLsposedActive, appVersion) }
        item { ParamBridgeStatusCard(sp) }
        item {
            GlassCard(cornerRadius = 22.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    InfoRow("模块版本", appVersion)
                    InfoRow("作用域", "系统桌面 / 系统界面")
                    InfoRow("Xposed API", "102")
                    InfoRow("设备型号", Build.MODEL)
                    InfoRow("系统版本", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", isLast = true)
                }
            }
        }
        item {
            GlassCard(cornerRadius = 22.dp) {
                ArrowPreference(
                    title = "重启系统桌面",
                    summary = "需要 Root 权限，立即生效无需重启手机",
                    startAction = { ColorfulIcon(MiuixIcons.Refresh, Color(0xFF36D1DC)) },
                    onClick = onRestartClick
                )
            }
        }
        item {
            GlassCard(cornerRadius = 22.dp) {
                ArrowPreference(
                    title = "重启系统界面",
                    summary = "上滑解锁的改动生效，需要 Root 权限",
                    startAction = { ColorfulIcon(MiuixIcons.Refresh, Color(0xFF36D1DC)) },
                    onClick = onRestartSystemUiClick
                )
            }
        }
    }
}

@Composable
private fun LsposedStatusCard(active: Boolean, appVersion: String) {
    // 已激活 = 绿色，未激活 = 橙色，一眼能区分
    val container = if (active) ActivateGreen else Color(0xFFFF9500)
    val content = Color.White
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        colors = CardDefaults.defaultColors(color = container),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixIcon(
                painter = painterResource(
                    if (active) R.drawable.ic_check_circle_outline else R.drawable.ic_status_warning
                ),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.padding(start = 20.dp)) {
                MiuixText(
                    text = if (active) "LSPosed 已激活" else "LSPosed 未激活",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = content
                )
                MiuixText(
                    text = "幕启 v$appVersion · " + if (active) "动画模块运行中" else "请在 LSPosed 中启用模块并重启系统桌面",
                    fontSize = 14.sp,
                    color = content.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** 参数通道状态卡：显示 root 直写参数文件的结果（writeParamsViaRoot 回写 sp），可点击重试。 */
@Composable
private fun ParamBridgeStatusCard(sp: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(sp.getString("param_bridge_status", null)) }
    var diag by remember { mutableStateOf(sp.getString("param_bridge_diag", "")) }
    LaunchedEffect(Unit) {
        while (true) {
            status = sp.getString("param_bridge_status", null)
            diag = sp.getString("param_bridge_diag", "")
            kotlinx.coroutines.delay(1000)
        }
    }
    val (color, title) = when (status) {
        "ok" -> ActivateGreen to "参数通道已通，滑块已生效"
        "verify_fail" -> Color(0xFFFF9500) to "参数已写入，但校验未通过"
        "write_fail" -> Color(0xFFFF3B30) to "参数写入失败：未获取到 root 授权"
        "exception" -> Color(0xFFFF3B30) to "参数写入异常"
        else -> Color(0xFF8E8E93) to "参数通道待检测"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            colors = CardDefaults.defaultColors(color = color),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pushPrefsToLauncher(ctx) }  // 点击重试一次
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    painter = painterResource(
                        if (status == "ok") R.drawable.ic_check_circle_outline else R.drawable.ic_status_warning
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    MiuixText(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    MiuixText(
                        text = "点此重试写入",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        val diagText = diag.orEmpty()
        if (diagText.isNotBlank() && status != "ok") {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                MiuixText(
                    text = diagText.take(500),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun RestartLauncherDialog(
    ctx: Context,
    sp: android.content.SharedPreferences,
    onDismiss: () -> Unit,
) {
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var restarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasRoot = checkRoot()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            cornerRadius = 22.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                MiuixText(
                    text = "重启系统桌面",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                if (hasRoot == null) {
                    MiuixText(
                        text = "正在检测 Root 权限…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else if (hasRoot == false) {
                    MiuixText(
                        text = "需要获取 Root 权限才能重启系统桌面。\n请在 Magisk / KernelSU 中授予本应用 Root 权限后重试。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    MiuixText(
                        text = "确认重启系统桌面？\n桌面会立即重启，动画设置将生效，无需重启手机。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        MiuixText("取消", color = MiuixTheme.colorScheme.primary)
                    }
                    if (hasRoot == true && !restarting) {
                        androidx.compose.material3.TextButton(onClick = {
                            restarting = true
                            Thread {
                                restartLauncher(ctx)
                                onDismiss()
                            }.start()
                        }) {
                            MiuixText("确认", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun checkRoot(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.contains("uid=0")
    } catch (e: Exception) {
        false
    }
}

private fun restartLauncher(ctx: Context) {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop com.miui.home"))
        process.waitFor()
    } catch (e: Exception) {
    }
}

@Composable
private fun RestartSystemUiDialog(
    onDismiss: () -> Unit,
) {
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var restarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasRoot = checkRoot()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            cornerRadius = 22.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                MiuixText(
                    text = "重启系统界面",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                if (hasRoot == null) {
                    MiuixText(
                        text = "正在检测 Root 权限…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else if (hasRoot == false) {
                    MiuixText(
                        text = "需要获取 Root 权限才能重启系统界面。\n请在 Magisk / KernelSU 中授予本应用 Root 权限后重试。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    MiuixText(
                        text = "确认重启系统界面？\n锁屏会短暂黑一下，上滑解锁的改动将生效，无需重启手机。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        MiuixText("取消", color = MiuixTheme.colorScheme.primary)
                    }
                    if (hasRoot == true && !restarting) {
                        androidx.compose.material3.TextButton(onClick = {
                            restarting = true
                            Thread {
                                restartSystemUi()
                                onDismiss()
                            }.start()
                        }) {
                            MiuixText("确认", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun restartSystemUi() {
    try {
        val pid = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof com.android.systemui"))
            .inputStream.bufferedReader().readText().trim()
        if (pid.isNotEmpty()) {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "kill $pid")).waitFor()
            Thread.sleep(800)
            val still = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof com.android.systemui"))
                .inputStream.bufferedReader().readText().trim()
            if (still.isNotEmpty()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -9 $still")).waitFor()
            }
        }
    } catch (e: Exception) {
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false, url: String? = null) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { openUrl(ctx, url) } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MiuixText(
                text = label,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            MiuixText(
                text = value,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (url != null) {
            MiuixIcon(
                imageVector = MiuixIcons.Forward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    // 行尾补间距，保证行与行之间均匀；最后一行不加，避免卡片底部多余留白
    if (!isLast) Spacer(Modifier.height(16.dp))
}

// ========================= 动画页 =========================
@Composable
private fun AnimPage(sp: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground())
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MiuixText(
                text = "动画",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        item {
            GlassCard(cornerRadius = 22.dp) {
                MiuixText(
                    text = "iOS26 飞入",
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
                var iosEnabled by remember { mutableStateOf(sp.getBoolean("enable", true)) }
                SwitchPreference(
                    title = "启用动画效果",
                    summary = "重启系统桌面后生效",
                    checked = iosEnabled,
                    startAction = {
                        // 开启=解锁、关闭=锁定，图标跟开关状态一致
                        ColorfulIcon(
                            if (iosEnabled) MiuixIcons.Unlock else MiuixIcons.Lock,
                            Color(0xFF4FACFE),
                        )
                    },
                    onCheckedChange = {
                        iosEnabled = it
                        sp.edit().putBoolean("enable", it).commit(); pushPrefsToLauncher(ctx)
                        writeEnabledFlag(it)
                    }
                )
                SliderPref(sp, "stagger", "错峰强度", 0, 200, 100)
                SliderPref(sp, "h_stagger", "横向错峰", 0, 200, 100)
                SliderPref(sp, "bounce", "回弹幅度", 0, 200, 100)
                SliderPref(sp, "icon_speed", "图标动画速度", 50, 200, 100)
            }
        }
        item {
            GlassCard(cornerRadius = 22.dp) {
                MiuixText(
                    text = "上滑解锁",
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
                MiuixText(
                    text = "锁屏上滑解锁的 iOS 样式分层滑动：上层（时钟等）随手指整体划走，小白条跟手，卡片底边左右两角弧形圆角。调整后重启系统界面生效。",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                var sysuiEnabled by remember { mutableStateOf(sp.getBoolean("sysui_enable", true)) }
                SwitchPreference(
                    title = "启用上滑解锁",
                    summary = "关闭后锁屏不再接管上滑手势，重启系统界面生效",
                    checked = sysuiEnabled,
                    startAction = {
                        ColorfulIcon(
                            if (sysuiEnabled) MiuixIcons.Unlock else MiuixIcons.Lock,
                            Color(0xFF4FACFE),
                        )
                    },
                    onCheckedChange = {
                        sysuiEnabled = it
                        sp.edit().putBoolean("sysui_enable", it).commit(); pushPrefsToLauncher(ctx)
                    }
                )
                SliderPref(sp, "sysui_corner_radius_v2", "卡片底边圆角", 8, 48, 28)
                SliderPref(sp, "sysui_bg_dim", "背景明暗度", 0, 100, 30)
                SliderPref(sp, "sysui_threshold", "解锁触发阈值", 20, 100, 50)
                SliderPref(sp, "sysui_fling_velocity", "上抛解锁速度", 500, 3000, 1200, step = 50)
                SliderPref(sp, "sysui_auto_speed", "自动上滑速度", 1, 20, 10)
                SliderPref(sp, "sysui_handle_width_v2", "小白条宽度", 40, 300, 130)
                SliderPref(sp, "sysui_shift_ratio", "内容划出距离", 50, 100, 100)
            }
        }
    }
}

@Composable
private fun SliderPref(
    sp: android.content.SharedPreferences,
    key: String,
    title: String,
    min: Int,
    max: Int,
    default: Int,
    step: Int = 1
) {
    val ctx = LocalContext.current
    val stored = sp.getInt(key, default)
    var value by remember { mutableIntStateOf(if (stored in min..max) stored else default) }
    var showInput by remember { mutableStateOf(false) }
    SliderPreference(
        title = title,
        value = value.toFloat(),
        valueRange = min.toFloat()..max.toFloat(),
        steps = (((max - min) / step) - 1).coerceAtLeast(0),
        valueText = value.toString(),
        onClick = { showInput = true },
        onValueChange = {
            value = it.toInt()
            sp.edit().putInt(key, it.toInt()).commit(); pushPrefsToLauncher(ctx)
        }
    )
    if (showInput) {
        NumberInputDialog(
            title = title,
            min = min,
            max = max,
            default = default,
            initial = value,
            onConfirm = { v ->
                value = v
                sp.edit().putInt(key, v).commit(); pushPrefsToLauncher(ctx)
                showInput = false
            },
            onDismiss = { showInput = false }
        )
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    min: Int,
    max: Int,
    default: Int,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            cornerRadius = 22.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                MiuixText(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                MiuixText(
                    text = "默认值：$default | 范围：$min ~ $max",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(
                        onClick = { text = default.toString() },
                        modifier = Modifier.weight(1f)
                    ) {
                        MiuixText("恢复默认", color = MiuixTheme.colorScheme.primary)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        MiuixText("取消", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val parsed = text.toIntOrNull()
                            val v = when {
                                parsed == null -> default
                                parsed < min -> min
                                parsed > max -> max
                                else -> parsed
                            }
                            onConfirm(v)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        MiuixText("确认", color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ========================= 关于页 =========================
@Composable
private fun AboutPage(
    sp: android.content.SharedPreferences,
    aboutBgStyle: Int,
    onRestartClick: () -> Unit,
    onNavigate: (AboutSubPage, Offset) -> Unit,
) {
    val ctx = LocalContext.current
    val appVersion = remember { getAppVersion(ctx) }
    val listState = rememberLazyListState()
    // 流光效果模式：0 全局 / 1 局部(横幅) / 2 仅关于页
    val glowMode = LocalGlowMode.current
    val isDark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    // 仅关于页模式：整个关于页透出流光（其余页面纯色）
    val flowing = glowMode == 2
    // 顶部区块文字颜色：彩色流光 / 深色小米流光用白色；浅色小米流光用深色
    val bannerTitleColor = if (aboutBgStyle == 1) Color.White
        else if (isDark) Color.White
        else Color(0xFF1A1A1A)

    // —— 头部布局：图标 + 名称 + 版本号【固定不动】（zIndex 低），卡片 LazyColumn 覆盖其上
    //    （zIndex 高）。上滑时卡片盖住图标，图标 alpha 淡出 + 平滑模糊，实现「被覆盖 + 模糊消失」。
    //    「关于」标题独立在 LazyColumn 之外，完全不参与动画。 ——
    val density = LocalDensity.current
    val headerHeight = 210.dp          // 图标区块高度
    val cardGap = 48.dp                // 图标底部到第一张卡片的间距（加大，视觉协调）
    val headerHeightPx = with(density) { headerHeight.toPx() }

    // 折叠进度：卡片滚动「覆盖图标」的程度（0 未滚 → 1 完全覆盖）。
    // 注意：LazyListItemInfo.offset 在未滚动时是 0（相对可见区起点，不含 contentPadding），
    // 向上滚动后变为负值，因此滚动量 = -offset，静止时 collapse=0（图标可见）。
    val headerCollapse by remember(headerHeightPx) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            when {
                first == null -> 0f
                first.index > 0 -> 1f
                else -> {
                    val scrolled = (-first.offset).toFloat().coerceAtLeast(0f)
                    (scrolled / headerHeightPx).coerceIn(0f, 1f)
                }
            }
        }
    }

    // 模糊：跨过阈值后平滑 0→14dp，避免 blur 每帧跟随滚动重算导致的「颤抖」。
    val headerBlurred = headerCollapse > 0.45f
    val headerBlur by animateDpAsState(
        targetValue = if (headerBlurred) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "headerBlur",
    )

    CompositionLocalProvider(LocalFlowingBackground provides flowing) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (flowing) {
                // 仅关于页：整个关于页背景流光
                if (aboutBgStyle == 1) {
                    AuroraBackground(modifier = Modifier.fillMaxSize())
                } else {
                    MeshAuroraBackground(modifier = Modifier.fillMaxSize(), dark = isDark)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (flowing) Color.Transparent else pageBackground())
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                // —— 「关于」标题：固定顶部，不参与任何滚动 / 折叠动画 ——
                MiuixText(
                    text = "关于",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                )

                // —— 内容区：Box 叠放。固定图标区块（zIndex 低）在下，卡片 LazyColumn（zIndex 高）
                //    在上。上滑时卡片覆盖图标，图标 alpha 淡出 + 平滑模糊消失；下拉重新出现。 ——
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // 固定图标区块：位置不动，只随滚动淡出 + 平滑模糊，被上层卡片覆盖。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(headerHeight)
                            .zIndex(0f)
                            .then(
                                if (glowMode == 1) Modifier.clip(RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .blur(headerBlur)
                            .graphicsLayer { alpha = 1f - headerCollapse },
                    ) {
                        // 局部模式带流光横幅背景（圆角裁剪）；全局 / 仅关于页模式透明显示。
                        if (glowMode == 1) {
                            if (aboutBgStyle == 1) {
                                AuroraBackground(modifier = Modifier.fillMaxSize())
                            } else {
                                MeshAuroraBackground(modifier = Modifier.fillMaxSize(), dark = isDark)
                            }
                        }
                        AppHeaderContent(appVersion, bannerTitleColor)
                    }

                    // 卡片列表：覆盖在图标之上，上滑时盖住图标。
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            top = headerHeight + cardGap,
                            end = 16.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                    // 创作者：头像 + 昵称，整行可点，直接跳酷安主页
                item {
                    GlassCard(cornerRadius = 22.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openCoolapk(ctx) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.creator_avatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                MiuixText(
                                    text = CREATOR_NAME,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                MiuixText(
                                    text = "酷安 @$CREATOR_NAME",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            MiuixIcon(
                                imageVector = MiuixIcons.Forward,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                item {
                    val origin = remember { mutableStateOf(Offset.Zero) }
                    GlassCard(
                        modifier = Modifier.recordNavOrigin(origin),
                        cornerRadius = 22.dp,
                    ) {
                        ArrowPreference(
                            title = "捐赠",
                            summary = "感谢您的支持",
                            startAction = { ColorfulIcon(MiuixIcons.FavoritesFill, Color(0xFFFF5F6D)) },
                            onClick = { onNavigate(AboutSubPage.Donate, origin.value) },
                        )
                    }
                }
                item {
                    val origin = remember { mutableStateOf(Offset.Zero) }
                    GlassCard(
                        modifier = Modifier.recordNavOrigin(origin),
                        cornerRadius = 22.dp,
                    ) {
                        ArrowPreference(
                            title = "主题设置",
                            summary = "主题模式 · 悬浮底栏 · 液态玻璃 · 模糊",
                            startAction = { ColorfulIcon(MiuixIcons.Theme, Color(0xFF7C4DFF)) },
                            onClick = { onNavigate(AboutSubPage.ThemeSettings, origin.value) },
                        )
                    }
                }
                item {
                    val origin = remember { mutableStateOf(Offset.Zero) }
                    GlassCard(
                        modifier = Modifier.recordNavOrigin(origin),
                        cornerRadius = 22.dp,
                    ) {
                        ArrowPreference(
                            title = "日志与诊断",
                            summary = "收集日志开关 · 导出诊断日志",
                            startAction = { ColorfulIcon(MiuixIcons.ListView, Color(0xFFFFA751)) },
                            onClick = { onNavigate(AboutSubPage.LogDiag, origin.value) },
                        )
                    }
                }
                item {
                    val origin = remember { mutableStateOf(Offset.Zero) }
                    GlassCard(
                        modifier = Modifier.recordNavOrigin(origin),
                        cornerRadius = 22.dp,
                    ) {
                        ArrowPreference(
                            title = "关于",
                            summary = "项目仓库 · 框架信息 · 致谢",
                            startAction = { ColorfulIcon(MiuixIcons.Layers, Color(0xFF11998E)) },
                            onClick = { onNavigate(AboutSubPage.OpenSource, origin.value) },
                        )
                    }
                }
                // 底部留白：保证列表总高明显超过一屏，让下方卡片有足够滚动空间。
                item(key = "about_bottom_spacer") {
                    Spacer(Modifier.height(240.dp))
                }
            }
            }
            }
        }
    }
}

/** 关于页顶部的应用信息区块：图标 + 名字 + 版本号，居中对齐。 */
@Composable
private fun AppHeaderContent(appVersion: String, titleColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 应用图标：用 Image 原样显示彩色图案（MiuixIcon 会套 tint 把彩色图标染色）
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
        MiuixText(
            text = "幕启",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            modifier = Modifier.padding(top = 16.dp)
        )
        MiuixText(
            text = "v$appVersion",
            fontSize = 15.sp,
            color = titleColor.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 「关于」页顶部的流光背景：彩色渐变缓慢流动，对齐 HyperOS「我的设备」的观感。 */
@Composable
private fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auroraShift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2E6BE6),
            Color(0xFF8E5BD6),
            Color(0xFFE25B9A),
            Color(0xFFF0A34B),
            Color(0xFF2E6BE6),
        ),
        start = Offset(-700f + shift * 1400f, 0f),
        end = Offset(700f + shift * 1400f, 1000f),
    )
    Box(modifier = modifier.background(brush))
}

/** 极光背景里的一个柔和光斑。 */
private class AuroraBlob(
    val x: Float,     // 中心 x（相对画布宽，0..1）
    val y: Float,     // 中心 y（相对画布高，0..1）
    val r: Float,     // 半径（相对画布长边）
    val phase: Float, // 漂移相位
    val color: Color,
)

/** 小米「我的设备」风格的流光背景：多个柔和光斑叠加的 mesh gradient（极光）效果，
 *  浅色 / 深色自动切换配色，光斑沿椭圆轨迹明显漂移、循环往复，营造「流光」动感。 */
@Composable
private fun MeshAuroraBackground(modifier: Modifier = Modifier, dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "meshAurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "meshShift",
    )

    val blobs = remember(dark) {
        if (dark) listOf(
            AuroraBlob(0.50f, 0.06f, 0.85f, 0.0f, Color(0xFF2E6BB0)),  // 顶部青蓝
            AuroraBlob(0.88f, 0.12f, 0.70f, 1.4f, Color(0xFF7B3F9B)),  // 右上紫红
            AuroraBlob(0.15f, 0.72f, 0.80f, 2.8f, Color(0xFF1F4A7A)),  // 左下蓝
            AuroraBlob(0.72f, 0.92f, 0.75f, 4.1f, Color(0xFF4A2A6E)),  // 右下紫
        ) else listOf(
            AuroraBlob(0.46f, 0.08f, 0.90f, 0.0f, Color(0xFFFFD1E0)),  // 粉
            AuroraBlob(0.84f, 0.16f, 0.80f, 1.5f, Color(0xFFE3D0FF)),  // 紫
            AuroraBlob(0.20f, 0.55f, 0.85f, 2.9f, Color(0xFFD0E2FF)),  // 蓝
            AuroraBlob(0.74f, 0.86f, 0.85f, 4.3f, Color(0xFFCFF0F5)),  // 青
        )
    }
    val base = if (dark) Color(0xFF0B1020) else Color(0xFFF5F3F8)

    Canvas(modifier = modifier) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val maxR = maxOf(w, h)
        // 光斑漂移：相位在 -1..1 之间往复（Reverse 模式），乘上较大的幅度，
        // 让光斑在画布上有肉眼可见的位移，形成流动感。
        val drift = t * 2f - 1f  // -1..1
        for (b in blobs) {
            val ang = drift.toDouble() * 2.0 * PI + b.phase.toDouble()
            // 每个光斑沿自己的椭圆轨迹漂移，幅度明显（约 18%~22% 画布尺寸）
            val cx = b.x * w + (sin(ang) * 0.18f * w).toFloat()
            val cy = b.y * h + (cos(ang) * 0.22f * h).toFloat()
            val center = Offset(cx, cy)
            val radius = b.r * maxR
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(b.color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                size = size,
            )
        }
    }
}

// ========================= 开源声明页 =========================
@Composable
private fun OpenSourcePage(onBack: () -> Unit) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground()),
    ) {
        // 标题栏：返回 + 「开源声明」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            MiuixText(
                text = "关于",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "项目仓库",
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InfoRow("GitHub", "Qianzao7/MuQi", url = GITHUB_REPO_URL)
                    }
                }
            }
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "框架信息",
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InfoRow("框架", "LSPosed / Xposed")
                        InfoRow("动画参考", "OriginOS / iOS")
                        InfoRow("界面组件", "Miuix (Apache-2.0)")
                        InfoRow("模块许可", "AGPL-3.0", isLast = true, url = GITHUB_REPO_URL)
                    }
                }
            }
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "致谢与开源声明",
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InfoRow("UI 组件库", "Miuix (Apache-2.0)", url = "https://github.com/YuKongA/miuix")
                        InfoRow("Hook 框架 API", "libxposed (Apache-2.0)", url = "https://github.com/libxposed/api")
                        InfoRow("基础框架", "Jetpack Compose / AndroidX (Apache-2.0)", url = "https://github.com/androidx/androidx")
                        InfoRow("开发语言", "Kotlin (Apache-2.0)", url = "https://github.com/JetBrains/kotlin")
                        InfoRow("图标", "Material Icons (Apache-2.0)", isLast = true, url = "https://github.com/google/material-design-icons")
                    }
                }
            }
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        InfoRow("系统界面", "com.android.systemui")
                        InfoRow("系统桌面", "com.miui.home", isLast = true)
                    }
                }
            }
        }
    }
}

// ========================= 捐赠页 =========================
@Composable
private fun DonatePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val isDark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    val btnBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE4E4E4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground()),
    ) {
        // 标题栏：返回 + 「捐赠」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            MiuixText(
                text = "捐赠",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 感谢卡片
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        MiuixText(
                            text = "感谢您的支持",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        MiuixText(
                            text = "如果您喜欢幕启，欢迎自愿捐赠，感谢您的支持",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            // 二维码卡片
            item {
                GlassCard(cornerRadius = 22.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.donate_qr),
                            contentDescription = "微信收款码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            // 提示文字
            item {
                MiuixText(
                    text = "保存后，打开微信扫一扫，从相册选择收款码。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // 保存按钮
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(btnBg)
                        .clickable { saveQrToGallery(ctx) },
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = "保存收款码",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ========================= 日志与诊断页 =========================
@Composable
private fun LogDiagPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(sp.getBoolean("log_collect_enabled", false)) }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground()),
    ) {
        // 标题栏：返回 + 「日志与诊断」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            MiuixText(
                text = "日志与诊断",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 收集日志开关
            item {
                GlassCard(cornerRadius = 22.dp) {
                    SwitchPreference(
                        title = "收集日志",
                        summary = "打开后记录运行日志，用于排查崩溃；默认关闭",
                        checked = enabled,
                        startAction = { ColorfulIcon(MiuixIcons.ListView, Color(0xFFFFA751)) },
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean("log_collect_enabled", it).commit()
                            writeLogEnabledFlag(it)
                        },
                    )
                }
            }
            // 说明文字
            item {
                MiuixText(
                    text = "日志仅用于排查桌面崩溃。开启后模块会在解锁动画等时刻记录少量日志，" +
                            "请按需开启；排查结束后建议关闭，避免占用存储。切换后约 10 秒内生效。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // 导出按钮
            item {
                GlassCard(cornerRadius = 22.dp) {
                    ArrowPreference(
                        title = "导出诊断日志",
                        summary = "生成日志文件并分享，用于定位问题",
                        startAction = { ColorfulIcon(MiuixIcons.File, Color(0xFF5B86E5)) },
                        onClick = { exportDiagnosticLogs(ctx) },
                    )
                }
            }
            // 清空日志按钮
            item {
                GlassCard(cornerRadius = 22.dp) {
                    ArrowPreference(
                        title = "清空日志",
                        summary = "删除已记录的运行日志（qtanim_hb.log），方便重新抓取",
                        startAction = { ColorfulIcon(MiuixIcons.Refresh, Color(0xFFE5645B)) },
                        onClick = { clearDiagnosticLogs(ctx) },
                    )
                }
            }
        }
    }
}

/** 把「启用动画效果」开关写到 sdcard 标志文件 qtanim_enabled，native 重启桌面时读一次据此决定是否装 hook。 */
private fun writeEnabledFlag(enabled: Boolean) {
    val v = if (enabled) "1" else "0"
    val suCands = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "su")
    Thread {
        for (su in suCands) {
            try {
                val cmd = "echo $v > /data/media/0/qtanim_enabled; chmod 666 /data/media/0/qtanim_enabled"
                val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText()
                val exit = p.waitFor()
                if (exit == 0) break
            } catch (_: Throwable) {}
        }
    }.start()
}

/** 把「是否收集日志」开关写到 sdcard 标志文件，native 每 10s 读一次据此决定是否写日志。 */
private fun writeLogEnabledFlag(enabled: Boolean) {
    val v = if (enabled) "1" else "0"
    val suCands = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "su")
    Thread {
        for (su in suCands) {
            try {
                val cmd = "echo $v > /data/media/0/qtanim_log_enabled; chmod 666 /data/media/0/qtanim_log_enabled"
                val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText()
                val exit = p.waitFor()
                if (exit == 0) break
            } catch (_: Throwable) {}
        }
    }.start()
}

/** 清空诊断日志：优先广播给 SystemUI 进程删除（无需 root），root 与直接删除作兜底。 */
private fun clearDiagnosticLogs(ctx: Context) {
    Thread {
        var ok = false
        // 1. 首选：广播给 SystemUI 进程（platform_app 能直接删 sdcard 上的日志，无需 root）
        try {
            val intent = android.content.Intent("com.qtanim.module.CLEAR_LOG")
            intent.setPackage("com.android.systemui")
            ctx.sendBroadcast(intent)
            ok = true
        } catch (_: Throwable) {}
        // 2. root 兜底（设备有 root 时直接删）
        if (!ok) {
            val suCands = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "su")
            for (su in suCands) {
                try {
                    val cmd = "rm -f /data/media/0/qtanim_hb.log /storage/emulated/0/qtanim_hb.log 2>/dev/null"
                    val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
                    p.inputStream.bufferedReader().readText()
                    val exit = p.waitFor()
                    if (exit == 0) { ok = true; break }
                } catch (_: Throwable) {}
            }
        }
        // 3. 直接删除兜底（App 若持有公共存储访问权限）
        if (!ok) {
            try {
                val f = java.io.File("/storage/emulated/0/qtanim_hb.log")
                if (f.exists() && f.delete()) ok = true
            } catch (_: Throwable) {}
        }
        val msg = if (ok) "日志已清空" else "清空失败"
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        }
    }.start()
}

/**
 * 把内置收款码写入系统相册（Pictures/MuQi），无需存储权限（Android 10+ 走 MediaStore）。
 */
private fun saveQrToGallery(ctx: Context) {
    try {
        val bmp = BitmapFactory.decodeResource(ctx.resources, R.drawable.donate_qr)
        if (bmp == null) {
            Toast.makeText(ctx, "保存失败：无法读取收款码", Toast.LENGTH_SHORT).show()
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "muqi_donate_qr.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MuQi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = ctx.contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Toast.makeText(ctx, "已保存到相册 Pictures/MuQi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Throwable) {
        Toast.makeText(ctx, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 跳转到酷安个人主页：优先用酷安 App 的 scheme（coolmarket://u/<UID>），
 * 未安装酷安时退回网页版（coolapk.com/u/<UID>）。
 */
private fun openCoolapk(ctx: Context) {
    if (CREATOR_COOLAPK_UID.isEmpty()) return
    val flag = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val scheme = "coolmarket://u/$CREATOR_COOLAPK_UID"
    val web = "https://www.coolapk.com/u/$CREATOR_COOLAPK_UID"
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(scheme)).addFlags(flag)
        )
        return
    } catch (_: Throwable) {
    }
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web)).addFlags(flag)
        )
    } catch (_: Throwable) {
    }
}

/** 用系统浏览器打开一个网页链接（GitHub 仓库、第三方库主页等）。 */
private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}

private fun getAppVersion(ctx: Context): String {
    return try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0.0"
    }
}

/**
 * 导出诊断日志：su 读 native 日志 + 设备信息 + 已装 LSPosed 模块，组装成 txt 保存到
 * Download/MuQi 并调起分享面板，方便用户把崩溃日志返回来定位「桌面疯狂崩溃」。
 */
private fun exportDiagnosticLogs(ctx: Context) {
    Thread {
        var shareText = ""
        var savedUri: Uri? = null
        var message = "导出失败"
        try {
            val sb = StringBuilder()
            sb.append("==== 幕启 MuQi 诊断日志 ====\n")
            sb.append("导出时间: ").append(
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
            ).append("\n")
            sb.append("模块版本: ").append(getAppVersion(ctx)).append("\n")
            sb.append("设备型号: ").append(Build.MODEL).append("\n")
            sb.append("系统版本: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            try {
                val p = ctx.packageManager.getPackageInfo("com.miui.home", 0)
                sb.append("系统桌面版本: ").append(p.versionName)
                    .append(" (").append(p.versionCode).append(")\n")
            } catch (_: Throwable) {}
            sb.append("\n")

            // su 收集：桌面进程已加载模块清单（判断冲突）+ native 日志（崩溃时 FAULT 记录）
            val suCands = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "su")
            var modules = ""
            var logText = ""
            // 主通道：native 已把「注入进桌面进程的其它模块库」写到 sdcard 清单
            for (su in suCands) {
                try {
                    val p = ProcessBuilder(su, "-c",
                        "cat /data/media/0/qtanim_modules.txt 2>/dev/null || cat /storage/emulated/0/qtanim_modules.txt 2>/dev/null"
                    ).redirectErrorStream(true).start()
                    modules = p.inputStream.bufferedReader().readText().trim()
                    p.waitFor()
                    if (modules.isNotBlank()) break
                } catch (_: Throwable) {}
            }
            // 兜底：旧通道读 LSPosed 配置（新版 LSPosed 此路径常读不到）
            if (modules.isBlank()) {
                for (su in suCands) {
                    try {
                        val p = ProcessBuilder(su, "-c",
                            "cat /data/adb/lspd/config/modules_config 2>/dev/null"
                        ).redirectErrorStream(true).start()
                        modules = p.inputStream.bufferedReader().readText().trim()
                        p.waitFor()
                        if (modules.isNotBlank()) break
                    } catch (_: Throwable) {}
                }
            }
            for (su in suCands) {
                try {
                    val p = ProcessBuilder(su, "-c",
                        "cat /data/media/0/qtanim_hb.log 2>/dev/null || cat /storage/emulated/0/qtanim_hb.log 2>/dev/null"
                    ).redirectErrorStream(true).start()
                    logText = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    if (logText.isNotBlank()) break
                } catch (_: Throwable) {}
            }
            // 兜底解析：即使桌面还在跑旧 native 库（覆盖安装后未重启桌面，build 标记
            // 仍是旧版、也就没生成 qtanim_modules.txt），native 日志的 "dumping app
            // libs" 段也已列出全部注入库，直接从日志里提取「非幕启的模块 so」即可
            // 拿到冲突模块清单，不再依赖新库重启。
            if (modules.isBlank() && logText.isNotBlank()) {
                val parsed = StringBuilder()
                for (line in logText.lineSequence()) {
                    val t = line.trim()
                    if (t.contains("/base.apk!/lib/") && !t.contains("com.qtanim.module")) {
                        parsed.append(t).append("\n")
                    }
                }
                if (parsed.isNotBlank()) modules = parsed.toString().trim()
            }

            sb.append("==== 桌面进程已加载的其它模块（用于排查冲突） ====\n")
            if (modules.isBlank()) {
                sb.append("（未检测到其它模块：可能确实只装了幕启，或模块尚未重启桌面注入）\n")
            } else {
                sb.append(modules).append("\n")
            }
            sb.append("\n==== native 日志 (qtanim_hb.log) ====\n")
            if (logText.isBlank()) {
                sb.append("（日志为空：可能尚未发生解锁，或 root 未授权）\n")
            } else {
                sb.append(logText)
            }

            shareText = sb.toString()

            // 保存到 Download/MuQi
            val fileName = "MuQi_diag_" +
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date()) + ".txt"
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/MuQi")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(shareText.toByteArray()) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    savedUri = uri
                }
            }
            // 兜底：写到 App 外部私有目录，至少用户能拿到文件
            if (savedUri == null) {
                val dir = ctx.getExternalFilesDir(null)
                if (dir != null) {
                    val f = java.io.File(dir, fileName)
                    f.writeText(shareText)
                    message = "日志已保存到 ${f.absolutePath}"
                }
            } else {
                message = "日志已保存到 Download/MuQi，请选择方式发送"
            }
        } catch (e: Throwable) {
            message = "导出失败：${e.message}"
        }

        // 回主线程弹 Toast + 分享面板
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            try {
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
            if (shareText.isNotBlank()) {
                try {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        if (savedUri != null) {
                            putExtra(android.content.Intent.EXTRA_STREAM, savedUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    ctx.startActivity(android.content.Intent.createChooser(share, "导出诊断日志"))
                } catch (_: Throwable) {}
            }
        }
    }.start()
}

/**
 * 把当前全部设置通过显式包名广播推给桌面进程里的 hook。
 * 桌面进程（com.miui.home）无法读本 App 的私有 SharedPreferences（SELinux 拦截跨包访问），
 * 所以设置项只能走广播这条通道，不需要 root。
 */
private fun pushPrefsToLauncher(ctx: Context) {
    try {
        val sp = ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE)
        val intent = android.content.Intent("com.qtanim.module.PREFS")
        intent.setPackage("com.miui.home")
        for ((k, v) in sp.all) {
            when (v) {
                is Boolean -> intent.putExtra(k, v)
                is Int -> intent.putExtra(k, v)
                is Long -> intent.putExtra(k, v)
                is Float -> intent.putExtra(k, v)
                is String -> intent.putExtra(k, v)
            }
        }
        ctx.sendBroadcast(intent)
    } catch (_: Throwable) {}
    // muqi49：root 直写参数文件（主通道）。桌面进程里的 Java hook 在 HyperOS4 上
    // 不可靠（日志一直 params-file MISS），所以这里用 root 直接把滑块值写进
    // /data/data/com.miui.home/files/qtanim_params.bin，native 库每次解锁读该文件。
    writeParamsViaRoot(ctx)
}

/** 上次 root 直写参数的时间戳（elapsedRealtime，用于节流防频繁 su）。 */
private val lastRootWriteMs = java.util.concurrent.atomic.AtomicLong(0L)

/**
 * muqi49：用 root（su）把 4 个动画参数序列化成 56 字节二进制（magic + 6 double），
 * 直接写到桌面进程自己的 files 目录。native 库（platform_app）读自己的 files 目录
 * 是 owner 权限，SELinux 放行；而模块 App 直写该目录会被 SELinux 拦，所以借道 su。
 *
 * 二进制经 base64 编码后通过 su -c 传入，避免 0x00/换行等字节被 shell 破坏。
 * 不依赖桌面进程 Java hook、不依赖跨包读 SharedPreferences，链路最短最可靠。
 */
private fun writeParamsViaRoot(ctx: Context) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastRootWriteMs.get() < 400L) return
    lastRootWriteMs.set(now)
    Thread {
        var status = "unknown"
        val diag = StringBuilder()
        try {
            val sp = ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE)
            val stagger   = sp.getInt("stagger", 100).coerceIn(0, 200) / 100.0
            val hStagger  = sp.getInt("h_stagger", 100).coerceIn(0, 200) / 100.0
            val iconSpeed = sp.getInt("icon_speed", 100).coerceIn(50, 200) / 100.0
            val bounce    = sp.getInt("bounce", 100).coerceIn(0, 200) / 100.0
            val bb = java.nio.ByteBuffer.allocate(56).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.putLong(0x5154414E494D0001L)  // "QTANIM\0\1"
            bb.putDouble(stagger)
            bb.putDouble(hStagger)
            bb.putDouble(iconSpeed)
            bb.putDouble(0.0)   // middle_row：滑块已移除，固定 0
            bb.putDouble(bounce)
            bb.putDouble(1.0)   // dock_lead：滑块已移除，固定 1
            val b64 = android.util.Base64.encodeToString(bb.array(), android.util.Base64.NO_WRAP)
            // muqi56：主通道走 sdcard（FUSE 全局挂载），绕开 KernelSU su 写 /data/data
            // 的 mount namespace 隔离。根目录 + Download 两处都写，chown 到 media_rw(1023)
            // 让 FUSE 正确映射 owner，app 通过 /storage/emulated/0 才能读到。
            val sdcardTarget = "/data/media/0/qtanim_params.bin"
            val sdcardDlTarget = "/data/media/0/Download/qtanim_params.bin"
            val target = "/data/data/com.miui.home/cache/qtanim_params.bin"
            val filesTarget = "/data/data/com.miui.home/files/qtanim_params.bin"

            // 依次探测 su 路径（不同 root 方案 su 位置不同）
            val suCands = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "su")
            var written = false
            for (su in suCands) {
                val cmd = "mkdir -p /data/media/0/Download /data/data/com.miui.home/files /data/data/com.miui.home/cache; " +
                        "echo $b64 | base64 -d | tee $sdcardTarget > $target; " +
                        "cp $sdcardTarget $sdcardDlTarget 2>/dev/null; " +
                        "cp $sdcardTarget $filesTarget 2>/dev/null; " +
                        "chown 1023:1023 $sdcardTarget $sdcardDlTarget 2>/dev/null; " +
                        "chmod 666 $sdcardTarget $sdcardDlTarget; chmod 644 $target $filesTarget"
                try {
                    val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
                    val out = p.inputStream.bufferedReader().readText()
                    val exit = p.waitFor()
                    diag.append("su=$su exit=$exit out=${out.take(100)}\n")
                    if (exit == 0) { written = true; break }
                } catch (e: Throwable) {
                    diag.append("su=$su err=${e.message}\n")
                }
            }
            // 回读验证：以 sdcard 根目录路径为准（native 主读取通道），确认 56 字节
            if (written) {
                status = "verify_fail"
                for (su in suCands) {
                    try {
                        val p = ProcessBuilder(su, "-c", "wc -c < $sdcardTarget; ls -l /data/media/0/qtanim_params.bin").redirectErrorStream(true).start()
                        val out = p.inputStream.bufferedReader().readText().trim()
                        p.waitFor()
                        diag.append("verify su=$su out=$out\n")
                        if (out.startsWith("56")) { status = "ok"; break }
                    } catch (e: Throwable) {
                        diag.append("verify su=$su err=${e.message}\n")
                    }
                }
            } else {
                status = "write_fail"
            }
            // diag136/muqi61: 检测 native 是否被 LSPosed 注入（实时）。KernelSU su
            // 与桌面进程的 /data/data 处在不同 mount namespace，su 读不到桌面私有
            // 目录里的 hb.log（这正是「仍显示未激活」的根因）。native 构造函数注入
            // 后会在 sdcard 写 qtanim_active 心跳标记，内容是 wall-clock 秒，并且每
            // 10 秒刷新一次。sdcard 是唯一跨 namespace 一致的通道，所以主通道改为
            // su 读 /data/media/0/qtanim_active 的时间戳：新鲜（<30s）即 native 仍在
            // 运行 = 已激活；取消 LSPosed 作用域并重启桌面后心跳停止，时间戳过期，
            // 这里就会判定为未激活（不再是「永远已激活」）。
            var lspActive = false
            val nowSec = System.currentTimeMillis() / 1000L
            for (su in suCands) {
                try {
                    val p = ProcessBuilder(su, "-c",
                        "cat /data/media/0/qtanim_active 2>/dev/null || cat /storage/emulated/0/qtanim_active 2>/dev/null"
                    ).redirectErrorStream(true).start()
                    val out = p.inputStream.bufferedReader().readText().trim()
                    p.waitFor()
                    val ts = out.toLongOrNull()
                    if (ts != null && ts > 0 && kotlin.math.abs(nowSec - ts) < 30) {
                        lspActive = true
                        diag.append("lsp-sdcard su=$su ts=$ts age=${nowSec - ts}s\n")
                        break
                    }
                    diag.append("lsp-sdcard su=$su out=${out.take(40)}\n")
                } catch (e: Throwable) {
                    diag.append("lsp-sdcard su=$su err=${e.message}\n")
                }
            }
            // 兜底：旧通道 grep hb.log（某些 root 方案 su 能看到桌面私有目录）。
            if (!lspActive) {
                for (su in suCands) {
                    try {
                        val p = ProcessBuilder(su, "-c",
                            "grep -c 'build=muqi' /data/data/com.miui.home/cache/hb.log 2>/dev/null"
                        ).redirectErrorStream(true).start()
                        val out = p.inputStream.bufferedReader().readText().trim()
                        val exit = p.waitFor()
                        if (exit == 0 && out.isNotBlank() && (out.toIntOrNull() ?: 0) > 0) {
                            lspActive = true
                            break
                        }
                    } catch (e: Throwable) {
                        diag.append("lsp-hblog su=$su err=${e.message}\n")
                    }
                }
            }
            sp.edit().putBoolean("lsp_active", lspActive).commit()
            diag.append("lsp_active=$lspActive\n")
        } catch (e: Throwable) {
            diag.append("outer err=${e.message}\n")
            status = "exception"
        }
        // 结果回写 sp（首页显示）+ 落诊断文件到外部目录（用户文件管理器可看）
        try {
            val sp = ctx.getSharedPreferences("qtanim", Context.MODE_PRIVATE)
            sp.edit().putString("param_bridge_status", status)
                .putString("param_bridge_diag", diag.toString()).commit()
            val dir = ctx.getExternalFilesDir(null)
            if (dir != null) {
                java.io.File(dir, "param_write.log").writeText(
                    "=== ${System.currentTimeMillis()} ===\nstatus=$status\n$diag")
            }
        } catch (_: Throwable) {}
    }.start()
}

/**
 * 模块是否已激活：只要检测到 native 被 LSPosed 注入过（hb.log 里有 build 标记）
 * 就视为已激活。writeParamsViaRoot 用 su 检测后回写 lsp_active 布尔值。
 */
private fun isModuleActive(sp: android.content.SharedPreferences): Boolean {
    return sp.getBoolean("lsp_active", false)
}
