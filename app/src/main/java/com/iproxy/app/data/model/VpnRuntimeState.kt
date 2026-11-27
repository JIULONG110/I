package com.iproxy.app.data.model

/**
 * VPN 运行状态枚举
 */
enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * 实时运行态快照，用于驱动首页仪表盘、通知栏与日志页
 */
data class VpnRuntimeState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeNodeId: String? = null,
    val activeNodeName: String? = null,
    val activeNodeProtocol: String? = null,
    val connectedSinceMs: Long = 0L,
    val uplinkSpeedBps: Long = 0L,
    val downlinkSpeedBps: Long = 0L,
    val totalUplinkBytes: Long = 0L,
    val totalDownlinkBytes: Long = 0L,
    val lastLatencyMs: Long? = null,
    val lastError: String? = null,
    val coreVersion: String = ""
)

/**
 * 单条核心日志记录
 */
data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val tag: String = "I",
    val message: String
)
