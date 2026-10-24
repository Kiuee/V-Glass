package com.moji.liquidglass.ui.screen.apps

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode
import com.moji.liquidglass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal data class TargetAppInfo(
    val displayName: String,
    val packageName: String,
    val note: String,
    /** 已安装的 versionName；null = 未安装。 */
    val installedVersion: String?,
    /** 已适配的 versionName 列表（来自适配器声明，chip 点击展开查看）。 */
    val adaptedVersions: List<String>,
    /** 适配状态：已安装版本精确命中适配器 adaptedVersions 列表 = 已适配。 */
    val adaptState: AdaptState,
) {
    /** 行内副标题：包名 + 已安装版本。 */
    val summaryLine: String
        get() = when {
            installedVersion == null -> packageName
            else -> "$packageName（$installedVersion）"
        }
}

internal enum class AdaptState { Adapted, NotAdapted, NotInstalled }

/** 目标应用清单（名称/包名/适配版本来自适配器注册表，安装版本读 PackageManager，适配要点按包名映射到文案）。 */
@Composable
internal fun rememberTargetApps(): List<TargetAppInfo> {
    val noteStore = stringResource(R.string.app_note_store)
    val noteGallery = stringResource(R.string.app_note_gallery)
    val noteAmap = stringResource(R.string.app_note_amap)
    val noteXhs = stringResource(R.string.app_note_xhs)
    val noteFileManager = stringResource(R.string.app_note_filemanager)
    val context = LocalContext.current
    return remember(noteStore, noteGallery, noteAmap, noteXhs, noteFileManager, context) {
        val pm = context.packageManager
        AdapterRegistry.all.map { adapter ->
            val pkg = adapter.targetPackage
            val installedVersion = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName
                }
            } catch (_: Exception) {
                null
            }
            TargetAppInfo(
                displayName = adapter.displayName,
                packageName = pkg,
                note = when (pkg) {
                    "com.bbk.appstore" -> noteStore
                    "com.vivo.gallery" -> noteGallery
                    "com.autonavi.minimap" -> noteAmap
                    "com.xingin.xhs" -> noteXhs
                    "com.android.filemanager" -> noteFileManager
                    else -> ""
                },
                installedVersion = installedVersion,
                adaptedVersions = adapter.adaptedVersions,
                adaptState = when {
                    installedVersion == null -> AdaptState.NotInstalled
                    installedVersion in adapter.adaptedVersions -> AdaptState.Adapted
                    else -> AdaptState.NotAdapted
                },
            )
        }
    }
}

/** 未适配的已安装应用签名（pkg@version 排序拼接）：红条关闭状态按此持久化，集合变化后红条重现。 */
internal fun unadaptedSignature(apps: List<TargetAppInfo>): String = apps
    .filter { it.adaptState == AdaptState.NotAdapted }
    .joinToString("|") { "${it.packageName}@${it.installedVersion}" }

/** 适配状态 chip：配色对齐状态卡/警告条方案（动态取色走主题容器色，固定色与卡片同源色板），
 *  字号与内边距按列表行文本尺度设计，不用 StatusTag 的内嵌小徽章规格。 */
@Composable
internal fun AdaptChip(state: AdaptState) {
    val dark = isInDarkTheme()
    val label = stringResource(
        when (state) {
            AdaptState.Adapted -> R.string.app_adapted
            AdaptState.NotAdapted -> R.string.app_not_adapted
            AdaptState.NotInstalled -> R.string.app_not_installed
        },
    )
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val dynamic = MiuixTheme.isDynamicColor
            val background = when {
                dynamic && state == AdaptState.Adapted -> colorScheme.secondaryContainer
                dynamic && state == AdaptState.NotAdapted -> colorScheme.errorContainer
                dynamic -> colorScheme.surfaceContainer
                state == AdaptState.Adapted -> if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
                state == AdaptState.NotAdapted -> if (dark) Color(0xFF310808) else Color(0xFFF8E2E2)
                else -> if (dark) Color(0xFF2A2A2A) else Color(0xFFECECEC)
            }
            val content = when {
                dynamic && state == AdaptState.Adapted -> colorScheme.onSecondaryContainer
                dynamic && state == AdaptState.NotAdapted -> colorScheme.onErrorContainer
                dynamic -> colorScheme.onSurfaceContainer
                state == AdaptState.Adapted -> if (dark) Color(0xFFB8E8C5) else Color(0xFF164A29)
                state == AdaptState.NotAdapted -> Color(0xFFF72727)
                else -> if (dark) Color(0xFFAAAAAA) else Color(0xFF666666)
            }
            Box(
                modifier = Modifier.background(
                    color = background,
                    shape = RoundedCornerShape(8.dp),
                ),
            ) {
                MiuixText(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }

        UiMode.Material -> {
            val (background, content) = when (state) {
                AdaptState.Adapted -> MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
                AdaptState.NotAdapted -> MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
                AdaptState.NotInstalled -> MaterialTheme.colorScheme.surfaceContainerHighest to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier.background(
                    color = background,
                    shape = RoundedCornerShape(6.dp),
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 应用页分发器。 */
@Composable
fun AppsPager(bottomInnerPadding: Dp, config: ConfigController) {
    when (LocalUiMode.current) {
        UiMode.Material -> AppsPagerMaterial(bottomInnerPadding, config)
        UiMode.Miuix -> AppsPagerMiuix(bottomInnerPadding, config)
    }
}
