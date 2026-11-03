package com.example.inventorymaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.inventorymaster.ui.liquidglass.LiquidGlass
import com.example.inventorymaster.ui.liquidglass.LiquidGlassStyle
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// 计算器固定配色：不随应用主题变化（玻璃面板底色统一收在 LiquidGlassStyle.DarkGlass）
private val GlassAccent = Color(0xFFFF9F0A)
private val GlassTextPrimary = Color(0xFFF5F5F7)
private val GlassError = Color(0xFFFF6B61)

// 按键玻璃材质：数字/功能键为低透明白色玻璃，运算键为橙色染色玻璃；
// 小尺寸键的高光/暗边取面板参数的低档，避免 1dp 边缘在小键上过重
private val CalcKeyDigitStyle = LiquidGlassStyle(
    containerColor = Color.White,
    backgroundAlpha = 0.13f,
    borderAlpha = 0.45f,
    highlightAlpha = 0.70f,
    secondaryHighlightAlpha = 0.35f,
    innerShadowAlpha = 0.06f,
    innerShadowWidth = 1.dp,
    outerShadowAlpha = 0.10f,
    outerShadowElevation = 3.dp
)
private val CalcKeyUtilityStyle = CalcKeyDigitStyle.copy(backgroundAlpha = 0.20f)
private val CalcKeyOperatorStyle = CalcKeyDigitStyle.copy(
    containerColor = GlassAccent,
    backgroundAlpha = 0.88f,
    borderAlpha = 0.55f
)

@Composable
fun InventoryCalcDialog(
    initialValue: String = "",
    canBackfill: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val hapticType = HapticFeedbackType.TextHandleMove
    var expression by remember { mutableStateOf(initialValue) }
    var isCalculated by remember { mutableStateOf(initialValue.isNotEmpty() && initialValue.toDoubleOrNull() != null) }

    val resultValue = remember(expression) { CalcEngine.evaluate(expression) }
    val isError = !resultValue.isFinite()

    val displayResult = remember(resultValue) {
        if (isError) "无法计算"
        else DecimalFormat("0.####", DecimalFormatSymbols(Locale.US)).format(resultValue)
    }

    val resultFontSize = when {
        displayResult.length > 15 -> 24.sp
        displayResult.length > 13 -> 28.sp
        displayResult.length > 11 -> 32.sp
        else -> 40.sp
    }

    Dialog(
        onDismissRequest = {
            if (!canBackfill) {
                if (expression.isNotEmpty() && !isError) {
                    onConfirm(displayResult)
                } else if (expression.isEmpty()) {
                    onConfirm("")
                }
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            // 弹窗窗口本身不加暗化、不加背景模糊：周边背景保持原样清晰
            window.setDimAmount(0f)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(28.dp),
                style = LiquidGlassStyle.DarkGlass
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
                    // 表达式行：超长时横向滚动，输入后始终跟随到末尾
                    val exprScroll = rememberScrollState()
                    LaunchedEffect(expression) {
                        withFrameNanos { } // 等新表达式完成布局，maxValue 才是最新值
                        exprScroll.scrollTo(exprScroll.maxValue)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(exprScroll),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = expression.ifEmpty { "请输入..." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = GlassTextPrimary.copy(alpha = 0.55f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // 结果行：左侧常驻半透提示符引导点击回填，点击提示符或结果文本均可快速回填确认
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canBackfill && !isError) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onConfirm(displayResult)
                                    onDismiss()
                                } else Modifier
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canBackfill && !isError && displayResult.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(
                                        color = (if (isCalculated) GlassAccent else Color.White).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "↵",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = (if (isCalculated) GlassAccent else GlassTextPrimary).copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "点击回填",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = (if (isCalculated) GlassAccent else GlassTextPrimary).copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Text(
                            text = displayResult,
                            fontSize = if (isError) 20.sp else resultFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            color = when {
                                isError -> GlassError
                                isCalculated -> GlassAccent
                                else -> GlassTextPrimary
                            },
                            maxLines = 1
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    )

                    CalcKeypad { key ->
                        haptic.performHapticFeedback(hapticType)
                        val (newExpr, calcStatus) = handleInputLogic(
                            expression, key, isCalculated, displayResult
                        )
                        expression = newExpr
                        isCalculated = calcStatus
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKeypad(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CalcKeyRow("C", "⌫", "÷", "×", onKey = onKey)
        CalcKeyRow("7", "8", "9", "-", onKey = onKey)
        CalcKeyRow("4", "5", "6", "+", onKey = onKey)
        // 底部区域：「=」纵跨两行（右下高键），「0」横跨两列
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(3f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalcKey("1", Modifier.weight(1f).height(48.dp), onKey)
                    CalcKey("2", Modifier.weight(1f).height(48.dp), onKey)
                    CalcKey("3", Modifier.weight(1f).height(48.dp), onKey)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalcKey("0", Modifier.weight(2f).height(48.dp), onKey)
                    CalcKey(".", Modifier.weight(1f).height(48.dp), onKey)
                }
            }
            CalcKey("=", Modifier.weight(1f).fillMaxHeight(), onKey)
        }
    }
}

@Composable
private fun CalcKeyRow(vararg keys: String, onKey: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { key ->
            CalcKey(key, Modifier.weight(1f).height(48.dp), onKey)
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    onKey: (String) -> Unit
) {
    val isOperator = "÷×-+=".contains(label)
    val isUtility = label == "C" || label == "⌫"
    val interactionSource = remember { MutableInteractionSource() }

    LiquidGlass(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null, // 按压反馈由玻璃材质承担，不用 ripple
            onClick = { onKey(label) }
        ),
        shape = RoundedCornerShape(16.dp),
        style = when {
            isOperator -> CalcKeyOperatorStyle
            isUtility -> CalcKeyUtilityStyle
            else -> CalcKeyDigitStyle
        },
        interactionSource = interactionSource,
        contentAlignment = Alignment.Center
    ) {
        // 橙色玻璃上用深色文字保证对比度，其余用白字
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (isOperator) Color(0xFF1C1C1E) else Color.White,
            fontWeight = if (isOperator) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Input logic ───
private fun handleInputLogic(
    current: String,
    input: String,
    wasCalculated: Boolean,
    lastResult: String
): Pair<String, Boolean> {
    return when (input) {
        "C" -> "" to false
        // 计算完成后退格 = 回到编辑原表达式
        "⌫" -> current.dropLast(1) to false
        // 小数点：已计算则重新输入；当前数字段已有小数点则忽略；空段补前导 0
        "." -> {
            if (wasCalculated) {
                "0." to false
            } else {
                val segment = current.split("÷", "×", "-", "+").last()
                when {
                    segment.contains('.') -> current to false
                    segment.isEmpty() -> (current + "0.") to false
                    else -> (current + ".") to false
                }
            }
        }
        "=" -> current to true
        "÷", "×", "-", "+" -> {
            if (wasCalculated && lastResult.toDoubleOrNull() != null) {
                (lastResult + input) to false
            } else if (wasCalculated) {
                // 上一次结果无效（无法计算）：保留原表达式等待退格修改
                current to false
            } else if (current.isEmpty()) {
                if (input == "-") "-" to false else "" to false
            } else {
                val lastChar = current.last()
                if ("÷×-+".contains(lastChar)) {
                    if (current.length == 1 && lastChar == '-') {
                        "" to false
                    } else {
                        (current.dropLast(1) + input) to false
                    }
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
                } else if (current == "-0") {
                    if (input == "0") current to false else ("-" + input) to false
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

// ─── Calculation engine ───
// internal：CalcTest 需要 JVM 单测访问（private 文件级可见性导致测试源集编译失败）
internal object CalcEngine {
    fun evaluate(expr: String): Double {
        if (expr.isEmpty()) return 0.0
        var cleanExpr = expr
        while (cleanExpr.isNotEmpty() && "÷×-+".contains(cleanExpr.last())) {
            cleanExpr = cleanExpr.dropLast(1)
        }
        if (cleanExpr.isEmpty() || cleanExpr == "-") return 0.0

        return try {
            val tokens = tokenize(cleanExpr)
            if (tokens.isEmpty()) return 0.0

            val postMDList = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token == "×" || token == "÷") {
                    val prevNum = postMDList.removeAt(postMDList.size - 1).toDouble()
                    val nextNum = tokens[i + 1].toDouble()
                    if (token == "÷" && nextNum == 0.0) return Double.NaN
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
            finalResult
        } catch (e: Exception) {
            Double.NaN // 异常严禁返回 0.0，必须返回 NaN 提示无法计算
        }
    }

    private fun tokenize(expr: String): List<String> {
        val result = mutableListOf<String>()
        var numberAccumulator = ""
        for (i in expr.indices) {
            val char = expr[i]
            if (char in "0123456789.") {
                numberAccumulator += char
            } else if (char in "÷×-+") {
                // 负数前缀判定：当 '-' 出现在表达式开头，或前一个 token 是操作符时，视为负号
                val isLeadingNegative = char == '-' && numberAccumulator.isEmpty() &&
                    (result.isEmpty() || result.last() in "÷×-+")
                if (isLeadingNegative) {
                    numberAccumulator += char
                } else {
                    if (numberAccumulator.isNotEmpty()) {
                        result.add(numberAccumulator)
                        numberAccumulator = ""
                    }
                    result.add(char.toString())
                }
            }
        }
        if (numberAccumulator.isNotEmpty()) result.add(numberAccumulator)
        return result
    }
}
