package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.JsonSourceRepository
import com.example.model.JsonInspectorParser
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsonSourceRepositoryTest {

  @Test
  fun loadFromUri_readsLocalJsonAndSavesRecentFile() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = JsonSourceRepository(context)
    val tempFile = File(context.cacheDir, "local_access_log.json")
    tempFile.writeText(
      """
      {
        "logs": [
          {
            "appletName": "Local Inspector",
            "userEmail": "tester@example.com",
            "accessedAt": "2026-11-13T06:35:00Z",
            "action": "LOADED"
          }
        ]
      }
      """.trimIndent()
    )

    val result = repository.loadFromUri(Uri.fromFile(tempFile))

    assertTrue(result.isSuccess)
    val doc = result.getOrThrow()
    assertEquals("local_access_log.json", doc.fileName)
    assertFalse(doc.isBundledAttachment)
    assertNull(doc.parseError)
    assertEquals(1, doc.records.size)
    assertEquals("Local Inspector", doc.records[0].title)

    val recents = repository.getRecentFiles()
    assertTrue(recents.any { it.displayName == "local_access_log.json" })
  }

  @Test
  fun loadFromUri_rejectsBinaryFileWithNullBytes() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = JsonSourceRepository(context)
    val binFile = File(context.cacheDir, "image.png")
    binFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x01, 0x02))

    val result = repository.loadFromUri(Uri.fromFile(binFile))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("binary", ignoreCase = true) == true)
  }

  @Test
  fun saveTextToUri_writesFormattedJsonAndSuggestedFileName() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = JsonSourceRepository(context)
    val suggested = repository.suggestedFormattedFileName("raw access.jsonl", "formatted")
    assertEquals("raw_access_formatted.json", suggested)

    val rawJson = """{"history":[{"userId":"usr_1","action":"OPENED"},{"userId":"usr_2","action":"EXPORT"}]}"""
    val doc = JsonInspectorParser.parse("raw.json", rawJson)
    val processedJson = repository.buildProcessedReportJson(doc, doc.records, filterQuery = "")

    val outFile = File(context.cacheDir, "exported_processed.json")
    val saveResult = repository.saveTextToUri(Uri.fromFile(outFile), processedJson)

    assertTrue(saveResult.isSuccess)
    val written = outFile.readText(Charsets.UTF_8)
    assertTrue(written.contains("\n  \"accessSummary\""))
    val parsedOut = JSONObject(written)
    assertEquals("raw.json", parsedOut.getString("sourceFile"))
    assertEquals(2, parsedOut.getJSONObject("accessSummary").getInt("totalEvents"))
    assertEquals(2, parsedOut.getJSONArray("records").length())
  }

  @Test
  fun buildFilteredRecordsCsv_escapesCsvCellsAndWritesValidFile() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = JsonSourceRepository(context)
    val csvName = repository.suggestedFormattedFileName("access.log.json", "filtered_logs", "csv")
    assertEquals("access.log_filtered_logs.csv", csvName)

    val rawJson =
      """
      {
        "history": [
          {
            "appletName": "Budget, \"Pro\"",
            "userId": "usr_1",
            "action": "OPENED",
            "timestamp": "2026-11-13T06:50:00Z",
            "note": "=SUM(A1:A2)",
            "delta": -5
          }
        ]
      }
      """.trimIndent()
    val doc = JsonInspectorParser.parse("logs.json", rawJson)
    val csv = repository.buildFilteredRecordsCsv(doc.records)

    assertTrue(csv.contains("index,collection,title,user_id,action_name,timestamp,appletName,userId,action,note,delta\r\n"))
    assertTrue(csv.contains("\"Budget, \"\"Pro\"\"\""))
    assertTrue(csv.contains("'=SUM(A1:A2)"))
    assertTrue(csv.contains(",-5\r\n"))

    val csvFile = File(context.cacheDir, "filtered_export.csv")
    val writeResult = repository.saveTextToUri(Uri.fromFile(csvFile), csv)
    assertTrue(writeResult.isSuccess)
    assertEquals(csv, csvFile.readText(Charsets.UTF_8))
  }
}
