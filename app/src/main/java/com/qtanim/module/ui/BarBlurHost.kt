package com.qtanim.module.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 提供液态玻璃底栏所需的「背景采样层」。
 *
 * 原理：把整个页面内容录制进一个 GraphicsLayer（[LayerBackdrop]），
 * 底栏再通过 [top.yukonga.miuix.kmp.blur.drawBackdrop] 从这个图层实时采样，
 * 结合模糊 + 折射 + 色散 shader，就得到真实的液态玻璃效果。
 */
internal val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun BarBlurHost(
    enabled: Boolean,
    captureForEffects: Boolean = false,
    liquidGlassEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if ((enabled || captureForEffects || liquidGlassEnabled) && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(
        LocalBarBlurBackdrop provides backdrop,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/** 把页面内容标记为 backdrop 的采样源。 */
@Composable
internal fun BarBackdropContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBarBlurBackdrop.current
    Box(
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ),
    ) {
        content()
    }
}
