package com.iproxy.app.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iproxy.app.data.local.AppStorage
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.MainActivity

/**
 * Android 下拉控制中心快捷开关磁贴（Quick Settings Tile）
 *
 * 让用户无需进入 App 即可一键开启/关闭「I」代理
 */
class IQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileUi()
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = IVpnService.runtimeState.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
            IVpnService.stop(applicationContext)
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = "已断开"
                }
                updateTile()
            }
            return
        }

        val storage = AppStorage(applicationContext)
        val settings = storage.readSettingsSnapshotSync()
        val node = storage.getSelectedNodeSync()
        if (node == null) {
            // 无节点时跳转主页引导导入
            openMainActivity()
            return
        }

        if (!settings.proxyOnlyMode && VpnService.prepare(applicationContext) != null) {
            // 尚未授予 VPN 权限时唤起主界面申请系统弹窗
            openMainActivity()
            return
        }

        IVpnService.start(applicationContext)
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = node.name
            }
            updateTile()
        }
    }

    private fun syncTileUi() {
        val tile = qsTile ?: return
        val runtime = IVpnService.runtimeState.value
        val selectedNode = AppStorage(applicationContext).getSelectedNodeSync()
        when (runtime.status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = runtime.activeNodeName ?: selectedNode?.name ?: "已连接"
                }
            }
            else -> {
                tile.state = if (selectedNode != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = selectedNode?.name ?: "请先添加节点"
                }
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestTileSync(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, IQuickTileService::class.java)
                )
            }
        }
    }
}
