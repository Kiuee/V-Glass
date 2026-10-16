@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.component.material

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.component.bottombar.LocalMainPagerState

/** Material 风格底部导航：实心 FlexibleBottomAppBar + 三 Tab（主页/应用/设置）。 */
@Composable
fun BottomBarMaterial() {
    val mainPagerState = LocalMainPagerState.current

    data class Destination(val labelRes: Int, val selected: ImageVector, val unselected: ImageVector)

    val items = listOf(
        Destination(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
        Destination(R.string.apps, Icons.Filled.Apps, Icons.Outlined.Apps),
        Destination(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    FlexibleBottomAppBar {
        items.forEachIndexed { index, destination ->
            val selected = mainPagerState.selectedPage == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) destination.selected else destination.unselected,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = {
                    Text(
                        stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
