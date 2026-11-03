package com.example.inventorymaster.data.network

/**
 * 协作接口的业务错误（M1/M2）：服务端契约 403/409/428 + {"detail","code"}（doc §6.4-d）。
 * code 例：TASK_ARCHIVED / JOIN_CLOSED / DEVICE_NOT_BOUND / DEVICE_NOT_IDENTIFIED /
 * DEVICE_NOT_PARTICIPANT / ARCHIVE_BLOCKED / OPERATOR_INACTIVE / ALREADY_BOUND / INVALID_CODE。
 * 终态错误不可重试（RetryPolicy 只用于网络类失败）。
 */
class CollabApiException(
    val code: String,
    message: String
) : Exception(message)
