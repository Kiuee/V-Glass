package com.moji.liquidglass.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceBridge.bind()
        setContent {
            LiquidGlassTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(modifier: Modifier = Modifier) {
        // 服务状态/配置随绑定回调刷新
        var config by rememberConfig()
        fun commit(next: ModuleConfig) {
            config = next
            scope.launch { ServiceBridge.putConfig(next) }
        }
        Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("液态玻璃底栏", style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    ServiceBridge.bound -> {
                        val s = ServiceBridge.service
                        val fw = s?.let { "${it.frameworkName} ${it.frameworkVersion}" } ?: ""
                        "已连接 LSPosed 服务${if (fw.isEmpty()) "" else "（$fw）"}"
                    }
                    ServiceBridge.lastError != null ->
                        "服务注册失败：${ServiceBridge.lastError}"
                    else -> {
                        val push = ServiceBridge.providerPushCount
                        val pushInfo = when {
                            ServiceBridge.lastPushError != null -> "；最近一次推送分发失败：${ServiceBridge.lastPushError}"
                            push > 0 -> "；provider 已收到 $push 次推送"
                            else -> ""
                        }
                        "等待 LSPosed 推送服务 binder：请确认已在 LSPosed 启用本模块并勾选作用域；" +
                            "强制停止本应用重开，或重启手机后再试（vivo 系统可能冻结了 LSPosed 管理器）$pushInfo"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!ServiceBridge.bound) {
                TextButton(onClick = {
                    ServiceBridge.rebind()
                }) { Text("重新连接") }
            }

            Card(colors = CardDefaults.cardColors()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用玻璃底栏", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "作用域：${AdapterRegistry.all.joinToString("、") { it.displayName }}（样式全局共享）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        enabled = ServiceBridge.bound,
                        onCheckedChange = { enabled ->
                            val next = config.copy(enabled = enabled)
                            config = next
                            scope.launch { ServiceBridge.putConfig(next) }
                        }
                    )
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("内容延伸策略", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "让信息流延伸到玻璃栏与小白条下方。策略在商店进程启动时决定，切换后需强制停止应用商店重新打开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        ModuleConfig.CONTENT_EXTEND_SOURCE to "source：源头清零（推荐）",
                        ModuleConfig.CONTENT_EXTEND_APPLIER to "applier：落地改写（留白计算不受影响）",
                        ModuleConfig.CONTENT_EXTEND_OFF to "off：不干预，保留原列表留白（调试用）",
                    ).forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.contentExtend == value,
                                enabled = ServiceBridge.bound,
                                onClick = {
                                    val next = config.copy(contentExtend = value)
                                    config = next
                                    scope.launch { ServiceBridge.putConfig(next) }
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("玻璃栏样式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "回到应用商店即生效（后台切换或重开）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StyleSlider(
                        label = "最小安全边距",
                        value = config.barMarginDp,
                        range = 0f..40f,
                        enabled = ServiceBridge.bound,
                        onCommit = { commit(config.copy(barMarginDp = it)) }
                    )
                    StyleSlider(
                        label = "底部间隙",
                        value = config.barBottomGapDp,
                        range = 0f..24f,
                        enabled = ServiceBridge.bound,
                        onCommit = { commit(config.copy(barBottomGapDp = it)) }
                    )
                    StyleSlider(
                        label = "栏高",
                        value = config.barHeightDp,
                        range = 48f..88f,
                        enabled = ServiceBridge.bound,
                        onCommit = { commit(config.copy(barHeightDp = it)) }
                    )
                    StyleSlider(
                        label = "模糊半径",
                        value = config.barBlurDp,
                        range = 0f..24f,
                        enabled = ServiceBridge.bound,
                        onCommit = { commit(config.copy(barBlurDp = it)) }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "显示文字标签",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = config.barShowLabels,
                            enabled = ServiceBridge.bound,
                            onCheckedChange = { checked ->
                                commit(config.copy(barShowLabels = checked))
                            }
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("使用方法", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. 在 LSPosed 管理器中启用本模块并勾选作用域「vivo 应用商店」\n" +
                            "2. 强制停止应用商店后重新打开\n" +
                            "3. 底栏将替换为液态玻璃样式，实时折射下方滚动内容；点击玻璃 tab 会转发到原底栏完成切页\n" +
                            "4. 关闭开关后重启应用商店即可还原",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "· 玻璃效果基于 Kyant0/AndroidLiquidGlass（backdrop 2.0.1），与参考项目 BottomTabs 组件 1:1 同构\n" +
                            "· 需要Android 13（API 33）及以上（AGSL 折射着色器），低版本自动跳过注入\n" +
                            "· 强调色自动取自应用商店底栏选中色，读取失败回退 iOS 蓝\n" +
                            "· 诊断：logcat 过滤 tag \"LiquidGlass\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun rememberConfig(): androidx.compose.runtime.MutableState<ModuleConfig> {
        val state = androidx.compose.runtime.remember { mutableStateOf(ServiceBridge.readConfig()) }
        androidx.compose.runtime.DisposableEffect(ServiceBridge.bound) {
            state.value = ServiceBridge.readConfig()
            onDispose { }
        }
        return state
    }
}

@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    // 拖动期间用本地值即时显示，松手才写远程配置
    var dragging by androidx.compose.runtime.remember(value) { mutableStateOf(value) }
    Column {
        Text(
            "$label：${dragging.roundToInt()}dp",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            valueRange = range,
            enabled = enabled,
            onValueChangeFinished = { onCommit(dragging) }
        )
    }
}

@Composable
fun LiquidGlassTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content
    )
}
