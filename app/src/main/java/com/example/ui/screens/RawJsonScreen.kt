package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ParsedJsonDocument
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors
import com.example.ui.theme.SyntaxColors

@Composable
fun RawJsonScreen(
  document: ParsedJsonDocument,
  prettyMode: Boolean,
  onTogglePretty: (Boolean) -> Unit,
  onCopyRaw: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntax = LocalSyntaxColors.current
  val displayText =
    if (document.parseError != null) document.rawText
    else if (prettyMode) document.prettyText else document.compactText

  val lines = remember(displayText) { displayText.lines() }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = prettyMode,
            onClick = { onTogglePretty(true) },
            label = { Text(stringResource(R.string.action_format)) },
            modifier = Modifier.testTag("raw_pretty_chip"),
          )
          FilterChip(
            selected = !prettyMode,
            onClick = { onTogglePretty(false) },
            label = { Text(stringResource(R.string.action_minify)) },
            modifier = Modifier.testTag("raw_compact_chip"),
          )
        }

        Button(
          onClick = { onCopyRaw(displayText) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("copy_raw_json_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_copy_raw))
        }
      }

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxSize().testTag("raw_json_code_block"),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "JSON • ${lines.size} lines • ${document.stats.byteSize} B",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (prettyMode) "2-space indent" else "minified",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          val horizontalScroll = rememberScrollState()
          LazyColumn(
            modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScroll),
            contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
          itemsIndexed(lines) { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = (index + 1).toString().padStart(3, ' '),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(34.dp),
              )
              Text(
                text = highlightJsonLine(line, syntax),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                softWrap = false,
              )
            }
            }
          }
        }
      }
    }
  }
}

private fun highlightJsonLine(line: String, syntax: SyntaxColors): AnnotatedString {
  return buildAnnotatedString {
    val keyMatch = Regex("""^(\s*)"([^"]+)"(\s*:\s*)(.*)$""").find(line)
    if (keyMatch != null) {
      val (indent, key, colon, rest) = keyMatch.destructured
      append(indent)
      withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
      withStyle(SpanStyle(color = syntax.brace)) { append(colon) }
      appendValueToken(rest, syntax)
    } else {
      appendValueToken(line, syntax)
    }
  }
}

private fun AnnotatedString.Builder.appendValueToken(token: String, syntax: SyntaxColors) {
  val trimmed = token.trim()
  val color =
    when {
      trimmed.startsWith("\"") -> syntax.stringValue
      trimmed.startsWith("true") || trimmed.startsWith("false") -> syntax.booleanValue
      trimmed.startsWith("null") -> syntax.nullValue
      trimmed.firstOrNull()?.let { it.isDigit() || it == '-' } == true -> syntax.numberValue
      else -> syntax.brace
    }
  withStyle(SpanStyle(color = color)) { append(token) }
}
