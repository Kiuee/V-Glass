package com.moji.liquidglass.ui.component.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme

/**
 * API 33 回退底栏：COUI NavigationBar 仅 34+ 可用，33 用同色系胶囊导航条保证观感一致。
 * 34+ 仍由 NavigationBar 呈现带材质的浮动底栏。
 */
@Composable
internal fun CouiBottomBarFallback(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dark = CouiTheme.isDark
    val accent = CouiTheme.colorScheme.accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val source = remember { MutableInteractionSource() }
            val selected = index == selectedIndex
            val color = if (selected) accent else colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CouiCapsuleShape)
                    .couiNavigationFeedback(dark, source)
                    .selectable(
                        selected = selected,
                        interactionSource = source,
                        indication = null,
                        enabled = true,
                        role = Role.Tab,
                        onClick = { onSelection(index) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Text(text = item.title, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}
