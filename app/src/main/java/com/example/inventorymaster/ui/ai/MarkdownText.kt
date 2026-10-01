package com.example.inventorymaster.ui.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.utils.getUnescapedTextInNode

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph)

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
            }
        )
    )
}
