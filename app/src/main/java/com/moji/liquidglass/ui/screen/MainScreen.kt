package com.moji.liquidglass.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Scaffold
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import com.itos.coui.topappbar.LargeTopAppBar
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.component.coui.CouiBottomBarFallback
import com.moji.liquidglass.ui.component.coui.CouiTopBarAction
import com.moji.liquidglass.ui.prefs.AdaptedVersionsStore
import com.moji.liquidglass.ui.screen.apps.AppsCoui
import com.moji.liquidglass.ui.screen.home.HomeCoui
import com.moji.liquidglass.ui.screen.settings.SettingsCoui
import kotlinx.coroutines.launch

/** 主界面（COUI，demo 同构）：一级页面瞬时切换（page + SaveableStateHolder），无 Pager 滑动。 */
@Composable
fun MainScreen(
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val config = ConfigController.remember()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = CouiTheme.colorScheme
    val appName = stringResource(R.string.app_name)
    val appsLabel = stringResource(R.string.apps)
    val settingsLabel = stringResource(R.string.settings)
    val homeLabel = stringResource(R.string.home)
    val searchHint = stringResource(R.string.apps_search_hint)
    val searchCancel = stringResource(R.string.action_cancel)
    val searchClear = stringResource(R.string.apps_search_clear)
    val refreshLabel = stringResource(R.string.apps_refresh_adapted_versions)
    val refreshText = stringResource(R.string.apps_refresh)

    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageState = rememberSaveableStateHolder()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchState = rememberCouiSearchBarState(searchExpanded)
    var refreshing by remember { mutableStateOf(false) }
    val refreshSpin = rememberInfiniteTransition(label = "refresh")
    val refreshAngle by refreshSpin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "refreshAngle",
    )
    val scrolls = List(3) { rememberScrollState() }
    val headers = List(3) { index ->
        CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            canScroll = { scrolls[index].maxValue > 0 },
        )
    }
    val density = LocalDensity.current

    val navItems = listOf(
        CouiNavigationItem(homeLabel, CouiIcons.Filled.Grid, CouiIcons.Filled.Grid),
        CouiNavigationItem(appsLabel, CouiIcons.Filled.ListFilter, CouiIcons.Filled.ListFilter),
        CouiNavigationItem(settingsLabel, CouiIcons.Filled.Setting, CouiIcons.Filled.Setting),
    )
    val selectPage: (Int) -> Unit = { page = it }

    LaunchedEffect(page) {
        if (page != 1) {
            searchExpanded = false
            searchQuery = ""
        }
    }
    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        searchQuery = ""
    }

    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
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
                refreshing = false
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(headers[page].nestedScrollConnection),
        containerColor = colors.background,
        topBar = {
            when (page) {
                0 -> LargeTopAppBar(
                    title = {
                        androidx.compose.material3.Text(
                            text = appName,
                            maxLines = 1,
                        )
                    },
                    scrollBehavior = headers[0],
                    dark = dark,
                )
                1 -> {
                    val fraction = headers[1].state.collapsedFraction
                    val topBarHeight = 128.dp - 64.dp * fraction
                    TopAppBarSurface(
                        modifier = Modifier.fillMaxWidth(),
                        fadeHeight = 200.dp,
                        bottomExtension = 24.dp,
                        dark = dark,
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .height(topBarHeight),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.Text(
                                        text = appsLabel,
                                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                                        modifier = Modifier.graphicsLayer {
                                            val scale = 1f - 0.18f * fraction
                                            scaleX = scale
                                            scaleY = scale
                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                        },
                                    )
                                    Spacer(Modifier.weight(1f))
                                    CouiTopBarAction(
                                        description = refreshLabel,
                                        onClick = refresh,
                                        enabled = !refreshing,
                                        label = refreshText,
                                        labelColor = colors.title,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .graphicsLayer {
                                                    rotationZ = if (refreshing) refreshAngle else 0f
                                                },
                                            tint = colors.title,
                                        )
                                    }
                                }
                                SearchBar(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .alpha(1f - fraction),
                                    state = searchState,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onExpandedChange = { expanded ->
                                        searchExpanded = expanded
                                        if (!expanded) searchQuery = ""
                                    },
                                    placeholder = searchHint,
                                    cancelText = searchCancel,
                                    clearDescription = searchClear,
                                    dark = dark,
                                )
                            }
                        },
                    )
                }
                else -> LargeTopAppBar(
                    title = {
                        androidx.compose.material3.Text(
                            text = settingsLabel,
                            maxLines = 1,
                        )
                    },
                    scrollBehavior = headers[2],
                    dark = dark,
                )
            }
        },
        bottomBar = {
            if (Build.VERSION.SDK_INT >= 34) {
                NavigationBar(
                    items = navItems,
                    selectedIndex = page,
                    onSelection = selectPage,
                    dark = dark,
                    selectedColor = colorScheme.accent,
                    unselectedColor = colorScheme.onSurfaceVariant,
                )
            } else {
                CouiBottomBarFallback(
                    items = navItems,
                    selectedIndex = page,
                    onSelection = selectPage,
                )
            }
        },
    ) { padding ->
        val contentPadding = remember(page, headers[page], padding, density) {
            headers[page].state.contentPadding(padding, density)
        }
        pageState.SaveableStateProvider(page) {
            when (page) {
                0 -> HomeCoui(
                    scrollState = scrolls[0],
                    contentPadding = contentPadding,
                    onOpenUsage = onOpenUsage,
                )
                1 -> AppsCoui(
                    scrollState = scrolls[1],
                    contentPadding = contentPadding,
                    config = config,
                    searchQuery = searchQuery,
                )
                else -> SettingsCoui(
                    scrollState = scrolls[2],
                    contentPadding = contentPadding,
                    onOpenTheme = onOpenTheme,
                    onOpenBarStyle = onOpenBarStyle,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}
