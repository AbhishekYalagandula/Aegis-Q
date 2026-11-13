package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.example.R
import com.example.model.JsonNode
import com.example.model.JsonNodeKind
import com.example.ui.FlattenedTreeRow
import com.example.ui.TreeFilter
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

@Composable
fun ExplorerScreen(
  rows: List<FlattenedTreeRow>,
  searchQuery: String,
  treeFilter: TreeFilter,
  parseError: String?,
  onSearchChange: (String) -> Unit,
  onFilterChange: (TreeFilter) -> Unit,
  onToggleExpand: (String) -> Unit,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit,
  onCopyValue: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier.fillMaxWidth().testTag("explorer_search_input"),
          placeholder = { Text(stringResource(R.string.search_hint)) },
          leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
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
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TreeFilter.entries.forEach { filter ->
              FilterChip(
                selected = treeFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("tree_filter_${filter.name.lowercase()}"),
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExpandAll, modifier = Modifier.testTag("expand_all_button")) {
              Icon(
                imageVector = Icons.Outlined.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.action_expand_all))
            }
            TextButton(onClick = onCollapseAll, modifier = Modifier.testTag("collapse_all_button")) {
              Icon(
                imageVector = Icons.Outlined.UnfoldLess,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.action_collapse_all))
            }
          }
        }
      }

      if (parseError != null) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Text(
            text = "Tree unavailable — JSON syntax error:\n$parseError",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      } else if (rows.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Text(
            text =
              if (searchQuery.isNotBlank()) "No JSON nodes match \"$searchQuery\"."
              else "No JSON nodes to display.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(rows, key = { it.node.path }) { row ->
            TreeNodeRow(
              row = row,
              onToggle = { onToggleExpand(row.node.path) },
              onCopy = { onCopyValue(nodeCopyText(row.node)) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TreeNodeRow(
  row: FlattenedTreeRow,
  onToggle: () -> Unit,
  onCopy: () -> Unit,
) {
  val syntax = LocalSyntaxColors.current
  val node = row.node
  val indentDp = (node.depth.coerceAtMost(6) * 14).dp

  val typeColor =
    when (node.kind) {
      JsonNodeKind.OBJECT,
      JsonNodeKind.ARRAY -> syntax.key
      JsonNodeKind.STRING -> syntax.stringValue
      JsonNodeKind.NUMBER -> syntax.numberValue
      JsonNodeKind.BOOLEAN -> syntax.booleanValue
      JsonNodeKind.NULL -> syntax.nullValue
    }

  val valueSummary =
    when (node) {
      is JsonNode.ObjectNode ->
        if (node.children.isEmpty()) "{ } (empty object)" else "{ ${node.children.size} keys }"
      is JsonNode.ArrayNode ->
        if (node.children.isEmpty()) "[ ] (empty array)" else "[ ${node.children.size} items ]"
      is JsonNode.StringNode -> "\"${node.value}\""
      is JsonNode.NumberNode -> node.value
      is JsonNode.BooleanNode -> node.value.toString()
      is JsonNode.NullNode -> "null"
    }

  val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
  val clampedDepth = node.depth.coerceAtMost(6)
  val stepDp = 14.dp

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .drawBehind {
          val stepPx = stepDp.toPx()
          for (level in 0 until clampedDepth) {
            val x = level * stepPx + stepPx * 0.45f
            drawLine(
              color = guideColor,
              start = Offset(x, 0f),
              end = Offset(x, size.height),
              strokeWidth = 1.5.dp.toPx(),
            )
          }
          if (clampedDepth > 0) {
            val branchX = (clampedDepth - 1) * stepPx + stepPx * 0.45f
            drawLine(
              color = guideColor,
              start = Offset(branchX, size.height * 0.5f),
              end = Offset(clampedDepth * stepPx, size.height * 0.5f),
              strokeWidth = 1.5.dp.toPx(),
            )
          }
        }
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      modifier =
        Modifier.fillMaxWidth()
          .padding(start = indentDp)
          .clickable(enabled = row.hasChildren, onClick = onToggle)
          .testTag("tree_node_${node.path}"),
    ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (row.hasChildren) {
        Icon(
          imageVector =
            if (row.isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
          contentDescription = stringResource(R.string.cd_expand_node),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      } else {
        Spacer(modifier = Modifier.width(22.dp))
      }

      Spacer(modifier = Modifier.width(6.dp))

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = node.keyName,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = JetBrainsMonoFontFamily,
            color = syntax.key,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = typeColor.copy(alpha = 0.14f),
          ) {
            Text(
              text = node.kind.badge,
              style = MaterialTheme.typography.labelSmall,
              color = typeColor,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Text(
          text = valueSummary,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = JetBrainsMonoFontFamily,
          color =
            if (node is JsonNode.ObjectNode || node is JsonNode.ArrayNode)
              MaterialTheme.colorScheme.onSurfaceVariant
            else typeColor,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = node.path,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onCopy,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_node_${node.path}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = stringResource(R.string.cd_copy),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
      }
    }
  }
}

private fun nodeCopyText(node: JsonNode): String =
  when (node) {
    is JsonNode.StringNode -> node.value
    is JsonNode.NumberNode -> node.value
    is JsonNode.BooleanNode -> node.value.toString()
    is JsonNode.NullNode -> "null"
    is JsonNode.ObjectNode -> "${node.path} (${node.children.size} keys)"
    is JsonNode.ArrayNode -> "${node.path} (${node.children.size} items)"
  }
