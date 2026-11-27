package com.iproxy.app.data.model

import java.util.UUID

/**
 * 订阅分组数据模型
 */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val autoUpdateEnabled: Boolean = true,
    val updateIntervalHours: Int = 24,
    val lastUpdatedAt: Long = 0L,
    val nodeCount: Int = 0,
    // 以下信息解析自响应头 Subscription-Userinfo
    val uploadBytes: Long = -1L,
    val downloadBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val expireTimestampSec: Long = -1L,
    val lastError: String? = null
) {
    val usedBytes: Long
        get() = if (uploadBytes >= 0 && downloadBytes >= 0) uploadBytes + downloadBytes else -1L

    val remainingBytes: Long
        get() = if (totalBytes > 0 && usedBytes >= 0) (totalBytes - usedBytes).coerceAtLeast(0L) else -1L
}
