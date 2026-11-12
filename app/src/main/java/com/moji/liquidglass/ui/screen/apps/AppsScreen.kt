package com.moji.liquidglass.ui.screen.apps

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.R
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.component.SearchStatus
import com.moji.liquidglass.ui.component.StatusTag
import com.moji.liquidglass.ui.component.bottombar.LocalMainPagerState
import com.moji.liquidglass.ui.prefs.AdaptedVersionsStore
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode
import com.moji.liquidglass.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

internal data class TargetAppInfo(
    val displayName: String,
    val packageName: String,
    /** 已安装的 versionName；null = 未安装。 */
    val installedVersion: String?,
    /** 已适配的 versionName 列表（来自适配器声明，整行点击展开查看）。 */
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
        }
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

/** 适配状态小标签：规格走共享 StatusTag（对齐参考项目超级用户页），
 *  已适配/未安装配色分别对齐其 ROOT / UMOUNT 标签，未适配沿用警示容器色。 */
@Composable
internal fun AdaptStatusTag(state: AdaptState) {
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
            val background = when (state) {
                AdaptState.Adapted -> colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                AdaptState.NotAdapted -> if (dynamic) {
                    colorScheme.errorContainer
                } else if (dark) {
                    Color(0xFF310808)
                } else {
                    Color(0xFFF8E2E2)
                }
                AdaptState.NotInstalled -> if (dark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
            }
            val content = when (state) {
                AdaptState.Adapted -> colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                AdaptState.NotAdapted -> if (dynamic) {
                    colorScheme.onErrorContainer
                } else {
                    Color(0xFFF72727)
                }
                AdaptState.NotInstalled -> if (dark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.8f)
            }
            StatusTag(label, backgroundColor = background, contentColor = content)
        }

        UiMode.Material -> {
            val (background, content) = when (state) {
                AdaptState.Adapted -> MaterialTheme.colorScheme.primary to
                    MaterialTheme.colorScheme.onPrimary
                AdaptState.NotAdapted -> MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
                AdaptState.NotInstalled -> MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            }
            StatusTag(label, backgroundColor = background, contentColor = content)
        }
    }
}

/** 应用页分发器：搜索状态机/搜索词/展开集上提共用，切离本页时强制收起搜索（对齐参考项目）。 */
@Composable
fun AppsPager(bottomInnerPadding: Dp, config: ConfigController) {
    val apps = rememberTargetApps()
    val context = LocalContext.current
    val searchHint = stringResource(R.string.apps_search_hint)
    var searchStatus by remember(searchHint) { mutableStateOf(SearchStatus(label = searchHint)) }
    var expandedPkgs by rememberSaveable { mutableStateOf(listOf<String>()) }
    val onSearchStatusChange: (SearchStatus) -> Unit = { searchStatus = it }
    val onToggleExpand: (String) -> Unit = { pkg ->
        expandedPkgs = if (pkg in expandedPkgs) expandedPkgs - pkg else expandedPkgs + pkg
    }

    // 免适配版本表手动刷新：仅用户点顶栏刷新时联网；三态结果（已更新/无变更/失败）Toast 反馈
    var refreshingAdaptedVersions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val onRefreshAdaptedVersions: () -> Unit = {
        if (!refreshingAdaptedVersions) {
            refreshingAdaptedVersions = true
            scope.launch {
                val message = when (val result = AdaptedVersionsStore.refresh(context)) {
                    is AdaptedVersionsStore.RefreshResult.Updated -> context.getString(
                        R.string.apps_adapted_versions_updated_fmt,
                        result.updatedAt.ifBlank { context.getString(R.string.apps_adapted_versions_unknown_time) },
                    )
                    AdaptedVersionsStore.RefreshResult.Unchanged ->
                        context.getString(R.string.apps_adapted_versions_unchanged)
                    is AdaptedVersionsStore.RefreshResult.Failed ->
                        context.getString(R.string.apps_adapted_versions_refresh_failed, result.reason)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                refreshingAdaptedVersions = false
            }
        }
    }

    // 切离应用页即收起搜索并清词，避免返回时残留在展开态
    val mainPagerState = LocalMainPagerState.current
    LaunchedEffect(mainPagerState) {
        snapshotFlow { mainPagerState.selectedPage }.collect { page ->
            if (page != 1 && !searchStatus.isCollapsed()) {
                searchStatus = searchStatus.copy(searchText = "", current = SearchStatus.Status.COLLAPSED)
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Material -> AppsPagerMaterial(
            bottomInnerPadding = bottomInnerPadding,
            config = config,
            apps = apps,
            searchStatus = searchStatus,
            onSearchStatusChange = onSearchStatusChange,
            expandedPkgs = expandedPkgs,
            onToggleExpand = onToggleExpand,
            onRefreshAdaptedVersions = onRefreshAdaptedVersions,
            refreshingAdaptedVersions = refreshingAdaptedVersions,
        )
        UiMode.Miuix -> AppsPagerMiuix(
            bottomInnerPadding = bottomInnerPadding,
            config = config,
            apps = apps,
            searchStatus = searchStatus,
            onSearchStatusChange = onSearchStatusChange,
            expandedPkgs = expandedPkgs,
            onToggleExpand = onToggleExpand,
            onRefreshAdaptedVersions = onRefreshAdaptedVersions,
            refreshingAdaptedVersions = refreshingAdaptedVersions,
        )
    }
}
