package com.example.inventorymaster.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.ai.agent.PendingQuestion

/**
 * 交互式问答全宽底部卡片（Human-in-the-Loop 全宽面板）。
 *
 * 优化特性：
 * 1. 【防键盘挤压变形】：内容区加入 verticalScroll，限制最大屏幕高度占比，输入框固定高度与内边距，弹起键盘时自适应滚动绝不变形；
 * 2. 【单选严格互斥】：单选模式下，选择文本框输入会自动取消预设选项的高亮；点击预设选项则清空并收起文本框，保证单选唯一性；
 * 3. 【无冗余单一输入框】：在列表项「✎ 其他补充...」内就地输入，移除底部重复多余的胶囊框，节省纵向空间；
 * 4. 【多选兼容】：多选模式下允许同时选中预设选项与输入补充。
 */
@Composable
fun InteractiveQuestionCard(
    pending: PendingQuestion,
    onSubmit: (answers: Map<Int, List<String>>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pending.questions.isEmpty()) return

    var currentIndex by remember(pending.requestId) { mutableIntStateOf(0) }
    val currentQ = pending.questions[currentIndex]

    // 用户选择状态：题号 -> List<String>
    val selectedAnswers = remember(pending.requestId) { mutableStateMapOf<Int, List<String>>() }
    // 用户自定义补充输入：题号 -> 文本
    val customInputs = remember(pending.requestId) { mutableStateMapOf<Int, String>() }

    // 是否展开列表项内置的输入框
    var isInlineCustomExpanded by remember(currentIndex) {
        mutableStateOf(!customInputs[currentIndex].isNullOrBlank())
    }
    val inlineFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val maxPanelHeight = (configuration.screenHeightDp * 0.78f).dp

    // 底部安全距离计算（支持键盘弹起与手势条）
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = maxOf(imeBottom, navBottom)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset)
            .heightIn(max = maxPanelHeight),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // ── 顶部栏：分页器与关闭按钮 ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左上角分页器：< 1/3 >
                if (pending.questions.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "上一题",
                                tint = if (currentIndex > 0) Color(0xFF475569) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${currentIndex + 1} / ${pending.questions.size}",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { if (currentIndex < pending.questions.size - 1) currentIndex++ },
                            enabled = currentIndex < pending.questions.size - 1,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "下一题",
                                tint = if (currentIndex < pending.questions.size - 1) Color(0xFF475569) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // 右上角关闭按钮
                IconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── 可滚动内容区（题干与所有选项，键盘弹起时自适应滚动不挤压） ─────────
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
            ) {
                // 标签与题干
                Text(
                    text = if (currentQ.isMultiSelect) "多选" else "单选",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )

                Text(
                    text = currentQ.question,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 选项列表
                val curSelections = selectedAnswers[currentIndex] ?: emptyList()
                val customText = customInputs[currentIndex].orEmpty()

                // 1. 预设选项
                currentQ.options.forEachIndexed { index, option ->
                    val isSelected = curSelections.contains(option)
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF8FAFC),
                        animationSpec = tween(120),
                        label = "optionBg"
                    )

                    Surface(
                        onClick = {
                            if (currentQ.isMultiSelect) {
                                // 多选模式：自由勾选/反选
                                val next = if (isSelected) curSelections - option else curSelections + option
                                selectedAnswers[currentIndex] = next
                            } else {
                                // 单选模式（核心修复2）：选择预设选项时，清空自定义输入并收起，保证互斥唯一
                                selectedAnswers[currentIndex] = listOf(option)
                                customInputs[currentIndex] = ""
                                isInlineCustomExpanded = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = bgColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 序号徽章
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFFBFDBFE) else Color(0xFFE2E8F0),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = option,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 列表内置「✎ 其他补充...」可输入选项
                if (currentQ.allowCustom) {
                    val hasCustomText = customText.isNotBlank()
                    val isCustomSelected = hasCustomText || isInlineCustomExpanded
                    val customBgColor by animateColorAsState(
                        targetValue = if (isCustomSelected) Color(0xFFE8F0FE) else Color(0xFFF8FAFC),
                        animationSpec = tween(120),
                        label = "customBg"
                    )

                    Surface(
                        onClick = {
                            val targetExpanded = !isInlineCustomExpanded
                            isInlineCustomExpanded = targetExpanded
                            if (targetExpanded && !currentQ.isMultiSelect) {
                                // 单选模式（核心修复2）：点击选择自定义选项时，立刻清空预设选项
                                selectedAnswers[currentIndex] = emptyList()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = customBgColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCustomSelected) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            color = if (isCustomSelected) Color(0xFFBFDBFE) else Color(0xFFE2E8F0),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = if (isCustomSelected) Color(0xFF1D4ED8) else Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = if (hasCustomText) "其他补充: $customText" else "其他补充...",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = if (isCustomSelected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isCustomSelected) Color(0xFF1E3A8A) else Color(0xFF64748B)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                if (isCustomSelected) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 展开就地文本输入框（固定高度与内边距，配合外层滚动绝不挤扁）
                            AnimatedVisibility(
                                visible = isInlineCustomExpanded,
                                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                                exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                            ) {
                                LaunchedEffect(isInlineCustomExpanded) {
                                    if (isInlineCustomExpanded) {
                                        inlineFocusRequester.requestFocus()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 2.dp)
                                        .height(44.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = customText,
                                        onValueChange = { newText ->
                                            customInputs[currentIndex] = newText
                                            if (!currentQ.isMultiSelect && newText.isNotBlank()) {
                                                // 单选模式（核心修复2）：输入文字时清空已选的预设选项
                                                selectedAnswers[currentIndex] = emptyList()
                                            }
                                        },
                                        textStyle = TextStyle(fontSize = 13.5.sp, color = Color(0xFF1E293B)),
                                        cursorBrush = SolidColor(Color(0xFF1D4ED8)),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(inlineFocusRequester),
                                        decorationBox = { innerTextField ->
                                            if (customText.isEmpty()) {
                                                Text(
                                                    "在此输入自定义选项或具体要求...",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 底部操作栏（去冗余，右侧操作清晰） ─────────────────────────
            val curSelections = selectedAnswers[currentIndex] ?: emptyList()
            val customText = customInputs[currentIndex].orEmpty()
            val hasChoice = curSelections.isNotEmpty() || customText.isNotBlank()
            val isLast = currentIndex >= pending.questions.size - 1

            fun proceed() {
                if (isLast) {
                    val finalResult = mutableMapOf<Int, List<String>>()
                    for (i in pending.questions.indices) {
                        val isMulti = pending.questions[i].isMultiSelect
                        val list = (selectedAnswers[i] ?: emptyList()).toMutableList()
                        val extra = customInputs[i]?.trim().orEmpty()

                        if (isMulti) {
                            // 多选模式：预设选择 + 补充说明组合提交
                            if (extra.isNotBlank()) {
                                val formattedExtra = if (list.isEmpty()) extra else "补充: $extra"
                                if (!list.contains(formattedExtra)) {
                                    list.add(formattedExtra)
                                }
                            }
                        } else {
                            // 单选模式：互斥生效，有输入内容且未选预设时，以输入内容为准；否则以预选项为准
                            if (extra.isNotBlank() && list.isEmpty()) {
                                list.add(extra)
                            }
                        }

                        if (list.isNotEmpty()) {
                            finalResult[i] = list
                        }
                    }
                    onSubmit(finalResult)
                } else {
                    currentIndex++
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧跳过按钮
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("跳过", fontSize = 13.5.sp, color = Color(0xFF64748B))
                }

                // 右侧主操作按钮（下一题 / 提交）
                Button(
                    onClick = { if (hasChoice) proceed() },
                    enabled = hasChoice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1D4ED8),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE2E8F0),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLast) "确定提交" else "下一题",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
