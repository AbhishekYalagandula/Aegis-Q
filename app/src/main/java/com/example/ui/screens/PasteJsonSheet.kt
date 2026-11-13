package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteJsonSheet(
  label: String,
  jsonText: String,
  onLabelChange: (String) -> Unit,
  onTextChange: (String) -> Unit,
  onApply: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.paste_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )

      OutlinedTextField(
        value = label,
        onValueChange = onLabelChange,
        label = { Text(stringResource(R.string.paste_dialog_label)) },
        placeholder = { Text("custom_history.json") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("paste_label_input"),
      )

      OutlinedTextField(
        value = jsonText,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.paste_dialog_placeholder)) },
        textStyle =
          TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
          ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp).testTag("paste_json_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).testTag("paste_cancel_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(stringResource(R.string.paste_dialog_cancel))
        }
        Button(
          onClick = onApply,
          modifier = Modifier.weight(1f).testTag("paste_apply_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(stringResource(R.string.paste_dialog_apply))
        }
      }
    }
  }
}
