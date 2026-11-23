package com.moji.liquidglass.ui.component.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.moji.liquidglass.R

/** 警示条（COUI 样式）：错误容器色卡片 + 错误图标 + 单行说明，可带关闭入口。 */
@Composable
internal fun CouiBanner(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CouiPageDefaults.shape)
            .background(colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            kind = CouiTextKind.ItemSummary,
            color = colorScheme.onErrorContainer,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.apps_unadapted_dismiss),
                    tint = colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
