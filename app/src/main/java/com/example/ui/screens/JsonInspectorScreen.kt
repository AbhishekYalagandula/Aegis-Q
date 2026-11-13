package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RecentLocalFile
import com.example.model.AccessRecord
import com.example.model.Bookmark
import com.example.model.JsonNode
import com.example.model.ParsedJsonDocument
import com.example.ui.FlattenedTreeRow
import com.example.ui.InspectorTab
import com.example.ui.SummaryFilterScope
import com.example.ui.TreeFilter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonInspectorScreen(
  document: ParsedJsonDocument,
  activeTab: InspectorTab,
  searchQuery: String,
  treeFilter: TreeFilter,
  expandedPaths: Set<String>,
  rawPrettyMode: Boolean,
  flattenTree: (JsonNode?, Set<String>, String, TreeFilter) -> List<FlattenedTreeRow>,
  onSelectTab: (InspectorTab) -> Unit,
  onSearchChange: (String) -> Unit,
  onFilterChange: (TreeFilter) -> Unit,
  onToggleExpand: (String) -> Unit,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit,
  onTogglePretty: (Boolean) -> Unit,
  onOpenFile: () -> Unit,
  onPasteJson: () -> Unit,
  onCopyText: (String) -> Unit,
  modifier: Modifier = Modifier,
  summarySearchQuery: String = searchQuery,
  summaryFilterScope: SummaryFilterScope = SummaryFilterScope.ALL,
  onSummarySearchChange: (String) -> Unit = onSearchChange,
  onSummaryFilterScopeChange: (SummaryFilterScope) -> Unit = {},
  recentFiles: List<RecentLocalFile> = emptyList(),
  onOpenRecentFile: (RecentLocalFile) -> Unit = {},
  onResetBundled: () -> Unit = {},
  onShareReport: (String) -> Unit = {},
  onExportCsv: () -> Unit = {},
  bookmarks: List<Bookmark> = emptyList(),
  selectedBookmarkTag: String? = null,
  showBookmarkedOnly: Boolean = false,
  onToggleBookmarkedOnly: () -> Unit = {},
  onSelectBookmarkTag: (String) -> Unit = {},
  onBookmarkRecordClick: (AccessRecord) -> Unit = {},
  onEditSavedBookmark: (Bookmark) -> Unit = {},
  onRemoveSavedBookmark: (Bookmark) -> Unit = {},
) {
  val tabs = InspectorTab.entries
  val pagerState = rememberPagerState(initialPage = activeTab.ordinal, pageCount = { tabs.size })
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(activeTab) {
    if (pagerState.currentPage != activeTab.ordinal) {
      pagerState.animateScrollToPage(activeTab.ordinal)
    }
  }

  LaunchedEffect(pagerState.currentPage) {
    val pageTab = tabs[pagerState.currentPage]
    if (pageTab != activeTab) {
      onSelectTab(pageTab)
    }
  }

  Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
      contentAlignment = Alignment.Center,
    ) {
      PrimaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        },
        modifier = Modifier.fillMaxWidth().testTag("inspector_tab_row"),
      ) {
        tabs.forEachIndexed { index, tab ->
          val selected = pagerState.currentPage == index
          Tab(
            selected = selected,
            onClick = {
              onSelectTab(tab)
              coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.testTag("inspector_tab_${tab.name.lowercase()}"),
            icon = {
              Icon(
                imageVector = tabIcon(tab = tab, selected = selected),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            text = {
              Text(
                text = tabTitle(tab),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize().weight(1f).testTag("inspector_pager"),
    ) { page ->
      when (tabs[page]) {
        InspectorTab.OVERVIEW ->
          OverviewScreen(
            document = document,
            searchQuery = summarySearchQuery,
            onSearchChange = onSummarySearchChange,
            onOpenFile = onOpenFile,
            onPasteJson = onPasteJson,
            onNavigateToExplorer = {
              onSelectTab(InspectorTab.EXPLORER)
              coroutineScope.launch {
                pagerState.animateScrollToPage(InspectorTab.EXPLORER.ordinal)
              }
            },
            onNavigateToRaw = {
              onSelectTab(InspectorTab.RAW)
              coroutineScope.launch {
                pagerState.animateScrollToPage(InspectorTab.RAW.ordinal)
              }
            },
            filterScope = summaryFilterScope,
            onFilterScopeChange = onSummaryFilterScopeChange,
            recentFiles = recentFiles,
            onOpenRecentFile = onOpenRecentFile,
            onResetBundled = onResetBundled,
            onExportCsv = onExportCsv,
            bookmarks = bookmarks,
            selectedBookmarkTag = selectedBookmarkTag,
            showBookmarkedOnly = showBookmarkedOnly,
            onToggleBookmarkedOnly = onToggleBookmarkedOnly,
            onSelectBookmarkTag = onSelectBookmarkTag,
            onBookmarkRecordClick = onBookmarkRecordClick,
            onEditSavedBookmark = onEditSavedBookmark,
            onRemoveSavedBookmark = onRemoveSavedBookmark,
            modifier = Modifier.padding(top = 4.dp),
          )

        InspectorTab.REPORT ->
          ReportScreen(
            document = document,
            onOpenFile = onOpenFile,
            onPasteJson = onPasteJson,
            onCopyReport = onCopyText,
            onShareReport = onShareReport,
            onFilterByUser = { userId ->
              onSummaryFilterScopeChange(SummaryFilterScope.USER_ID)
              onSummarySearchChange(userId)
              onSelectTab(InspectorTab.OVERVIEW)
              coroutineScope.launch {
                pagerState.animateScrollToPage(InspectorTab.OVERVIEW.ordinal)
              }
            },
          )

        InspectorTab.EXPLORER -> {
          val rows =
            remember(document.rootNode, expandedPaths, searchQuery, treeFilter) {
              flattenTree(document.rootNode, expandedPaths, searchQuery, treeFilter)
            }
          ExplorerScreen(
            rows = rows,
            searchQuery = searchQuery,
            treeFilter = treeFilter,
            parseError = document.parseError,
            onSearchChange = onSearchChange,
            onFilterChange = onFilterChange,
            onToggleExpand = onToggleExpand,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            onCopyValue = onCopyText,
          )
        }

        InspectorTab.RAW ->
          RawJsonScreen(
            document = document,
            prettyMode = rawPrettyMode,
            onTogglePretty = onTogglePretty,
            onCopyRaw = onCopyText,
          )
      }
    }
  }
}

private fun tabIcon(tab: InspectorTab, selected: Boolean) =
  when (tab) {
    InspectorTab.OVERVIEW ->
      if (selected) Icons.AutoMirrored.Rounded.FormatListBulleted
      else Icons.AutoMirrored.Outlined.FormatListBulleted
    InspectorTab.REPORT -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
    InspectorTab.EXPLORER -> if (selected) Icons.Filled.AccountTree else Icons.Outlined.AccountTree
    InspectorTab.RAW -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
  }

@Composable
private fun tabTitle(tab: InspectorTab): String =
  when (tab) {
    InspectorTab.OVERVIEW -> stringResource(R.string.tab_overview)
    InspectorTab.REPORT -> stringResource(R.string.tab_report)
    InspectorTab.EXPLORER -> stringResource(R.string.tab_tree)
    InspectorTab.RAW -> stringResource(R.string.tab_raw)
  }
