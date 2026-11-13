package com.example.model

data class ActionCount(
  val actionName: String,
  val count: Int,
  val percentage: Float,
)

data class UserActionBreakdown(
  val userId: String,
  val totalCount: Int,
  val actionCounts: Map<String, Int>,
)

data class TimeBucketCount(
  val label: String,
  val count: Int,
)

data class AccessPatternReport(
  val fileName: String,
  val totalEvents: Int,
  val uniqueUsers: Int,
  val uniqueActions: Int,
  val topUser: UserActionBreakdown?,
  val topAction: ActionCount?,
  val actionTotals: List<ActionCount>,
  val userBreakdowns: List<UserActionBreakdown>,
  val timeBuckets: List<TimeBucketCount>,
  val summaryText: String,
)

object AccessReportGenerator {

  fun generate(document: ParsedJsonDocument): AccessPatternReport {
    val records = document.records
    if (records.isEmpty()) {
      return AccessPatternReport(
        fileName = document.fileName,
        totalEvents = 0,
        uniqueUsers = 0,
        uniqueActions = 0,
        topUser = null,
        topAction = null,
        actionTotals = emptyList(),
        userBreakdowns = emptyList(),
        timeBuckets = emptyList(),
        summaryText = "Access Pattern Report (${document.fileName})\nNo log records found.",
      )
    }

    val totalEvents = records.size
    val actionCountsMap = linkedMapOf<String, Int>()
    val userActionMap = linkedMapOf<String, LinkedHashMap<String, Int>>()
    val timeBucketMap = linkedMapOf<String, Int>()

    for (record in records) {
      val user =
        record.userId?.takeIf { it.isNotBlank() }
          ?: record.subtitle?.takeIf { it.isNotBlank() }
          ?: "unknown_user"
      val action =
        record.actionName?.takeIf { it.isNotBlank() }
          ?: record.statusOrAction?.takeIf { it.isNotBlank() }
          ?: "unspecified"

      actionCountsMap[action] = (actionCountsMap[action] ?: 0) + 1
      val perUser = userActionMap.getOrPut(user) { linkedMapOf() }
      perUser[action] = (perUser[action] ?: 0) + 1

      val bucket = extractTimeBucket(record.timestamp)
      if (bucket != null) {
        timeBucketMap[bucket] = (timeBucketMap[bucket] ?: 0) + 1
      }
    }

    val actionTotals =
      actionCountsMap.entries
        .sortedByDescending { it.value }
        .map { (action, count) ->
          ActionCount(
            actionName = action,
            count = count,
            percentage = (count.toFloat() / totalEvents.toFloat()) * 100f,
          )
        }

    val userBreakdowns =
      userActionMap.entries
        .map { (user, perAction) ->
          UserActionBreakdown(
            userId = user,
            totalCount = perAction.values.sum(),
            actionCounts = perAction.entries.sortedByDescending { it.value }.associate { it.toPair() },
          )
        }
        .sortedByDescending { it.totalCount }

    val timeBuckets =
      timeBucketMap.entries
        .sortedBy { it.key }
        .map { (label, count) -> TimeBucketCount(label = label, count = count) }

    val topUser = userBreakdowns.firstOrNull()
    val topAction = actionTotals.firstOrNull()

    val reportText = buildString {
      appendLine("Access Pattern Summary Report — ${document.fileName}")
      appendLine("Total Log Events: $totalEvents")
      appendLine("Unique Users: ${userBreakdowns.size}")
      appendLine("Distinct Actions: ${actionTotals.size}")
      if (topUser != null) {
        appendLine("Most Active User: ${topUser.userId} (${topUser.totalCount} events)")
      }
      if (topAction != null) {
        appendLine(
          "Top Action: ${topAction.actionName} (${topAction.count} events, ${"%.1f".format(topAction.percentage)}%)"
        )
      }
      appendLine()
      appendLine("Action Breakdown:")
      actionTotals.forEach { item ->
        appendLine("- ${item.actionName}: ${item.count} (${"%.1f".format(item.percentage)}%)")
      }
      appendLine()
      appendLine("Frequency of Actions per User:")
      userBreakdowns.take(15).forEach { user ->
        val actionsStr = user.actionCounts.entries.joinToString(", ") { "${it.key}=${it.value}" }
        appendLine("- ${user.userId} (${user.totalCount}): $actionsStr")
      }
    }

    return AccessPatternReport(
      fileName = document.fileName,
      totalEvents = totalEvents,
      uniqueUsers = userBreakdowns.size,
      uniqueActions = actionTotals.size,
      topUser = topUser,
      topAction = topAction,
      actionTotals = actionTotals,
      userBreakdowns = userBreakdowns,
      timeBuckets = timeBuckets,
      summaryText = reportText.trim(),
    )
  }

  private fun extractTimeBucket(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    val trimmed = timestamp.trim()
    // Match ISO date + hour if present: 2026-11-13T06... -> 11-13 06h
    val isoHour = Regex("""^\d{4}-(\d{2}-\d{2})[T ](\d{2})""").find(trimmed)
    if (isoHour != null) {
      return "${isoHour.groupValues[1]} ${isoHour.groupValues[2]}h"
    }
    // Match ISO date: 2026-11-13
    val isoDate = Regex("""^\d{4}-(\d{2}-\d{2})""").find(trimmed)
    if (isoDate != null) {
      return isoDate.groupValues[1]
    }
    return null
  }
}
