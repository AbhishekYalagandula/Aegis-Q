package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.model.Bookmark
import com.example.model.BookmarkKeys
import com.example.model.JsonInspectorParser
import com.example.model.JsonNode
import com.example.ui.AccessLogUiState
import com.example.ui.FlattenedTreeRow
import com.example.ui.InspectorLoadState
import com.example.ui.InspectorTab
import com.example.ui.SummaryFilterScope
import com.example.ui.TreeFilter
import com.example.ui.screens.BookmarkTagDialog
import com.example.ui.screens.JsonInspectorScreen
import com.example.ui.theme.AccessLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsonInspectorScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun tabbedInterface_switchesBetweenSummaryTreeAndRawJson() {
    val sampleJson =
      """
      {
        "history": [
          {
            "appletName": "Field Notes",
            "userId": "usr_101",
            "accessedAt": "2026-11-13T06:00:00Z",
            "action": "OPENED"
          }
        ]
      }
      """.trimIndent()
    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = sampleJson)

    composeTestRule.setContent {
      AccessLogTheme {
        var activeTab by remember { mutableStateOf(InspectorTab.OVERVIEW) }
        var expandedPaths by remember { mutableStateOf(setOf("$", "$.history", "$.history[0]")) }
        var prettyMode by remember { mutableStateOf(true) }

        JsonInspectorScreen(
          document = doc,
          activeTab = activeTab,
          searchQuery = "",
          treeFilter = TreeFilter.ALL,
          expandedPaths = expandedPaths,
          rawPrettyMode = prettyMode,
          flattenTree = { root, expanded, _, _ -> flattenForTest(root, expanded) },
          onSelectTab = { activeTab = it },
          onSearchChange = {},
          onFilterChange = {},
          onToggleExpand = { path ->
            expandedPaths =
              if (path in expandedPaths) expandedPaths - path else expandedPaths + path
          },
          onExpandAll = {},
          onCollapseAll = {},
          onTogglePretty = { prettyMode = it },
          onOpenFile = {},
          onPasteJson = {},
          onCopyText = {},
        )
      }
    }

    // Tab 1: Summary list of log records with top search bar
    composeTestRule.onNodeWithTag("inspector_tab_row").assertIsDisplayed()
    composeTestRule.onNodeWithTag("overview_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("record_card_1"))
    composeTestRule.onNodeWithTag("record_card_1").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Field Notes")[0].assertIsDisplayed()

    // Tab 2: Hierarchical tree view
    composeTestRule.onNodeWithTag("inspector_tab_explorer").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tree_node_$.history").assertIsDisplayed()

    // Tab 3: Raw JSON code block viewer
    composeTestRule.onNodeWithTag("inspector_tab_raw").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("raw_json_code_block").assertIsDisplayed()
  }

  @Test
  fun summarySearchBar_filtersLogEntriesByUserIdOrActionName() {
    val sampleJson =
      """
      {
        "history": [
          {
            "appletName": "Field Notes",
            "userId": "usr_alpha_01",
            "accessedAt": "2026-11-13T06:00:00Z",
            "action": "OPENED"
          },
          {
            "appletName": "Audit Trail",
            "userId": "usr_beta_99",
            "accessedAt": "2026-11-13T06:10:00Z",
            "action": "EXPORT_CSV"
          }
        ]
      }
      """.trimIndent()
    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = sampleJson)
    var exportClicks = 0

    composeTestRule.setContent {
      AccessLogTheme {
        var summaryQuery by remember { mutableStateOf("") }
        var filterScope by remember { mutableStateOf(SummaryFilterScope.ALL) }

        JsonInspectorScreen(
          document = doc,
          activeTab = InspectorTab.OVERVIEW,
          searchQuery = "",
          treeFilter = TreeFilter.ALL,
          expandedPaths = setOf("$"),
          rawPrettyMode = true,
          flattenTree = { _, _, _, _ -> emptyList() },
          onSelectTab = {},
          onSearchChange = {},
          onFilterChange = {},
          onToggleExpand = {},
          onExpandAll = {},
          onCollapseAll = {},
          onTogglePretty = {},
          onOpenFile = {},
          onPasteJson = {},
          onCopyText = {},
          summarySearchQuery = summaryQuery,
          summaryFilterScope = filterScope,
          onSummarySearchChange = { summaryQuery = it },
          onSummaryFilterScopeChange = { filterScope = it },
          onExportCsv = { exportClicks++ },
        )
      }
    }

    // Search by user ID
    composeTestRule.onNodeWithTag("overview_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("summary_scope_user_id").performClick()
    composeTestRule.onNodeWithTag("overview_search_input").performTextInput("usr_beta_99")
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("record_card_1").assertCountEquals(0)
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("overview_export_csv_button"))
    composeTestRule.onNodeWithTag("overview_export_csv_button").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, exportClicks)
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("record_card_2"))
    composeTestRule.onNodeWithTag("record_card_2").assertIsDisplayed()

    // Filter by action name
    composeTestRule.onNodeWithTag("overview_search_input").performTextClearance()
    composeTestRule.onNodeWithTag("summary_scope_action").performClick()
    composeTestRule.onNodeWithTag("overview_search_input").performTextInput("OPENED")
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("record_card_2").assertCountEquals(0)
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("record_card_1"))
    composeTestRule.onNodeWithTag("record_card_1").assertIsDisplayed()

    // Non-matching filter disables CSV export button
    composeTestRule.onNodeWithTag("overview_search_input").performTextClearance()
    composeTestRule.onNodeWithTag("overview_search_input").performTextInput("NO_SUCH_ACTION")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("overview_export_csv_button"))
    composeTestRule.onNodeWithTag("overview_export_csv_button").assertIsNotEnabled()
  }

  @Test
  fun reportTab_rendersActionsPerUserChartAndSummaryReport() {
    val sampleJson =
      """
      {
        "history": [
          {"appletName": "Field Notes", "userId": "usr_alpha", "action": "OPENED", "timestamp": "2026-11-13T06:00:00Z"},
          {"appletName": "Field Notes", "userId": "usr_alpha", "action": "EXPORT", "timestamp": "2026-11-13T07:00:00Z"},
          {"appletName": "Audit Trail", "userId": "usr_beta", "action": "OPENED", "timestamp": "2026-11-13T07:10:00Z"}
        ]
      }
      """.trimIndent()
    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = sampleJson)

    composeTestRule.setContent {
      AccessLogTheme {
        var activeTab by remember { mutableStateOf(InspectorTab.REPORT) }
        JsonInspectorScreen(
          document = doc,
          activeTab = activeTab,
          searchQuery = "",
          treeFilter = TreeFilter.ALL,
          expandedPaths = setOf("$"),
          rawPrettyMode = true,
          flattenTree = { _, _, _, _ -> emptyList() },
          onSelectTab = { activeTab = it },
          onSearchChange = {},
          onFilterChange = {},
          onToggleExpand = {},
          onExpandAll = {},
          onCollapseAll = {},
          onTogglePretty = {},
          onOpenFile = {},
          onPasteJson = {},
          onCopyText = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("report_kpi_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("report_list").performScrollToNode(hasTestTag("report_actions_per_user_chart"))
    composeTestRule.onNodeWithTag("report_actions_per_user_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chart_user_bar_usr_alpha").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chart_filter_by_selected_user").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("report_list").performScrollToNode(hasTestTag("report_summary_card"))
    composeTestRule.onNodeWithTag("report_summary_card").assertIsDisplayed()
  }

  @Test
  fun summaryBookmarks_canTagSaveAndFilterSavedLogEntries() {
    val sampleJson =
      """
      {
        "history": [
          {
            "appletName": "Field Notes",
            "userId": "usr_alpha",
            "action": "OPENED",
            "timestamp": "2026-11-13T06:00:00Z"
          },
          {
            "appletName": "Audit Trail",
            "userId": "usr_beta",
            "action": "EXPORT_CSV",
            "timestamp": "2026-11-13T06:10:00Z"
          }
        ]
      }
      """.trimIndent()
    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = sampleJson)

    composeTestRule.setContent {
      AccessLogTheme {
        var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
        var showBookmarkedOnly by remember { mutableStateOf(false) }
        var selectedTag by remember { mutableStateOf<String?>(null) }
        var dialogRecordIndex by remember { mutableStateOf<Int?>(null) }

        JsonInspectorScreen(
          document = doc,
          activeTab = InspectorTab.OVERVIEW,
          searchQuery = "",
          treeFilter = TreeFilter.ALL,
          expandedPaths = setOf("$"),
          rawPrettyMode = true,
          flattenTree = { _, _, _, _ -> emptyList() },
          onSelectTab = {},
          onSearchChange = {},
          onFilterChange = {},
          onToggleExpand = {},
          onExpandAll = {},
          onCollapseAll = {},
          onTogglePretty = {},
          onOpenFile = {},
          onPasteJson = {},
          onCopyText = {},
          bookmarks = bookmarks,
          selectedBookmarkTag = selectedTag,
          showBookmarkedOnly = showBookmarkedOnly,
          onToggleBookmarkedOnly = { showBookmarkedOnly = !showBookmarkedOnly },
          onSelectBookmarkTag = { tag -> selectedTag = if (selectedTag == tag) null else tag },
          onBookmarkRecordClick = { rec -> dialogRecordIndex = rec.index },
          onRemoveSavedBookmark = { bm -> bookmarks = bookmarks.filterNot { it.id == bm.id } },
        )

        val targetRec = doc.records.firstOrNull { it.index == dialogRecordIndex }
        if (targetRec != null) {
          val key = BookmarkKeys.recordKeyFor(doc.fileName, targetRec)
          val existing = bookmarks.firstOrNull { it.recordKey == key }
          BookmarkTagDialog(
            entryTitle = targetRec.title,
            entrySubtitle = targetRec.userId,
            initialTags = existing?.tags ?: emptyList(),
            initialNote = existing?.note ?: "",
            isExistingBookmark = existing != null,
            onSave = { tags, note ->
              bookmarks =
                bookmarks.filterNot { it.id == key } +
                  Bookmark(
                    id = key,
                    userId = "test_uid",
                    sourceFile = doc.fileName,
                    recordKey = key,
                    recordIndex = targetRec.index.toLong(),
                    title = targetRec.title,
                    subtitle = targetRec.subtitle.orEmpty(),
                    recordUserId = targetRec.userId.orEmpty(),
                    actionName = targetRec.actionName.orEmpty(),
                    timestampText = targetRec.timestamp.orEmpty(),
                    tags = tags,
                    note = note,
                  )
              dialogRecordIndex = null
            },
            onRemove = null,
            onDismiss = { dialogRecordIndex = null },
          )
        }
      }
    }

    // Open bookmark dialog for record 1 and tag/save it
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("record_card_1"))
    composeTestRule.onNodeWithTag("bookmark_button_1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("bookmark_preset_tag_investigate").performClick()
    composeTestRule.onNodeWithTag("bookmark_note_input").performTextInput("Check token spike")
    composeTestRule.onNodeWithTag("bookmark_save_button").performClick()
    composeTestRule.waitForIdle()

    // Saved bookmarks section and tag filter chip should now appear
    composeTestRule.onNodeWithTag("summary_tag_filter_investigate").assertIsDisplayed()
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("saved_bookmarks_section"))
    composeTestRule.onNodeWithTag("saved_bookmarks_section").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("#Investigate")[0].assertIsDisplayed()

    // Filtering by Bookmarked shows record 1 and hides unbookmarked record 2
    composeTestRule.onNodeWithTag("summary_scope_bookmarked").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("record_card_2").assertCountEquals(0)
    composeTestRule.onNodeWithTag("overview_list").performScrollToNode(hasTestTag("record_card_1"))
    composeTestRule.onNodeWithTag("record_card_1").assertIsDisplayed()
  }

  @Test
  fun accessLogScaffold_overflowMenuShowsSaveExportAndSignOut() {
    val sampleJson =
      """{"history": [{"appletName": "Notes", "userId": "u1", "action": "OPENED"}]}"""
    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = sampleJson)
    var signedOut = false

    composeTestRule.setContent {
      AccessLogTheme {
        AccessLogScaffold(
          uiState = AccessLogUiState(loadState = InspectorLoadState.Ready(doc)),
          bookmarks = emptyList(),
          selectedBookmarkTag = null,
          showBookmarkedOnly = false,
          flattenTree = { _, _, _, _ -> emptyList() },
          onSelectTab = {},
          onSearchChange = {},
          onFilterChange = {},
          onToggleExpand = {},
          onExpandAll = {},
          onCollapseAll = {},
          onTogglePretty = {},
          onSummarySearchChange = {},
          onSummaryFilterScopeChange = {},
          onToggleBookmarkedOnly = {},
          onSelectBookmarkTag = {},
          onBookmarkRecordClick = {},
          onEditSavedBookmark = {},
          onRemoveSavedBookmark = {},
          onOpenFile = {},
          onPasteClick = {},
          onCopyText = {},
          onOpenRecentFile = {},
          onResetBundled = {},
          onSaveFormattedJson = {},
          onSaveProcessedSummaryJson = {},
          onExportCsv = {},
          onShareReport = {},
          onSignOut = { signedOut = true },
          onClearStatusMessage = {},
          onPasteLabelChange = {},
          onPasteTextChange = {},
          onApplyPastedJson = {},
          onDismissPasteSheet = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("top_more_menu_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_save_formatted_json").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_export_filtered_csv").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_sign_out").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(signedOut)
  }

  private fun flattenForTest(root: JsonNode?, expanded: Set<String>): List<FlattenedTreeRow> {
    if (root == null) return emptyList()
    val rows = mutableListOf<FlattenedTreeRow>()
    fun walk(node: JsonNode) {
      val children =
        when (node) {
          is JsonNode.ObjectNode -> node.children
          is JsonNode.ArrayNode -> node.children
          else -> emptyList()
        }
      val isExpanded = node.path in expanded
      rows.add(FlattenedTreeRow(node, children.isNotEmpty(), children.size, isExpanded))
      if (isExpanded) children.forEach(::walk)
    }
    walk(root)
    return rows
  }
}
