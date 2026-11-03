package com.example.inventorymaster.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃材质组件。
 *
 * 绘制结构（自外向内）：L4 外部软阴影 → L1 渐变边框 → L2 内侧方向性高光
 * （左上主高光 + 右下次高光，135° 渐变让边缘不同方向亮度不同）→ L3 内侧暗边
 * → 半透明主体。
 *
 * 有意不含背景模糊：窗口级模糊会波及弹窗/组件以外的整个背景（"蒙板"），
 * 大面积玻璃的质感全部由边缘系统承担；仅小面积元素未来可单独评估磨砂。
 *
 * 按压状态（"按进玻璃"）：传入 interactionSource 时启用——外阴影减弱、
 * 内侧高光收暗、内侧暗边增强、整体下沉 1dp；不做缩小、不用 ripple。
 * 不传则保持纯静态。
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    style: LiquidGlassStyle = LiquidGlassStyle.DarkGlass,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    // 未传入 interactionSource 时用永远不发射的空源，保持静态
    val effectiveInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by effectiveInteraction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "LiquidGlassPress"
    )

    // L1：沿 135° 方向渐变的边框（左上亮 → 中段暗 → 右下中亮）
    val borderBrush = remember(style.borderAlpha) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = style.borderAlpha * 0.85f),
                0.45f to Color.White.copy(alpha = style.borderAlpha * 0.20f),
                1.00f to Color.White.copy(alpha = style.borderAlpha * 0.55f)
            )
        )
    }

    Box(
        modifier = modifier
            // 按压下沉：在 layer 层做，不触发重组
            .graphicsLayer { translationY = 1.dp.toPx() * pressProgress }
            // L4 外部软阴影：跟形状轮廓走，等价 CSS drop-shadow；按压时减弱
            .shadow(
                elevation = style.outerShadowElevation,
                shape = shape,
                spotColor = Color.Black.copy(
                    alpha = style.outerShadowAlpha * (1f - 0.4f * pressProgress)
                ),
                ambientColor = Color.Black.copy(
                    alpha = style.outerShadowAlpha * 0.5f * (1f - 0.4f * pressProgress)
                )
            )
            .clip(shape)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)

                // 按压光学变化：高光收暗、内暗边增强
                val mainHighlight = style.highlightAlpha * (1f - 0.75f * pressProgress)
                val secondaryHighlight = style.secondaryHighlightAlpha * (1f - 0.75f * pressProgress)
                val innerDark = style.innerShadowAlpha + 0.07f * pressProgress

                // 等距同心内缩描边：
                // 对于圆角矩形，直接对 RoundRect 进行几何内缩，避免 scale(scaleX, scaleY) 在宽矩形上将圆角拉扯为椭圆；
                // 其余形状兜底采用画布中心等比收缩。
                fun insetBrushStroke(px: Float, widthPx: Float, brush: Brush) {
                    if (size.width <= px * 2f || size.height <= px * 2f) return
                    if (outline is Outline.Rounded) {
                        val rr = outline.roundRect
                        val innerLeft = rr.left + px
                        val innerTop = rr.top + px
                        val innerRight = rr.right - px
                        val innerBottom = rr.bottom - px
                        if (innerRight <= innerLeft || innerBottom <= innerTop) return
                        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = (rr.topLeftCornerRadius.x - px).coerceAtLeast(0f),
                            y = (rr.topLeftCornerRadius.y - px).coerceAtLeast(0f)
                        )
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(innerLeft, innerTop),
                            size = androidx.compose.ui.geometry.Size(innerRight - innerLeft, innerBottom - innerTop),
                            cornerRadius = cornerRadius,
                            style = Stroke(width = widthPx)
                        )
                    } else {
                        scale(
                            scaleX = (size.width - px * 2f) / size.width,
                            scaleY = (size.height - px * 2f) / size.height,
                            pivot = center
                        ) {
                            drawOutline(outline, brush = brush, style = Stroke(width = widthPx))
                        }
                    }
                }

                fun insetColorStroke(px: Float, widthPx: Float, color: Color) {
                    insetBrushStroke(px, widthPx, androidx.compose.ui.graphics.SolidColor(color))
                }

                // 主体半透明填充
                drawOutline(
                    outline,
                    color = style.containerColor.copy(alpha = style.backgroundAlpha)
                )

                // 凸面表面流光（若启用）：从左上向右下过渡，赋予透镜弧面饱满感
                if (style.surfaceSheenAlpha > 0f) {
                    val currentSheen = style.surfaceSheenAlpha * (1f - 0.5f * pressProgress)
                    drawOutline(
                        outline,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = currentSheen),
                                Color.White.copy(alpha = currentSheen * 0.1f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                }

                // L3 内侧暗边：拉出边缘厚度，防止高光糊成白雾（黑色透明度必须低）
                insetColorStroke(
                    style.borderWidth.toPx(),
                    style.innerShadowWidth.toPx(),
                    Color.Black.copy(alpha = innerDark)
                )

                // L2 次高光（右下偏内一层，修复：由右下角向左上角发散）
                insetBrushStroke(
                    style.borderWidth.toPx() + style.highlightWidth.toPx(),
                    style.highlightWidth.toPx(),
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = secondaryHighlight),
                            Color.White.copy(alpha = secondaryHighlight * 0.1f)
                        ),
                        start = Offset(size.width, size.height),
                        end = Offset.Zero
                    )
                )

                // L2 主高光（左上，紧贴边缘，整个效果的核心层）
                insetBrushStroke(
                    style.borderWidth.toPx() * 0.5f,
                    style.highlightWidth.toPx(),
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = mainHighlight),
                            Color.White.copy(alpha = mainHighlight * 0.15f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
            .border(style.borderWidth, borderBrush, shape),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}
