package com.moji.liquidglass.ui.screen.apps

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.moji.liquidglass.R
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import com.moji.liquidglass.ui.component.coui.CouiTag
import com.moji.liquidglass.ui.prefs.AdaptedVersionsStore
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import java.text.Collator
import java.util.Locale

internal data class TargetAppInfo(
    val displayName: String,
    val packageName: String,
    /** 已安装的 versionName；null = 未安装。 */
    val installedVersion: String?,
    /** 已适配的 versionName 列表（来自适配器声明）。 */
    val adaptedVersions: List<String>,
    /** 适配状态：已安装版本精确命中适配器 adaptedVersions 列表 = 已适配。 */
    val adaptState: AdaptState,
)

internal enum class AdaptState { Adapted, NotAdapted, NotInstalled }

/** 目标应用清单（名称/包名/适配版本来自适配器注册表，安装版本读 PackageManager）。 */
@Composable
internal fun rememberTargetApps(): List<TargetAppInfo> {
    val context = LocalContext.current
    // 远程免适配覆盖表代次参与 remember key：手动刷新拉到新表后 chip/展开列表立即重组
    val overlayRevision = AdaptedVersionsStore.overlayRevision
    return remember(context, overlayRevision) {
        val pm = context.packageManager
        val collator = Collator.getInstance(Locale.CHINA)
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
                installedVersion = installedVersion,
                adaptedVersions = adapter.adaptedVersions,
                adaptState = when {
                    installedVersion == null -> AdaptState.NotInstalled
                    installedVersion in adapter.adaptedVersions -> AdaptState.Adapted
                    else -> AdaptState.NotAdapted
                },
            )
        }.sortedWith(
            compareBy<TargetAppInfo> { if (it.adaptState == AdaptState.Adapted) 0 else 1 }
                .thenComparator { a, b -> collator.compare(a.displayName, b.displayName) },
        )
    }
}

/** 展开区末行文案：已安装显示"当前安装：x.y.z"，未安装显示"未安装"。 */
@Composable
internal fun installedVersionLine(app: TargetAppInfo): String =
    if (app.installedVersion != null) {
        stringResource(R.string.app_installed_version_fmt, app.installedVersion)
    } else {
        stringResource(R.string.app_not_installed)
    }

/** 未适配的已安装应用签名（pkg@version 排序拼接）：红条关闭状态按此持久化，集合变化后红条重现。 */
internal fun unadaptedSignature(apps: List<TargetAppInfo>): String = apps
    .filter { it.adaptState == AdaptState.NotAdapted }
    .joinToString("|") { "${it.packageName}@${it.installedVersion}" }

/** 适配状态小标签：COUI 胶囊 + Material 语义色。 */
@Composable
internal fun AdaptStatusTag(state: AdaptState) {
    val label = stringResource(
        when (state) {
            AdaptState.Adapted -> R.string.app_adapted
            AdaptState.NotAdapted -> R.string.app_not_adapted
            AdaptState.NotInstalled -> R.string.app_not_installed
        },
    )
    val colorScheme = MaterialTheme.colorScheme
    val materialTheme = ManagerPrefs.materialTheme
    val (background, content) = when (state) {
        AdaptState.Adapted ->
            if (materialTheme) colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            else Color(0xFFD6E4FF) to Color(0xFF0B3D91)
        AdaptState.NotAdapted ->
            if (materialTheme) colorScheme.errorContainer to colorScheme.onErrorContainer
            else Color(0xFFFFDAD6) to Color(0xFF8C1D18)
        AdaptState.NotInstalled ->
            if (materialTheme) colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
            else Color(0xFFE8E8E8) to Color(0xFF3C3C3C)
    }
    CouiTag(text = label, backgroundColor = background, contentColor = content)
}

/** 按应用名/包名过滤（大小写不敏感包含匹配）。 */
internal fun filterApps(apps: List<TargetAppInfo>, query: String): List<TargetAppInfo> {
    if (query.isBlank()) return apps
    return apps.filter {
        it.displayName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }
}
