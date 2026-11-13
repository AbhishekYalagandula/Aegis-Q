package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AccessRecord
import com.example.model.Bookmark
import com.example.model.BookmarkKeys
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BookmarkRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun observeBookmarks(userId: String): Flow<List<Bookmark>> {
    require(userId.isNotBlank()) { "Authenticated userId is required to observe bookmarks." }
    val colRef = db.collection("users").document(userId).collection("bookmarks")
    val path = colRef.path
    return colRef
      .orderBy("updatedAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(Bookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun getBookmark(userId: String, bookmarkId: String): Bookmark? {
    require(userId.isNotBlank()) { "Authenticated userId is required." }
    val docRef =
      db.collection("users").document(userId).collection("bookmarks").document(bookmarkId)
    return try {
      val snapshot = docRef.get().await()
      snapshot.toObject(Bookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun saveBookmark(
    userId: String,
    sourceFile: String,
    record: AccessRecord,
    tags: List<String>,
    note: String,
  ): Result<String> {
    require(userId.isNotBlank()) { "Authenticated userId is required to save bookmarks." }
    val recordKey = BookmarkKeys.recordKeyFor(sourceFile, record)
    val docRef =
      db.collection("users").document(userId).collection("bookmarks").document(recordKey)

    val cleanTags =
      tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(10)
    val cleanNote = note.trim().take(500)
    val cleanSource = sourceFile.trim().take(200)
    val cleanTitle = record.title.trim().ifEmpty { "Entry #${record.index}" }.take(200)
    val cleanSubtitle = record.subtitle.orEmpty().trim().take(300)
    val cleanRecordUser = record.userId.orEmpty().trim().take(200)
    val cleanAction = (record.actionName ?: record.statusOrAction).orEmpty().trim().take(120)
    val cleanTimestamp = record.timestamp.orEmpty().trim().take(120)
    val cleanPreview = BookmarkKeys.fieldsPreviewFor(record)

    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        val updates =
          mapOf(
            "sourceFile" to cleanSource,
            "recordIndex" to record.index.toLong().coerceAtLeast(0L),
            "title" to cleanTitle,
            "subtitle" to cleanSubtitle,
            "recordUserId" to cleanRecordUser,
            "actionName" to cleanAction,
            "timestampText" to cleanTimestamp,
            "fieldsPreview" to cleanPreview,
            "tags" to cleanTags,
            "note" to cleanNote,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.update(updates).await()
      } else {
        val payload =
          mapOf(
            "userId" to userId,
            "sourceFile" to cleanSource,
            "recordKey" to recordKey,
            "recordIndex" to record.index.toLong().coerceAtLeast(0L),
            "title" to cleanTitle,
            "subtitle" to cleanSubtitle,
            "recordUserId" to cleanRecordUser,
            "actionName" to cleanAction,
            "timestampText" to cleanTimestamp,
            "fieldsPreview" to cleanPreview,
            "tags" to cleanTags,
            "note" to cleanNote,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      }
      Result.success(recordKey)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val op = OperationType.WRITE
      handleFirestoreError(e, op, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateBookmarkTagsAndNote(
    userId: String,
    bookmarkId: String,
    tags: List<String>,
    note: String,
  ): Result<Unit> {
    require(userId.isNotBlank()) { "Authenticated userId is required." }
    require(bookmarkId.isNotBlank()) { "Bookmark ID is required." }
    val docRef =
      db.collection("users").document(userId).collection("bookmarks").document(bookmarkId)
    val cleanTags =
      tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(10)
    val cleanNote = note.trim().take(500)
    return try {
      docRef
        .update(
          mapOf(
            "tags" to cleanTags,
            "note" to cleanNote,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteBookmark(userId: String, bookmarkId: String): Result<Unit> {
    require(userId.isNotBlank()) { "Authenticated userId is required." }
    require(bookmarkId.isNotBlank()) { "Bookmark ID is required." }
    val docRef =
      db.collection("users").document(userId).collection("bookmarks").document(bookmarkId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }
}
