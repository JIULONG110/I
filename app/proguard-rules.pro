# 保留 Xray Go-Mobile 绑定与 hev-socks5-tunnel JNI 符号
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.wgtunnel.hevtunnel.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**

# 保留 Gson 序列化模型字段
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.iproxy.app.data.model.** { *; }
