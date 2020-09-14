package org.jellyfin.mobile.ui.screen.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import org.jellyfin.mobile.ui.TopBarElevation

@Composable
@OptIn(ExperimentalPagerApi::class)
fun TabbedContent(
    tabTitles: List<String>,
    currentTabState: MutableState<Int>,
    pageContent: @Composable (Int) -> Unit
) {
    Column {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = tabTitles.size, initialPage = 0)

        val elevationPx = with(LocalDensity.current) { TopBarElevation.toPx() }
        ScrollableTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(shadowElevation = elevationPx)
                .zIndex(TopBarElevation.value),
            selectedTabIndex = pagerState.currentPage,
            backgroundColor = MaterialTheme.colors.primary,
            divider = {},
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(selected = index == pagerState.currentPage, onClick = {
                    coroutineScope.launch {
                        currentTabState.value = index
                        pagerState.scrollToPage(index)
                    }
                }, text = {
                    Text(title)
                })
            }
        }
        HorizontalPager(state = pagerState) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                pageContent(page)
            }
        }
    }
}
