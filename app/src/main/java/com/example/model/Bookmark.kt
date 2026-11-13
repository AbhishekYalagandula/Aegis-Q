package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@IgnoreExtraProperties
data class Bookmark(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sourceFile: String = "",
  val recordKey: String = "",
  val recordIndex: Long = 0L,
  val title: String = "",
  val subtitle: String = "",
  val recordUserId: String = "",
  val actionName: String = "",
  val timestampText: String = "",
  val fieldsPreview: String = "",
  val tags: List<String> = emptyList(),
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

object BookmarkKeys {
  val PRESET_TAGS = listOf("Investigate", "Anomaly", "Follow-up", "Security", "Reviewed")

  fun recordKeyFor(sourceFile: String, record: AccessRecord): String {
    val fingerprint = buildString {
      append(sourceFile)
      append('|')
      append(record.sourceCollection)
      append('|')
      append(record.index)
      append('|')
      append(record.title)
      append('|')
      append(record.userId.orEmpty())
      append('|')
      append(record.actionName.orEmpty())
      append('|')
      append(record.timestamp.orEmpty())
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
    val hex = digest.take(12).joinToString("") { "%02x".format(it) }
    return "bm_$hex"
  }

  fun fieldsPreviewFor(record: AccessRecord): String {
    return record.fields
      .take(6)
      .joinToString(" • ") { (k, v) -> "$k: $v" }
      .take(1400)
  }
}
