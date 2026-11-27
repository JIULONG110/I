package com.iproxy.app

import android.app.Application
import com.iproxy.app.worker.SubscriptionSyncWorker
import com.tencent.mmkv.MMKV

/**
 * 应用程序入口：初始化多进程 MMKV 存储并注册定时订阅同步任务
 */
class IApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        SubscriptionSyncWorker.schedule(this, intervalHours = 12L)
    }
}
