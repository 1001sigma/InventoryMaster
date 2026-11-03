package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.local.LocalLlmRuntime
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KV 复用决策单测：planReuse 的前缀比对与各失效场景、mergeTurns 的指纹来源行为。
 * 原则：严格相等、宁错杀重建——任何可疑输入都必须落到 Recreate（等于无复用的历史行为）。
 */
class LocalLlmRuntimeReusePlanTest {

    private fun fp(role: String, text: String) = LocalLlmRuntime.TurnFp(role, text)

    // ── planReuse ───────────────────────────────────────────

    @Test
    fun `首次调用无已喂指纹_重建`() {
        val plan = LocalLlmRuntime.planReuse(null, emptyList(), 1, listOf(fp("user", "你好")))
        assertEquals(LocalLlmRuntime.ReusePlan.Recreate, plan)
    }

    @Test
    fun `前缀命中且追加一条user_复用`() {
        val fed = listOf(fp("user", "查近效期"))
        val full = listOf(fp("user", "查近效期"), fp("user", "工具结果：\n共5条"))
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Reuse("工具结果：\n共5条"), plan)
    }

    @Test
    fun `多轮后追加用户消息_复用`() {
        val fed = listOf(
            fp("user", "q1"), fp("model", "{\"tool\":\"query_records\"}"),
            fp("user", "工具结果：\nOK"), fp("model", "答案是5条")
        )
        val full = fed + fp("user", "q2")
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Reuse("q2"), plan)
    }

    @Test
    fun `历史分叉_重建`() {
        val fed = listOf(fp("user", "q1"), fp("model", "旧回复"))
        val full = listOf(fp("user", "q1"), fp("model", "不同的回复"), fp("user", "q2"))
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Recreate, plan)
    }

    @Test
    fun `incoming不长于已喂_重建`() {
        val fed = listOf(fp("user", "a"), fp("model", "b"))
        assertEquals(
            LocalLlmRuntime.ReusePlan.Recreate,
            LocalLlmRuntime.planReuse(1, fed, 1, fed)
        )
        assertEquals(
            LocalLlmRuntime.ReusePlan.Recreate,
            LocalLlmRuntime.planReuse(1, fed, 1, fed.dropLast(1))
        )
    }

    @Test
    fun `系统提示词哈希变化_重建`() {
        val fed = listOf(fp("user", "a"))
        val full = listOf(fp("user", "a"), fp("user", "b"))
        assertEquals(
            LocalLlmRuntime.ReusePlan.Recreate,
            LocalLlmRuntime.planReuse(1, fed, 2, full)
        )
    }

    @Test
    fun `双方均无系统提示词_可复用`() {
        val fed = listOf(fp("user", "a"))
        val full = listOf(fp("user", "a"), fp("user", "b"))
        val plan = LocalLlmRuntime.planReuse(null, fed, null, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Reuse("b"), plan)
    }

    @Test
    fun `delta超过一条_重建`() {
        val fed = listOf(fp("user", "a"))
        val full = listOf(fp("user", "a"), fp("user", "b"), fp("user", "c"))
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Recreate, plan)
    }

    @Test
    fun `delta为model角色_重建`() {
        val fed = listOf(fp("user", "a"))
        val full = listOf(fp("user", "a"), fp("model", "x"))
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Recreate, plan)
    }

    @Test
    fun `delta空白文本_重建`() {
        val fed = listOf(fp("user", "a"))
        val full = listOf(fp("user", "a"), fp("user", "  "))
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, full)
        assertEquals(LocalLlmRuntime.ReusePlan.Recreate, plan)
    }

    // ── mergeTurns（指纹来源，与 toHistoryMessages 同源） ──────

    @Test
    fun `mergeTurns_tool加前缀且与相邻user合并`() {
        // assistant 为空被跳过时，user 与 tool 相邻合并（该边角下 KV 复用会失配重建，方向保守安全）
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(AiMessage("user", "查"), AiMessage("tool", "共5条", toolCallId = "c1"))
        )
        assertEquals(listOf(fp("user", "查\n\n工具结果：\n共5条")), fps)
    }

    @Test
    fun `mergeTurns_model轮隔开时tool不合并`() {
        // 工具循环第 2 轮的典型历史形态：user → model(信封) → user(工具结果)
        val args = JsonParser.parseString("{}").asJsonObject
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(
                AiMessage("user", "查"),
                AiMessage(
                    "assistant", """{"tool":"query_records"}""",
                    toolCalls = listOf(AiToolCall("c1", "query_records", args))
                ),
                AiMessage("tool", "OK", toolCallId = "c1")
            )
        )
        assertEquals(
            listOf(
                fp("user", "查"),
                fp("model", """{"tool":"query_records"}"""),
                fp("user", "工具结果：\nOK")
            ),
            fps
        )
    }

    @Test
    fun `mergeTurns_连续同role合并`() {
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(AiMessage("user", "a"), AiMessage("user", "b"))
        )
        assertEquals(listOf(fp("user", "a\n\nb")), fps)
    }

    @Test
    fun `mergeTurns_空白assistant跳过`() {
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(AiMessage("user", "a"), AiMessage("assistant", "  "), AiMessage("user", "b"))
        )
        assertEquals(listOf(fp("user", "a\n\nb")), fps)
    }

    @Test
    fun `mergeTurns_assistant带toolCalls无content还原JSON形态`() {
        val args = JsonParser.parseString("""{"limit":5}""").asJsonObject
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(AiMessage("assistant", null, toolCalls = listOf(AiToolCall("c1", "query_records", args))))
        )
        assertEquals(listOf(fp("model", """{"tool":"query_records","arguments":{"limit":5}}""")), fps)
    }

    @Test
    fun `mergeTurns_assistant非空content优先于toolCalls`() {
        val args = JsonParser.parseString("{}").asJsonObject
        val fps = LocalLlmRuntime.mergeTurns(
            listOf(
                AiMessage(
                    "assistant", "模型原始输出",
                    toolCalls = listOf(AiToolCall("c1", "t", args))
                )
            )
        )
        assertEquals(listOf(fp("model", "模型原始输出")), fps)
    }

    @Test
    fun `mergeTurns_连续多条tool合并为一条user消息支持KV复用`() {
        val messages = listOf(
            AiMessage("user", "查A和B"),
            AiMessage("assistant", """{"tool":"query_records"}"""),
            AiMessage("tool", "结果A", toolCallId = "c1"),
            AiMessage("tool", "结果B", toolCallId = "c2")
        )
        val canonical = LocalLlmRuntime.mergeTurns(messages)
        assertEquals(
            listOf(
                fp("user", "查A和B"),
                fp("model", """{"tool":"query_records"}"""),
                fp("user", "工具结果：\n结果A\n\n工具结果：\n结果B")
            ),
            canonical
        )

        // 验证上次 fed 包含前 2 轮时，多工具合并结果依然能命中单条增量复用
        val fed = canonical.take(2)
        val plan = LocalLlmRuntime.planReuse(1, fed, 1, canonical)
        assertEquals(LocalLlmRuntime.ReusePlan.Reuse("工具结果：\n结果A\n\n工具结果：\n结果B"), plan)
    }

    // ── findRepetitionCollapseIndex ─────────────────────────

    @Test
    fun `8个连续感叹号_检测出坍塌起始位置`() {
        val text = "这是库存回答！已完成!!!!!!!!"
        val idx = LocalLlmRuntime.findRepetitionCollapseIndex(text)
        assertEquals(text.indexOf("!!!!!!!!"), idx)
    }

    @Test
    fun `狂吐数十个感叹号_返回首个感叹号位置以便彻底切除`() {
        val text = "结果正常内" + "!".repeat(50)
        val idx = LocalLlmRuntime.findRepetitionCollapseIndex(text)
        assertEquals(5, idx)
        assertEquals("结果正常内", text.substring(0, idx))
    }

    @Test
    fun `Markdown分割线与等号表头_不触发坍塌`() {
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex("正常回答\n--------------------\n"))
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex("正常回答\n====================\n"))
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex("正常回答\n********************\n"))
    }

    @Test
    fun `纯数字条码与GTIN_不触发坍塌`() {
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex("产品条码：00000000000000"))
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex("批号：11111111"))
    }

    @Test
    fun `短词片段连续重复4次_检测出坍塌起始位置`() {
        val prefix = "查询完成，"
        val text = prefix + "任务ID".repeat(5)
        val idx = LocalLlmRuntime.findRepetitionCollapseIndex(text)
        assertEquals(prefix.length, idx)
        assertEquals(prefix, text.substring(0, idx))
    }

    @Test
    fun `正常业务回答_不触发坍塌`() {
        val text = "已为您查询到该盘库任务（ID：1）下有效记录共 1 条：\n" +
            "1. 医用缝合线（批号 20260301）｜效期 2028-03-01｜库位 A-01-01\n" +
            "该记录效期尚早，状态正常。如需查看完整列表，请参考下方卡片。"
        assertEquals(-1, LocalLlmRuntime.findRepetitionCollapseIndex(text))
    }
}

