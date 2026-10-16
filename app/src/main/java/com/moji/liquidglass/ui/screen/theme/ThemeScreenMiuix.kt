package com.moji.liquidglass.ui.screen.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.ColorMode
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.theme.keyColorOptions
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 主题设置页 Miuix 版（对齐参考项目 ColorPaletteScreenMiuix）：预览卡 + TabRow + monet 联动下拉 + 效果开关。 */
@Composable
fun ThemeScreenMiuix(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val keyColor = ManagerPrefs.keyColor
    val currentColorMode = ManagerPrefs.colorMode
    val isDark = currentColorMode.isDark || currentColorMode.isSystem && isSystemInDarkTheme()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.theme_page),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            androidx.compose.material3.Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    ThemePreviewCardMiuix(
                        keyColor = keyColor,
                        isDark = isDark,
                        miuixMonet = ManagerPrefs.miuixMonet,
                        enableFloatingBottomBar = ManagerPrefs.floatingBottomBar,
                        enableFloatingBottomBarBlur = ManagerPrefs.floatingBottomBarBlur,
                        paletteStyle = ManagerPrefs.paletteStyle,
                        colorSpec = ManagerPrefs.colorSpec,
                    )
                    Spacer(modifier = Modifier.height(72.dp))

                    val themeItems = listOf(
                        stringResource(R.string.color_mode_system),
                        stringResource(R.string.color_mode_light),
                        stringResource(R.string.color_mode_dark),
                    )
                    TabRow(
                        tabs = themeItems,
                        selectedTabIndex = (if (currentColorMode.value >= 3) currentColorMode.value - 3 else currentColorMode.value)
                            .coerceIn(0, 2),
                        onTabSelected = { index ->
                            ManagerPrefs.setThemeModeIndex(index)
                        },
                        height = 48.dp,
                    )

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.miuix_monet),
                            startAction = {
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.Wallpaper,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = ManagerPrefs.miuixMonet,
                            onCheckedChange = { ManagerPrefs.setMiuixMonet(it) },
                        )

                        AnimatedVisibility(visible = ManagerPrefs.miuixMonet) {
                            Column {
                                val colorNames = listOf(
                                    stringResource(R.string.key_color_dynamic),
                                    stringResource(R.string.color_red),
                                    stringResource(R.string.color_pink),
                                    stringResource(R.string.color_purple),
                                    stringResource(R.string.color_deep_purple),
                                    stringResource(R.string.color_indigo),
                                    stringResource(R.string.color_blue),
                                    stringResource(R.string.color_cyan),
                                    stringResource(R.string.color_teal),
                                    stringResource(R.string.color_green),
                                    stringResource(R.string.color_yellow),
                                    stringResource(R.string.color_amber),
                                    stringResource(R.string.color_orange),
                                    stringResource(R.string.color_brown),
                                    stringResource(R.string.color_blue_grey),
                                    stringResource(R.string.color_sakura),
                                )
                                val colorValues = listOf(0) + keyColorOptions
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.key_color),
                                    items = colorNames,
                                    startAction = {
                                        androidx.compose.material3.Icon(
                                            Icons.Rounded.Colorize,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = null,
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                    selectedIndex = colorValues.indexOf(keyColor).takeIf { it >= 0 } ?: 0,
                                    onSelectedIndexChange = { index ->
                                        ManagerPrefs.setKeyColor(colorValues[index])
                                    },
                                )

                                AnimatedVisibility(visible = keyColor != 0) {
                                    Column {
                                        val styles = PaletteStyle.entries
                                        OverlayDropdownPreference(
                                            title = stringResource(R.string.palette_style),
                                            startAction = {
                                                androidx.compose.material3.Icon(
                                                    Icons.Rounded.Style,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = null,
                                                    tint = colorScheme.onBackground,
                                                )
                                            },
                                            items = styles.map { it.name },
                                            selectedIndex = styles.indexOf(ManagerPrefs.paletteStyle).coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                ManagerPrefs.setPaletteStyle(styles[index])
                                            },
                                        )

                                        val specs = ColorSpec.SpecVersion.entries
                                        OverlayDropdownPreference(
                                            title = stringResource(R.string.color_spec),
                                            startAction = {
                                                androidx.compose.material3.Icon(
                                                    Icons.Rounded.DesignServices,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = null,
                                                    tint = colorScheme.onBackground,
                                                )
                                            },
                                            items = specs.map { it.name },
                                            selectedIndex = specs.indexOf(ManagerPrefs.colorSpec).coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                ManagerPrefs.setColorSpec(specs[index])
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val blurCapable = Build.VERSION.SDK_INT >= 33
                        if (blurCapable) {
                            SwitchPreference(
                                title = stringResource(R.string.enable_blur),
                                summary = stringResource(R.string.enable_blur_summary),
                                startAction = {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.BlurOn,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = ManagerPrefs.enableBlur,
                                onCheckedChange = { ManagerPrefs.setEnableBlur(it) },
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.floating_bottom_bar),
                            summary = stringResource(R.string.floating_bottom_bar_summary),
                            startAction = {
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.CallToAction,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = ManagerPrefs.floatingBottomBar,
                            onCheckedChange = { ManagerPrefs.setFloatingBottomBar(it) },
                        )
                        AnimatedVisibility(visible = ManagerPrefs.floatingBottomBar && blurCapable) {
                            SwitchPreference(
                                title = stringResource(R.string.floating_bottom_bar_blur),
                                summary = stringResource(R.string.floating_bottom_bar_blur_summary),
                                startAction = {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.WaterDrop,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = ManagerPrefs.floatingBottomBarBlur,
                                onCheckedChange = { ManagerPrefs.setFloatingBottomBarBlur(it) },
                            )
                        }
                    }
                }
                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                12.dp
                        )
                    )
                }
            }
        }
    }
}

/** 主题预览卡 Miuix 版：带悬浮底栏形态（对齐参考项目 ThemePreviewCardMiuix）。 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCardMiuix(
    keyColor: Int,
    isDark: Boolean,
    miuixMonet: Boolean,
    enableFloatingBottomBar: Boolean = false,
    enableFloatingBottomBarBlur: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.Default,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight

    val seedColor = if (keyColor == 0) colorScheme.primary else Color(keyColor)
    val effectiveStyle = if (keyColor == 0) PaletteStyle.TonalSpot else paletteStyle
    val effectiveSpec = if (keyColor == 0) ColorSpec.SpecVersion.Default else colorSpec
    val dynamicCs = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = effectiveStyle,
        specVersion = effectiveSpec,
    )

    val bgColor = if (miuixMonet) dynamicCs.background else colorScheme.surface
    val textColor = if (miuixMonet) dynamicCs.onSurface else colorScheme.onBackground
    val accentCardColor = when {
        miuixMonet -> dynamicCs.secondaryContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (miuixMonet) dynamicCs.surfaceContainerHighest else colorScheme.surfaceVariant
    val navBarColor = if (miuixMonet) dynamicCs.surfaceContainer else colorScheme.surface
    val iconColor = if (miuixMonet) dynamicCs.primary else colorScheme.primary
    val navSelectedColor = colorScheme.onSurfaceContainer
    val navUnselectedColor = colorScheme.onSurfaceContainer.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontSize = 12.sp,
                        color = textColor,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PreviewBlockMiuix(
                        color = accentCardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                    )
                    PreviewBlockMiuix(
                        color = cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                    )
                    PreviewBlockMiuix(
                        color = cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                    )
                }
            }

            if (enableFloatingBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enableFloatingBottomBarBlur) navBarColor.copy(alpha = 0.5f)
                                else navBarColor
                            )
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f)),
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBlockMiuix(
    color: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}
