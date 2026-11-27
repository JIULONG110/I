# 保留 Xray Go-Mobile 绑定与 hev-socks5-tunnel JNI 符号
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.wgtunnel.hevtunnel.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**

# 保留 MMKV 多进程存储 JNI 符号
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# 保留 Gson 序列化模型字段、泛型签名与枚举
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.iproxy.app.data.model.** { *; }
-keepclassmembers enum com.iproxy.app.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.iproxy.app.data.local.AppStorage$BackupPayload { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
