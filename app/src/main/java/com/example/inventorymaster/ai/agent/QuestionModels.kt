package com.example.inventorymaster.ai.agent

import java.util.UUID

/**
 * 交互式问答单题定义（对应用户交互卡片中的一道题目）。
 */
data class QuestionItem(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val isMultiSelect: Boolean = false,
    val allowCustom: Boolean = true
)

/**
 * 用户交互回答结果。
 */
data class UserQuestionAnswer(
    val isSkipped: Boolean = false,
    /** key 为题号（0-based），value 为用户选择或输入的选项文本列表 */
    val selectedOptions: Map<Int, List<String>> = emptyMap()
)

/**
 * 界面挂起中的问答状态（用于 ViewModel 传递到 UI 卡片渲染）。
 */
data class PendingQuestion(
    val requestId: String,
    val questions: List<QuestionItem>
)
