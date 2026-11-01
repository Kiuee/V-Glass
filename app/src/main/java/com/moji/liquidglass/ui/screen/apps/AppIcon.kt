package com.moji.liquidglass.ui.screen.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val AppIconSize = 48.dp

/** 应用图标位图（PackageManager 同步加载到 48dp，未安装/加载失败返回 null 由占位兜底）。 */
@Composable
internal fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(packageName, context, density) {
        try {
            val sizePx = with(density) { AppIconSize.roundToPx() }
            context.packageManager.getApplicationIcon(packageName).toBitmap(width = sizePx, height = sizePx).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

/** 应用图标：命中显示位图，未安装显示圆角底占位（对齐参考项目超级用户页 PlaceHolderBox）。 */
@Composable
internal fun AppIconImage(
    packageName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(packageName)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = contentDescription, modifier = modifier)
    } else {
        val container = when (LocalUiMode.current) {
            UiMode.Miuix -> MiuixTheme.colorScheme.secondaryContainer
            UiMode.Material -> MaterialTheme.colorScheme.secondaryContainer
        }
        Box(
            modifier = modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
        )
    }
}
