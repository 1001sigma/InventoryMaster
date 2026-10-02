package com.example.inventorymaster.ai.tools

import com.google.gson.JsonObject

/**
 * 工具参数校验（docs/AI_Runtime §5.1：把"参数合法性"从工具内部前移到执行边界）。
 *
 * 校验工具 parameters 声明（JSON Schema 子集）：
 * 1. required 字段存在且非 null；
 * 2. 已提供字段的 type 匹配（string/number/integer/boolean/object/array）；
 * 3. enum 取值合法。
 * 业务校验（产品是否存在、session 是否有效等）仍归工具自身。
 *
 * 返回 null 表示通过；否则返回带可自纠文案的 [ToolError]，由 ToolExecutor 拦截执行并回喂模型。
 */
object ToolValidator {

    fun validate(schema: JsonObject, arguments: JsonObject): ToolError? {
        val properties = schema.getAsJsonObject("properties") ?: return null

        schema.getAsJsonArray("required")?.forEach { element ->
            val key = element.asString
            val value = arguments.get(key)
            if (value == null || value.isJsonNull) {
                return ToolError(
                    ToolErrorKind.INVALID_ARGUMENTS,
                    "参数错误：缺少必填参数 \"$key\"。请补全该参数后重新调用。"
                )
            }
        }

        properties.entrySet().forEach { (key, schemaElement) ->
            val value = arguments.get(key)
            if (value == null || value.isJsonNull) return@forEach
            if (!schemaElement.isJsonObject) return@forEach
            val fieldSchema = schemaElement.asJsonObject

            val expectedType = fieldSchema.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            if (expectedType != null && !matchesType(value, expectedType)) {
                return ToolError(
                    ToolErrorKind.INVALID_ARGUMENTS,
                    "参数错误：\"$key\" 类型应为 $expectedType，实际收到 \"$value\"。请修正后重试。"
                )
            }

            val enumValues = fieldSchema.getAsJsonArray("enum")
            if (enumValues != null && value.isJsonPrimitive) {
                val allowed = enumValues.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                if (allowed.isNotEmpty() && value.asString !in allowed) {
                    return ToolError(
                        ToolErrorKind.INVALID_ARGUMENTS,
                        "参数错误：\"$key\" 取值 \"$value\" 非法。可选值：${allowed.joinToString(" / ") { "\"$it\"" }}。"
                    )
                }
            }
        }
        return null
    }

    private fun matchesType(value: com.google.gson.JsonElement, expectedType: String): Boolean = when (expectedType) {
        "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
        "number" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
        "integer" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber && !value.asJsonPrimitive.asString.contains('.')
        "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        "object" -> value.isJsonObject
        "array" -> value.isJsonArray
        else -> true
    }
}
