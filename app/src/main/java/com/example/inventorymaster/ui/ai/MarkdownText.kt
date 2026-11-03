package com.example.inventorymaster.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.utils.getUnescapedTextInNode

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    // P1-7 阅读排版基线（docs/AI_UI_OPTIMIZATION.md §六）：正文行高 = 字号 × 1.65（bodyLarge 16sp → 26.4sp）
    val base = MaterialTheme.typography.bodyLarge
    val baseStyle = base.copy(
        lineBreak = LineBreak.Paragraph,
        lineHeight = base.fontSize * 1.65f
    )

    val customTypography = markdownTypography(
        text = baseStyle,
        paragraph = baseStyle,
        h1 = MaterialTheme.typography.displayLarge.copy(lineBreak = LineBreak.Paragraph),
        h2 = MaterialTheme.typography.displayMedium.copy(lineBreak = LineBreak.Paragraph),
        h3 = MaterialTheme.typography.displaySmall.copy(lineBreak = LineBreak.Paragraph),
        h4 = MaterialTheme.typography.headlineMedium.copy(lineBreak = LineBreak.Paragraph),
        h5 = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Paragraph),
        h6 = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Paragraph),
        code = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            lineBreak = LineBreak.Paragraph
        ),
    )

    Markdown(
        content = text,
        modifier = modifier,
        colors = markdownColor(),
        typography = customTypography,
        components = markdownComponents(
            text = { model ->
                Text(
                    text = model.node.getUnescapedTextInNode(model.content),
                    style = model.typography.text,
                    color = LocalMarkdownColors.current.text,
                    softWrap = true,
//                    overflow = TextOverflow.Clip
                )
            },
            table = { model -> AutoWrapMarkdownTable(model) },
            heading1 = headerComponent(1),
            heading2 = headerComponent(2),
            heading3 = headerComponent(3),
            heading4 = headerComponent(4),
            heading5 = headerComponent(5),
            heading6 = headerComponent(6)
        )
    )
}

/** 标题非对称外距（建议一 §六.3：上边距 > 下边距）；库已在每个块前统一加 2dp 间隔，此处叠加层级呼吸感 */
private fun headingMargin(level: Int): PaddingValues = when (level) {
    1 -> PaddingValues(top = 12.dp, bottom = 4.dp)
    2 -> PaddingValues(top = 10.dp, bottom = 4.dp)
    3 -> PaddingValues(top = 8.dp, bottom = 4.dp)
    4 -> PaddingValues(top = 6.dp, bottom = 2.dp)
    else -> PaddingValues(top = 6.dp, bottom = 2.dp)
}

private fun headerComponent(level: Int): MarkdownComponent = { model ->
    val style = when (level) {
        1 -> model.typography.h1
        2 -> model.typography.h2
        3 -> model.typography.h3
        4 -> model.typography.h4
        5 -> model.typography.h5
        else -> model.typography.h6
    }
    Box(Modifier.padding(headingMargin(level))) {
        MarkdownHeader(model.content, model.node, style)
    }
}
