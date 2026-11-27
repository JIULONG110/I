package com.iproxy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.iproxy.app.R
import com.iproxy.app.core.util.CoreLogBuffer
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.core.xray.XrayConfigGenerator
import com.iproxy.app.data.local.AppStorage
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.PerAppMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.VpnRuntimeState
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.MainActivity
import com.wgtunnel.hevtunnel.HevTunnelConfig
import com.wgtunnel.hevtunnel.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「I」核心 VPN 前台服务
 *
 * 负责协调：
 * 1. Xray-core (libv2ray) 内核初始化、配置启动与实时流量采样
 * 2. Android VpnService TUN 虚拟网卡建立与分应用路由规则
 * 3. hev-socks5-tunnel JNI 隧道将 TUN 数据包转发到本地 SOCKS5 入站
 */
class IVpnService : VpnService(), CoreCallbackHandler {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var hevRunning = false
    private var statsJob: Job? = null
    private val transitionLock = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        ensureNotificationChannel()
        CoreLogBuffer.i(TAG, "IVpnService 已创建，核心版本: ${runCatching { Libv2ray.checkVersionX() }.getOrDefault("unknown")}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnTunnel()
                return START_NOT_STICKY
            }
            ACTION_START, SERVICE_INTERFACE, null -> {
                startForegroundWithNotification("正在连接...", "准备启动代理内核")
                if (transitionLock.compareAndSet(false, true)) {
                    serviceScope.launch {
                        try {
                            startVpnTunnelInternal()
                        } finally {
                            transitionLock.set(false)
                        }
                    }
                }
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        CoreLogBuffer.w(TAG, "系统撤销了 VPN 权限，停止服务")
        stopVpnTunnel()
    }

    override fun onDestroy() {
        stopVpnTunnelSync()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpnTunnelInternal() {
        val storage = AppStorage(applicationContext)
        val node = storage.getSelectedNodeSync()
        val settings = storage.readSettingsSnapshotSync()

        if (node == null) {
            val msg = "未选择任何可用节点，请先导入节点或订阅"
            CoreLogBuffer.e(TAG, msg)
            updateState(
                VpnRuntimeState(
                    status = VpnStatus.ERROR,
                    lastError = msg
                )
            )
            stopSelf()
            return
        }

        // 若此前仍在运行，先安全关闭旧实例
        stopVpnTunnelSync(resetState = false)

        updateState(
            VpnRuntimeState(
                status = VpnStatus.CONNECTING,
                activeNodeId = node.id,
                activeNodeName = node.name,
                activeNodeProtocol = node.badgeText(),
                coreVersion = runCatching { Libv2ray.checkVersionX() }.getOrDefault("")
            )
        )

        try {
            val assetDir = ensureXrayAssetsDirectory(applicationContext)
            Libv2ray.initCoreEnv(assetDir.absolutePath, "")

            val controller = Libv2ray.newCoreController(this)
            coreController = controller

            val xrayConfig = XrayConfigGenerator.generateConfig(node, settings)
            CoreLogBuffer.i(TAG, "启动节点 [${node.name}] (${node.badgeText()})，模式: ${settings.routingMode.title}")

            // 建立 Android VPN TUN（纯本地代理模式则跳过）
            var tunFd = -1
            if (!settings.proxyOnlyMode) {
                if (prepare(this) != null) {
                    error("尚未获得系统 VPN 授权")
                }
                val pfd = establishVpnInterface(settings)
                vpnInterface = pfd
                tunFd = pfd.fd
            }

            // 先启动 Xray 本地入站监听（tunFd = 0 表示使用 hev-socks5-tunnel 转发 TUN 流量）
            controller.startLoop(xrayConfig, 0)

            // 再启动 hev-socks5-tunnel 将 TUN 数据包喂给本地 SOCKS5
            if (!settings.proxyOnlyMode && tunFd > 0) {
                startHevTunnel(settings, tunFd)
            }

            val connectedAt = System.currentTimeMillis()
            updateState(
                _runtimeState.value.copy(
                    status = VpnStatus.CONNECTED,
                    connectedSinceMs = connectedAt,
                    lastError = null
                )
            )
            updateNotification(node.name, "已连接 · ${node.badgeText()}")
            startTrafficStatsPolling(node, settings)
            IQuickTileService.requestTileSync(applicationContext)
        } catch (t: Throwable) {
            CoreLogBuffer.e(TAG, "启动代理连接失败", t)
            stopVpnTunnelSync(resetState = false)
            updateState(
                VpnRuntimeState(
                    status = VpnStatus.ERROR,
                    activeNodeId = node.id,
                    activeNodeName = node.name,
                    activeNodeProtocol = node.badgeText(),
                    lastError = t.message ?: "启动失败"
                )
            )
            IQuickTileService.requestTileSync(applicationContext)
            stopSelf()
        }
    }

    private fun establishVpnInterface(settings: AppSettings): ParcelFileDescriptor {
        val builder = Builder().apply {
            setSession("I")
            setMtu(settings.vpnMtu)
            addAddress(VPN_IPV4_CLIENT, 30)
            addRoute("0.0.0.0", 0)

            if (settings.enableIpv6) {
                addAddress(VPN_IPV6_CLIENT, 126)
                addRoute("::", 0)
            }

            val dnsList = listOf(settings.remoteDns, settings.directDns)
                .map { it.trim() }
                .filter { FormatUtils.isIpLiteral(it) }
                .distinct()
                .ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
            dnsList.forEach { addDnsServer(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false)
            }

            configurePerAppRouting(this, settings)
        }

        return builder.establish() ?: error("VpnService.Builder.establish() 返回空句柄")
    }

    private fun configurePerAppRouting(builder: Builder, settings: AppSettings) {
        val selfPkg = packageName
        val selected = settings.selectedAppPackages
        when (settings.perAppMode) {
            PerAppMode.DISABLED -> {
                runCatching { builder.addDisallowedApplication(selfPkg) }
            }
            PerAppMode.BYPASS_SELECTED -> {
                (selected + selfPkg).forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
            PerAppMode.ONLY_PROXY_SELECTED -> {
                val targets = selected.filter { it != selfPkg }
                if (targets.isEmpty()) {
                    runCatching { builder.addDisallowedApplication(selfPkg) }
                } else {
                    targets.forEach { pkg ->
                        try {
                            builder.addAllowedApplication(pkg)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                }
            }
        }
    }

    private fun startHevTunnel(settings: AppSettings, tunFd: Int) {
        val conf = HevTunnelConfig(
            mtu = settings.vpnMtu,
            ipv4 = VPN_IPV4_CLIENT,
            ipv6 = if (settings.enableIpv6) VPN_IPV6_CLIENT else "",
            address = "127.0.0.1",
            port = settings.socksPort,
            username = "",
            password = ""
        )
        val confFile = TProxyService.createHevTunnelConfig(conf, cacheDir)
        val started = TProxyService.TProxyStartService(confFile.absolutePath, tunFd)
        hevRunning = started
        CoreLogBuffer.i(TAG, "hev-socks5-tunnel 启动状态: $started (fd=$tunFd, socks=${settings.socksPort})")
    }

    private fun startTrafficStatsPolling(node: ServerNode, settings: AppSettings) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var totalUp = 0L
            var totalDown = 0L
            while (isActive) {
                delay(1000L)
                val controller = coreController ?: break
                val rawStats = runCatching { controller.queryAllOutboundTrafficStats() }.getOrNull().orEmpty()
                var deltaUp = 0L
                var deltaDown = 0L
                if (rawStats.isNotEmpty()) {
                    rawStats.split(';').forEach { segment ->
                        val parts = segment.split(',')
                        if (parts.size == 3) {
                            val tag = parts[0]
                            val dir = parts[1]
                            val value = parts[2].toLongOrNull() ?: 0L
                            if (tag == "proxy" || tag == "direct") {
                                if (dir == "uplink") deltaUp += value
                                if (dir == "downlink") deltaDown += value
                            }
                        }
                    }
                }
                totalUp += deltaUp
                totalDown += deltaDown

                updateState(
                    _runtimeState.value.copy(
                        uplinkSpeedBps = deltaUp,
                        downlinkSpeedBps = deltaDown,
                        totalUplinkBytes = totalUp,
                        totalDownlinkBytes = totalDown
                    )
                )
                val speedSummary = "↑ ${FormatUtils.formatSpeed(deltaUp)}  ↓ ${FormatUtils.formatSpeed(deltaDown)}"
                updateNotification(node.name, "$speedSummary · ${if (settings.proxyOnlyMode) "纯代理" else "VPN"}")
            }
        }
    }

    private fun stopVpnTunnel() {
        serviceScope.launch {
            stopVpnTunnelSync(resetState = true)
            IQuickTileService.requestTileSync(applicationContext)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopVpnTunnelSync(resetState: Boolean = true) {
        statsJob?.cancel()
        statsJob = null

        if (hevRunning) {
            runCatching { TProxyService.TProxyStopService() }
            hevRunning = false
        }

        runCatching { coreController?.stopLoop() }
        coreController = null

        runCatching { vpnInterface?.close() }
        vpnInterface = null

        if (resetState) {
            updateState(VpnRuntimeState(status = VpnStatus.DISCONNECTED))
            CoreLogBuffer.i(TAG, "代理连接已断开")
        }
    }

    // region CoreCallbackHandler 回调
    override fun startup(): Long {
        CoreLogBuffer.i(TAG, "Xray 核心回调: Startup 成功")
        return 0L
    }

    override fun shutdown(): Long {
        CoreLogBuffer.i(TAG, "Xray 核心回调: Shutdown 完成")
        return 0L
    }

    override fun onEmitStatus(status: Long, message: String?): Long {
        if (!message.isNullOrBlank()) {
            CoreLogBuffer.i(TAG, "Xray 状态 [$status]: $message")
        }
        return 0L
    }
    // endregion

    // region 通知管理
    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "I 代理运行状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "持续显示当前 VPN 连接状态与实时网速"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, IVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_i)
            .setContentTitle("I · $title")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "断开连接", stopIntent)
            .build()
    }
    // endregion

    companion object {
        private const val TAG = "IVpnService"
        private const val CHANNEL_ID = "i_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_IPV4_CLIENT = "10.0.0.2"
        private const val VPN_IPV6_CLIENT = "fd00::2"

        const val ACTION_START = "com.iproxy.app.action.START"
        const val ACTION_STOP = "com.iproxy.app.action.STOP"

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        private fun updateState(newState: VpnRuntimeState) {
            _runtimeState.value = newState
        }

        fun start(context: Context) {
            val intent = Intent(context, IVpnService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * 确保应用私有目录内存在 geoip.dat / geosite.dat，首次从 APK assets 复制
         */
        fun ensureXrayAssetsDirectory(context: Context): File {
            val dir = File(context.filesDir, "xray_assets").apply { mkdirs() }
            listOf("geoip.dat", "geosite.dat").forEach { fileName ->
                val target = File(dir, fileName)
                if (!target.exists() || target.length() == 0L) {
                    runCatching {
                        context.assets.open(fileName).use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }.onFailure {
                        CoreLogBuffer.w(TAG, "从 assets 复制 $fileName 失败: ${it.message}")
                    }
                }
            }
            return dir
        }
    }
}
