package com.example.inventorymaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.text.DecimalFormat

@Composable
fun InventoryCalcDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val hapticType = HapticFeedbackType.LongPress
    var expression by remember { mutableStateOf("") }
    var isCalculated by remember { mutableStateOf(false) }

    val resultValue by remember(expression) {
        derivedStateOf { CalcEngine.evaluate(expression) }
    }

    val displayResult = remember(resultValue) {
        val df = DecimalFormat("0.####")
        df.format(resultValue)
    }

    val resultFontSize = when {
        displayResult.length > 15 -> 24.sp
        displayResult.length > 13 -> 28.sp
        displayResult.length > 11 -> 32.sp
        else -> 40.sp
    }

    // 增加 DialogProperties，允许内容更自由地布局
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 核心修复：强制清除 Dialog 默认的灰色/纯色背景，并调暗外部遮罩
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setDimAmount(0f) // 让弹窗外围略微变暗，从而衬托出玻璃的白亮
            window.setBackgroundDrawableResource(android.R.color.transparent) // 关键：抽掉底层的灰色遮罩板
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp) // 控制边缘距离
        ) {
            // ─── 极致透亮玻璃主面板 ───
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 24.dp,
                        bottom = 16.dp
                    ),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression.ifEmpty { "请输入..." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )

                    Text(
                        text = displayResult,
                        fontSize = resultFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = if (isCalculated) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable {
                                onConfirm(displayResult)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = Color.White.copy(alpha = 0.3f)
                    )

                    val buttons = listOf(
                        "7", "8", "9", "÷",
                        "4", "5", "6", "×",
                        "1", "2", "3", "-",
                        "DYNAMIC_C", "0", "=", "+"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(buttons) { btn ->
                            val displayBtn = if (btn == "DYNAMIC_C") {
                                if (isCalculated) "C" else "⌫"
                            } else btn

                            CalculatorButton(displayBtn) {
                                haptic.performHapticFeedback(hapticType)
                                val (newExpr, calcStatus) = handleInputLogic(
                                    expression, displayBtn, isCalculated, displayResult
                                )
                                expression = newExpr
                                isCalculated = calcStatus
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── 纯净透亮玻璃背板 ───
@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    // 提升亮白度，剔除暗色，让界面更清透
    val glassBackgroundBrush = Brush.linearGradient(
        colors = listOf(
//            Color.White.copy(alpha = 0.7f), // 左上角更白亮
//            Color.White.copy(alpha = 0.25f) // 右下角保持通透度
            Color.Transparent,
            Color.Transparent
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // 强化左上角光泽感
    val glassBorderBrush = Brush.linearGradient(
        0.0f to Color.White.copy(alpha = 0.9f),
        0.3f to Color.White.copy(alpha = 0.3f),
        1.0f to Color.White.copy(alpha = 0.05f),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Surface(
        color = Color.Transparent, // 必须透明
        shape = shape,
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .border(width = 1.5.dp, brush = glassBorderBrush, shape = shape)
            .background(glassBackgroundBrush, shape)
            .clip(shape)
    ) {
        content()
    }
}

// ─── 轻量化透明按钮 ───
@Composable
private fun CalculatorButton(text: String, onClick: () -> Unit) {
    val isOperator = "÷×-+=C".contains(text)
    val colors = MaterialTheme.colorScheme

    // 按钮使用较亮的半透明白色，不再发灰
    val bgColor = if (isOperator) colors.primary.copy(alpha = 0.15f)
    else Color.White.copy(alpha = 0.4f)

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = if (isOperator) colors.primary else colors.onSurface
        ),
        elevation = null,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.3f) // 加强按键的细微描边
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (isOperator) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Input logic (保持不变) ───
private fun handleInputLogic(
    current: String,
    input: String,
    wasCalculated: Boolean,
    lastResult: String
): Pair<String, Boolean> {
    return when (input) {
        "C" -> "" to false
        "⌫" -> {
            if (current.isNotEmpty()) current.dropLast(1) to false else "" to false
        }
        "=" -> current to true
        "÷", "×", "-", "+" -> {
            if (wasCalculated) {
                (lastResult + input) to false
            } else if (current.isEmpty()) {
                "" to false
            } else {
                val lastChar = current.last()
                if ("÷×-+".contains(lastChar)) {
                    (current.dropLast(1) + input) to false
                } else {
                    (current + input) to false
                }
            }
        }
        else -> {
            if (wasCalculated) {
                input to false
            } else {
                if (current == "0") {
                    if (input == "0") current to false else input to false
                } else if (current.endsWith("+0") || current.endsWith("-0") ||
                    current.endsWith("×0") || current.endsWith("÷0")) {
                    if (input == "0") current to false else (current.dropLast(1) + input) to false
                } else {
                    (current + input) to false
                }
            }
        }
    }
}

// ─── Calculation engine (保持不变) ───
private object CalcEngine {
    fun evaluate(expr: String): Double {
        if (expr.isEmpty()) return 0.0
        var cleanExpr = expr
        while (cleanExpr.isNotEmpty() && "÷×-+".contains(cleanExpr.last())) {
            cleanExpr = cleanExpr.dropLast(1)
        }
        if (cleanExpr.isEmpty()) return 0.0

        try {
            val tokens = tokenize(cleanExpr)
            val postMDList = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token == "×" || token == "÷") {
                    val prevNum = postMDList.removeAt(postMDList.size - 1).toDouble()
                    val nextNum = tokens[i + 1].toDouble()
                    val res = if (token == "×") prevNum * nextNum else prevNum / nextNum
                    postMDList.add(res.toString())
                    i += 2
                } else {
                    postMDList.add(token)
                    i++
                }
            }

            var finalResult = postMDList[0].toDouble()
            var j = 1
            while (j < postMDList.size) {
                val op = postMDList[j]
                val valNext = postMDList[j + 1].toDouble()
                finalResult = if (op == "+") finalResult + valNext else finalResult - valNext
                j += 2
            }
            return finalResult
        } catch (e: Exception) {
            return 0.0
        }
    }

    private fun tokenize(expr: String): List<String> {
        val result = mutableListOf<String>()
        var numberAccumulator = ""
        for (char in expr) {
            if (char in "0123456789.") {
                numberAccumulator += char
            } else if (char in "÷×-+") {
                if (numberAccumulator.isNotEmpty()) {
                    result.add(numberAccumulator)
                    numberAccumulator = ""
                }
                result.add(char.toString())
            }
        }
        if (numberAccumulator.isNotEmpty()) result.add(numberAccumulator)
        return result
    }
}