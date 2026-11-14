package com.qtanim.module.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 小米设置风格的彩色设置项图标：扁平纯色圆角底 + 白色矢量图标。
 *
 * 不用原生 Icon 直接画（Miuix 图标是 Material outline 风格的细线条），而是用
 * Canvas 重绘：fill 型细线轮廓在填充原形后再沿边界描一圈粗边，stroke 型直接加粗，
 * 让白色图案比默认更粗、更醒目，同时保留 Miuix 图标的原始形状。
 *
 * 注意：Miuix 图标的 viewport 是 1261.2（源自 misymbol 字体字形坐标），不是 24。
 * 所以描边宽度必须用「目标像素 → viewport 单位」换算，否则会被 scale 缩到几乎不可见。
 */
@Composable
internal fun ColorfulIcon(
    imageVector: ImageVector,
    color: Color,
    iconTint: Color = Color.White,
    iconSize: Dp = 20.dp,
    strokeWidthDp: Dp = 1.4.dp,   // 描边加粗宽度（dp，与 viewport 无关）
) {
    val strokePx = with(LocalDensity.current) { strokeWidthDp.toPx() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val sx = size.width / imageVector.viewportWidth
            val sy = size.height / imageVector.viewportHeight
            // 目标像素宽度 → viewport 单位（因为 scale 会把线宽再乘回 sx）
            val strokeVp = strokePx / minOf(sx, sy)
            scale(sx, sy, pivot = Offset.Zero) {
                drawVectorGroup(imageVector.root, iconTint, strokeVp)
            }
        }
    }
}

private fun DrawScope.drawVectorGroup(group: VectorGroup, tint: Color, strokeWidth: Float) {
    // 应用 group 自身的变换。Miuix 图标的根 group 带 scaleY = -1（字体字形坐标
    // Y 轴向上 → SVG 坐标 Y 轴向下），之前只画 pathData 而忽略了这个翻转，
    // 导致所有图标图案上下颠倒。这里按 Compose ImageVector 的顺序补上变换。
    val pivot = Offset(group.pivotX, group.pivotY)
    withTransform({
        translate(group.translationX, group.translationY)
        rotate(group.rotation, pivot)
        scale(group.scaleX, group.scaleY, pivot)
    }) {
        for (child in group) {
            when (child) {
                is VectorPath -> {
                    if (child.pathData.isEmpty()) continue
                    val p = PathParser().addPathNodes(child.pathData).toPath(Path())
                    if (child.stroke != null) {
                        // 已是描边型：直接加粗描边
                        drawPath(
                            path = p,
                            color = tint,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    } else {
                        // 填充型（Material outline 的细线其实是闭合区域用 fill 填充）：
                        // 先填充原形，再沿边界描一圈粗边，把线条视觉加粗。
                        drawPath(path = p, color = tint, style = Fill)
                        drawPath(
                            path = p,
                            color = tint,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
                is VectorGroup -> drawVectorGroup(child, tint, strokeWidth)
            }
        }
    }
}
