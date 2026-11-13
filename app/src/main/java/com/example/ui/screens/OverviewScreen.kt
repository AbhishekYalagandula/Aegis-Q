package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import com.example.R
import com.example.data.RecentLocalFile
import com.example.model.AccessRecord
import com.example.model.Bookmark
import com.example.model.BookmarkKeys
import com.example.model.JsonNodeKind
import com.example.model.JsonRootType
import com.example.model.ParsedJsonDocument
import com.example.ui.SummaryFilterScope
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
  document: ParsedJsonDocument,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onOpenFile: () -> Unit,
  onPasteJson: () -> Unit,
  onNavigateToExplorer: () -> Unit,
  onNavigateToRaw: () -> Unit,
  modifier: Modifier = Modifier,
  filterScope: SummaryFilterScope = SummaryFilterScope.ALL,
  onFilterScopeChange: (SummaryFilterScope) -> Unit = {},
  recentFiles: List<RecentLocalFile> = emptyList(),
  onOpenRecentFile: (RecentLocalFile) -> Unit = {},
  onResetBundled: () -> Unit = {},
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
  val bookmarksByRecordKey =
    remember(bookmarks) {
      bookmarks.associateBy { it.recordKey }
    }

  val availableBookmarkTags =
    remember(bookmarks) {
      bookmarks.flatMap { it.tags }.distinct().sorted()
    }

  val filteredBookmarks =
    remember(bookmarks, searchQuery, selectedBookmarkTag) {
      val q = searchQuery.trim().lowercase()
      bookmarks.filter { bm ->
        val matchesTag =
          selectedBookmarkTag == null ||
            bm.tags.any { it.equals(selectedBookmarkTag, ignoreCase = true) }
        val matchesQuery =
          q.isEmpty() ||
            bm.title.lowercase().contains(q) ||
            bm.recordUserId.lowercase().contains(q) ||
            bm.actionName.lowercase().contains(q) ||
            bm.sourceFile.lowercase().contains(q) ||
            bm.note.lowercase().contains(q) ||
            bm.tags.any { it.lowercase().contains(q) }
        matchesTag && matchesQuery
      }
    }

  val filteredRecords =
    remember(
      document.records,
      document.fileName,
      searchQuery,
      filterScope,
      showBookmarkedOnly,
      selectedBookmarkTag,
      bookmarksByRecordKey,
    ) {
      val q = searchQuery.trim().lowercase()
      document.records.filter { rec ->
        val matchesQuery = q.isEmpty() || matchesRecordFilter(rec, q, filterScope)
        val bookmark = bookmarksByRecordKey[BookmarkKeys.recordKeyFor(document.fileName, rec)]
        val matchesBookmarked = !showBookmarkedOnly || bookmark != null
        val matchesTag =
          selectedBookmarkTag == null ||
            (bookmark != null &&
              bookmark.tags.any { it.equals(selectedBookmarkTag, ignoreCase = true) })
        matchesQuery && matchesBookmarked && matchesTag
      }
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier.fillMaxWidth().testTag("overview_search_input"),
          placeholder = { Text(stringResource(R.string.summary_search_hint)) },
          leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchChange("") },
                modifier = Modifier.testTag("overview_search_clear"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_clear_search),
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SummaryFilterScope.entries.forEach { scope ->
            FilterChip(
              selected = filterScope == scope,
              onClick = { onFilterScopeChange(scope) },
              label = { Text(scope.label) },
              modifier = Modifier.testTag("summary_scope_${scope.name.lowercase()}"),
            )
          }
          FilterChip(
            selected = showBookmarkedOnly,
            onClick = onToggleBookmarkedOnly,
            leadingIcon = {
              Icon(
                imageVector =
                  if (showBookmarkedOnly) Icons.Filled.Bookmark
                  else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text("Bookmarked (${bookmarks.size})") },
            modifier = Modifier.testTag("summary_scope_bookmarked"),
          )
        }

        if (availableBookmarkTags.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            availableBookmarkTags.forEach { tag ->
              val selected = selectedBookmarkTag.equals(tag, ignoreCase = true)
              FilterChip(
                selected = selected,
                onClick = { onSelectBookmarkTag(tag) },
                label = { Text("#$tag") },
                modifier = Modifier.testTag("summary_tag_filter_${tag.lowercase()}"),
              )
            }
          }
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("overview_list"),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          HeroSummaryCard(
            document = document,
            onNavigateToExplorer = onNavigateToExplorer,
            onNavigateToRaw = onNavigateToRaw,
          )
        }

        if (recentFiles.isNotEmpty()) {
          item {
            RecentFilesRow(
              recentFiles = recentFiles,
              activeFileName = document.fileName,
              isBundledActive = document.isBundledAttachment,
              onOpenRecentFile = onOpenRecentFile,
              onResetBundled = onResetBundled,
            )
          }
        }

        if (filteredBookmarks.isNotEmpty()) {
          item {
            SavedBookmarksSection(
              bookmarks = filteredBookmarks,
              totalBookmarkCount = bookmarks.size,
              selectedTagFilter = selectedBookmarkTag,
              onSelectTagFilter = onSelectBookmarkTag,
              onEditBookmark = onEditSavedBookmark,
              onRemoveBookmark = onRemoveSavedBookmark,
            )
          }
        }

        if (document.parseError != null) {
          item { ParseErrorCard(error = document.parseError, onPasteJson = onPasteJson) }
        } else if (document.stats.isEmptyContainer) {
          item {
            EmptyJsonNoticeCard(
              document = document,
              onOpenFile = onOpenFile,
              onPasteJson = onPasteJson,
            )
          }
        } else {
          if (document.records.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text =
                      if (
                        searchQuery.isNotBlank() ||
                          showBookmarkedOnly ||
                          selectedBookmarkTag != null
                      ) {
                        "Log Records (${filteredRecords.size} of ${document.records.size})"
                      } else {
                        "Log Records (${filteredRecords.size})"
                      },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                  onClick = onExportCsv,
                  enabled = filteredRecords.isNotEmpty(),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("overview_export_csv_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.TableView,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.action_export_csv),
                    style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
            }

            if (filteredRecords.isEmpty()) {
              item {
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.fillMaxWidth().testTag("overview_empty_filter"),
                ) {
                  Text(
                    text =
                      buildString {
                        append("No log entries match")
                        if (searchQuery.isNotBlank()) {
                          append(" \"$searchQuery\" in ${filterScope.label}")
                        }
                        if (showBookmarkedOnly) {
                          append(" (bookmarked only)")
                        }
                        if (selectedBookmarkTag != null) {
                          append(" with tag #$selectedBookmarkTag")
                        }
                        append(".")
                      },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                  )
                }
              }
            } else {
              items(filteredRecords, key = { "${it.sourceCollection}_${it.index}" }) { record ->
                val bookmark =
                  bookmarksByRecordKey[BookmarkKeys.recordKeyFor(document.fileName, record)]
                AccessRecordCard(
                  record = record,
                  bookmark = bookmark,
                  onBookmarkClick = { onBookmarkRecordClick(record) },
                )
              }
            }
          } else {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Scalar / Key-Value Payload",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    "This JSON document does not contain an array of log entries. Switch to Tree View to inspect its nested keys or Raw JSON to view the formatted payload.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

          if (document.topLevelKeys.isNotEmpty()) {
            item {
              TopLevelSchemaCard(
                rootType = document.stats.rootType,
                entries = document.topLevelKeys,
                onExploreClick = onNavigateToExplorer,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HeroSummaryCard(
  document: ParsedJsonDocument,
  onNavigateToExplorer: () -> Unit,
  onNavigateToRaw: () -> Unit,
) {
  val gradient =
    Brush.linearGradient(
      colors =
        listOf(
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        )
    )

  Surface(
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("hero_summary_card"),
  ) {
    Column(modifier = Modifier.background(gradient).padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                when (document.stats.rootType) {
                  JsonRootType.ARRAY -> Icons.Rounded.DataArray
                  else -> Icons.Outlined.DataObject
                },
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column {
            Text(
              text = document.fileName,
              style = MaterialTheme.typography.titleMedium,
              fontFamily = JetBrainsMonoFontFamily,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                if (document.isBundledAttachment) "Bundled attachment" else "User-loaded JSON",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        StatusPill(document = document)
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TelemetryChip(
          label = "Size",
          value = "${document.stats.byteSize} B",
          modifier = Modifier.weight(1f),
        )
        TelemetryChip(
          label = "Keys",
          value = document.stats.totalKeys.toString(),
          modifier = Modifier.weight(1f),
        )
        TelemetryChip(
          label = "Depth",
          value = document.stats.maxDepth.toString(),
          modifier = Modifier.weight(1f),
        )
        TelemetryChip(
          label = "Entries",
          value = document.records.size.toString(),
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = onNavigateToExplorer,
          modifier = Modifier.weight(1f).testTag("quick_explorer_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Layers,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.tab_tree))
        }
        OutlinedButton(
          onClick = onNavigateToRaw,
          modifier = Modifier.weight(1f).testTag("quick_raw_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.tab_raw))
        }
      }
    }
  }
}

@Composable
private fun StatusPill(document: ParsedJsonDocument) {
  val (label, container, content) =
    when {
      document.parseError != null ->
        Triple(
          "Syntax Error",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
        )
      document.stats.isEmptyContainer ->
        Triple(
          "Empty ${document.stats.rootType.label}",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      else ->
        Triple(
          document.stats.rootType.label,
          MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
          MaterialTheme.colorScheme.primary,
        )
    }

  Surface(shape = CircleShape, color = container) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector =
          if (document.parseError != null) Icons.Outlined.ErrorOutline
          else Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = content,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun TelemetryChip(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = JetBrainsMonoFontFamily,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun EmptyJsonNoticeCard(
  document: ParsedJsonDocument,
  onOpenFile: () -> Unit,
  onPasteJson: () -> Unit,
) {
  val isArray = document.stats.rootType == JsonRootType.ARRAY
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    modifier = Modifier.fillMaxWidth().testTag("empty_json_notice_card"),
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.tertiaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.DataObject,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        Column {
          Text(
            text =
              if (isArray) stringResource(R.string.empty_array_title)
              else stringResource(R.string.empty_attached_title),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Raw content: ${document.prettyText.ifBlank { "{}" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Text(
        text =
          if (isArray) stringResource(R.string.empty_array_body)
          else stringResource(R.string.empty_attached_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onOpenFile,
          modifier = Modifier.weight(1f).testTag("empty_open_file_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_open_file))
        }
        OutlinedButton(
          onClick = onPasteJson,
          modifier = Modifier.weight(1f).testTag("empty_paste_json_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.EditNote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_paste_json))
        }
      }
    }
  }
}

@Composable
private fun ParseErrorCard(error: String, onPasteJson: () -> Unit) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    modifier = Modifier.fillMaxWidth().testTag("parse_error_card"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.ErrorOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
          text = "Unable to Parse JSON",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      Text(
        text = error,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
      OutlinedButton(onClick = onPasteJson, shape = RoundedCornerShape(12.dp)) {
        Text("Edit Pasted JSON", color = MaterialTheme.colorScheme.onErrorContainer)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopLevelSchemaCard(
  rootType: JsonRootType,
  entries: List<Pair<String, JsonNodeKind>>,
  onExploreClick: () -> Unit,
) {
  val syntax = LocalSyntaxColors.current
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Top-Level Structure (${entries.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text = rootType.label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        entries.take(18).forEach { (key, kind) ->
          val badgeColor =
            when (kind) {
              JsonNodeKind.OBJECT,
              JsonNodeKind.ARRAY -> syntax.key
              JsonNodeKind.STRING -> syntax.stringValue
              JsonNodeKind.NUMBER -> syntax.numberValue
              JsonNodeKind.BOOLEAN -> syntax.booleanValue
              JsonNodeKind.NULL -> syntax.nullValue
            }
          Surface(
            onClick = onExploreClick,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = kind.badge,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessRecordCard(
  record: AccessRecord,
  bookmark: Bookmark?,
  onBookmarkClick: () -> Unit,
) {
  val isBookmarked = bookmark != null
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isBookmarked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
          } else {
            MaterialTheme.colorScheme.surface
          }
      ),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (isBookmarked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
          } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          },
      ),
    modifier = Modifier.fillMaxWidth().testTag("record_card_${record.index}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = record.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (!record.subtitle.isNullOrBlank()) {
            Text(
              text = record.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = record.statusOrAction ?: "#${record.index}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          IconButton(
            onClick = onBookmarkClick,
            modifier = Modifier.testTag("bookmark_button_${record.index}"),
          ) {
            Icon(
              imageVector =
                if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = stringResource(R.string.cd_bookmark_entry),
              tint =
                if (isBookmarked) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
        }
      }

      if (!record.userId.isNullOrBlank() || !record.timestamp.isNullOrBlank()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!record.userId.isNullOrBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = record.userId,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (!record.timestamp.isNullOrBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = record.timestamp,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        record.fields.take(10).forEach { (k, v) ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "$k:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = v,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (bookmark != null && (bookmark.tags.isNotEmpty() || bookmark.note.isNotBlank())) {
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        if (bookmark.tags.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            bookmark.tags.forEach { tag ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = "#$tag",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }
        }
        if (bookmark.note.isNotBlank()) {
          Text(
            text = bookmark.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

internal fun matchesRecordFilter(
  record: AccessRecord,
  normalizedQuery: String,
  scope: SummaryFilterScope,
): Boolean {
  if (normalizedQuery.isEmpty()) return true
  return when (scope) {
    SummaryFilterScope.ALL ->
      (record.userId?.lowercase()?.contains(normalizedQuery) == true) ||
        (record.actionName?.lowercase()?.contains(normalizedQuery) == true) ||
        record.title.lowercase().contains(normalizedQuery) ||
        (record.subtitle?.lowercase()?.contains(normalizedQuery) == true) ||
        (record.timestamp?.lowercase()?.contains(normalizedQuery) == true) ||
        (record.statusOrAction?.lowercase()?.contains(normalizedQuery) == true) ||
        record.fields.any { (k, v) ->
          k.lowercase().contains(normalizedQuery) || v.lowercase().contains(normalizedQuery)
        }
    SummaryFilterScope.USER_ID ->
      (record.userId?.lowercase()?.contains(normalizedQuery) == true) ||
        record.fields.any { (k, v) ->
          val keyLower = k.lowercase()
          (keyLower.contains("user") ||
            keyLower.contains("uid") ||
            keyLower.contains("actor") ||
            keyLower.contains("email") ||
            keyLower.contains("account")) && v.lowercase().contains(normalizedQuery)
        }
    SummaryFilterScope.ACTION ->
      (record.actionName?.lowercase()?.contains(normalizedQuery) == true) ||
        (record.statusOrAction?.lowercase()?.contains(normalizedQuery) == true) ||
        record.fields.any { (k, v) ->
          val keyLower = k.lowercase()
          (keyLower.contains("action") ||
            keyLower.contains("event") ||
            keyLower.contains("method") ||
            keyLower.contains("operation") ||
            keyLower.contains("status") ||
            keyLower.contains("type")) && v.lowercase().contains(normalizedQuery)
        }
  }
}

@Composable
private fun RecentFilesRow(
  recentFiles: List<RecentLocalFile>,
  activeFileName: String,
  isBundledActive: Boolean,
  onOpenRecentFile: (RecentLocalFile) -> Unit,
  onResetBundled: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Recent Local Files",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (!isBundledActive) {
        AssistChip(
          onClick = onResetBundled,
          label = { Text(stringResource(R.string.action_reset_bundled)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.DataObject,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          modifier = Modifier.testTag("recent_chip_bundled"),
        )
      }
      recentFiles.forEachIndexed { idx, recent ->
        AssistChip(
          onClick = { onOpenRecentFile(recent) },
          enabled = isBundledActive || recent.displayName != activeFileName,
          label = {
            Text(
              text = recent.displayName,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          modifier = Modifier.testTag("recent_chip_$idx"),
        )
      }
    }
  }
}
