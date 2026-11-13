package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JsonSourceRepository
import com.example.data.RecentLocalFile
import com.example.model.JsonNode
import com.example.model.ParsedJsonDocument
import com.example.ui.screens.matchesRecordFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InspectorTab {
  OVERVIEW,
  REPORT,
  EXPLORER,
  RAW,
}

enum class TreeFilter(val label: String) {
  ALL("All"),
  CONTAINERS("Objects & Arrays"),
  PRIMITIVES("Values"),
}

enum class SummaryFilterScope(val label: String) {
  ALL("All"),
  USER_ID("User ID"),
  ACTION("Action"),
}

data class FlattenedTreeRow(
  val node: JsonNode,
  val hasChildren: Boolean,
  val childCount: Int,
  val isExpanded: Boolean,
)

sealed interface InspectorLoadState {
  data object Loading : InspectorLoadState

  data class Ready(val document: ParsedJsonDocument) : InspectorLoadState
}

data class AccessLogUiState(
  val loadState: InspectorLoadState = InspectorLoadState.Loading,
  val activeTab: InspectorTab = InspectorTab.OVERVIEW,
  val summarySearchQuery: String = "",
  val summaryFilterScope: SummaryFilterScope = SummaryFilterScope.ALL,
  val searchQuery: String = "",
  val treeFilter: TreeFilter = TreeFilter.ALL,
  val expandedPaths: Set<String> = setOf("$"),
  val rawPrettyMode: Boolean = true,
  val isLoadingFile: Boolean = false,
  val recentFiles: List<RecentLocalFile> = emptyList(),
  val showPasteSheet: Boolean = false,
  val pasteInputLabel: String = "",
  val pasteInputText: String = "",
  val statusMessage: String? = null,
)

class AccessLogViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = JsonSourceRepository(application.applicationContext)

  private val _uiState = MutableStateFlow(AccessLogUiState())
  val uiState: StateFlow<AccessLogUiState> = _uiState.asStateFlow()

  private var pendingExportContent: String? = null

  init {
    _uiState.update { it.copy(recentFiles = repository.getRecentFiles()) }
    loadBundledAttachment()
  }

  fun loadBundledAttachment() {
    viewModelScope.launch {
      _uiState.update { it.copy(loadState = InspectorLoadState.Loading) }
      val doc = repository.loadBundledAttachment()
      _uiState.update {
        it.copy(
          loadState = InspectorLoadState.Ready(doc),
          expandedPaths = defaultExpandedPaths(doc.rootNode),
          summarySearchQuery = "",
          searchQuery = "",
          isLoadingFile = false,
        )
      }
    }
  }

  fun loadDocumentFromUri(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingFile = true) }
      val result = repository.loadFromUri(uri)
      result.fold(
        onSuccess = { doc ->
          _uiState.update {
            it.copy(
              loadState = InspectorLoadState.Ready(doc),
              expandedPaths = defaultExpandedPaths(doc.rootNode),
              summarySearchQuery = "",
              searchQuery = "",
              isLoadingFile = false,
              recentFiles = repository.getRecentFiles(),
              statusMessage =
                if (doc.parseError == null) {
                  "Loaded ${doc.fileName} (${doc.records.size} records)"
                } else {
                  "Loaded ${doc.fileName} with syntax warning"
                },
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoadingFile = false,
              statusMessage = error.message ?: "Failed to read selected file.",
            )
          }
        },
      )
    }
  }

  fun openRecentFile(recent: RecentLocalFile) {
    loadDocumentFromUri(Uri.parse(recent.uriString))
  }

  fun prepareFormattedJsonExport(): String? {
    val doc = (_uiState.value.loadState as? InspectorLoadState.Ready)?.document ?: return null
    if (doc.parseError != null) {
      notifyMessage("Fix JSON syntax errors before saving a formatted JSON file.")
      return null
    }
    pendingExportContent = doc.prettyText
    return repository.suggestedFormattedFileName(doc.fileName, suffix = "formatted")
  }

  fun prepareProcessedSummaryExport(): String? {
    val state = _uiState.value
    val doc = (state.loadState as? InspectorLoadState.Ready)?.document ?: return null
    if (doc.parseError != null) {
      notifyMessage("Fix JSON syntax errors before saving processed data.")
      return null
    }
    val q = state.summarySearchQuery.trim().lowercase()
    val exportRecords =
      if (q.isEmpty()) doc.records
      else doc.records.filter { matchesRecordFilter(it, q, state.summaryFilterScope) }
    pendingExportContent =
      repository.buildProcessedReportJson(
        document = doc,
        records = exportRecords,
        filterQuery = state.summarySearchQuery,
      )
    return repository.suggestedFormattedFileName(doc.fileName, suffix = "processed")
  }

  fun prepareFilteredCsvExport(): String? {
    val state = _uiState.value
    val doc = (state.loadState as? InspectorLoadState.Ready)?.document ?: return null
    if (doc.parseError != null) {
      notifyMessage("Fix JSON syntax errors before exporting CSV.")
      return null
    }
    val q = state.summarySearchQuery.trim().lowercase()
    val exportRecords =
      if (q.isEmpty()) doc.records
      else doc.records.filter { matchesRecordFilter(it, q, state.summaryFilterScope) }
    if (exportRecords.isEmpty()) {
      notifyMessage("No matching log records to export to CSV.")
      return null
    }
    pendingExportContent = repository.buildFilteredRecordsCsv(exportRecords)
    return repository.suggestedFormattedFileName(
      originalName = doc.fileName,
      suffix = if (q.isEmpty()) "logs" else "filtered_logs",
      extension = "csv",
    )
  }

  fun savePendingExportToUri(uri: Uri) {
    val content = pendingExportContent ?: return
    pendingExportContent = null
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingFile = true) }
      val result = repository.saveTextToUri(uri, content)
      result.fold(
        onSuccess = { savedName ->
          _uiState.update {
            it.copy(
              isLoadingFile = false,
              statusMessage = "Saved $savedName",
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoadingFile = false,
              statusMessage = error.message ?: "Failed to save formatted JSON file.",
            )
          }
        },
      )
    }
  }

  fun selectTab(tab: InspectorTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun updateSummarySearchQuery(query: String) {
    _uiState.update { it.copy(summarySearchQuery = query) }
  }

  fun updateSummaryFilterScope(scope: SummaryFilterScope) {
    _uiState.update { it.copy(summaryFilterScope = scope) }
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun updateTreeFilter(filter: TreeFilter) {
    _uiState.update { it.copy(treeFilter = filter) }
  }

  fun toggleNodeExpanded(path: String) {
    _uiState.update { state ->
      val next = state.expandedPaths.toMutableSet()
      if (path in next) next.remove(path) else next.add(path)
      state.copy(expandedPaths = next)
    }
  }

  fun expandAll() {
    val doc = (_uiState.value.loadState as? InspectorLoadState.Ready)?.document ?: return
    val root = doc.rootNode ?: return
    val allPaths = mutableSetOf<String>()
    fun collect(node: JsonNode) {
      when (node) {
        is JsonNode.ObjectNode -> {
          allPaths.add(node.path)
          node.children.forEach(::collect)
        }
        is JsonNode.ArrayNode -> {
          allPaths.add(node.path)
          node.children.forEach(::collect)
        }
        else -> Unit
      }
    }
    collect(root)
    _uiState.update { it.copy(expandedPaths = allPaths) }
  }

  fun collapseAll() {
    _uiState.update { it.copy(expandedPaths = setOf("$")) }
  }

  fun toggleRawPrettyMode(pretty: Boolean) {
    _uiState.update { it.copy(rawPrettyMode = pretty) }
  }

  fun openPasteSheet() {
    val currentDoc = (_uiState.value.loadState as? InspectorLoadState.Ready)?.document
    _uiState.update {
      it.copy(
        showPasteSheet = true,
        pasteInputLabel = if (currentDoc?.isBundledAttachment == false) currentDoc.fileName else "",
        pasteInputText = currentDoc?.prettyText ?: "{}",
      )
    }
  }

  fun closePasteSheet() {
    _uiState.update { it.copy(showPasteSheet = false) }
  }

  fun updatePasteLabel(label: String) {
    _uiState.update { it.copy(pasteInputLabel = label) }
  }

  fun updatePasteText(text: String) {
    _uiState.update { it.copy(pasteInputText = text) }
  }

  fun applyPastedJson() {
    val state = _uiState.value
    val doc = repository.parsePastedJson(state.pasteInputLabel, state.pasteInputText)
    _uiState.update {
      it.copy(
        loadState = InspectorLoadState.Ready(doc),
        expandedPaths = defaultExpandedPaths(doc.rootNode),
        showPasteSheet = false,
        summarySearchQuery = "",
        searchQuery = "",
        statusMessage =
          if (doc.parseError == null) "Parsed ${doc.fileName}"
          else "Loaded with JSON syntax error",
      )
    }
  }

  fun notifyMessage(message: String) {
    _uiState.update { it.copy(statusMessage = message) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  fun flattenTree(
    root: JsonNode?,
    expandedPaths: Set<String>,
    query: String,
    filter: TreeFilter,
  ): List<FlattenedTreeRow> {
    if (root == null) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    val rows = mutableListOf<FlattenedTreeRow>()

    fun nodeMatchesQuery(node: JsonNode): Boolean {
      if (normalizedQuery.isEmpty()) return true
      if (node.keyName.lowercase().contains(normalizedQuery)) return true
      if (node.path.lowercase().contains(normalizedQuery)) return true
      return when (node) {
        is JsonNode.StringNode -> node.value.lowercase().contains(normalizedQuery)
        is JsonNode.NumberNode -> node.value.lowercase().contains(normalizedQuery)
        is JsonNode.BooleanNode -> node.value.toString().contains(normalizedQuery)
        is JsonNode.NullNode -> "null".contains(normalizedQuery)
        is JsonNode.ObjectNode -> node.children.any(::nodeMatchesQuery)
        is JsonNode.ArrayNode -> node.children.any(::nodeMatchesQuery)
      }
    }

    fun matchesFilter(node: JsonNode): Boolean {
      return when (filter) {
        TreeFilter.ALL -> true
        TreeFilter.CONTAINERS -> node is JsonNode.ObjectNode || node is JsonNode.ArrayNode
        TreeFilter.PRIMITIVES ->
          node !is JsonNode.ObjectNode && node !is JsonNode.ArrayNode || node.depth == 0
      }
    }

    fun walk(node: JsonNode) {
      if (!nodeMatchesQuery(node)) return
      val children =
        when (node) {
          is JsonNode.ObjectNode -> node.children
          is JsonNode.ArrayNode -> node.children
          else -> emptyList()
        }
      val hasChildren = children.isNotEmpty()
      val isExpanded = normalizedQuery.isNotEmpty() || node.path in expandedPaths

      if (matchesFilter(node)) {
        rows.add(
          FlattenedTreeRow(
            node = node,
            hasChildren = hasChildren,
            childCount = children.size,
            isExpanded = isExpanded,
          )
        )
      }

      if (hasChildren && isExpanded) {
        children.forEach(::walk)
      }
    }

    walk(root)
    return rows
  }

  private fun defaultExpandedPaths(root: JsonNode?): Set<String> {
    val paths = mutableSetOf("$")
    if (root is JsonNode.ObjectNode) {
      root.children.take(12).forEach { child ->
        if (child is JsonNode.ObjectNode || child is JsonNode.ArrayNode) {
          paths.add(child.path)
        }
      }
    } else if (root is JsonNode.ArrayNode) {
      root.children.take(5).forEach { child -> paths.add(child.path) }
    }
    return paths
  }
}
