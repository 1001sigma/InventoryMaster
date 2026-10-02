package com.example.inventorymaster.ai.tools

/** 工具失败分类（docs/AI_Runtime 架构改造与演进建议 §9.3） */
enum class ToolErrorKind {
    /** 参数缺失/类型错误/取值非法——错误文案回喂模型自纠（Agent Recovery） */
    INVALID_ARGUMENTS,

    /** 工具名不存在于注册表 */
    NOT_FOUND,

    /** 工具被禁用或用户拒绝执行 */
    PERMISSION_DENIED,

    /** 执行超时 */
    TIMEOUT,

    /** 业务失败（数据库错误、AI 子调用失败等） */
    BUSINESS_ERROR,

    /** 未预期异常 */
    INTERNAL_ERROR
}

data class ToolError(val kind: ToolErrorKind, val message: String)
