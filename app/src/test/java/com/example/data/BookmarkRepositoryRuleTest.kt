package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AccessRecord
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BookmarkRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun sampleRecord(index: Int = 1): AccessRecord =
    AccessRecord(
      index = index,
      sourceCollection = "history",
      title = "Field Notes",
      subtitle = "usr_alpha",
      timestamp = "2026-11-13T07:00:00Z",
      statusOrAction = "OPENED",
      userId = "usr_alpha",
      actionName = "OPENED",
      fields = listOf("appletName" to "Field Notes", "userId" to "usr_alpha", "action" to "OPENED"),
    )

  @Test
  fun saveAndObserveBookmarks_authenticatedOwner_persistsTagsAndUpdates() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BookmarkRepository(firestore)

    val saveResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveBookmark(
          userId = uid,
          sourceFile = "history_${System.currentTimeMillis()}.json",
          record = sampleRecord(),
          tags = listOf("Investigate", "Security"),
          note = "Initial bookmark note",
        )
      }
    assertTrue("Save should succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)
    val bookmarkId = saveResult.getOrThrow()

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeBookmarks(uid).first { list -> list.any { it.id == bookmarkId } }
      }
    val saved = observed.first { it.id == bookmarkId }
    assertEquals(listOf("Investigate", "Security"), saved.tags)
    assertEquals("Initial bookmark note", saved.note)

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateBookmarkTagsAndNote(
          userId = uid,
          bookmarkId = bookmarkId,
          tags = listOf("Reviewed"),
          note = "Confirmed expected export",
        )
      }
    assertTrue("Update should succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getBookmark(uid, bookmarkId) }
    assertNotNull(updated)
    assertEquals(listOf("Reviewed"), updated?.tags)
    assertEquals("Confirmed expected export", updated?.note)

    val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteBookmark(uid, bookmarkId) }
    assertTrue(deleteResult.isSuccess)
  }

  @Test
  fun getBookmark_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = BookmarkRepository(firestore)
    val bookmarkId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveBookmark(
            userId = aliceUid,
            sourceFile = "cross_user_${System.currentTimeMillis()}.json",
            record = sampleRecord(),
            tags = listOf("Anomaly"),
            note = "Private note",
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.getBookmark(aliceUid, bookmarkId) }
      fail("Expected PERMISSION_DENIED when Bob reads Alice's bookmark")
    } catch (e: Exception) {
      val firestoreError = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreError)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError?.code)
    }
  }

  @Test
  fun observeBookmarks_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = BookmarkRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeBookmarks(aliceUid).first() }
      fail("Expected PERMISSION_DENIED for unauthenticated listener")
    } catch (e: Exception) {
      val firestoreError = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreError)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_bookmarks@test.com"
    const val BOB_EMAIL = "bob_bookmarks@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
