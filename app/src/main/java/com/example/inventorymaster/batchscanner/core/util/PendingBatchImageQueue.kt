package com.example.inventorymaster.batchscanner.core.util

import android.net.Uri

/**
 * 跨页面传递多选图片队列的临时容器
 *
 * 任务详情页"图片"按钮多选后，导航路由只支持单个 uri，
 * 先把完整队列存这里，批量识别页初始化时 [consume] 消费（取走即清空）
 */
object PendingBatchImageQueue {

    private var uris: List<Uri> = emptyList()

    fun put(list: List<Uri>) {
        uris = list
    }

    /** 取出并清空 */
    fun consume(): List<Uri> {
        val result = uris
        uris = emptyList()
        return result
    }
}
