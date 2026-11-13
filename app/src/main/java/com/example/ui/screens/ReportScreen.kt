package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AccessPatternReport
import com.example.model.AccessReportGenerator
import com.example.model.ActionCount
import com.example.model.ParsedJsonDocument
import com.example.model.TimeBucketCount
import com.example.model.UserActionBreakdown
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
  document: ParsedJsonDocument,
  onOpenFile: () -> Unit,
  onPasteJson: () -> Unit,
  onCopyReport: (String) -> Unit,
  onShareReport: (String) -> Unit,
  onFilterByUser: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val report = remember(document) { AccessReportGenerator.generate(document) }
  val syntax = LocalSyntaxColors.current
  val chartPalette =
    listOf(
      MaterialTheme.colorScheme.primary,
      MaterialTheme.colorScheme.secondary,
      MaterialTheme.colorScheme.tertiary,
      syntax.stringValue,
      syntax.numberValue,
      syntax.booleanValue,
      MaterialTheme.colorScheme.error,
    )

  val actionColorMap =
    remember(report.actionTotals, chartPalette) {
      report.actionTotals.mapIndexed { idx, action ->
        action.actionName to chartPalette[idx % chartPalette.size]
      }.toMap()
    }

  var selectedUser by remember(document) { mutableStateOf<UserActionBreakdown?>(null) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).testTag("report_list"),
      contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (report.totalEvents == 0) {
        item {
          EmptyReportCard(
            fileName = document.fileName,
            onOpenFile = onOpenFile,
            onPasteJson = onPasteJson,
          )
        }
      } else {
        item { ReportKpiGrid(report = report) }

        item {
          ActionsPerUserChartCard(
            users = report.userBreakdowns.take(10),
            actionTotals = report.actionTotals,
            actionColorMap = actionColorMap,
            selectedUser = selectedUser,
            onSelectUser = { user ->
              selectedUser = if (selectedUser?.userId == user.userId) null else user
            },
            onFilterByUser = onFilterByUser,
          )
        }

        item {
          ActionDistributionCard(
            actions = report.actionTotals,
            totalEvents = report.totalEvents,
            actionColorMap = actionColorMap,
          )
        }

        if (report.timeBuckets.size >= 2) {
          item { TimelineActivityCard(buckets = report.timeBuckets) }
        }

        item {
          SummaryReportTextCard(
            reportText = report.summaryText,
            onCopyReport = { onCopyReport(report.summaryText) },
            onShareReport = { onShareReport(report.summaryText) },
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyReportCard(
  fileName: String,
  onOpenFile: () -> Unit,
  onPasteJson: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth().testTag("report_empty_card"),
  ) {
    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Column {
          Text(
            text = "No Access Patterns to Chart",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text =
          "The currently loaded JSON contains 0 log records. Open a local JSON or JSON-Lines log file (or paste sample log records) to generate user action frequency charts and a summary report.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onOpenFile,
          modifier = Modifier.weight(1f).testTag("report_open_file_button"),
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
          modifier = Modifier.weight(1f).testTag("report_paste_json_button"),
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
private fun ReportKpiGrid(report: AccessPatternReport) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("report_kpi_grid"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ReportMetricCard(
        label = "Total Events",
        value = report.totalEvents.toString(),
        subtitle = "Parsed records",
        modifier = Modifier.weight(1f),
      )
      ReportMetricCard(
        label = "Unique Users",
        value = report.uniqueUsers.toString(),
        subtitle = report.topUser?.let { "Top: ${it.userId}" } ?: "",
        modifier = Modifier.weight(1f),
      )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ReportMetricCard(
        label = "Action Types",
        value = report.uniqueActions.toString(),
        subtitle = report.topAction?.let { "Top: ${it.actionName}" } ?: "",
        modifier = Modifier.weight(1f),
      )
      ReportMetricCard(
        label = "Peak User Share",
        value =
          report.topUser?.let {
            "${((it.totalCount.toFloat() / report.totalEvents) * 100).toInt()}%"
          } ?: "0%",
        subtitle = report.topUser?.let { "${it.totalCount} events" } ?: "",
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ReportMetricCard(
  label: String,
  value: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.primary,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsPerUserChartCard(
  users: List<UserActionBreakdown>,
  actionTotals: List<ActionCount>,
  actionColorMap: Map<String, Color>,
  selectedUser: UserActionBreakdown?,
  onSelectUser: (UserActionBreakdown) -> Unit,
  onFilterByUser: (String) -> Unit,
) {
  val maxUserTotal = (users.maxOfOrNull { it.totalCount } ?: 1).coerceAtLeast(1)
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("report_actions_per_user_chart"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Actions per User",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Tap any user row to inspect action counts or filter the Summary view",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        actionTotals.forEach { action ->
          val swatch = actionColorMap[action.actionName] ?: MaterialTheme.colorScheme.primary
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(swatch))
            Text(
              text = action.actionName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        users.forEach { user ->
          val isSelected = selectedUser?.userId == user.userId
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                  else Color.Transparent
                )
                .clickable { onSelectUser(user) }
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("chart_user_bar_${user.userId}"),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = user.userId,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "${user.totalCount} events",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
              val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
              drawRoundRect(color = trackColor, size = size, cornerRadius = radius)

              val fullBarWidth = size.width * (user.totalCount.toFloat() / maxUserTotal.toFloat())
              var currentX = 0f
              val segments = user.actionCounts.entries.toList()
              segments.forEachIndexed { idx, (actionName, count) ->
                val segWidth =
                  if (idx == segments.lastIndex) (fullBarWidth - currentX).coerceAtLeast(0f)
                  else fullBarWidth * (count.toFloat() / user.totalCount.toFloat())
                val segColor = actionColorMap[actionName] ?: Color.Gray
                if (segWidth > 0f) {
                  drawRoundRect(
                    color = segColor,
                    topLeft = Offset(currentX, 0f),
                    size = Size(segWidth, size.height),
                    cornerRadius = if (segments.size == 1) radius else CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                  )
                  currentX += segWidth
                }
              }
            }
          }
        }
      }

      if (selectedUser != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${selectedUser.userId} Breakdown",
            style = MaterialTheme.typography.titleSmall,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            selectedUser.actionCounts.forEach { (action, count) ->
              val color = actionColorMap[action] ?: MaterialTheme.colorScheme.primary
              Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.16f)) {
                Text(
                  text = "$action: $count",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
          OutlinedButton(
            onClick = { onFilterByUser(selectedUser.userId) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("chart_filter_by_selected_user"),
          ) {
            Icon(
              imageVector = Icons.Outlined.FilterAlt,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Filter Summary by ${selectedUser.userId}")
          }
        }
      }
    }
  }
}

@Composable
private fun ActionDistributionCard(
  actions: List<ActionCount>,
  totalEvents: Int,
  actionColorMap: Map<String, Color>,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("report_action_distribution_chart"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(
        text = "Action Frequency Distribution",
        style = MaterialTheme.typography.titleMedium,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            var startAngle = -90f
            actions.forEach { item ->
              val sweep = (item.count.toFloat() / totalEvents.toFloat()) * 360f
              val color = actionColorMap[item.actionName] ?: Color.Gray
              drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
              )
              startAngle += sweep
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = totalEvents.toString(),
              style = MaterialTheme.typography.titleLarge,
              fontFamily = JetBrainsMonoFontFamily,
            )
            Text(
              text = "events",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          actions.take(6).forEach { item ->
            val color = actionColorMap[item.actionName] ?: MaterialTheme.colorScheme.primary
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
              ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(
                  text = item.actionName,
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = "${item.count} (${item.percentage.toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimelineActivityCard(buckets: List<TimeBucketCount>) {
  val barColor = MaterialTheme.colorScheme.secondary
  val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("report_timeline_chart"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(text = "Activity Timeline", style = MaterialTheme.typography.titleMedium)

      Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        val count = buckets.size
        val gap = 6.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(6f)
        buckets.forEachIndexed { idx, bucket ->
          val ratio = bucket.count.toFloat() / maxCount.toFloat()
          val barHeight = (size.height * ratio).coerceAtLeast(6.dp.toPx())
          val x = idx * (barWidth + gap)
          val y = size.height - barHeight
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
          )
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = buckets.first().label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "${buckets.size} intervals",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = buckets.last().label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SummaryReportTextCard(
  reportText: String,
  onCopyReport: () -> Unit,
  onShareReport: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("report_summary_card"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "Generated Summary Report",
        style = MaterialTheme.typography.titleMedium,
      )
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = reportText,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(14.dp),
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = onCopyReport,
          modifier = Modifier.weight(1f).testTag("copy_report_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Report")
        }
        Button(
          onClick = onShareReport,
          modifier = Modifier.weight(1f).testTag("share_report_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share Report")
        }
      }
    }
  }
}
