package com.moji.liquidglass.ui.screen.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.R
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

internal data class TargetAppInfo(
    val displayName: String,
    val packageName: String,
    val note: String,
)

/** 目标应用清单（名称/包名来自适配器注册表，适配要点按包名映射到文案）。 */
@Composable
internal fun rememberTargetApps(): List<TargetAppInfo> {
    val noteStore = stringResource(R.string.app_note_store)
    val noteGallery = stringResource(R.string.app_note_gallery)
    val noteAmap = stringResource(R.string.app_note_amap)
    val appsNote = stringResource(R.string.apps_note)
    return remember(noteStore, noteGallery, noteAmap, appsNote) {
        AdapterRegistry.all.map { adapter ->
            TargetAppInfo(
                displayName = adapter.displayName,
                packageName = adapter.targetPackage,
                note = when (adapter.targetPackage) {
                    "com.bbk.appstore" -> noteStore
                    "com.vivo.gallery" -> noteGallery
                    "com.autonavi.minimap" -> noteAmap
                    else -> ""
                },
            )
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
