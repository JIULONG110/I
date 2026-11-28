package com.iproxy.app.data.model

/**
 * 路由分流模式
 */
enum class RoutingMode(val title: String, val subtitle: String) {
    RULE_BYPASS_CN("规则分流（推荐）", "绕过局域网及中国大陆直连，海外流量走代理"),
    GLOBAL_PROXY("全局代理", "除局域网外所有网络流量均通过代理转发"),
    DIRECT("直连模式", "仅提供基础网络通道，所有流量直连不经过代理")
}

/**
 * 分应用代理模式
 */
enum class PerAppMode(val title: String) {
    DISABLED("关闭（全局应用）"),
    BYPASS_SELECTED("绕过选中的应用"),
    ONLY_PROXY_SELECTED("仅代理选中的应用")
}

/**
 * 主题模式
 */
enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

/**
 * 应用语言设置
 */
enum class AppLanguage(val tag: String, val title: String) {
    ZH_CN("zh-CN", "简体中文"),
    EN("en", "English")
}

/**
 * 全局应用设置
 */
data class AppSettings(
    val routingMode: RoutingMode = RoutingMode.RULE_BYPASS_CN,
    val blockAds: Boolean = true,
    val remoteDns: String = "1.1.1.1",
    val directDns: String = "223.5.5.5",
    val enableFakeDns: Boolean = false,
    val enableIpv6: Boolean = false,
    val enableMux: Boolean = false,
    val enableFragment: Boolean = false,
    val allowLanShare: Boolean = false,
    val proxyOnlyMode: Boolean = false,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val vpnMtu: Int = 1500,
    val speedTestUrl: String = "https://www.gstatic.com/generate_204",
    val perAppMode: PerAppMode = PerAppMode.DISABLED,
    val selectedAppPackages: Set<String> = emptySet(),
    val autoReconnect: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ZH_CN,
    val customDirectRules: String = "",
    val customProxyRules: String = "",
    val customBlockRules: String = ""
) {
    /**
     * 防止 Gson UnsafeAllocator 在读取旧版本/残缺 JSON 时注入 null，并兜底非法端口与 MTU。
     */
    @Suppress("USELESS_ELVIS")
    fun sanitized(): AppSettings = copy(
        routingMode = (routingMode as RoutingMode?) ?: RoutingMode.RULE_BYPASS_CN,
        remoteDns = (remoteDns as String?)?.ifBlank { null } ?: "1.1.1.1",
        directDns = (directDns as String?)?.ifBlank { null } ?: "223.5.5.5",
        socksPort = if (socksPort in 1024..65535) socksPort else 10808,
        httpPort = if (httpPort in 1024..65535) httpPort else 10809,
        vpnMtu = if (vpnMtu in 1200..9000) vpnMtu else 1500,
        speedTestUrl = (speedTestUrl as String?)?.ifBlank { null } ?: "https://www.gstatic.com/generate_204",
        perAppMode = (perAppMode as PerAppMode?) ?: PerAppMode.DISABLED,
        selectedAppPackages = (selectedAppPackages as Set<String>?) ?: emptySet(),
        themeMode = (themeMode as ThemeMode?) ?: ThemeMode.SYSTEM,
        language = (language as AppLanguage?) ?: AppLanguage.ZH_CN,
        customDirectRules = (customDirectRules as String?) ?: "",
        customProxyRules = (customProxyRules as String?) ?: "",
        customBlockRules = (customBlockRules as String?) ?: ""
    )
}
