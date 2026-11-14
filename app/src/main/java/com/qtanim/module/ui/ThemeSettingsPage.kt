package com.qtanim.module.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ThemeModes = listOf("跟随系统", "浅色", "深色")
private val AboutBgStyles = listOf("小米流光", "彩色流光")
private val GlowModes = listOf("全局", "局部", "仅关于页")
private val SubpageTransitions = listOf("侧滑手势", "卡片弹出")

/**
 * 流光效果模式：
 *   0 = 全局（整个 App 所有页面背景流光 + 卡片毛玻璃）
 *   1 = 局部（仅「关于」页顶部横幅流光，其余页面纯色）
 *   2 = 仅关于页（整个「关于」页背景流光，其余页面纯色）
 * 由 App() 在顶层提供，pageBackground / GlassCard 据此决定背景与卡片样式。
 */
internal val LocalGlowMode = staticCompositionLocalOf { 2 }

/**
 * 当前页面背景是否「透出流光」：
 *   - 全局模式下所有页面为 true（流光由 App 顶层铺设）
 *   - 仅关于页模式下，只有「关于」页自己铺流光时为 true
 * 与 [LocalGlowMode] 一起决定 pageBackground 是否透明、GlassCard 是否毛玻璃。
 */
internal val LocalFlowingBackground = staticCompositionLocalOf { false }

/**
 * 页面背景色：浅色下用水泥灰、深色下用纯黑，
 * 这样白色 / 深灰色的 Card 才能和背景拉出清晰边界（对齐 HyperOS 设置页的观感）。
 * 全局流光 / 当前页透出流光时返回透明，让流光背景透出来。
 */
@Composable
internal fun pageBackground(): Color {
    if (LocalGlowMode.current == 0 || LocalFlowingBackground.current) return Color.Transparent
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    return if (dark) Color(0xFF000000) else Color(0xFFF2F2F2)
}

/**
 * 流光背景上的毛玻璃卡片：半透明容器让背景极光透出，形成「背景模糊」观感。
 * 纯色背景下退化为普通 Card（用 Miuix 默认 surfaceContainer 色）。
 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalGlowMode.current == 0 || LocalFlowingBackground.current) {
        val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
        val container = if (dark) Color(0xFF21232D).copy(alpha = 0.66f)
            else Color(0xFFFFFFFF).copy(alpha = 0.72f)
        Card(
            modifier = modifier,
            cornerRadius = cornerRadius,
            colors = CardDefaults.defaultColors(color = container),
            content = content,
        )
    } else {
        Card(modifier = modifier, cornerRadius = cornerRadius, content = content)
    }
}

/**
 * 分段选项控件：把 Miuix TabRow 的容器背景改为透明，让每个胶囊独立浮在页面背景上，
 * 消除「胶囊 + 深色容器底连成一片」的割裂感；再外包一层圆角裁剪兜底。
 */
@Composable
private fun RoundedTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    ) {
        TabRow(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.fillMaxWidth(),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = Color.Transparent,
                contentColor = cs.onSurfaceVariantSummary,
                selectedBackgroundColor = cs.surfaceContainer,
                selectedContentColor = cs.onBackground,
            ),
        )
    }
}

@Composable
internal fun ThemeSettingsPage(
    currentThemeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    floating: Boolean,
    onFloatingChange: (Boolean) -> Unit,
    liquidGlass: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    blur: Boolean,
    onBlurChange: (Boolean) -> Unit,
    aboutBgStyle: Int,
    onAboutBgChange: (Int) -> Unit,
    glowMode: Int,
    onGlowModeChange: (Int) -> Unit,
    subpageTransition: Int,
    onSubpageTransitionChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground()),
    ) {
        // 标题栏
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
                text = "主题设置",
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
                RoundedTabRow(
                    tabs = ThemeModes,
                    selectedTabIndex = currentThemeMode,
                    onTabSelected = { onThemeModeChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用类 Apple 风格的悬浮底栏",
                        checked = floating,
                        startAction = { ColorfulIcon(MiuixIcons.Home, Color(0xFF4FACFE)) },
                        onCheckedChange = onFloatingChange,
                    )

                    AnimatedVisibility(
                        visible = floating,
                        enter = expandVertically(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) + slideInVertically(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it / 2 },
                        ) + fadeIn(tween(180)),
                        exit = shrinkVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ) + slideOutVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 2 },
                        ) + fadeOut(tween(140)),
                    ) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用悬浮底栏的液态玻璃效果（真实折射 + 色散）",
                            checked = liquidGlass,
                            startAction = { ColorfulIcon(MiuixIcons.Layers, Color(0xFF00BCD4)) },
                            onCheckedChange = onLiquidGlassChange,
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    SwitchPreference(
                        title = "界面模糊",
                        summary = "启用界面的背景模糊采样（影响底栏）",
                        checked = blur,
                        startAction = { ColorfulIcon(MiuixIcons.GridView, Color(0xFFA18CD1)) },
                        onCheckedChange = onBlurChange,
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "二级界面转场",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        RoundedTabRow(
                            tabs = SubpageTransitions,
                            selectedTabIndex = subpageTransition,
                            onTabSelected = onSubpageTransitionChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "流光效果",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        // 彩色流光（关于页背景 = 彩色流光）只支持「局部」显示，
                        // 全局 / 仅关于页两种模式是为「小米流光」准备的。
                        if (aboutBgStyle == 1) {
                            MiuixText(
                                text = "彩色流光仅支持「局部」显示，全局 / 仅关于页仅适用于小米流光",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        RoundedTabRow(
                            tabs = GlowModes,
                            selectedTabIndex = if (aboutBgStyle == 1) 1 else glowMode,
                            onTabSelected = onGlowModeChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (aboutBgStyle == 1) Modifier.alpha(0.45f) else Modifier),
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixText(
                            text = "关于页背景",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        RoundedTabRow(
                            tabs = AboutBgStyles,
                            selectedTabIndex = aboutBgStyle,
                            onTabSelected = onAboutBgChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                MiuixText(
                    text = "提示：液态玻璃依赖设备 GPU 对 RuntimeShader 的支持，不支持时会自动退化为普通悬浮底栏。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}
