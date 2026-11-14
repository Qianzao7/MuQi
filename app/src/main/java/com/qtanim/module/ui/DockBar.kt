package com.qtanim.module.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏容器：根据 prefs 渲染三种互斥样式。
 *   0 = 标准 NavigationBar（高斯模糊毛玻璃 + 延伸到导航条/小白条区域）
 *   1 = 悬浮 FloatingNavigationBar（毛玻璃胶囊）
 *   2 = 液态玻璃 LiquidGlassNavigationBar（真实折射 + 色散）
 */
@Composable
internal fun DockBar(
    style: Int,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    items: List<LiquidGlassNavigationItem>,
    modifier: Modifier = Modifier,
) {
    when (style) {
        2 -> LiquidGlassNavigationBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            items = items,
            modifier = modifier,
        )

        1 -> FloatingDockBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            items = items,
            modifier = modifier,
        )

        else -> StandardBlurNavigationBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            items = items,
            modifier = modifier,
        )
    }
}

/**
 * 悬浮底栏（style 1）：毛玻璃胶囊。
 *
 * 根因（上一版仍在左上角）：Miuix 的 [FloatingNavigationBar] 会把传入的 `modifier`
 * 作用在它【内部 Row】上（`.then(modifier)` 位置），而不是最外层容器。因此把
 * `Modifier.align(Alignment.BottomCenter)`（BoxScope 扩展）传进去时，align 在非
 * Box 的 Row 上不生效，胶囊失去底部定位，飘到屏幕左上角。
 *
 * 正确做法：用外层 Box 承接 `align` 定位（Box 是父容器的直接子项），
 * [FloatingNavigationBar] 本身【不传任何 modifier】，让它内部自带
 * `fillMaxWidth` + 底部 inset padding + 水平居中正常渲染。
 */
@Composable
private fun FloatingDockBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    items: List<LiquidGlassNavigationItem>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        FloatingNavigationBar(
            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.85f),
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavigationBarItem(
                    selected = selectedTabIndex() == index,
                    onClick = { onTabSelected(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    }
}

/**
 * 标准底栏（style 0）：材质与「选项卡片」（GlassCard）保持一致。
 *
 * 在全局流光模式下，GlassCard 用半透明容器色让背景极光透出；这里用同一套
 * 半透明色，使底栏与上方卡片视觉统一。其余模式退回 Miuix 默认不透明 surface。
 *
 * 小白条覆盖由 Miuix [NavigationBar] 自带（`defaultWindowInsetsPadding=true` 时
 * Column 末尾追加 captionBar 高度 Spacer，且位于 `.background(color)` 之内，
 * 背景色天然向下铺到小白条区域），因此无需再手动加 padding。
 */
@Composable
private fun StandardBlurNavigationBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    items: List<LiquidGlassNavigationItem>,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    // 与 GlassCard 完全一致的材质：全局流光下透出极光的半透明容器，否则不透明 surface。
    val navColor = if (LocalGlowMode.current == 0) {
        if (isDark) Color(0xFF21232D).copy(alpha = 0.66f)
        else Color(0xFFFFFFFF).copy(alpha = 0.72f)
    } else {
        MiuixTheme.colorScheme.surface
    }

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        color = navColor,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTabIndex() == index,
                onClick = { onTabSelected(index) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}
