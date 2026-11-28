package com.iproxy.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iproxy.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class NavigationRegressionTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val grantNotificationPermissionRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        val instrumentation = InstrumentationRegistry.getInstrumentation()
                        instrumentation.uiAutomation.grantRuntimePermission(
                            instrumentation.targetContext.packageName,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(grantNotificationPermissionRule).around(composeRule)

    private fun dismissOptionalUpdateDialog() {
        val later = composeRule.onAllNodesWithText("暂不更新").fetchSemanticsNodes()
        if (later.isNotEmpty()) {
            composeRule.onNode(hasText("暂不更新") and hasClickAction()).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun clickNavTab(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun openAllFourTabs_switchRepeatedly_andClickCommonControls_doesNotCrash() {
        composeRule.waitUntil(timeoutMillis = 12_000L) {
            composeRule.onAllNodesWithText("极简加密代理").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("正在检查版本更新...").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodes(hasContentDescription("玻璃试管主体，点击切换连接"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        dismissOptionalUpdateDialog()

        // 1. 首页普通点击：点击主试管、分流按钮、当前节点弹窗
        composeRule.onNodeWithContentDescription("玻璃试管主体，点击切换连接").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("全局代理") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("直连") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("绕过大陆") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("暂无节点", substring = true)).performClick()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()

        // 2. 节点页：打开与操作筛选 / 添加菜单与弹窗
        clickNavTab("节点")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("手动导入").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("手动导入") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加节点或订阅").performClick()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()

        // 3. 分流页：切换分流规则 / 运行日志与开关点击
        clickNavTab("分流")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("智能分流").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("运行日志", substring = true) and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("分流规则") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("拦截常见广告域名", substring = true)).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("拦截常见广告域名", substring = true)).performClick()
        composeRule.waitForIdle()

        // 4. 设置页：验证版本条目（触发 Libv2ray.checkVersionX）、开关、备份弹窗、Toast 及关于页
        clickNavTab("设置")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("暗黑界面 · 核心参数与备份").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("FakeDNS 加速", substring = true)).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("FakeDNS 加速", substring = true)).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("导出全部节点与配置备份")).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("从备份 JSON 恢复配置")).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("取消") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("关于软件 I")).performScrollTo()
        composeRule.waitForIdle()

        // 5. 反复切换四个 Tab 3 轮，回归状态保持与重组稳定性
        repeat(3) {
            clickNavTab("连接")
            clickNavTab("节点")
            clickNavTab("分流")
            clickNavTab("设置")
        }
        clickNavTab("连接")
    }
}
