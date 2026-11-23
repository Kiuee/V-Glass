package com.moji.liquidglass.ui.component.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape

/** 状态小标签（COUI 样式）：胶囊底 + 单行小字，配色由调用方按业务状态传入。 */
@Composable
internal fun CouiTag(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .background(backgroundColor)
            .padding(horizontal = 5.dp),
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}
