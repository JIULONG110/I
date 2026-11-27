// 根项目 Gradle 构建配置：声明全局插件别名但不直接应用
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
