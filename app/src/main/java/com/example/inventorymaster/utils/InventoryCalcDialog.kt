package com.example.inventorymaster.utils

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import java.text.DecimalFormat


@Composable
fun InventoryCalcDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticType = androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress // 模拟点击感
    // 保存当前输入的表达式，例如 "12+5"
    var expression by remember { mutableStateOf("") }

    // 新增状态：标记是否刚刚点击过“=”
    var isCalculated by remember { mutableStateOf(false) }

    // 实时计算结果 (基于表达式的变化自动计算)
    val resultValue by remember(expression) {
        derivedStateOf {
            CalcEngine.evaluate(expression)
        }
    }

    val displayResult = remember(resultValue) {
        val df = DecimalFormat("0.####") // 最多保留8位小数
        df.format(resultValue)
    }

    // 动态计算字号，防止结果过长溢出
    val resultFontSize = when {
        displayResult.length > 15 -> 24.sp
        displayResult.length > 13 -> 28.sp
        displayResult.length > 11 -> 32.sp
        else -> 40.sp
    }

    Dialog(onDismissRequest = onDismiss) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider ?.window?.setDimAmount(0f)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 4.dp
                ),
                horizontalAlignment = Alignment.End
            ) {
                // 1. 运算过程回显区
                Text(
                    text = expression.ifEmpty { "请输入..." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                // 2. 最终结果显示区（点击此处直接覆盖并关闭）
                Text(
                    text = displayResult,
                    fontSize = resultFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = if (isCalculated) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    maxLines = 1,
                    modifier = Modifier
                        .clickable {
                            onConfirm(displayResult) // 回传结果
                            onDismiss() // 关闭弹窗
                        }
                        .padding(vertical = 12.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                // 3. 4x4 按键矩阵
                val buttons = listOf(
                    "7", "8", "9", "÷",
                    "4", "5", "6", "×",
                    "1", "2", "3", "-",
                    "DYNAMIC_C", "0", "=", "+"
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(buttons) { btn ->
                        val displayBtn = if (btn == "DYNAMIC_C") {
                            if (isCalculated) "C" else "⌫"
                        } else btn

                        CalculatorButton(displayBtn) {
                            // 触发震动
                            haptic.performHapticFeedback(hapticType)

                            // 处理逻辑
                            val (newExpr, calcStatus) = handleInputLogic(
                                expression, displayBtn, isCalculated, displayResult
                            )
                            expression = newExpr
                            isCalculated = calcStatus
                        }
                        /**
                         * val result = Pair("12+5", false)
                         *
                         * println(result.first)  // 输出: 12+5
                         * println(result.second) // 输出: false
                         */
                    }
                }
            }
        }
    }
}

/**
 * 内部组件：计算器按钮
 */
@Composable
fun CalculatorButton(text: String, onClick: () -> Unit) {
    val isOperator = "÷×-+=C".contains(text)
    val buttonAlpha = 0.4f
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = if (isOperator) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(buttonAlpha),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxSize().aspectRatio(1f)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * 输入逻辑处理（防呆逻辑）
 */
private fun handleInputLogic(
    current: String,
    input: String,
    wasCalculated: Boolean,
    lastResult: String
): Pair<String, Boolean> {
    return when (input) {
        "C" -> "" to false
        "⌫" -> { // 输入过程中点退格
            if (current.isNotEmpty()) {
                current.dropLast(1) to false
            } else {
                "" to false
            }
        }
        "=" -> current to true // 等于号在我们的逻辑里其实不需要特殊处理，因为是实时计算的
        "÷", "×", "-", "+" -> {
            if (wasCalculated) {
                // 【关键：连续计算】如果刚算完又点运算符，把结果接在前面
                (lastResult + input) to false
            } else if(current.isEmpty()) {
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
        else -> { // 点击数字 0-9
            if (wasCalculated) {
                input to false
            } else {
                // --- 核心防御：防止一排 0 ---
                if (current == "0") {
                    // 如果当前只有 0，点 0 没反应，点 1-9 替换它
                    if (input == "0") current to false else input to false
                } else if (current.endsWith("+0") || current.endsWith("-0") ||
                    current.endsWith("×0") || current.endsWith("÷0")) {
                    // 如果是运算符后面跟着 0，再点 0 没反应，点 1-9 替换那个 0
                    if (input == "0") current to false else (current.dropLast(1) + input) to false
                } else {
                    (current + input) to false
                }
            }
        }
    }
}

/**
 * 运算引擎：支持先乘除后加减
 */
//private
object CalcEngine {
    fun evaluate(expr: String): Double {
        if (expr.isEmpty()) return 0.0

        // 1. 预处理：如果末尾是运算符，先去掉，防止解析失败
        var cleanExpr = expr
        while (cleanExpr.isNotEmpty() && "÷×-+".contains(cleanExpr.last())) {
            cleanExpr = cleanExpr.dropLast(1)
        }
        if (cleanExpr.isEmpty()) return 0.0

        try {
            // 2. 将字符串拆解为数字和运算符列表
            // 例如 "10+5×2" -> ["10", "+", "5", "×", "2"]
            val tokens = tokenize(cleanExpr)

            // 3. 第一轮逻辑：处理 乘法 和 除法
            val postMDList = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token == "×" || token == "÷") {
                    val prevNum = postMDList.removeAt(postMDList.size - 1).toDouble()
                    val nextNum = tokens[i + 1].toDouble()
                    val res = if (token == "×") prevNum * nextNum else prevNum / nextNum
                    postMDList.add(res.toString())
                    i += 2 // 跳过运算符和下一个数字
                } else {
                    postMDList.add(token)
                    i++
                }
            }

            // 4. 第二轮逻辑：处理 加法 和 减法
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
            return 0.0 // 解析出错返回 0
        }
    }

    // 辅助函数：将 "10+5.5" 这种字符串切成列表
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