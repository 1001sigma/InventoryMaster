package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.tools.ToolErrorKind
import com.example.inventorymaster.ai.tools.ToolValidator
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具参数校验测试（docs/AI_Runtime §5.1：参数合法性前移到执行边界） */
class ToolValidatorTest {

    private val schema: JsonObject = JsonParser.parseString("""
        {
          "type": "object",
          "properties": {
            "session_id": {"type": "integer"},
            "product_keyword": {"type": "string"},
            "group_by": {"type": "string", "enum": ["productName", "location"]},
            "options": {"type": "object"}
          },
          "required": ["session_id"]
        }
    """).asJsonObject

    private fun validate(args: String) = ToolValidator.validate(schema, JsonParser.parseString(args).asJsonObject)

    @Test
    fun `合法参数通过`() {
        assertNull(validate("""{"session_id": 3, "product_keyword": "输液器", "group_by": "location"}"""))
    }

    @Test
    fun `缺少必填参数报INVALID_ARGUMENTS`() {
        val error = validate("""{"product_keyword": "输液器"}""")
        assertEquals(ToolErrorKind.INVALID_ARGUMENTS, error?.kind)
        assertTrue(error?.message.orEmpty().contains("session_id"))
    }

    @Test
    fun `null值视为缺失`() {
        val error = validate("""{"session_id": null}""")
        assertTrue(error?.message.orEmpty().contains("session_id"))
    }

    @Test
    fun `类型不匹配报错`() {
        val error = validate("""{"session_id": 3, "product_keyword": 123}""")
        assertTrue(error?.message.orEmpty().contains("product_keyword"))
        assertTrue(error?.message.orEmpty().contains("string"))
    }

    @Test
    fun `integer拒绝小数`() {
        val error = validate("""{"session_id": 3.5}""")
        assertTrue(error?.message.orEmpty().contains("session_id"))
        assertNull(validate("""{"session_id": 3}"""))
    }

    @Test
    fun `enum非法取值报错并列出可选值`() {
        val error = validate("""{"session_id": 1, "group_by": "danger"}""")
        assertEquals(ToolErrorKind.INVALID_ARGUMENTS, error?.kind)
        assertTrue(error?.message.orEmpty().contains("productName"))
        assertTrue(error?.message.orEmpty().contains("location"))
    }

    @Test
    fun `object类型匹配`() {
        assertNull(validate("""{"session_id": 1, "options": {"k": "v"}}"""))
        assertTrue(validate("""{"session_id": 1, "options": "not-object"}""") != null)
    }

    @Test
    fun `无properties的schema直接通过`() {
        assertNull(ToolValidator.validate(JsonObject(), JsonParser.parseString("""{"any":1}""").asJsonObject))
    }
}
