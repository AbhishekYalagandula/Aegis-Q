package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BookmarkRepository
import com.example.model.AccessRecord
import com.example.model.Bookmark
import com.example.model.BookmarkKeys
import com.example.model.JsonNode
import com.example.ui.AccessLogUiState
import com.example.ui.AccessLogViewModel
import com.example.ui.BookmarkListState
import com.example.ui.BookmarkViewModel
import com.example.ui.FlattenedTreeRow
import com.example.ui.InspectorLoadState
import com.example.ui.InspectorTab
import com.example.ui.SummaryFilterScope
import com.example.ui.TreeFilter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookmarkTagDialog
import com.example.ui.screens.JsonInspectorScreen
import com.example.ui.screens.PasteJsonSheet
import com.example.ui.screens.attemptAutoSignIn
import com.example.ui.screens.authStateFlow
import com.example.ui.screens.signOutUser
import com.example.ui.theme.AccessLogTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AccessLogTheme {
        AccessLogApp()
      }
    }
  }
}

private sealed interface BookmarkDialogTarget {
  data class RecordTarget(
    val sourceFile: String,
    val record: AccessRecord,
    val existingBookmark: Bookmark?,
  ) : BookmarkDialogTarget

  data class SavedTarget(val bookmark: Bookmark) : BookmarkDialogTarget
}

@Composable
fun AccessLogApp() {
  val context = LocalContext.current
  remember(context) {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
        ?: FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.APPLICATION_ID)
            .setProjectId("accesslog-local-fallback")
            .setApiKey("local-fallback-key")
            .build(),
        )
    } else {
      FirebaseApp.getInstance()
    }
  }
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val currentUser by
    Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  LaunchedEffect(Unit) {
    if (currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
    return
  }

  key(user.uid) {
    val accessLogViewModel: AccessLogViewModel = viewModel()
    val bookmarkViewModel: BookmarkViewModel =
      viewModel(
        key = "bookmark_${user.uid}",
        factory =
          viewModelFactory {
            initializer {
              val app = checkNotNull(this[APPLICATION_KEY])
              val dbId = app.getString(R.string.firestore_database_id)
              val db = FirebaseFirestore.getInstance(dbId)
              BookmarkViewModel(BookmarkRepository(db), user.uid)
            }
          },
      )

    AccessLogAuthenticatedContent(
      accessLogViewModel = accessLogViewModel,
      bookmarkViewModel = bookmarkViewModel,
      onSignOut = {
        signOutUser(credentialManager = credentialManager, scope = scope)
      },
    )
  }
}

@Composable
private fun AccessLogAuthenticatedContent(
  accessLogViewModel: AccessLogViewModel,
  bookmarkViewModel: BookmarkViewModel,
  onSignOut: () -> Unit,
) {
  val uiState by accessLogViewModel.uiState.collectAsStateWithLifecycle()
  val bookmarkListState by bookmarkViewModel.bookmarkState.collectAsStateWithLifecycle()
  val selectedTagFilter by bookmarkViewModel.selectedTagFilter.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var showBookmarkedOnly by remember { mutableStateOf(false) }
  var dialogTarget by remember { mutableStateOf<BookmarkDialogTarget?>(null) }

  val bookmarks = (bookmarkListState as? BookmarkListState.Ready)?.bookmarks ?: emptyList()

  LaunchedEffect(bookmarkListState) {
    val err = bookmarkListState as? BookmarkListState.Error
    if (err != null) {
      accessLogViewModel.notifyMessage(err.message)
    }
  }

  val openDocumentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        accessLogViewModel.loadDocumentFromUri(uri)
      }
    }

  val createDocumentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        accessLogViewModel.savePendingExportToUri(uri)
      }
    }

  val createCsvLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        accessLogViewModel.savePendingExportToUri(uri)
      }
    }

  val openFilePicker = {
    openDocumentLauncher.launch(
      arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
    )
  }

  val saveFormattedJson: () -> Unit = {
    accessLogViewModel.prepareFormattedJsonExport()?.let { suggestedName ->
      createDocumentLauncher.launch(suggestedName)
    }
  }

  val saveProcessedSummaryJson: () -> Unit = {
    accessLogViewModel.prepareProcessedSummaryExport()?.let { suggestedName ->
      createDocumentLauncher.launch(suggestedName)
    }
  }

  val exportFilteredCsv: () -> Unit = {
    accessLogViewModel.prepareFilteredCsvExport()?.let { suggestedName ->
      createCsvLauncher.launch(suggestedName)
    }
  }

  val copyToClipboard: (String) -> Unit = { text ->
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("json_value", text))
    accessLogViewModel.notifyMessage(context.getString(R.string.copied_to_clipboard))
  }

  val shareReport: (String) -> Unit = { reportText ->
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Access Pattern Summary Report")
        putExtra(Intent.EXTRA_TEXT, reportText)
      }
    context.startActivity(Intent.createChooser(shareIntent, "Share Access Report"))
  }

  AccessLogScaffold(
    uiState = uiState,
    bookmarks = bookmarks,
    selectedBookmarkTag = selectedTagFilter,
    showBookmarkedOnly = showBookmarkedOnly,
    flattenTree = accessLogViewModel::flattenTree,
    onSelectTab = accessLogViewModel::selectTab,
    onSearchChange = accessLogViewModel::updateSearchQuery,
    onFilterChange = accessLogViewModel::updateTreeFilter,
    onToggleExpand = accessLogViewModel::toggleNodeExpanded,
    onExpandAll = accessLogViewModel::expandAll,
    onCollapseAll = accessLogViewModel::collapseAll,
    onTogglePretty = accessLogViewModel::toggleRawPrettyMode,
    onSummarySearchChange = accessLogViewModel::updateSummarySearchQuery,
    onSummaryFilterScopeChange = accessLogViewModel::updateSummaryFilterScope,
    onToggleBookmarkedOnly = { showBookmarkedOnly = !showBookmarkedOnly },
    onSelectBookmarkTag = { tag -> bookmarkViewModel.setTagFilter(tag) },
    onBookmarkRecordClick = { record ->
      val doc = (uiState.loadState as? InspectorLoadState.Ready)?.document ?: return@AccessLogScaffold
      val key = BookmarkKeys.recordKeyFor(doc.fileName, record)
      val existing = bookmarks.firstOrNull { it.recordKey == key }
      dialogTarget = BookmarkDialogTarget.RecordTarget(doc.fileName, record, existing)
    },
    onEditSavedBookmark = { bookmark ->
      dialogTarget = BookmarkDialogTarget.SavedTarget(bookmark)
    },
    onRemoveSavedBookmark = { bookmark ->
      bookmarkViewModel.deleteBookmark(bookmark.id) { _, msg ->
        accessLogViewModel.notifyMessage(msg)
      }
    },
    onOpenFile = openFilePicker,
    onPasteClick = accessLogViewModel::openPasteSheet,
    onCopyText = copyToClipboard,
    onOpenRecentFile = accessLogViewModel::openRecentFile,
    onResetBundled = accessLogViewModel::loadBundledAttachment,
    onSaveFormattedJson = saveFormattedJson,
    onSaveProcessedSummaryJson = saveProcessedSummaryJson,
    onExportCsv = exportFilteredCsv,
    onShareReport = shareReport,
    onSignOut = onSignOut,
    onClearStatusMessage = accessLogViewModel::clearStatusMessage,
    onPasteLabelChange = accessLogViewModel::updatePasteLabel,
    onPasteTextChange = accessLogViewModel::updatePasteText,
    onApplyPastedJson = accessLogViewModel::applyPastedJson,
    onDismissPasteSheet = accessLogViewModel::closePasteSheet,
  )

  when (val target = dialogTarget) {
    is BookmarkDialogTarget.RecordTarget -> {
      val existing = target.existingBookmark
      BookmarkTagDialog(
        entryTitle = target.record.title,
        entrySubtitle =
          listOfNotNull(
            target.record.userId?.takeIf { it.isNotBlank() },
            target.record.actionName?.takeIf { it.isNotBlank() },
          ).joinToString(" • ").ifBlank { target.sourceFile },
        initialTags = existing?.tags ?: emptyList(),
        initialNote = existing?.note ?: "",
        isExistingBookmark = existing != null,
        onSave = { tags, note ->
          dialogTarget = null
          if (existing != null) {
            bookmarkViewModel.updateBookmark(existing.id, tags, note) { _, msg ->
              accessLogViewModel.notifyMessage(msg)
            }
          } else {
            bookmarkViewModel.saveBookmark(target.sourceFile, target.record, tags, note) { _, msg ->
              accessLogViewModel.notifyMessage(msg)
            }
          }
        },
        onRemove =
          if (existing != null) {
            {
              dialogTarget = null
              bookmarkViewModel.deleteBookmark(existing.id) { _, msg ->
                accessLogViewModel.notifyMessage(msg)
              }
            }
          } else {
            null
          },
        onDismiss = { dialogTarget = null },
      )
    }

    is BookmarkDialogTarget.SavedTarget -> {
      val bm = target.bookmark
      BookmarkTagDialog(
        entryTitle = bm.title.ifBlank { "Record #${bm.recordIndex}" },
        entrySubtitle =
          listOfNotNull(
            bm.sourceFile.takeIf { it.isNotBlank() },
            bm.recordUserId.takeIf { it.isNotBlank() },
            bm.actionName.takeIf { it.isNotBlank() },
          ).joinToString(" • "),
        initialTags = bm.tags,
        initialNote = bm.note,
        isExistingBookmark = true,
        onSave = { tags, note ->
          dialogTarget = null
          bookmarkViewModel.updateBookmark(bm.id, tags, note) { _, msg ->
            accessLogViewModel.notifyMessage(msg)
          }
        },
        onRemove = {
          dialogTarget = null
          bookmarkViewModel.deleteBookmark(bm.id) { _, msg ->
            accessLogViewModel.notifyMessage(msg)
          }
        },
        onDismiss = { dialogTarget = null },
      )
    }

    null -> Unit
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessLogScaffold(
  uiState: AccessLogUiState,
  bookmarks: List<Bookmark>,
  selectedBookmarkTag: String?,
  showBookmarkedOnly: Boolean,
  flattenTree: (JsonNode?, Set<String>, String, TreeFilter) -> List<FlattenedTreeRow>,
  onSelectTab: (InspectorTab) -> Unit,
  onSearchChange: (String) -> Unit,
  onFilterChange: (TreeFilter) -> Unit,
  onToggleExpand: (String) -> Unit,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit,
  onTogglePretty: (Boolean) -> Unit,
  onSummarySearchChange: (String) -> Unit,
  onSummaryFilterScopeChange: (SummaryFilterScope) -> Unit,
  onToggleBookmarkedOnly: () -> Unit,
  onSelectBookmarkTag: (String) -> Unit,
  onBookmarkRecordClick: (AccessRecord) -> Unit,
  onEditSavedBookmark: (Bookmark) -> Unit,
  onRemoveSavedBookmark: (Bookmark) -> Unit,
  onOpenFile: () -> Unit,
  onPasteClick: () -> Unit,
  onCopyText: (String) -> Unit,
  onOpenRecentFile: (com.example.data.RecentLocalFile) -> Unit,
  onResetBundled: () -> Unit,
  onSaveFormattedJson: () -> Unit,
  onSaveProcessedSummaryJson: () -> Unit,
  onExportCsv: () -> Unit,
  onShareReport: (String) -> Unit,
  onSignOut: () -> Unit,
  onClearStatusMessage: () -> Unit,
  onPasteLabelChange: (String) -> Unit,
  onPasteTextChange: (String) -> Unit,
  onApplyPastedJson: () -> Unit,
  onDismissPasteSheet: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showOverflowMenu by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.statusMessage) {
    val msg = uiState.statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      onClearStatusMessage()
    }
  }

  BackHandler(enabled = uiState.activeTab != InspectorTab.OVERVIEW) {
    onSelectTab(InspectorTab.OVERVIEW)
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onOpenFile,
        icon = {
          Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
          )
        },
        text = { Text(stringResource(R.string.action_open_file)) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_open_json_file"),
      )
    },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
            val readyDoc = (uiState.loadState as? InspectorLoadState.Ready)?.document
            if (readyDoc != null) {
              Text(
                text = readyDoc.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
        actions = {
          val readyDoc = (uiState.loadState as? InspectorLoadState.Ready)?.document
          IconButton(
            onClick = onOpenFile,
            modifier = Modifier.testTag("top_open_file_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = stringResource(R.string.cd_open_file),
            )
          }
          IconButton(
            onClick = onPasteClick,
            modifier = Modifier.testTag("top_paste_json_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.EditNote,
              contentDescription = stringResource(R.string.cd_paste_json),
            )
          }
          Box {
            IconButton(
              onClick = { showOverflowMenu = true },
              modifier = Modifier.testTag("top_more_menu_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
              )
            }
            DropdownMenu(
              expanded = showOverflowMenu,
              onDismissRequest = { showOverflowMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_save_formatted_json)) },
                leadingIcon = {
                  Icon(imageVector = Icons.Outlined.SaveAs, contentDescription = null)
                },
                enabled = readyDoc != null,
                onClick = {
                  showOverflowMenu = false
                  onSaveFormattedJson()
                },
                modifier = Modifier.testTag("menu_save_formatted_json"),
              )
              if (readyDoc != null && readyDoc.records.isNotEmpty()) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_export_filtered_csv)) },
                  leadingIcon = {
                    Icon(imageVector = Icons.Outlined.TableView, contentDescription = null)
                  },
                  onClick = {
                    showOverflowMenu = false
                    onExportCsv()
                  },
                  modifier = Modifier.testTag("menu_export_filtered_csv"),
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_save_processed_json)) },
                  leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Analytics, contentDescription = null)
                  },
                  onClick = {
                    showOverflowMenu = false
                    onSaveProcessedSummaryJson()
                  },
                  modifier = Modifier.testTag("menu_save_processed_json"),
                )
              }
              if (readyDoc != null && !readyDoc.isBundledAttachment) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.cd_reset_bundled)) },
                  leadingIcon = {
                    Icon(imageVector = Icons.Outlined.RestartAlt, contentDescription = null)
                  },
                  onClick = {
                    showOverflowMenu = false
                    onResetBundled()
                  },
                  modifier = Modifier.testTag("menu_reset_bundled"),
                )
              }
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_sign_out)) },
                leadingIcon = {
                  Icon(imageVector = Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                },
                onClick = {
                  showOverflowMenu = false
                  onSignOut()
                },
                modifier = Modifier.testTag("menu_sign_out"),
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (uiState.isLoadingFile) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("file_loading_bar"))
      }

      when (val loadState = uiState.loadState) {
        InspectorLoadState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator()
              Text("Parsing JSON…", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        is InspectorLoadState.Ready -> {
          JsonInspectorScreen(
            document = loadState.document,
            activeTab = uiState.activeTab,
            searchQuery = uiState.searchQuery,
            treeFilter = uiState.treeFilter,
            expandedPaths = uiState.expandedPaths,
            rawPrettyMode = uiState.rawPrettyMode,
            flattenTree = flattenTree,
            onSelectTab = onSelectTab,
            onSearchChange = onSearchChange,
            onFilterChange = onFilterChange,
            onToggleExpand = onToggleExpand,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            onTogglePretty = onTogglePretty,
            onOpenFile = onOpenFile,
            onPasteJson = onPasteClick,
            onCopyText = onCopyText,
            summarySearchQuery = uiState.summarySearchQuery,
            summaryFilterScope = uiState.summaryFilterScope,
            onSummarySearchChange = onSummarySearchChange,
            onSummaryFilterScopeChange = onSummaryFilterScopeChange,
            recentFiles = uiState.recentFiles,
            onOpenRecentFile = onOpenRecentFile,
            onResetBundled = onResetBundled,
            onShareReport = onShareReport,
            onExportCsv = onExportCsv,
            bookmarks = bookmarks,
            selectedBookmarkTag = selectedBookmarkTag,
            showBookmarkedOnly = showBookmarkedOnly,
            onToggleBookmarkedOnly = onToggleBookmarkedOnly,
            onSelectBookmarkTag = onSelectBookmarkTag,
            onBookmarkRecordClick = onBookmarkRecordClick,
            onEditSavedBookmark = onEditSavedBookmark,
            onRemoveSavedBookmark = onRemoveSavedBookmark,
          )
        }
      }
    }
  }

  if (uiState.showPasteSheet) {
    PasteJsonSheet(
      label = uiState.pasteInputLabel,
      jsonText = uiState.pasteInputText,
      onLabelChange = onPasteLabelChange,
      onTextChange = onPasteTextChange,
      onApply = onApplyPastedJson,
      onDismiss = onDismissPasteSheet,
    )
  }
}
