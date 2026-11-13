package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BookmarkKeys

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkTagDialog(
  entryTitle: String,
  entrySubtitle: String?,
  initialTags: List<String>,
  initialNote: String,
  isExistingBookmark: Boolean,
  onSave: (tags: List<String>, note: String) -> Unit,
  onRemove: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  val selectedTags = remember(initialTags) { mutableStateListOf(*initialTags.toTypedArray()) }
  var customTagInput by remember { mutableStateOf("") }
  var noteInput by remember(initialNote) { mutableStateOf(initialNote) }

  val addCustomTag = {
    val clean = customTagInput.trim().take(40)
    if (clean.isNotEmpty() && selectedTags.none { it.equals(clean, ignoreCase = true) } && selectedTags.size < 10) {
      selectedTags.add(clean)
      customTagInput = ""
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.BookmarkAdded,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = if (isExistingBookmark) "Edit Bookmark & Tags" else "Bookmark Log Entry",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = entryTitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!entrySubtitle.isNullOrBlank()) {
          Text(
            text = entrySubtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Quick Tags",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          BookmarkKeys.PRESET_TAGS.forEach { preset ->
            val isSelected = selectedTags.any { it.equals(preset, ignoreCase = true) }
            FilterChip(
              selected = isSelected,
              onClick = {
                if (isSelected) {
                  selectedTags.removeAll { it.equals(preset, ignoreCase = true) }
                } else if (selectedTags.size < 10) {
                  selectedTags.add(preset)
                }
              },
              label = { Text(preset) },
              modifier = Modifier.testTag("bookmark_preset_tag_${preset.lowercase()}"),
            )
          }
        }

        val customAdded = selectedTags.filter { tag ->
          BookmarkKeys.PRESET_TAGS.none { it.equals(tag, ignoreCase = true) }
        }
        if (customAdded.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            customAdded.forEach { tag ->
              InputChip(
                selected = true,
                onClick = { selectedTags.remove(tag) },
                label = { Text(tag) },
                trailingIcon = {
                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove tag $tag",
                    modifier = Modifier.size(16.dp),
                  )
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = customTagInput,
            onValueChange = { if (it.length <= 40) customTagInput = it },
            label = { Text("Custom tag") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("bookmark_custom_tag_input"),
            shape = RoundedCornerShape(12.dp),
          )
          OutlinedButton(
            onClick = addCustomTag,
            enabled = customTagInput.isNotBlank() && selectedTags.size < 10,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("bookmark_add_tag_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = "Add custom tag",
              modifier = Modifier.size(18.dp),
            )
          }
        }

        OutlinedTextField(
          value = noteInput,
          onValueChange = { if (it.length <= 500) noteInput = it },
          label = { Text("Reference note (optional)") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth().testTag("bookmark_note_input"),
          shape = RoundedCornerShape(12.dp),
        )

        if (isExistingBookmark && onRemove != null) {
          TextButton(
            onClick = onRemove,
            modifier = Modifier.testTag("bookmark_remove_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Remove Bookmark", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(selectedTags.toList(), noteInput) },
        modifier = Modifier.testTag("bookmark_save_button"),
      ) {
        Text("Save Bookmark")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
