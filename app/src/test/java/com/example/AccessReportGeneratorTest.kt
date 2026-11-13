package com.example

import com.example.model.AccessReportGenerator
import com.example.model.JsonInspectorParser
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
class AccessReportGeneratorTest {

  @Test
  fun generateReport_aggregatesActionFrequencyPerUser() {
    val json =
      """
      {
        "history": [
          {"userId": "alice", "action": "LOGIN", "timestamp": "2026-11-13T06:00:00Z"},
          {"userId": "alice", "action": "OPENED", "timestamp": "2026-11-13T06:10:00Z"},
          {"userId": "alice", "action": "OPENED", "timestamp": "2026-11-13T07:00:00Z"},
          {"userId": "bob", "action": "LOGIN", "timestamp": "2026-11-13T07:15:00Z"}
        ]
      }
      """.trimIndent()

    val doc = JsonInspectorParser.parse("access.json", json)
    val report = AccessReportGenerator.generate(doc)

    assertEquals(4, report.totalEvents)
    assertEquals(2, report.uniqueUsers)
    assertEquals(2, report.uniqueActions)
    assertEquals("alice", report.topUser?.userId)
    assertEquals(3, report.topUser?.totalCount)
    assertEquals(2, report.topUser?.actionCounts?.get("OPENED"))
    assertEquals(1, report.topUser?.actionCounts?.get("LOGIN"))
    assertEquals(2, report.timeBuckets.size)
    assertTrue(report.summaryText.contains("alice (3)"))
    assertTrue(report.summaryText.contains("OPENED=2"))
  }

  @Test
  fun generateReport_emptyJsonProducesZeroMetrics() {
    val doc = JsonInspectorParser.parse("empty.json", "{}")
    val report = AccessReportGenerator.generate(doc)

    assertEquals(0, report.totalEvents)
    assertEquals(0, report.uniqueUsers)
    assertNull(report.topUser)
    assertNotNull(report.summaryText)
  }
}
