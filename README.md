# I (Android)

一款界面极简、功能全面的开源 Android 科学上网（多协议加密代理 / VPN）客户端，基于 **Kotlin + Jetpack Compose (Material 3)** 构建，底层采用 **Xray-core (`AndroidLibXrayLite`) + `hev-socks5-tunnel`** 双引擎驱动。

---

## 核心特性

- **极简「I」仪表盘**：超大号一键连接圆环开关、连接状态计时、实时上传/下载速率与累计流量统计。
- **全现代代理协议支持**：
  - `VLESS`（支持 TCP / WebSocket / gRPC / XHTTP、TLS / **REALITY**、XTLS Vision）
  - `VMess`（支持标准 v2rayN Base64 与 URI 格式）
  - `Trojan`（支持 TLS / REALITY / WS / gRPC）
  - `Shadowsocks`（支持 SIP002、传统格式与 SS2022 加密）
  - `Hysteria2`（支持 `hy2://` / `hysteria2://`、Salamander 混淆、`mport` 端口跳跃）
  - `WireGuard`、`SOCKS5`、`HTTP(S)` 及自定义 Xray JSON 节点
- **零门槛导入与订阅管理**：
  - 剪贴板一键智能批量导入（支持单条/多行链接、Base64 订阅、Clash YAML `proxies:` 节点提取）
  - 相机实时扫码 & 手机相册图片识别二维码
  - 订阅流量配额与到期时间解析（`Subscription-Userinfo`）、WorkManager 后台定时自动更新
  - 节点生成二维码分享给其他设备
- **真连接测速与智能分流**：
  - 并发真连接 HTTP 204 延迟测速、一键按延迟排序、一键清理超时节点
  - 三种模式：`绕过局域网及中国大陆`、`全局代理`、`直连模式`
  - 广告域名屏蔽（`geosite:category-ads-all`）、自定义直连/代理/屏蔽规则、在线更新 `geoip.dat` / `geosite.dat`
  - **分应用代理**：支持「绕过所选应用」或「仅代理所选应用」
- **系统集成与高级网络增强**：
  - Android **下拉快捷开关磁贴（Quick Settings Tile）**
  - 常驻通知栏实时显示网速并支持一键断开
  - 自定义远程/直连 DNS、FakeDNS、IPv6 开关、Mux 多路复用、TLS Fragment 分片防阻断
  - 局域网共享代理（`0.0.0.0:10808/10809`）、纯本地代理模式、实时彩色运行日志与全量配置 JSON 备份恢复

---

## 零基础 3 步使用教程

1. **下载安装 APK**：
   - 打开本仓库右侧 [**Releases**](../../releases)（或顶部 **Actions** 最新构建产物），下载 **`app-arm64-v8a-debug.apk`**（现代主流安卓手机推荐，体积约 47 MB）或通用版 **`app-universal-debug.apk`**，直接在手机上点击安装即可使用（`*-release-unsigned.apk` 为未签名构建，供自行签名发布使用）。
2. **导入节点或订阅**：
   - 打开 **I**，首页或「节点」页点击 **从剪贴板一键导入**、**扫码导入** 或 **添加订阅链接**。
3. **点击「I」连接**：
   - 回到首页点击中央超大 **`I`** 按钮，首次使用允许系统弹出的 VPN 连接授权请求，即可开始加密代理上网。

---

## 本地编译构建指南

本工程已在 Gradle 中内置 `downloadCoreAssets` 任务，克隆代码后会自动下载 `libv2ray.aar`、`geoip.dat` 和 `geosite.dat`，无需本地配置 Go 或 NDK 环境。

### 环境要求
- JDK 17+
- Android SDK（Platform `android-37.1` 与 Build-Tools `37.0.0`）

### 编译命令

```bash
# 运行全部单元测试（包含协议解析、订阅解析与 Xray 配置生成测试）
./gradlew testDebugUnitTest

# 构建完整 Debug APK（输出位于 app/build/outputs/apk/debug/）
./gradlew assembleDebug
```

---

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。核心组件致谢：
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) / [wgtunnel/hevtunnel](https://github.com/wgtunnel/core)
- [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)
