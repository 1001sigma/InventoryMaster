package com.example.inventorymaster.ai.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * OpenAI 兼容请求体构造（纯函数，可单测）。
 *
 * 兼容性怪癖（均有实机依据，改动需回归主流服务商）：
 * - assistant.content 为 null 时序列化为 ""（NVIDIA 网关反序列化崩）
 * - tool_calls.arguments 必须是 JSON **字符串**而非对象（OpenAI 规范，严格网关校验，
 *   回传成对象会被 NVIDIA 等直接 500）
 * - system 提示词置于消息列首；tools 为空数组时不携带
 */
object OpenAiRequestBuilder {

    fun build(
        gson: Gson,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?,
        stream: Boolean
    ): String {
        val json = JsonObject()
        json.addProperty("model", model)
        json.addProperty("stream", stream)

        val messagesArray = JsonArray()
        systemPrompt?.let {
            messagesArray.add(gson.toJsonTree(AiMessage("system", it)))
        }

        messages.forEach { msg ->
            when (msg.role) {
                "assistant" -> {
                    val obj = JsonObject()
                    obj.addProperty("role", "assistant")
                    // 完美兼容英伟达：content 为 null 强制转空字符串，防止反序列化异常
                    obj.addProperty("content", msg.content ?: "")

                    msg.toolCalls?.let { tcList ->
                        val arr = JsonArray()
                        tcList.forEach { tc ->
                            val tcObj = JsonObject()
                            tcObj.addProperty("id", tc.id)
                            tcObj.addProperty("type", "function")
                            val fnObj = JsonObject()
                            fnObj.addProperty("name", tc.name)
                            // OpenAI 规范：arguments 必须是 JSON 字符串（不是对象）
                            fnObj.addProperty("arguments", tc.arguments.toString())
                            tcObj.add("function", fnObj)
                            arr.add(tcObj)
                        }
                        obj.add("tool_calls", arr)
                    }
                    messagesArray.add(obj)
                }
                "tool" -> {
                    val obj = JsonObject()
                    obj.addProperty("role", "tool")
                    obj.addProperty("tool_call_id", msg.toolCallId ?: "")
                    obj.addProperty("content", msg.content ?: "")
                    messagesArray.add(obj)
                }
                else -> {
                    messagesArray.add(gson.toJsonTree(msg))
                }
            }
        }
        json.add("messages", messagesArray)

        tools?.let { toolDefs ->
            if (toolDefs.isNotEmpty()) {
                val toolsArray = JsonArray()
                toolDefs.forEach { def ->
                    val toolObj = JsonObject()
                    toolObj.addProperty("type", "function")
                    val functionObj = JsonObject()
                    functionObj.addProperty("name", def.name)
                    functionObj.addProperty("description", def.description)
                    functionObj.add("parameters", def.parameters)
                    toolObj.add("function", functionObj)
                    toolsArray.add(toolObj)
                }
                json.add("tools", toolsArray)
            }
        }

        return gson.toJson(json)
    }
}
