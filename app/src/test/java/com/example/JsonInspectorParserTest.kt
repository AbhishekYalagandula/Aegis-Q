package com.example

import com.example.model.JsonInspectorParser
import com.example.model.JsonRootType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsonInspectorParserTest {

  @Test
  fun parseEmptyObject_detectsEmptyContainer() {
    val doc =
      JsonInspectorParser.parse(
        fileName = "applet_access_history_44136fa3.json",
        rawText = "{}",
        isBundledAttachment = true,
      )

    assertNull(doc.parseError)
    assertEquals(JsonRootType.OBJECT, doc.stats.rootType)
    assertTrue(doc.stats.isEmptyContainer)
    assertEquals(0, doc.stats.totalKeys)
    assertEquals(0, doc.records.size)
  }

  @Test
  fun parsePopulatedAccessHistory_extractsRecords() {
    val json =
      """
      {
        "history": [
          {
            "appletName": "Budget Tracker",
            "userEmail": "alex@example.com",
            "accessedAt": "2026-11-13T06:00:00Z",
            "action": "OPENED"
          },
          {
            "appletName": "Note Sync",
            "userEmail": "sam@example.com",
            "accessedAt": "2026-11-13T06:15:00Z",
            "action": "EDITED"
          }
        ]
      }
      """.trimIndent()

    val doc = JsonInspectorParser.parse(fileName = "history.json", rawText = json)

    assertNull(doc.parseError)
    assertEquals(JsonRootType.OBJECT, doc.stats.rootType)
    assertEquals(2, doc.records.size)
    assertEquals("Budget Tracker", doc.records[0].title)
    assertEquals("alex@example.com", doc.records[0].subtitle)
    assertEquals("2026-11-13T06:00:00Z", doc.records[0].timestamp)
    assertEquals("OPENED", doc.records[0].statusOrAction)
  }

  @Test
  fun parseMalformedJson_returnsParseError() {
    val doc = JsonInspectorParser.parse(fileName = "broken.json", rawText = "{invalid:")

    assertEquals(JsonRootType.INVALID, doc.stats.rootType)
    assertNotNull(doc.parseError)
  }

  @Test
  fun parseNdjsonLogFile_extractsAllEntries() {
    val ndjson =
      """
      {"appletName": "Auth Flow", "action": "LOGIN", "timestamp": "2026-11-13T06:00:00Z"}
      {"appletName": "Sync Engine", "action": "EXPORT", "timestamp": "2026-11-13T06:05:00Z"}
      """.trimIndent()

    val doc = JsonInspectorParser.parse(fileName = "events.jsonl", rawText = ndjson)

    assertNull(doc.parseError)
    assertEquals(JsonRootType.ARRAY, doc.stats.rootType)
    assertEquals(2, doc.records.size)
    assertEquals("Auth Flow", doc.records[0].title)
    assertEquals("EXPORT", doc.records[1].statusOrAction)
  }
}
