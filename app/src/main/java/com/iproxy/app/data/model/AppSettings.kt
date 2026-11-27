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
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ZH_CN,
    val customDirectRules: String = "",
    val customProxyRules: String = "",
    val customBlockRules: String = ""
)
