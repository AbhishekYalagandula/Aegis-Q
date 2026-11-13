package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AccessRecord
import com.example.model.AccessReportGenerator
import com.example.model.JsonInspectorParser
import com.example.model.ParsedJsonDocument
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RecentLocalFile(
  val uriString: String,
  val displayName: String,
  val byteSize: Long,
  val openedAtMillis: Long,
)

class JsonSourceRepository(private val context: Context) {

  companion object {
    const val ATTACHED_ASSET_NAME = "applet_access_history_44136fa3.json"
    const val MAX_FILE_BYTES = 15L * 1024L * 1024L
    private const val PREFS_NAME = "access_log_recent_files"
    private const val KEY_RECENT_LIST = "recent_files_json"
    private const val MAX_RECENT_COUNT = 5
  }

  private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

  suspend fun loadBundledAttachment(): ParsedJsonDocument =
    withContext(Dispatchers.IO) {
      val content =
        context.assets.open(ATTACHED_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
      JsonInspectorParser.parse(
        fileName = ATTACHED_ASSET_NAME,
        rawText = content,
        isBundledAttachment = true,
      )
    }

  suspend fun loadFromUri(uri: Uri): Result<ParsedJsonDocument> =
    withContext(Dispatchers.IO) {
      runCatching {
        try {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        } catch (_: SecurityException) {
          // Some providers or test URIs do not support persistable permissions.
        } catch (_: UnsupportedOperationException) {
          // Ignore when provider does not support persistable grants.
        }

        val (displayName, reportedSize) = queryFileMetadata(uri)
        if (reportedSize != null && reportedSize > MAX_FILE_BYTES) {
          throw IllegalArgumentException(
            "File \"$displayName\" is too large (${reportedSize / (1024 * 1024)} MB). Maximum supported size is 15 MB."
          )
        }

        val bytes =
          context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0L
            while (true) {
              val read = input.read(buffer)
              if (read <= 0) break
              total += read
              if (total > MAX_FILE_BYTES) {
                throw IllegalArgumentException(
                  "File \"$displayName\" exceeds the 15 MB limit for local inspection."
                )
              }
              output.write(buffer, 0, read)
            }
            output.toByteArray()
          } ?: throw IllegalStateException("Unable to open selected file stream.")

        val sampleSize = bytes.size.coerceAtMost(1024)
        for (i in 0 until sampleSize) {
          if (bytes[i] == 0.toByte()) {
            throw IllegalArgumentException(
              "\"$displayName\" appears to be a binary file rather than UTF-8 JSON."
            )
          }
        }

        val text = bytes.toString(Charsets.UTF_8)
        val doc =
          JsonInspectorParser.parse(
            fileName = displayName,
            rawText = text,
            isBundledAttachment = false,
          )

        saveRecentFile(
          RecentLocalFile(
            uriString = uri.toString(),
            displayName = displayName,
            byteSize = bytes.size.toLong(),
            openedAtMillis = System.currentTimeMillis(),
          )
        )

        doc
      }
    }

  fun parsePastedJson(label: String, rawJson: String): ParsedJsonDocument {
    val cleanName = label.trim().ifEmpty { "pasted_payload.json" }
    return JsonInspectorParser.parse(
      fileName = cleanName,
      rawText = rawJson,
      isBundledAttachment = false,
    )
  }

  suspend fun saveTextToUri(uri: Uri, content: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val stream =
          runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open output stream for selected destination.")
        stream.use { out ->
          out.write(content.toByteArray(Charsets.UTF_8))
          out.flush()
        }
        queryFileMetadata(uri).first
      }
    }

  fun suggestedFormattedFileName(
    originalName: String,
    suffix: String = "formatted",
    extension: String = "json",
  ): String {
    val base =
      originalName
        .trim()
        .substringBeforeLast('.', missingDelimiterValue = originalName.trim())
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifEmpty { "access_log" }
    val cleanExt = extension.trim().trimStart('.').ifEmpty { "json" }
    return "${base}_${suffix}.${cleanExt}"
  }

  fun buildFilteredRecordsCsv(records: List<AccessRecord>): String {
    val fixedHeaders = listOf("index", "collection", "title", "user_id", "action_name", "timestamp")
    val fixedLower = fixedHeaders.mapTo(mutableSetOf()) { it.lowercase() }
    val extraKeys = linkedSetOf<String>()
    records.forEach { rec ->
      rec.fields.forEach { (key, _) ->
        if (key.isNotBlank() && key.lowercase() !in fixedLower) {
          extraKeys.add(key)
        }
      }
    }
    val allHeaders = fixedHeaders + extraKeys

    return buildString {
      append(allHeaders.joinToString(",") { escapeCsvCell(it) })
      append("\r\n")
      records.forEach { rec ->
        val fieldLookup = rec.fields.associate { (k, v) -> k to v }
        val rowValues =
          listOf(
            rec.index.toString(),
            rec.sourceCollection,
            rec.title,
            rec.userId.orEmpty(),
            rec.actionName ?: rec.statusOrAction.orEmpty(),
            rec.timestamp.orEmpty(),
          ) + extraKeys.map { key -> fieldLookup[key].orEmpty() }
        append(rowValues.joinToString(",") { escapeCsvCell(it) })
        append("\r\n")
      }
    }
  }

  private fun escapeCsvCell(raw: String): String {
    val safe =
      if (isFormulaInjectionRisk(raw)) {
        "'$raw"
      } else {
        raw
      }
    val needsQuotes =
      safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
        safe.startsWith(' ') ||
        safe.endsWith(' ')
    return if (needsQuotes) {
      "\"${safe.replace("\"", "\"\"")}\""
    } else {
      safe
    }
  }

  private fun isFormulaInjectionRisk(value: String): Boolean {
    if (value.isEmpty()) return false
    val first = value.first()
    if (first == '=' || first == '@' || first == '\t' || first == '\r') return true
    if (first == '+' || first == '-') {
      return value.toDoubleOrNull() == null
    }
    return false
  }

  fun buildProcessedReportJson(
    document: ParsedJsonDocument,
    records: List<AccessRecord>,
    filterQuery: String,
  ): String {
    val filteredDoc = document.copy(records = records)
    val report = AccessReportGenerator.generate(filteredDoc)
    val root =
      JSONObject().apply {
        put("sourceFile", document.fileName)
        put("rootType", document.stats.rootType.label)
        put("totalKeys", document.stats.totalKeys)
        put("maxDepth", document.stats.maxDepth)
        if (filterQuery.isNotBlank()) {
          put("activeFilterQuery", filterQuery.trim())
        }
        put(
          "accessSummary",
          JSONObject().apply {
            put("totalEvents", report.totalEvents)
            put("uniqueUsers", report.uniqueUsers)
            put("uniqueActions", report.uniqueActions)
            put(
              "actionTotals",
              JSONArray().apply {
                report.actionTotals.forEach { item ->
                  put(
                    JSONObject().apply {
                      put("actionName", item.actionName)
                      put("count", item.count)
                      put("percentage", (item.percentage * 10.0).roundToInt() / 10.0)
                    }
                  )
                }
              },
            )
            put(
              "actionsPerUser",
              JSONArray().apply {
                report.userBreakdowns.forEach { user ->
                  put(
                    JSONObject().apply {
                      put("userId", user.userId)
                      put("totalCount", user.totalCount)
                      put(
                        "actionCounts",
                        JSONObject().apply {
                          user.actionCounts.forEach { (action, count) -> put(action, count) }
                        },
                      )
                    }
                  )
                }
              },
            )
          },
        )
        put(
          "records",
          JSONArray().apply {
            records.forEach { record ->
              put(
                JSONObject().apply {
                  put("index", record.index)
                  put("collection", record.sourceCollection)
                  if (!record.userId.isNullOrBlank()) put("userId", record.userId)
                  if (!record.actionName.isNullOrBlank()) put("actionName", record.actionName)
                  if (!record.timestamp.isNullOrBlank()) put("timestamp", record.timestamp)
                  put(
                    "fields",
                    JSONObject().apply {
                      record.fields.forEach { (k, v) -> put(k, v) }
                    },
                  )
                }
              )
            }
          },
        )
      }
    return root.toString(2)
  }

  fun getRecentFiles(): List<RecentLocalFile> {
    val raw = prefs.getString(KEY_RECENT_LIST, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          val uriString = obj.optString("uriString")
          val displayName = obj.optString("displayName")
          if (uriString.isNotBlank() && displayName.isNotBlank()) {
            add(
              RecentLocalFile(
                uriString = uriString,
                displayName = displayName,
                byteSize = obj.optLong("byteSize", 0L),
                openedAtMillis = obj.optLong("openedAtMillis", 0L),
              )
            )
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun saveRecentFile(entry: RecentLocalFile) {
    val updated =
      (listOf(entry) + getRecentFiles().filterNot { it.uriString == entry.uriString })
        .take(MAX_RECENT_COUNT)
    val array = JSONArray()
    updated.forEach { item ->
      array.put(
        JSONObject().apply {
          put("uriString", item.uriString)
          put("displayName", item.displayName)
          put("byteSize", item.byteSize)
          put("openedAtMillis", item.openedAtMillis)
        }
      )
    }
    prefs.edit().putString(KEY_RECENT_LIST, array.toString()).apply()
  }

  private fun queryFileMetadata(uri: Uri): Pair<String, Long?> {
    var displayName: String? = null
    var fileSize: Long? = null
    try {
      context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
          }
        }
    } catch (_: Exception) {
      // Fallback to URI path segment if ContentProvider doesn't support OpenableColumns.
    }
    val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "selected_file.json"
    return resolvedName to fileSize
  }
}
